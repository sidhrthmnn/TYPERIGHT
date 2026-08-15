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
import kotlin.random.Random

/**
 * VoiceRecordingSttService handles real-time microphone audio capture and integrates
 * with Android SpeechRecognizer and Gemini Nano on-device AI for dictation,
 * stutter removal, filler filtering, and sentence formatting.
 */
class VoiceRecordingSttService(private val context: Context) {

    private val tag = "VoiceRecordingStt"

    companion object {
        private const val NOISE_GATE_THRESHOLD = 0.08f // Minimum RMS level required to qualify as intentional speech vs noise
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isRecordingActive = false
    private var fallbackJob: Job? = null
    private var hasSpeechCrossedNoiseGate = false

    private val accumulatedText = StringBuilder()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript

    private var speechRecognizerErrorCount = 0
    private var isMutedForVoice = false

    private val audioStreamsToMute = intArrayOf(
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_DTMF,
        AudioManager.STREAM_VOICE_CALL
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
            Log.e(tag, "Error muting voice system sounds: ${e.message}")
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
            Log.e(tag, "Error restoring voice system sounds: ${e.message}")
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
        accumulatedText.setLength(0)
        _isRecording.value = true
        _currentTranscript.value = ""
        _audioLevel.value = 0f
        isRecordingActive = true
        speechRecognizerErrorCount = 0
        hasSpeechCrossedNoiseGate = false

        // Start SpeechRecognizer directly. It handles hardware microphone access exclusively.
        startSpeechRecognizerEngine(scope, onPartialText, onLevelChange)
    }

    /**
     * Stops microphone recording and returns raw spoken transcript.
     */
    fun stopRecording(
        scope: CoroutineScope,
        shouldPolish: Boolean = true,
        onFinalTranscript: (String) -> Unit
    ) {
        isRecordingActive = false
        _isRecording.value = false
        fallbackJob?.cancel()

        stopPcmAudioLevelTracking()
        destroySpeechRecognizer()
        restoreVoiceSystemSounds()

        val rawText = accumulatedText.toString().trim()
        if (rawText.isEmpty()) {
            onFinalTranscript("")
            return
        }

        if (shouldPolish) {
            scope.launch {
                val polishedText = GeminiNanoManager.proofreadAndCleanVoiceText(rawText)
                _currentTranscript.value = polishedText
                onFinalTranscript(polishedText)
            }
        } else {
            onFinalTranscript(rawText)
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
                    Log.w(tag, "SpeechRecognizer not available on device, initializing fallback voice engine.")
                    startFallbackEngine(scope, onPartialText, onLevelChange)
                    return@launch
                }

                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
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
                                if (level >= NOISE_GATE_THRESHOLD) {
                                    hasSpeechCrossedNoiseGate = true
                                }
                                onLevelChange(level)
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                Log.d(tag, "SpeechRecognizer end of speech.")
                            }

                            override fun onError(error: Int) {
                                Log.w(tag, "SpeechRecognizer error code: $error")
                                speechRecognizerErrorCount++
                                if (isRecordingActive) {
                                    destroySpeechRecognizer()
                                    if (speechRecognizerErrorCount >= 3) {
                                        Log.w(tag, "Multiple SpeechRecognizer errors ($speechRecognizerErrorCount), switching to fallback voice engine.")
                                        startFallbackEngine(scope, onPartialText, onLevelChange)
                                    } else {
                                        scope.launch(Dispatchers.Main) {
                                            delay(300)
                                            if (isRecordingActive) {
                                                startSpeechRecognizerEngine(scope, onPartialText, onLevelChange)
                                            }
                                        }
                                    }
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                speechRecognizerErrorCount = 0
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val transcript = matches?.firstOrNull()?.trim() ?: ""
                                
                                // Noise Gate Check: Drop transcription if audio stayed below speech threshold
                                if (transcript.isNotEmpty() && hasSpeechCrossedNoiseGate) {
                                    if (accumulatedText.isNotEmpty()) {
                                        accumulatedText.append(" ")
                                    }
                                    accumulatedText.append(transcript)
                                }
                                val currentCombined = accumulatedText.toString()
                                _currentTranscript.value = currentCombined
                                onPartialText(currentCombined)

                                // Reset noise gate for next speech frame
                                hasSpeechCrossedNoiseGate = false

                                // Loop continuous listening while voice dictation is active
                                if (isRecordingActive) {
                                    scope.launch(Dispatchers.Main) {
                                        delay(150)
                                        if (isRecordingActive) {
                                            startSpeechRecognizerEngine(scope, onPartialText, onLevelChange)
                                        }
                                    }
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                if (!hasSpeechCrossedNoiseGate) return
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val partial = matches?.firstOrNull()?.trim() ?: ""
                                if (partial.isNotEmpty()) {
                                    val prefix = if (accumulatedText.isNotEmpty()) "$accumulatedText " else ""
                                    val fullPartial = prefix + partial
                                    _currentTranscript.value = fullPartial
                                    onPartialText(fullPartial)
                                }
                            }

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra("android.speech.extra.AUDIO_CUE", false)
                    putExtra("android.speech.extra.DISABLE_AUDIO_CUE", true)
                    putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 2000L)
                    putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 2000L)
                }
                speechRecognizer?.startListening(intent)

            } catch (e: Exception) {
                Log.e(tag, "SpeechRecognizer exception, starting fallback", e)
                startFallbackEngine(scope, onPartialText, onLevelChange)
            }
        }
    }

    private fun destroySpeechRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore
        } finally {
            speechRecognizer = null
            restoreVoiceSystemSounds()
        }
    }

    private fun startFallbackEngine(
        scope: CoroutineScope,
        onPartialText: (String) -> Unit,
        onLevelChange: (Float) -> Unit
    ) {
        startPcmAudioLevelTracking(scope, onLevelChange)

        fallbackJob?.cancel()
        fallbackJob = scope.launch {
            // Do not insert mock/random transcription phrases.
            // When real SpeechRecognizer is unavailable or silent, keep transcript as spoken.
        }
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
