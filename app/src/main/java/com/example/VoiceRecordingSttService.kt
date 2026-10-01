package com.example

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** Continuous dictation. All recognizer calls and callbacks stay on the main thread. */
class VoiceRecordingSttService(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var session = 0L
    private var listening = false
    private var finishing = false
    private val _isFinishing = MutableStateFlow(false)
    val isFinishing: StateFlow<Boolean> = _isFinishing
    private var restartJob: Job? = null
    private var finishJob: Job? = null
    private var errors = 0
    private val transcript = VoiceTranscriptBuffer()
    private var lastPublished = ""
    private var publish: (String) -> Unit = {}
    private var levels: (Float) -> Unit = {}
    private var reportError: (String) -> Unit = {}
    private var finalResult: ((String) -> Unit)? = null
    private var sessionScope: CoroutineScope? = null
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel
    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    @MainThread
    fun startRecording(scope: CoroutineScope, onPartialText: (String) -> Unit,
                       onLevelChange: (Float) -> Unit, onError: (String) -> Unit = {}) {
        cancelRecording()
        publish = onPartialText
        levels = onLevelChange
        reportError = onError
        sessionScope = scope
        _error.value = null
        if (!MicrophonePermissionHelper.hasMicrophonePermission(context)) {
            fail("Allow microphone access to use voice input.")
            return
        }
        val app = context.applicationContext
        val available = SpeechRecognizer.isRecognitionAvailable(app) ||
            (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(app))
        if (!available) {
            fail("No speech service is available. Install or enable a speech recognition service.")
            return
        }
        listening = true
        _isRecording.value = true
        errors = 0
        beginSegment(session)
    }

    @MainThread
    fun cancelRecording() {
        session++ // Reject callbacks from an old editor, cancelled session, or recognizer.
        listening = false
        finishing = false
        _isFinishing.value = false
        restartJob?.cancel()
        finishJob?.cancel()
        finalResult = null
        destroyRecognizer()
        transcript.clear()
        lastPublished = ""
        _currentTranscript.value = ""
        _isRecording.value = false
        _audioLevel.value = 0f
        publish = {}
        levels = {}
        reportError = {}
        sessionScope = null
    }

    @MainThread
    fun stopRecording(scope: CoroutineScope, shouldPolish: Boolean = false, onFinalTranscript: (String) -> Unit) {
        if (finishing) return
        if (!listening) {
            onFinalTranscript(format(transcript.text))
            return
        }
        listening = false
        finishing = true
        _isFinishing.value = true
        _isRecording.value = false
        _audioLevel.value = 0f
        levels(0f)
        restartJob?.cancel()
        val token = session
        finalResult = { raw ->
            if (shouldPolish && raw.isNotBlank()) scope.launch {
                val cleaned = WhisperCppBrain.whisperCleanAndPolish(raw)
                if (token == session) onFinalTranscript(cleaned)
            } else onFinalTranscript(raw)
        }
        // stopListening requests a final result; destroy() here would discard the last words.
        if (recognizer == null) {
            finish(token)
            return
        }
        finishJob = scope.launch(Dispatchers.Main.immediate) {
            delay(1_500)
            finish(token) // Providers that omit a final callback keep their last partial result.
        }
        try { recognizer?.stopListening() } catch (_: Exception) { finish(token) }
    }

    private fun beginSegment(token: Long) {
        if (token != session || !listening) return
        try {
            if (recognizer == null) {
                val app = context.applicationContext
                recognizer = if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(app)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
                } else SpeechRecognizer.createSpeechRecognizer(app)
            }
            val segmentRecognizer = recognizer!!
            segmentRecognizer.setRecognitionListener(object : RecognitionListener {
                private fun current() = token == session && recognizer === segmentRecognizer && (listening || finishing)
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() { if (current()) errors = 0 }
                override fun onRmsChanged(rmsdB: Float) {
                    if (!current() || finishing) return
                    val target = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    val smooth = _audioLevel.value + (target - _audioLevel.value) * .35f
                    _audioLevel.value = smooth
                    levels(smooth)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (current()) { _audioLevel.value = 0f; levels(0f) }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (!current()) return
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (partial.isNotBlank()) { transcript.updatePartial(partial); publishTranscript() }
                }
                override fun onResults(results: Bundle?) {
                    if (!current()) return
                    val result = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    transcript.commitSegment(result)
                    errors = 0
                    publishTranscript()
                    if (finishing) finish(token) else restart(token, 80)
                }
                override fun onError(error: Int) {
                    if (!current()) return
                    transcript.commitSegment("")
                    publishTranscript()
                    if (finishing) { finish(token); return }
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart(token, 120)
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Microphone permission was removed.")
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            fail("Speech recognition for this language is unavailable. Install its speech pack in Android settings.")
                        else -> {
                            errors++
                            if (errors >= 3) fail("Speech recognition stopped. Tap the microphone to try again.")
                            else { destroyRecognizer(); restart(token, 250L * errors) }
                        }
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                if (Build.VERSION.SDK_INT >= 33) {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY)
                }
            }
            recognizer!!.startListening(intent)
        } catch (_: Exception) { fail("Could not start speech recognition. Check your speech service and try again.") }
    }

    private fun restart(token: Long, delayMs: Long) {
        restartJob?.cancel()
        restartJob = sessionScope?.launch(Dispatchers.Main.immediate) {
            delay(delayMs)
            if (token == session && listening) beginSegment(token)
        }
    }

    private fun publishTranscript() {
        val value = format(transcript.text)
        _currentTranscript.value = value
        if (value != lastPublished) { lastPublished = value; publish(value) }
    }

    private fun finish(token: Long) {
        if (token != session || !finishing) return
        finishing = false
        _isFinishing.value = false
        finishJob?.cancel()
        transcript.commitSegment("")
        val value = format(transcript.text)
        _currentTranscript.value = value
        val callback = finalResult
        finalResult = null
        destroyRecognizer()
        callback?.invoke(value)
    }

    private fun fail(message: String) {
        listening = false
        finishing = false
        _isFinishing.value = false
        _isRecording.value = false
        _audioLevel.value = 0f
        _error.value = message
        restartJob?.cancel()
        finishJob?.cancel()
        destroyRecognizer()
        reportError(message)
    }

    private fun destroyRecognizer() {
        val old = recognizer
        recognizer = null
        try { old?.cancel() } catch (_: Exception) {}
        try { old?.destroy() } catch (_: Exception) {}
    }

    private fun format(raw: String): String = if (raw.isBlank()) "" else
        VoiceTranscriptionFormatter.formatTranscription(raw, TranscriptionFormatStyle.VERBATIM)
}
