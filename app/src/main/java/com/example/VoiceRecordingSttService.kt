package com.example

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * VoiceRecordingSttService provides continuous real-time voice transcription
 * using Android's SpeechRecognizer API (with On-Device recognition support on Android 12+),
 * combined with seamless Gemini multimodal audio fallback when the system recognizer is unavailable.
 */
class VoiceRecordingSttService(private val context: Context) {

    private val tag = "VoiceRecordingStt"

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionIntent: Intent? = null
    private var audioRecord: AudioRecord? = null
    private var isRecordingActive = false
    private var restartJob: Job? = null
    private var consecutiveSpeechErrors = 0
    private var fallbackToPcmActive = false

    // Session transcribed text buffers
    private val committedTranscript = StringBuilder()
    private var currentSegmentPartial = ""

    // PCM Audio capture buffer for fallback transcription (Gemini API)
    private val pcmAudioBuffer = ByteArrayOutputStream()
    private var maxObservedAudioRms = 0.0

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript

    /**
     * Starts continuous microphone audio capture and real-time speech-to-text recognition.
     */
    fun startRecording(
        scope: CoroutineScope,
        onPartialText: (String) -> Unit,
        onLevelChange: (Float) -> Unit
    ) {
        if (!MicrophonePermissionHelper.hasMicrophonePermission(context)) {
            Log.e(tag, "Cannot start recording: RECORD_AUDIO permission missing.")
            return
        }

        committedTranscript.setLength(0)
        currentSegmentPartial = ""
        _isRecording.value = true
        _currentTranscript.value = ""
        _audioLevel.value = 0f
        isRecordingActive = true
        consecutiveSpeechErrors = 0
        fallbackToPcmActive = false
        pcmAudioBuffer.reset()
        maxObservedAudioRms = 0.0

        val appContext = context.applicationContext ?: context
        val isSysRecognizerAvailable = try {
            SpeechRecognizer.isRecognitionAvailable(appContext)
        } catch (e: Exception) {
            false
        }

        if (isSysRecognizerAvailable) {
            startSpeechRecognizerEngine(scope, onPartialText, onLevelChange)
        } else {
            Log.i(tag, "System SpeechRecognizer not available. Starting AudioRecord + Gemini Cloud STT fallback pipeline.")
            startPcmAudioPipeline(scope, onPartialText, onLevelChange)
        }
    }

    /**
     * Cancels the current recording session immediately and discards buffered audio/text.
     */
    fun cancelRecording() {
        isRecordingActive = false
        _isRecording.value = false
        restartJob?.cancel()

        stopPcmAudioTracking()
        destroySpeechRecognizer()

        committedTranscript.setLength(0)
        currentSegmentPartial = ""
        _currentTranscript.value = ""
        _audioLevel.value = 0f
        pcmAudioBuffer.reset()
    }

