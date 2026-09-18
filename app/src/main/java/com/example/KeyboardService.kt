package com.example

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Direction enum for cursor navigation actions.
 */
enum class CursorDirection {
    LEFT, RIGHT, UP, DOWN
}

/**
 * Listener interface for observing and reacting to keyboard events, input lifecycle,
 * and text modifications.
 */
interface KeyboardEventListener {
    fun onTextInput(text: String) {}
    fun onDelete() {}
    fun onEnter() {}
    fun onSpace() {}
    fun onEditorAction(actionId: Int) {}
    fun onSelectionChanged(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int) {}
    fun onInputStarted(info: EditorInfo?, restarting: Boolean) {}
    fun onInputFinished() {}
    fun onHardwareKeyEvent(keyCode: Int, event: KeyEvent): Boolean = false
}

/**
 * Core KeyboardService class extending InputMethodService.
 * Hooks directly into the Android OS to receive text input, manage input connections,
 * process hardware/software keyboard events, track cursor/selection state, and coordinate
 * system IME window lifecycle.
 */
abstract class KeyboardService : InputMethodService() {

    protected lateinit var micPermissionHelper: MicrophonePermissionHelper
    private var audioManager: AudioManager? = null

    /** Tracks whether the soft keyboard input view is currently visible. */
    var isInputViewVisible: Boolean = false
        protected set

    /** Cached reference to the active editor info. */
    var currentEditorInfo: EditorInfo? = null
        protected set

    /** Current cursor/selection positions tracked from system callbacks. */
    var currentCursorPosition: Int = 0
        protected set
    var currentSelectionStart: Int = 0
        protected set
    var currentSelectionEnd: Int = 0
        protected set

    /** Track pending dead key accent (COMBINING_ACCENT). */
    protected var pendingDeadKey: Int = 0

    /** Active event listeners. */
    private val eventListeners = CopyOnWriteArrayList<KeyboardEventListener>()

    /**
     * Safely retrieves the active InputConnection to the focused target application.
     */
    val safeInputConnection: InputConnection?
        get() = currentInputConnection

