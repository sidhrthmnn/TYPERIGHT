package com.example

import android.text.Editable
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import kotlinx.coroutines.flow.first

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardEditingTest {
    private lateinit var service: TypeRightKeyboardService
    private val text = SpannableStringBuilder()

    @Before fun setup() {
        service = Robolectric.buildService(TypeRightKeyboardService::class.java).create().get()
        val connection = object : BaseInputConnection(View(service), true) {
            override fun getEditable(): Editable = text
        }
        ReflectionHelpers.setField(service, "mStartedInputConnection", connection)
        useEditor(InputType.TYPE_CLASS_TEXT)
        Selection.setSelection(text, 0)
    }

    @After fun tearDown() { service.onDestroy() }

    private fun useEditor(type: Int) {
        val info = EditorInfo().apply { inputType = type }
        ReflectionHelpers.setField(service, "mInputEditorInfo", info)
        service.onStartInput(info, false)
    }

    private fun invoke(name: String) = ReflectionHelpers.callInstanceMethod<Unit>(service, name)
    private fun key(value: String) = ReflectionHelpers.callInstanceMethod<Unit>(service, "handleKeyPress", ClassParameter.from(String::class.java, value))
    private fun type(value: String) { value.forEach { if (it == ' ') invoke("handleSpace") else key(it.toString()) } }

    @Test fun immediateTypoCorrectionCanBeUndoneAndStaysSuppressed() {
        type("teh ")
        assertEquals("the ", text.toString())
        invoke("handleDelete")
        assertEquals("teh", text.toString())
        invoke("handleSpace")
        assertEquals("teh ", text.toString())
    }

    @Test fun apostropheDoesNotSplitContraction() {
        type("don't ")
        assertEquals("don't ", text.toString())
    }

    @Test fun punctuationDoesNotInsertSpacesInsideDecimalsOrUrls() {
        type("3.14")
        assertEquals("3.14", text.toString())
        text.clear(); Selection.setSelection(text, 0)
        useEditor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        type("https://teh.com/a?x=3.14")
        assertEquals("https://teh.com/a?x=3.14", text.toString())
    }

    @Test fun deleteSelectionDoesNotAlsoDeletePreviousWord() {
        text.append("hello world")
        Selection.setSelection(text, 6, 11)
        invoke("handleDelete")
        assertEquals("hello ", text.toString())
    }

    @Test fun deleteEmojiDoesNotLeaveBrokenSurrogates() {
        text.append("hi 👨‍👩‍👧‍👦")
        Selection.setSelection(text, text.length)
        invoke("handleDelete")
        assertEquals("hi ", text.toString())
    }

    @Test fun passwordInputIsLiteralAndNeverPublishedToPredictor() {
        useEditor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        type("teh ")
        assertEquals("teh ", text.toString())
        assertTrue(service.asyncPredictionsState.value.suggestions.isEmpty())
        assertFalse(service.allowsTextAssistance())
    }

    @Test fun nextWordInsertionDoesNotDeleteFollowingWord() {
        text.append("hello world")
        Selection.setSelection(text, 6)
        ReflectionHelpers.callInstanceMethod<Unit>(service, "commitSuggestion", ClassParameter.from(String::class.java, "beautiful"))
        assertEquals("hello beautiful world", text.toString())
    }

    @Test fun proofreadingRefusesToOverwriteNewTyping() {
        text.append("hello world"); Selection.setSelection(text, text.length)
        val snapshot = service.captureEditorText()
        text.append("!"); Selection.setSelection(text, text.length)
        assertFalse(service.applyEditorReplacement(snapshot, "Hello world.", PolishMode.PROOFREAD))
        assertEquals("hello world!", text.toString())
    }

    @Test fun proofreadingRefusesToOverwriteAnotherEditor() {
        text.append("hello world"); Selection.setSelection(text, text.length)
        val snapshot = service.captureEditorText()
        useEditor(InputType.TYPE_CLASS_TEXT)
        assertFalse(service.applyEditorReplacement(snapshot, "Hello world.", PolishMode.PROOFREAD))
    }

    @Test fun proofreadingReplacesOnlyTheCapturedSelection() {
        text.append("say hello world please"); Selection.setSelection(text, 4, 15)
        val snapshot = service.captureEditorText()
        assertTrue(service.applyEditorReplacement(snapshot, "Hello world", PolishMode.PROOFREAD))
        assertEquals("say Hello world please", text.toString())
    }

    @Test fun passwordsNeverEnterPersonalDictionary() {
        useEditor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        repeat(4) { type("plughsecret ") }
        val dictionary = ReflectionHelpers.getField<DictionaryManager>(service, "dictionaryManager")
        assertFalse(dictionary.isWordInUserDictionary("plughsecret"))
        assertFalse(dictionary.isWordInDictionary("plughsecret"))
    }

    @Test fun noPersonalizedLearningFlagIsRespected() {
        service.currentInputEditorInfo.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        repeat(4) { type("plughsecret ") }
        val dictionary = ReflectionHelpers.getField<DictionaryManager>(service, "dictionaryManager")
        assertFalse(dictionary.isWordInUserDictionary("plughsecret"))
    }

    @Test fun settingsDisableImmediateCorrection() {
        KeyboardSettings(service).autocorrectEnabled = false
        type("teh ")
        assertEquals("teh ", text.toString())
    }

    @Test fun coreInputMethodServiceReceivesHardwareKeysAndCommitsText() {
        text.clear()
        Selection.setSelection(text, 0)
        
        // Dispatch hardware key down events through onKeyDown
        val keyEventH = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_H, 0)
        val keyEventI = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_I, 0)
        val keyEventSpace = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE)
        
        assertTrue(service.onKeyDown(KeyEvent.KEYCODE_H, keyEventH))
        assertTrue(service.onKeyDown(KeyEvent.KEYCODE_I, keyEventI))
        assertTrue(service.onKeyDown(KeyEvent.KEYCODE_SPACE, keyEventSpace))
        
        assertEquals("hi ", text.toString())
    }

    @Test fun coreInputMethodServiceHandlesHardwareBackspaceKey() {
        text.clear()
        text.append("hello")
        Selection.setSelection(text, text.length)
        
        val backspaceEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
        assertTrue(service.onKeyDown(KeyEvent.KEYCODE_DEL, backspaceEvent))
        
        assertEquals("hell", text.toString())
    }

    @Test fun coreInputMethodServiceHandlesHardwareEnterKey() {
        text.clear()
        text.append("line1")
        Selection.setSelection(text, text.length)
        useEditor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        
        val enterEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
        assertTrue(service.onKeyDown(KeyEvent.KEYCODE_ENTER, enterEvent))
        
        assertEquals("line1\n", text.toString())
    }

    @Test fun coreInputMethodServiceInspectsEditorTypesCorrectly() {
        useEditor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertTrue(service.isPasswordInputType())
        assertFalse(service.isEmailInputType())
        assertFalse(service.isUrlInputType())

        useEditor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        assertTrue(service.isEmailInputType())
        assertFalse(service.isPasswordInputType())

        useEditor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        assertTrue(service.isUrlInputType())

        useEditor(InputType.TYPE_CLASS_NUMBER)
        assertTrue(service.isNumberInputType())

        useEditor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        assertTrue(service.isMultilineInputType())
    }

    @Test fun keyboardEventListenerReceivesDispatchedEvents() {
        var textReceived = ""
        var deleteCalled = false
        var spaceCalled = false
        
        val listener = object : KeyboardEventListener {
            override fun onTextInput(text: String) { textReceived += text }
            override fun onDelete() { deleteCalled = true }
            override fun onSpace() { spaceCalled = true }
        }
        
        service.addKeyboardEventListener(listener)
        
        service.onKeyText("abc")
        assertEquals("abc", textReceived)
        
        service.onKeySpace()
        assertTrue(spaceCalled)
        
        service.onKeyDelete()
        assertTrue(deleteCalled)
        
        service.removeKeyboardEventListener(listener)
    }

    @Test fun coreInputConnectionHelpersManipulateTextDirectly() {
        text.clear()
        Selection.setSelection(text, 0)
        
        service.commitTextToInput("first second")
        assertEquals("first second", text.toString())
        
        assertEquals("first second", service.getTextBeforeCursor(50))
        
        service.deleteCharacters(beforeLength = 6, afterLength = 0)
        assertEquals("first ", text.toString())
    }

    @Test fun ctrlZTriggersUndo() {
        var undoCalled = false
        val testService = object : KeyboardService() {
            override fun onCreateInputView(): View = View(this)
            override fun undoText() { undoCalled = true }
        }
        val ctrlZEvent = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
        assertTrue(testService.onKeyDown(KeyEvent.KEYCODE_Z, ctrlZEvent))
        assertTrue(undoCalled)
    }

    @Test fun forwardDeleteRemovesSelectedTextOrNextCharacter() {
        text.clear()
        text.append("hello world")
        Selection.setSelection(text, 0, 5)
        service.performForwardDelete()
        assertEquals(" world", text.toString())

        Selection.setSelection(text, 0)
        service.performForwardDelete()
        assertEquals("world", text.toString())
    }

    @Test fun proofreadingDoesNotSilentlyReplaceHeartWithEmoji() = kotlinx.coroutines.runBlocking {
        val manager = AiPolishManager(service)
        val sample = "She has a kind heart."
        val proofread = manager.proofreadTextStream(sample).first()
        assertFalse(proofread.contains("❤️"))
    }
}
