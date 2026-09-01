package com.example

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
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

/**
 * VoiceRecordingSttService provides WhisperFlow-style continuous real-time voice transcription
 * using the Android SpeechRecognizer API and Whisper formatting engine.
 *
 * It maintains a continuous streaming session like Gboard, handles pauses and silence smoothly
 * without disconnecting or playing restart tones, and applies real-time punctuation and
 * capitalization formatting.
 */
class VoiceRecordingSttService(private val context: Context) {

    private val tag = "VoiceRecordingStt"

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionIntent: Intent? = null
    private var audioRecord: AudioRecord? = null
    private var isRecordingActive = false
    private var restartJob: Job? = null

    // Session transcribed text buffers
    private val committedTranscript = StringBuilder()
    private var currentSegmentPartial = ""

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript

    private var isMutedForVoice = false

    private val audioStreamsToMute = intArrayOf(
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_NOTIFICATION
    )

    private fun muteVoiceSystemSounds() {
        if (isMutedForVoice) return
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                for (stream in audioStreamsToMute) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                    } else {
                        @Suppress("DEPRECATION")
                        am.setStreamMute(stream, true)
                    }
                }
                isMutedForVoice = true
            }
        } catch (e: Exception) {
            Log.e(tag, "Error muting system cues: ${e.message}")
        }
    }

    private fun restoreVoiceSystemSounds() {
        if (!isMutedForVoice) return
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                for (stream in audioStreamsToMute) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                    } else {
                        @Suppress("DEPRECATION")
                        am.setStreamMute(stream, false)
                    }
                }
                isMutedForVoice = false
            }
        } catch (e: Exception) {
            Log.e(tag, "Error restoring system sounds: ${e.message}")
        }
    }

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

        muteVoiceSystemSounds()
        committedTranscript.setLength(0)
        currentSegmentPartial = ""
        _isRecording.value = true
        _currentTranscript.value = ""
        _audioLevel.value = 0f
        isRecordingActive = true

        startSpeechRecognizerEngine(scope, onPartialText, onLevelChange)
    }

    /**
     * Cancels the current recording session immediately and discards buffered audio/text.
     */
    fun cancelRecording() {
        isRecordingActive = false
        _isRecording.value = false
        restartJob?.cancel()

        stopPcmAudioLevelTracking()
        destroySpeechRecognizer()
        restoreVoiceSystemSounds()

        committedTranscript.setLength(0)
        currentSegmentPartial = ""
        _currentTranscript.value = ""
        _audioLevel.value = 0f
    }

    /**
     * Stops microphone recording and returns polished/raw spoken transcript.
     */
    fun stopRecording(
        scope: CoroutineScope,
        shouldPolish: Boolean = true,
        onFinalTranscript: (String) -> Unit
    ) {
        isRecordingActive = false
        _isRecording.value = false
        restartJob?.cancel()

        stopPcmAudioLevelTracking()
        destroySpeechRecognizer()
        restoreVoiceSystemSounds()

        val fullRaw = formatWhisperFlowText(getFullStreamingText())
        if (fullRaw.isEmpty()) {
            onFinalTranscript("")
            return
        }

        if (shouldPolish) {
            scope.launch {
                val polishedText = WhisperCppBrain.whisperCleanAndPolish(fullRaw)
                _currentTranscript.value = polishedText
                onFinalTranscript(polishedText)
            }
        } else {
            onFinalTranscript(fullRaw)
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

            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.w(tag, "SpeechRecognizer not available on device, initializing audio level fallback.")
                    startFallbackEngine(scope, onLevelChange)
                    return@launch
                }

                if (speechRecognizer == null) {
                    speechRecognizer = createOptimalSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                Log.d(tag, "SpeechRecognizer ready for speech.")
                            }

                            override fun onBeginningOfSpeech() {
                                Log.d(tag, "SpeechRecognizer beginning of speech detected.")
                            }

                            override fun onRmsChanged(rmsdB: Float) {
                                val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                                _audioLevel.value = level
                                onLevelChange(level)
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                Log.d(tag, "SpeechRecognizer segment speech ended.")
                            }

                            override fun onError(error: Int) {
                                Log.d(tag, "SpeechRecognizer non-fatal status/error code: $error")
                                if (!isRecordingActive) return

                                // Handle silence, pauses, or client busy smoothly without stopping continuous flow
                                when (error) {
                                    SpeechRecognizer.ERROR_NO_MATCH,
                                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                                    SpeechRecognizer.ERROR_CLIENT,
                                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                                        // User was silent or pause occurred; silently restart recognition loop
                                        restartContinuousListening(scope, onPartialText, onLevelChange, delayMs = 60)
                                    }
                                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                        Log.e(tag, "Fatal mic permission error")
                                        isRecordingActive = false
                                        _isRecording.value = false
                                    }
                                    else -> {
                                        // Transient recognition error; recreate and resume continuous listening
                                        restartContinuousListening(scope, onPartialText, onLevelChange, delayMs = 120, recreate = true)
                                    }
                                }
                            }

                            override fun onResults(results: Bundle?) {
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
                                    restartContinuousListening(scope, onPartialText, onLevelChange, delayMs = 30)
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
                    recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        // Extended silence windows to allow natural pauses while speaking
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                        putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 10000L)
                        putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 10000L)
                        putExtra("android.speech.extra.DICTATION_MODE", true)
                        putExtra("android.speech.extra.SEGMENTED_SESSION", "continuous")
                        putExtra("android.speech.extra.AUDIO_CUE", false)
                        putExtra("android.speech.extra.DISABLE_AUDIO_CUE", true)
                        putExtra("android.speech.extra.SUPPRESS_START_STOP_AUDIO_FEEDBACK", true)
                    }
                }

                recognitionIntent?.let { speechRecognizer?.startListening(it) }

            } catch (e: Exception) {
                Log.e(tag, "SpeechRecognizer exception, continuing with fallback", e)
                startFallbackEngine(scope, onLevelChange)
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
        if (!isRecordingActive) return
        restartJob?.cancel()
        restartJob = scope.launch(Dispatchers.Main) {
            if (!isRecordingActive) return@launch
            if (delayMs > 0) delay(delayMs)
            if (!isRecordingActive) return@launch

            try {
                if (recreate) {
                    try {
                        speechRecognizer?.cancel()
                        speechRecognizer?.destroy()
                    } catch (_: Exception) {}
                    speechRecognizer = null
                }
                startSpeechRecognizerEngine(scope, onPartialText, onLevelChange)
            } catch (e: Exception) {
                Log.w(tag, "Failed to restart continuous listening: ${e.message}")
            }
        }
    }

    private fun createOptimalSpeechRecognizer(ctx: Context): SpeechRecognizer {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        } else {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        }
    }

    /**
     * Real-time WhisperFlow formatting: converts spoken punctuation words (e.g. "period", "comma", "new line"),
     * ensures proper capitalization at start of sentences, and strips excess duplicate whitespace.
     */
    private fun formatWhisperFlowText(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw

        // Real-time spoken punctuation replacements
        val punctuationReplacements = listOf(
            Regex("(?i)\\bperiod\\b") to ".",
            Regex("(?i)\\bfull stop\\b") to ".",
            Regex("(?i)\\bcomma\\b") to ",",
            Regex("(?i)\\bquestion mark\\b") to "?",
            Regex("(?i)\\bexclamation mark\\b") to "!",
            Regex("(?i)\\bexclamation point\\b") to "!",
            Regex("(?i)\\bnew line\\b") to "\n",
            Regex("(?i)\\bcolon\\b") to ":",
            Regex("(?i)\\bsemicolon\\b") to ";"
        )

        for ((pattern, replacement) in punctuationReplacements) {
            text = text.replace(pattern, replacement)
        }

        // Clean up spaces before punctuation
        text = text.replace(Regex("\\s+([.,?!:;])"), "$1")
        // Ensure single space after punctuation (except newline)
        text = text.replace(Regex("([.,?!:;])([A-Za-z0-9])"), "$1 $2")

        // Auto-capitalize first character and characters after sentence endings
        val chars = text.toCharArray()
        var capitalizeNext = true
        for (i in chars.indices) {
            val c = chars[i]
            if (capitalizeNext && c.isLetter()) {
                chars[i] = c.uppercaseChar()
                capitalizeNext = false
            } else if (c == '.' || c == '?' || c == '!' || c == '\n') {
                capitalizeNext = true
            }
        }

        return String(chars).trim()
    }

    private fun destroySpeechRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore
        } finally {
            speechRecognizer = null
            recognitionIntent = null
        }
    }

    private fun startFallbackEngine(
        scope: CoroutineScope,
        onLevelChange: (Float) -> Unit
    ) {
        startPcmAudioLevelTracking(scope, onLevelChange)
    }

    private fun startPcmAudioLevelTracking(scope: CoroutineScope, onLevelChange: (Float) -> Unit) {
        try {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            if (MicrophonePermissionHelper.hasMicrophonePermission(context)) {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                audioRecord?.startRecording()

                scope.launch(Dispatchers.IO) {
                    val buffer = ShortArray(bufferSize)
                    while (isRecordingActive) {
                        val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readSize > 0) {
                            var sum = 0.0
                            for (i in 0 until readSize) {
                                sum += buffer[i] * buffer[i]
                            }
                            val rms = Math.sqrt(sum / readSize)
                            val normalizedLevel = (rms / 12000.0).toFloat().coerceIn(0f, 1f)
                            _audioLevel.value = normalizedLevel
                            scope.launch(Dispatchers.Main) {
                                onLevelChange(normalizedLevel)
                            }
                        }
                        delay(50)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start AudioRecord level tracking", e)
        }
    }

    private fun stopPcmAudioLevelTracking() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(tag, "AudioRecord cleanup exception", e)
        } finally {
            audioRecord = null
        }
    }
}