    override fun onCreate() {
        super.onCreate()
        micPermissionHelper = MicrophonePermissionHelper(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun onInitializeInterface() {
        super.onInitializeInterface()
    }

    /**
     * Called by system when soft keyboard view needs to be created.
     */
    abstract override fun onCreateInputView(): View

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        currentEditorInfo = attribute
        currentCursorPosition = attribute?.initialSelStart ?: 0
        currentSelectionStart = attribute?.initialSelStart ?: 0
        currentSelectionEnd = attribute?.initialSelEnd ?: 0
        pendingDeadKey = 0
        for (listener in eventListeners) {
            listener.onInputStarted(attribute, restarting)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isInputViewVisible = true
        currentEditorInfo = info
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        isInputViewVisible = false
    }

    override fun onFinishInput() {
        super.onFinishInput()
        currentEditorInfo = null
        currentCursorPosition = 0
        currentSelectionStart = 0
        currentSelectionEnd = 0
        pendingDeadKey = 0
        for (listener in eventListeners) {
            listener.onInputFinished()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isInputViewVisible = false
        eventListeners.clear()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        currentCursorPosition = newSelStart
        currentSelectionStart = newSelStart
        currentSelectionEnd = newSelEnd
        for (listener in eventListeners) {
            listener.onSelectionChanged(oldSelStart, oldSelEnd, newSelStart, newSelEnd)
        }
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        outInsets.contentTopInsets = outInsets.visibleTopInsets
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        // Soft keyboard should not consume fullscreen in landscape unless screen is extremely small
        val config = resources.configuration
        return config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                config.screenHeightDp < 320
    }

    // =========================================================================
    // KEYBOARD EVENT HANDLING (HARDWARE & SYSTEM EVENTS)
    // =========================================================================

    /**
     * Intercepts and processes system key down events, including physical keyboards,
     * external Bluetooth keyboards, D-pad navigation, and hardware shortcuts.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        // Notify event listeners first
        for (listener in eventListeners) {
            if (listener.onHardwareKeyEvent(keyCode, event)) {
                return true
            }
        }

        // Subclass hook for custom key down processing
        if (handleHardwareKeyDown(keyCode, event)) {
            return true
        }

        val isCtrl = event.isCtrlPressed
        val isAlt = event.isAltPressed
        val isShift = event.isShiftPressed

        // 1. Control Shortcuts (Ctrl+A, Ctrl+C, Ctrl+V, Ctrl+X, Ctrl+Z, Ctrl+Y)
        if (isCtrl && !isAlt) {
            when (keyCode) {
                KeyEvent.KEYCODE_A -> { selectAllText(); return true }
                KeyEvent.KEYCODE_C -> { copyText(); return true }
                KeyEvent.KEYCODE_V -> { pasteText(); return true }
                KeyEvent.KEYCODE_X -> { cutText(); return true }
                KeyEvent.KEYCODE_Z -> {
                    if (isShift) {
                        redoText()
                    } else {
                        undoText()
                    }
                    return true
                }
                KeyEvent.KEYCODE_Y -> {
                    redoText()
                    return true
                }
            }
        }

        // 2. Navigation and Selection Keys (Preserve modifiers for Shift+Arrows, Ctrl+Arrows, etc.)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_MOVE_HOME,
            KeyEvent.KEYCODE_MOVE_END -> {
                if (isShift || isCtrl || isAlt || event.isMetaPressed) {
                    return safeInputConnection?.sendKeyEvent(event) ?: super.onKeyDown(keyCode, event)
                }
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> onCursorMove(CursorDirection.LEFT)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> onCursorMove(CursorDirection.RIGHT)
                    KeyEvent.KEYCODE_DPAD_UP -> onCursorMove(CursorDirection.UP)
                    KeyEvent.KEYCODE_DPAD_DOWN -> onCursorMove(CursorDirection.DOWN)
                    else -> return safeInputConnection?.sendKeyEvent(event) ?: super.onKeyDown(keyCode, event)
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (isInputViewShown) {
                    hideKeyboard()
                    return true
                }
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_DEL -> {
                onKeyDelete()
                return true
            }
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                performForwardDelete()
                notifyDelete()
                return true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                onKeyEnter()
                return true
            }
            KeyEvent.KEYCODE_SPACE -> {
                onKeySpace()
                return true
            }
            KeyEvent.KEYCODE_TAB -> {
                handleTabNavigation(isShift)
                return true
            }
        }

        // 3. Forward unhandled Ctrl/Meta shortcuts to host editor (e.g. Ctrl+B, Ctrl+I, Ctrl+S)
        if (isCtrl || event.isMetaPressed) {
            return safeInputConnection?.sendKeyEvent(event) ?: super.onKeyDown(keyCode, event)
        }

        // 4. Printable Unicode Characters and Dead Key / Combining Accent Handling
        val rawUnicode = event.getUnicodeChar(event.metaState)
        if ((rawUnicode and KeyCharacterMap.COMBINING_ACCENT) != 0) {
            pendingDeadKey = rawUnicode and KeyCharacterMap.COMBINING_ACCENT_MASK
            return true
        }

        val unicode = if (pendingDeadKey != 0) {
            val combined = KeyCharacterMap.getDeadChar(pendingDeadKey, rawUnicode)
            val accent = pendingDeadKey
            pendingDeadKey = 0
            if (combined != 0) {
                combined
            } else {
                if (Character.isValidCodePoint(accent) && !Character.isISOControl(accent)) {
                    onKeyText(String(Character.toChars(accent)))
                }
                rawUnicode
            }
        } else {
            rawUnicode
        }

        if (unicode != 0 && !Character.isISOControl(unicode) && Character.isValidCodePoint(unicode)) {
            val charStr = String(Character.toChars(unicode))
            onKeyText(charStr)
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && handleHardwareKeyUp(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyMultiple(keyCode: Int, count: Int, event: KeyEvent?): Boolean {
        if (event != null) {
            val characters = event.characters
            if (!characters.isNullOrEmpty()) {
                onKeyText(characters)
                return true
            }
        }
        return super.onKeyMultiple(keyCode, count, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && handleHardwareKeyLongPress(keyCode, event)) {
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    // =========================================================================
    // EXTENSION HOOKS FOR SUBCLASSES
    // =========================================================================

    /**
     * Called when a text input string is produced (from physical or software keys).
     */
    open fun onKeyText(text: String) {
        commitTextToInput(text)
        notifyTextInput(text)
    }

    /**
     * Called when a delete/backspace event is triggered.
     */
    open fun onKeyDelete() {
        performBackspace()
        notifyDelete()
    }

    /**
     * Called when an enter / return key event is triggered.
     */
    open fun onKeyEnter() {
        performEnterAction()
        notifyEnter()
    }

    /**
     * Called when a space key event is triggered.
     */
    open fun onKeySpace() {
        performSpaceAction()
        notifySpace()
    }

    fun notifyTextInput(text: String) {
        for (listener in eventListeners) {
            listener.onTextInput(text)
        }
    }

    fun notifyDelete() {
        for (listener in eventListeners) {
            listener.onDelete()
        }
    }

    fun notifyEnter() {
        for (listener in eventListeners) {
            listener.onEnter()
        }
    }

    fun notifySpace() {
        for (listener in eventListeners) {
            listener.onSpace()
        }
    }

    /**
     * Called when a directional cursor movement key is triggered.
     */
    open fun onCursorMove(direction: CursorDirection) {
        when (direction) {
            CursorDirection.LEFT -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            CursorDirection.RIGHT -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            CursorDirection.UP -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
            CursorDirection.DOWN -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
        }
    }

    /**
     * Hook for subclasses to handle custom physical key down logic.
     * Return true to consume the event.
     */
    open fun handleHardwareKeyDown(keyCode: Int, event: KeyEvent): Boolean = false

    /**
     * Hook for subclasses to handle custom physical key up logic.
     * Return true to consume the event.
     */
    open fun handleHardwareKeyUp(keyCode: Int, event: KeyEvent): Boolean = false

    /**
     * Hook for subclasses to handle hardware key long-press events.
     */
    open fun handleHardwareKeyLongPress(keyCode: Int, event: KeyEvent): Boolean = false

    // =========================================================================
    // INPUT CONNECTION OPERATIONS (RECEIVE & DISPATCH TEXT INPUT)
    // =========================================================================

    /**
     * Commits text to the active InputConnection.
     */
    open fun commitTextToInput(text: CharSequence, newCursorPosition: Int = 1) {
        safeInputConnection?.commitText(text, newCursorPosition)
    }

    /**
     * Sets composing text (underlined / active editing state) on the active InputConnection.
     */
    open fun setComposingTextToInput(text: CharSequence, newCursorPosition: Int = 1) {
        safeInputConnection?.setComposingText(text, newCursorPosition)
    }

    /**
     * Finishes composing text on the active InputConnection.
     */
    open fun finishComposingText() {
        safeInputConnection?.finishComposingText()
    }

    /**
     * Performs standard backspace deletion handling selected text or deleting preceding characters.
     */
    open fun performBackspace() {
        val ic = safeInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(16, 0)?.toString().orEmpty()
        if (before.isNotEmpty()) {
            val deleteLen = TypingPolicy.lastCharacterLength(before).coerceAtLeast(1)
            ic.deleteSurroundingText(deleteLen, 0)
        } else {
            deleteCharacters(beforeLength = 1, afterLength = 0)
        }
    }

    /**
     * Performs standard forward-delete handling selected text or deleting subsequent characters.
     */
    open fun performForwardDelete() {
        val ic = safeInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val after = ic.getTextAfterCursor(16, 0)?.toString().orEmpty()
        if (after.isNotEmpty()) {
            val deleteLen = TypingPolicy.firstCharacterLength(after).coerceAtLeast(1)
            ic.deleteSurroundingText(0, deleteLen)
        } else {
            deleteCharacters(beforeLength = 0, afterLength = 1)
        }
    }

    /**
     * Performs standard enter action: executes IME action if defined, or commits newline.
     */
    open fun performEnterAction() {
        val ic = safeInputConnection ?: return
        val info = currentEditorInfo
        val hasNoEnterAction = info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val isMultiline = isMultilineInputType()
        val action = getImeAction()

        if (!hasNoEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED && !isMultiline) {
            ic.performEditorAction(action)
            for (listener in eventListeners) {
                listener.onEditorAction(action)
            }
        } else if (isMultiline || hasNoEnterAction) {
            ic.commitText("\n", 1)
        } else if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
            for (listener in eventListeners) {
                listener.onEditorAction(action)
            }
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    /**
     * Performs standard space key action.
     */
    open fun performSpaceAction() {
        commitTextToInput(" ", 1)
    }

    /**
     * Handles Tab key: executes NEXT editor action if available, or inserts tab character.
     */
    protected open fun handleTabNavigation(isShift: Boolean) {
        val ic = safeInputConnection ?: return
        if (isShift) {
            val now = SystemClock.uptimeMillis()
            val meta = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 0, meta))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB, 0, meta))
        } else {
            val action = getImeAction()
            if (action == EditorInfo.IME_ACTION_NEXT) {
                ic.performEditorAction(EditorInfo.IME_ACTION_NEXT)
            } else {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
            }
        }
    }