    /**
     * Stops microphone recording and returns polished/raw spoken transcript.
     * If SpeechRecognizer produced text, returns it formatted.
     * If SpeechRecognizer produced no text but audio was recorded, transcribes via Gemini API.
     */
    fun stopRecording(
        scope: CoroutineScope,
        shouldPolish: Boolean = false,
        onFinalTranscript: (String) -> Unit
    ) {
        isRecordingActive = false
        _isRecording.value = false
        restartJob?.cancel()

        stopPcmAudioTracking()
        destroySpeechRecognizer()

        val fullRaw = formatWhisperFlowText(getFullStreamingText())
        val capturedPcm = synchronized(pcmAudioBuffer) { pcmAudioBuffer.toByteArray() }

        // If system recognizer transcribed text, use it immediately
        if (fullRaw.isNotBlank()) {
            if (shouldPolish) {
                scope.launch {
                    val polishedText = WhisperCppBrain.whisperCleanAndPolish(fullRaw)
                    _currentTranscript.value = polishedText
                    onFinalTranscript(polishedText)
                }
            } else {
                onFinalTranscript(fullRaw)
            }
            return
        }

        // If recognizer had no text, but we captured microphone audio (PCM fallback)
        if (capturedPcm.size > 3200 && maxObservedAudioRms > 500.0) {
            scope.launch(Dispatchers.IO) {
                try {
                    val wavData = GeminiApiClient.pcmToWav(capturedPcm, sampleRate = 16000, channels = 1)
                    val cloudTranscript = GeminiApiClient.transcribeAudio(wavData)
                    if (!cloudTranscript.isNullOrBlank()) {
                        val cleaned = formatWhisperFlowText(cloudTranscript)
                        withContext(Dispatchers.Main) {
                            _currentTranscript.value = cleaned
                            if (shouldPolish) {
                                onFinalTranscript(WhisperCppBrain.whisperCleanAndPolish(cleaned))
                            } else {
                                onFinalTranscript(cleaned)
                            }
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Gemini audio transcription fallback failed: ${e.message}")
                }

                // If cloud STT not available and no speech text
                withContext(Dispatchers.Main) {
                    onFinalTranscript("")
                }
            }
        } else {
            onFinalTranscript("")
        }
    }

    private fun getFullStreamingText(): String {
        val committed = committedTranscript.toString().trim()
        val partial = currentSegmentPartial.trim()
        return when {
            committed.isNotEmpty() && partial.isNotEmpty() -> "$committed $partial"
            committed.isNotEmpty() -> committed
            else -> partial
        }
    }

    private fun startSpeechRecognizerEngine(
        scope: CoroutineScope,
        onPartialText: (String) -> Unit,
        onLevelChange: (Float) -> Unit
    ) {
        if (!isRecordingActive) return

        scope.launch(Dispatchers.Main) {
            if (!isRecordingActive) return@launch

            val appContext = context.applicationContext ?: context

            try {
                if (speechRecognizer == null) {
                    speechRecognizer = createOptimalSpeechRecognizer(appContext).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                Log.d(tag, "SpeechRecognizer ready for speech.")
                                consecutiveSpeechErrors = 0
                            }

                            override fun onBeginningOfSpeech() {
                                Log.d(tag, "SpeechRecognizer beginning of speech detected.")
                            }

                            override fun onRmsChanged(rmsdB: Float) {
                                val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                                _audioLevel.value = level
                                onLevelChange(level)
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {
                                if (buffer != null && buffer.isNotEmpty()) {
                                    synchronized(pcmAudioBuffer) {
                                        pcmAudioBuffer.write(buffer)
                                    }
                                }
                            }

                            override fun onEndOfSpeech() {
                                Log.d(tag, "SpeechRecognizer segment speech ended.")
                            }

                            override fun onError(error: Int) {
                                Log.d(tag, "SpeechRecognizer non-fatal status/error code: $error")
                                if (!isRecordingActive) return

                                // If partial speech was pending, commit it before resetting
                                if (currentSegmentPartial.isNotBlank()) {
                                    if (committedTranscript.isNotEmpty()) {
                                        committedTranscript.append(" ")
                                    }
                                    committedTranscript.append(currentSegmentPartial)
                                    currentSegmentPartial = ""
                                    val fullFormatted = formatWhisperFlowText(committedTranscript.toString())
                                    _currentTranscript.value = fullFormatted
                                    onPartialText(fullFormatted)
                                }

                                when (error) {
                                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                        Log.e(tag, "Fatal mic permission error")
                                        isRecordingActive = false
                                        _isRecording.value = false
                                    }
                                    SpeechRecognizer.ERROR_NO_MATCH,
                                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                        // Natural pause in speech: softly restart listening
                                        consecutiveSpeechErrors = 0
                                        restartContinuousListening(scope, onPartialText, onLevelChange, delayMs = 80, recreate = false)
                                    }
                                    else -> {
                                        consecutiveSpeechErrors++
                                        if (consecutiveSpeechErrors >= 2) {
                                            Log.w(tag, "Repeated SpeechRecognizer errors ($error). Switching to direct PCM + Gemini STT pipeline.")
                                            destroySpeechRecognizer()
                                            startPcmAudioPipeline(scope, onPartialText, onLevelChange)
                                        } else {
                                            restartContinuousListening(scope, onPartialText, onLevelChange, delayMs = 150, recreate = true)
                                        }
                                    }
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                consecutiveSpeechErrors = 0
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val segment = matches?.firstOrNull()?.trim() ?: ""

                                if (segment.isNotEmpty()) {
                                    if (committedTranscript.isNotEmpty()) {
                                        committedTranscript.append(" ")
                                    }
                                    committedTranscript.append(segment)
                                }
                                currentSegmentPartial = ""

                                val fullFormatted = formatWhisperFlowText(committedTranscript.toString())
                                _currentTranscript.value = fullFormatted
                                onPartialText(fullFormatted)

                                // Continuous loop: instantly restart listening for subsequent speech
                                if (isRecordingActive) {
                                    restartContinuousListening(scope, onPartialText, onLevelChange, delayMs = 40)
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val partial = matches?.firstOrNull()?.trim() ?: ""
                                if (partial.isNotEmpty()) {
                                    currentSegmentPartial = partial
                                    val fullFormatted = formatWhisperFlowText(getFullStreamingText())
                                    _currentTranscript.value = fullFormatted
                                    onPartialText(fullFormatted)
                                }
                            }

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                }

                if (recognitionIntent == null) {
                    val defaultLang = try {
                        val loc = Locale.getDefault()
                        val tag = loc.toLanguageTag()
                        if (tag.isNotBlank()) tag else "en-US"
                    } catch (_: Exception) { "en-US" }

                    recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, defaultLang)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
                    }
                }

                recognitionIntent?.let { speechRecognizer?.startListening(it) }

            } catch (e: Exception) {
                Log.e(tag, "SpeechRecognizer exception, switching to PCM fallback: ${e.message}")
                destroySpeechRecognizer()
                startPcmAudioPipeline(scope, onPartialText, onLevelChange)
            }
        }
    }

