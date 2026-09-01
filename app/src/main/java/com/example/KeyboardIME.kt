package com.example

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*

/**
 * Base InputMethodService implementation ("KeyboardIME") managing keyboard lifecycle,
 * mic toggle state, real-time waveform visualizers, local STT and local SLM Ramble processing,
 * and direct atomic injection to InputConnection.
 */
abstract class KeyboardIME : KeyboardService() {

    companion object {
        private const val TAG = "KeyboardIME"
    }

    protected val imeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 100% Local, Offline components
    protected lateinit var audioCaptureManager: AudioCaptureManager
    protected lateinit var localSpeechToText: LocalSpeechToText
    protected lateinit var localRambleFormatter: LocalRambleFormatter

    // Ramble & Voice Dictation States
    val isRecordingState = mutableStateOf(false)
    val isRefiningState = mutableStateOf(false)
    val currentAudioAmplitude = mutableStateOf(0f)
    val bufferedTranscript = mutableStateOf("")

    override fun onCreate() {
        super.onCreate()
        audioCaptureManager = AudioCaptureManager(this)
        localSpeechToText = LocalSpeechToText(this)
        localRambleFormatter = LocalRambleFormatter(this)
    }

    /**
     * Starts voice capture for stream-of-thought dictation (Ramble Mode).
     */
    fun startRambleDictation() {
        if (!checkMicrophonePermission()) {
            Log.w(TAG, "Audio permission not granted")
            return
        }

        isRecordingState.value = true
        isRefiningState.value = false
        bufferedTranscript.value = ""
        currentAudioAmplitude.value = 0f

        val success = audioCaptureManager.startCapture(
            onAmplitude = { amp ->
                currentAudioAmplitude.value = amp
            }
        )

        if (!success) {
            isRecordingState.value = false
            return
        }

        // Start offline STT in parallel
        localSpeechToText.startContinuousOfflineRecognition(
            onPartialTranscript = { partial ->
                bufferedTranscript.value = partial
            },
            onFinalTranscript = { finalRaw ->
                bufferedTranscript.value = finalRaw
            },
            onError = { error ->
                Log.w(TAG, "STT stream note: $error")
            }
        )
    }

    /**
     * Confirms dictation: Stops audio recording, runs local offline SLM formatting,
     * and atomically injects the cleaned text into active field.
     */
    fun confirmRambleDictation() {
        if (!isRecordingState.value) return

        isRecordingState.value = false
        isRefiningState.value = true

        val pcmAudio = audioCaptureManager.stopCapture()
        localSpeechToText.stopContinuousRecognition()

        imeScope.launch {
            try {
                // Step 1: Extract literal STT transcript from recorded audio or active buffer
                var rawText = bufferedTranscript.value.trim()
                if (rawText.isBlank() && pcmAudio.isNotEmpty()) {
                    rawText = localSpeechToText.transcribeAudio(pcmAudio)
                }

                if (rawText.isNotBlank()) {
                    // Step 2: Run Local SLM formatting (removes fillers, resolves self-corrections, applies meta-commands)
                    val cleanedText = localRambleFormatter.formatRambleText(rawText)
                    val finalTextToInject = if (cleanedText.isNotBlank()) cleanedText else rawText

                    // Step 3: Input Injection directly into active target
                    currentInputConnection?.commitText(finalTextToInject, 1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in local ramble pipeline: ${e.message}", e)
                if (bufferedTranscript.value.isNotBlank()) {
                    currentInputConnection?.commitText(bufferedTranscript.value, 1)
                }
            } finally {
                isRefiningState.value = false
                bufferedTranscript.value = ""
                currentAudioAmplitude.value = 0f
            }
        }
    }

    /**
     * Cancels active dictation and discards all buffered audio and transcript.
     */
    fun cancelRambleDictation() {
        isRecordingState.value = false
        isRefiningState.value = false
        currentAudioAmplitude.value = 0f
        bufferedTranscript.value = ""

        audioCaptureManager.cancelCapture()
        localSpeechToText.stopContinuousRecognition()
    }

    /**
     * Toggles recording state.
     */
    fun toggleRambleDictation() {
        if (isRecordingState.value) {
            confirmRambleDictation()
        } else {
            startRambleDictation()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioCaptureManager.cancelCapture()
        localSpeechToText.destroy()
        imeScope.cancel()
    }
}
