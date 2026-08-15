package com.example

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * Core KeyboardService class extending InputMethodService.
 * Handles system integration, soft input window visibility, input connection lifecycle,
 * and microphone runtime permission integration.
 */
abstract class KeyboardService : InputMethodService() {

    protected lateinit var micPermissionHelper: MicrophonePermissionHelper
    protected var isInputViewVisible: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        micPermissionHelper = MicrophonePermissionHelper(this)
    }

    /**
     * Called by system when input view (soft keyboard) needs to be created.
     */
    abstract override fun onCreateInputView(): View

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isInputViewVisible = true
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        isInputViewVisible = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isInputViewVisible = false
    }

    /**
     * Checks if RECORD_AUDIO permission is currently granted using MicrophonePermissionHelper.
     */
    fun checkMicrophonePermission(): Boolean {
        return micPermissionHelper.isPermissionGranted()
    }

    /**
     * Requests system to hide the soft keyboard input window.
     */
    fun hideKeyboard() {
        requestHideSelf(0)
    }

    /**
     * Switches to the next enabled Input Method (keyboard).
     */
    fun switchToNextKeyboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToNextInputMethod(false)
        } else {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }
    }

    /**
     * Safely inserts text into the active target input field via InputConnection.
     */
    fun commitTextToInput(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    /**
     * Perform tactile haptic feedback vibration for key taps.
     */
    fun triggerHapticFeedback(durationMs: Long = 10L) {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            durationMs,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {
            // Ignore if vibration permissions or hardware unavailable
        }
    }
}