    private fun restartContinuousListening(
        scope: CoroutineScope,
        onPartialText: (String) -> Unit,
        onLevelChange: (Float) -> Unit,
        delayMs: Long,
        recreate: Boolean = false
    ) {
        if (!isRecordingActive || fallbackToPcmActive) return
        restartJob?.cancel()
        restartJob = scope.launch(Dispatchers.Main) {
            if (!isRecordingActive || fallbackToPcmActive) return@launch
            if (delayMs > 0) delay(delayMs)
            if (!isRecordingActive || fallbackToPcmActive) return@launch

            try {
                if (recreate) {
                    destroySpeechRecognizer()
                } else {
                    try {
                        speechRecognizer?.cancel()
                    } catch (_: Exception) {}
                }
                startSpeechRecognizerEngine(scope, onPartialText, onLevelChange)
            } catch (e: Exception) {
                Log.w(tag, "Failed to restart continuous listening: ${e.message}")
            }
        }
    }

    private fun createOptimalSpeechRecognizer(ctx: Context): SpeechRecognizer {
        // Android 12+ (API 31+): check and prefer on-device recognition
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
                    Log.i(tag, "Using Android On-Device SpeechRecognizer")
                    return SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
                }
            } catch (e: Exception) {
                Log.w(tag, "On-device speech recognizer init check failed: ${e.message}")
            }
        }
        return SpeechRecognizer.createSpeechRecognizer(ctx)
    }

    /**
     * Fallback audio capture pipeline using direct PCM AudioRecord.
     * Records audio bytes while computing live RMS levels for UI waveform display,
     * and sends audio to Gemini Cloud STT when recording finishes.
     */
    private fun startPcmAudioPipeline(
        scope: CoroutineScope,
        onPartialText: (String) -> Unit,
        onLevelChange: (Float) -> Unit
    ) {
        fallbackToPcmActive = true
        destroySpeechRecognizer()

        try {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = minBufferSize.coerceAtLeast(3200)

            if (!MicrophonePermissionHelper.hasMicrophonePermission(context)) {
                Log.e(tag, "PCM fallback cannot start: RECORD_AUDIO not granted")
                return
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord failed to initialize")
                audioRecord = null
                return
            }

            audioRecord?.startRecording()

            scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(bufferSize)
                while (isRecordingActive) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readBytes > 0) {
                        synchronized(pcmAudioBuffer) {
                            pcmAudioBuffer.write(buffer, 0, readBytes)
                        }

                        // Calculate RMS level from 16-bit PCM samples
                        var sum = 0.0
                        val sampleCount = readBytes / 2
                        for (i in 0 until sampleCount) {
                            val sample = (buffer[i * 2].toInt() and 0xFF) or (buffer[i * 2 + 1].toInt() shl 8)
                            val sampleShort = sample.toShort()
                            sum += sampleShort * sampleShort
                        }
                        val rms = if (sampleCount > 0) Math.sqrt(sum / sampleCount) else 0.0
                        if (rms > maxObservedAudioRms) {
                            maxObservedAudioRms = rms
                        }

                        val normalizedLevel = ((rms - 300.0) / 4000.0).toFloat().coerceIn(0f, 1f)
                        _audioLevel.value = normalizedLevel
                        withContext(Dispatchers.Main) {
                            onLevelChange(normalizedLevel)
                        }
                    }
                    delay(30)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start PCM AudioRecord fallback", e)
        }
    }

    private fun stopPcmAudioTracking() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(tag, "AudioRecord cleanup exception", e)
        } finally {
            audioRecord = null
        }
    }

    private fun destroySpeechRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
            recognitionIntent = null
        }
    }

    /**
     * Real-time WhisperFlow formatting: converts spoken punctuation words,
     * numbers, currencies, and ensures proper capitalization at start of sentences.
     */
    private fun formatWhisperFlowText(raw: String): String {
        if (raw.isBlank()) return ""
        return VoiceTranscriptionFormatter.formatTranscription(raw, TranscriptionFormatStyle.SMART_CLEAN)
    }
}