    /**
     * Reverts last text action via context menu or synthesized Ctrl+Z event.
     */
    open fun undoText() {
        val ic = safeInputConnection ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (ic.performContextMenuAction(android.R.id.undo)) return
        }
        val now = SystemClock.uptimeMillis()
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, meta))
    }

    /**
     * Redoes reverted text action via context menu or synthesized Ctrl+Y / Ctrl+Shift+Z event.
     */
    open fun redoText() {
        val ic = safeInputConnection ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (ic.performContextMenuAction(android.R.id.redo)) return
        }
        val now = SystemClock.uptimeMillis()
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, meta))
    }

    /**
     * Deletes surrounding characters before and after the cursor, handling unicode code points safely.
     */
    fun deleteCharacters(beforeLength: Int = 1, afterLength: Int = 0) {
        val ic = safeInputConnection ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ic.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
        } else {
            ic.deleteSurroundingText(beforeLength, afterLength)
        }
    }

    /**
     * Sends down and up key events to the active target input connection.
     */
    fun sendKeyEvent(keyCode: Int) {
        sendDownUpKeyEvents(keyCode)
    }

    /**
     * Executes the specified editor action on the active InputConnection.
     */
    fun performEditorAction(actionCode: Int) {
        safeInputConnection?.performEditorAction(actionCode)
    }

    /**
     * Executes the default IME action configured in EditorInfo.
     */
    fun performDefaultEditorAction() {
        val action = getImeAction()
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            safeInputConnection?.performEditorAction(action)
        }
    }

    /**
     * Adjusts the cursor position by an offset relative to current position.
     */
    fun moveCursor(offset: Int) {
        val ic = safeInputConnection ?: return
        if (offset == 0) return
        val current = currentCursorPosition
        val target = (current + offset).coerceAtLeast(0)
        ic.setSelection(target, target)
    }

    /**
     * Sets selection range between start and end.
     */
    fun setSelectionRange(start: Int, end: Int) {
        safeInputConnection?.setSelection(start, end)
    }

    /**
     * Selects all text in the active input field.
     */
    fun selectAllText() {
        safeInputConnection?.performContextMenuAction(android.R.id.selectAll)
    }

    /**
     * Copies selected text to clipboard.
     */
    fun copyText() {
        safeInputConnection?.performContextMenuAction(android.R.id.copy)
    }

    /**
     * Cuts selected text to clipboard.
     */
    fun cutText() {
        safeInputConnection?.performContextMenuAction(android.R.id.cut)
    }

    /**
     * Pastes clipboard text into input field.
     */
    fun pasteText() {
        safeInputConnection?.performContextMenuAction(android.R.id.paste)
    }

    /**
     * Safely reads text before the current cursor position.
     */
    fun getTextBeforeCursor(length: Int = 100): String {
        return safeInputConnection?.getTextBeforeCursor(length, 0)?.toString().orEmpty()
    }

    /**
     * Safely reads text after the current cursor position.
     */
    fun getTextAfterCursor(length: Int = 100): String {
        return safeInputConnection?.getTextAfterCursor(length, 0)?.toString().orEmpty()
    }

    /**
     * Safely reads currently selected text.
     */
    fun getSelectedText(): String? {
        return safeInputConnection?.getSelectedText(0)?.toString()
    }

    /**
     * Wraps multiple input connection calls in a batch edit.
     */
    inline fun executeBatchEdit(action: (InputConnection) -> Unit) {
        val ic = safeInputConnection ?: return
        ic.beginBatchEdit()
        try {
            action(ic)
        } finally {
            ic.endBatchEdit()
        }
    }

    // =========================================================================
    // EDITOR INFO & FIELD INTROSPECTION
    // =========================================================================

    /**
     * Checks if the active target field is a password or sensitive field.
     */
    fun isPasswordInputType(): Boolean {
        val info = currentEditorInfo ?: return false
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                (info.inputType and InputType.TYPE_CLASS_NUMBER != 0 &&
                        variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    /**
     * Checks if the active target field is an email address.
     */
    fun isEmailInputType(): Boolean {
        val info = currentEditorInfo ?: return false
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
    }

    /**
     * Checks if the active target field is a URL or URI.
     */
    fun isUrlInputType(): Boolean {
        val info = currentEditorInfo ?: return false
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_URI
    }

    /**
     * Checks if the active target field expects numeric, phone, or date input.
     */
    fun isNumberInputType(): Boolean {
        val info = currentEditorInfo ?: return false
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        return inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME
    }

    /**
     * Checks if the active target field supports multi-line text.
     */
    fun isMultilineInputType(): Boolean {
        val info = currentEditorInfo ?: return false
        return (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
    }

    /**
     * Returns the active IME action (e.g. Done, Go, Search, Send, Next).
     */
    fun getImeAction(): Int {
        val info = currentEditorInfo ?: return EditorInfo.IME_ACTION_NONE
        return info.imeOptions and EditorInfo.IME_MASK_ACTION
    }

    // =========================================================================
    // EVENT LISTENER REGISTRATION
    // =========================================================================

    fun addKeyboardEventListener(listener: KeyboardEventListener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener)
        }
    }

    fun removeKeyboardEventListener(listener: KeyboardEventListener) {
        eventListeners.remove(listener)
    }

    // =========================================================================
    // FEEDBACK & SYSTEM HELPERS
    // =========================================================================

    /**
     * Checks if RECORD_AUDIO permission is currently granted.
     */
    fun checkMicrophonePermission(): Boolean {
        return micPermissionHelper.isPermissionGranted()
    }

    /**
     * Requests the system to hide the soft keyboard input window.
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
     * Performs tactile haptic feedback vibration for key taps.
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

    /**
     * Plays standard keyboard click audio effect.
     */
    fun playClickFeedback(soundEffect: Int = AudioManager.FX_KEYPRESS_STANDARD) {
        try {
            audioManager?.playSoundEffect(soundEffect)
        } catch (_: Exception) {
            // Ignore if audio manager unavailable
        }
    }
}

