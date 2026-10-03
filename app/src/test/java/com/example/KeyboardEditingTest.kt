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

    @After fun tearDown() {
        service.onDestroy()
        PersonalTypingProfile.get(service).apply { clear(); flush() }
    }

    private fun useEditor(type: Int) {
        val info = EditorInfo().apply { inputType = type }
        ReflectionHelpers.setField(service, "mInputEditorInfo", info)
        service.onStartInput(info, false)
    }

    private fun invoke(name: String) = ReflectionHelpers.callInstanceMethod<Unit>(service, name)
    private fun key(value: String) = ReflectionHelpers.callInstanceMethod<Unit>(service, "handleKeyPress", ClassParameter.from(String::class.java, value))
    private fun type(value: String) {
        value.forEach { if (it == ' ') invoke("handleSpace") else key(it.toString()) }
        drainBackgroundEdits()
    }

    @Test fun immediateTypoCorrectionCanBeUndoneAndStaysSuppressed() {
        AutocorrectMetrics.reset()
        type("teh ")
        assertEquals("the ", text.toString())
        invoke("handleDelete")
        assertEquals("teh", text.toString())
        assertEquals(1.0, AutocorrectMetrics.snapshot().undoRate, 0.0)
        java.io.File("build/reports/autocorrect").mkdirs()
        java.io.File("build/reports/autocorrect/undo.json").writeText("{\"trace\":\"one correction followed by immediate backspace\",\"applied\":1,\"undone\":1,\"undoRate\":1.0}")
        invoke("handleSpace")
        assertEquals("teh ", text.toString())
    }

    @Test fun acceptedPolishLearnsOnlyAfterCommitAndUndoRetractsIt() {
        val profile = PersonalTypingProfile.get(service).apply { clear() }
        text.append("send the mesage"); Selection.setSelection(text, text.length)
        val snapshot = service.captureEditorText()
        assertNull(profile.correction("mesage", emptyList()))
        assertTrue(service.applyEditorReplacement(snapshot, "send the message", PolishMode.PROOFREAD))
        assertEquals("message", profile.correction("mesage", emptyList()))
        assertFalse(service.applyEditorReplacement(snapshot, "send the message", PolishMode.PROOFREAD))
        assertTrue(service.undoEditorReplacement(snapshot))
        assertEquals("send the mesage", text.toString())
        assertNull(profile.correction("mesage", emptyList()))
    }

    @Test fun acceptedFullEditorPolishRefusesStaleTextAndLearnsNothing() {
        val profile = PersonalTypingProfile.get(service).apply { clear() }
        text.append("send the mesage"); Selection.setSelection(text, text.length)
        val snapshot = service.captureFullEditorText()
        Selection.setSelection(text, text.length); text.append(" now"); Selection.setSelection(text, text.length)
        assertFalse(service.applyEditorReplacement(snapshot, "send the message", PolishMode.PROOFREAD))
        assertNull(profile.correction("mesage", emptyList()))
        assertEquals("send the mesage now", text.toString())
    }

    @Test fun acceptedCorrectionIsUsedOnTheNextTypedWord() {
        val profile = PersonalTypingProfile.get(service).apply { clear() }
        text.append("send the mesage"); Selection.setSelection(text, text.length)
        assertTrue(service.applyEditorReplacement(service.captureEditorText(), "send the message", PolishMode.PROOFREAD))
        text.clear(); Selection.setSelection(text, 0)
        useEditor(InputType.TYPE_CLASS_TEXT)
        type("mesage ")
        assertEquals("message ", text.toString())
        invoke("handleDelete")
        assertEquals("mesage", text.toString())
        assertNull(profile.correction("mesage", emptyList()))
    }

    @Test fun noLearningFlagPreventsLearningAcceptedPolish() {
        val profile = PersonalTypingProfile.get(service).apply { clear() }
        service.currentInputEditorInfo.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        text.append("send the mesage"); Selection.setSelection(text, text.length)
        assertTrue(service.applyEditorReplacement(service.captureEditorText(), "send the message", PolishMode.PROOFREAD))
        assertNull(profile.correction("mesage", emptyList()))
        assertTrue(profile.candidates("", emptyList()).isEmpty())
    }

    @Test fun failedEditorCommitDoesNotTeachATypo() {
        val profile = PersonalTypingProfile.get(service).apply { clear() }
        val refusing = object : BaseInputConnection(View(service), true) {
            override fun getEditable(): Editable = text
            override fun commitText(value: CharSequence?, newCursorPosition: Int): Boolean = false
        }
        ReflectionHelpers.setField(service, "mStartedInputConnection", refusing)
        text.append("say mesage please"); Selection.setSelection(text, 4, 10)
        assertFalse(service.applyEditorReplacement(service.captureEditorText(), "message", PolishMode.PROOFREAD))
        assertNull(profile.correction("mesage", emptyList()))
        assertEquals("say mesage please", text.toString())
    }

    @Test fun learningSwitchStopsBothUsageAndAcceptedFixLearning() {
        val profile = PersonalTypingProfile.get(service).apply { clear() }
        KeyboardSettings(service).personalizedLearningEnabled = false
        text.append("send the mesage"); Selection.setSelection(text, text.length)
        assertTrue(service.applyEditorReplacement(service.captureEditorText(), "send the message", PolishMode.PROOFREAD))
        type(" hello ")
        assertNull(profile.correction("mesage", emptyList()))
        assertTrue(profile.candidates("", emptyList()).isEmpty())
    }

    @Test fun acceptedSelectionUndoPreservesSurroundingTextAndNewEditsAreProtected() {
        text.append("say mesage please"); Selection.setSelection(text, 4, 10)
        val snapshot = service.captureEditorText()
        assertTrue(service.applyEditorReplacement(snapshot, "message", PolishMode.PROOFREAD))
        assertTrue(service.undoEditorReplacement(snapshot))
        assertEquals("say mesage please", text.toString())
        Selection.setSelection(text, 4, 10)
        val next = service.captureEditorText()
        assertTrue(service.applyEditorReplacement(next, "message", PolishMode.PROOFREAD))
        text.append("!"); Selection.setSelection(text, text.length)
        assertFalse(service.undoEditorReplacement(next))
        assertEquals("say message please!", text.toString())
    }

    @Test fun repeatedSwipesAreLowercaseDespiteSentenceShift() {
        service.handleSwipeResult("Hello", listOf("Hello", "Hollow"), emptyList())
        service.handleSwipeResult("World", listOf("World", "Word"), emptyList())
        assertEquals("hello world ", text.toString())
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

    private fun drainBackgroundEdits() {
        repeat(300) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(10)); Thread.sleep(10)
            val dictionary=DictionaryManager.getInstance(service)
            val coordinator=ReflectionHelpers.getField<Lazy<*>>(service,"typingCoordinator\$delegate")
            val pending=ReflectionHelpers.getField<List<*>>(coordinator.value,"pending")
            if(dictionary.ready.isCompleted && EnglishFrequencyLexicon.get(service).ready.isCompleted && pending.isEmpty()) return
        }
    }
    @Test fun cacheMissCorrectsOnWorkerAndImmediateBackspaceRestoresOriginal() {
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Default) { DictionaryManager.getInstance(service).correctionPipeline.awaitDictionaries() }
        "teh".forEach { key(it.toString()) }
        invoke("handleSpace")
        assertEquals("teh ", text.toString())
        drainBackgroundEdits()
        assertEquals("the ", text.toString())
        invoke("handleDelete")
        assertEquals("teh", text.toString())
    }
    @Test fun staleBoundaryNeverEditsNewTypingOrAnotherEditor() {
        "teh".forEach { key(it.toString()) }; invoke("handleSpace"); key("x")
        drainBackgroundEdits()
        assertEquals("the x", text.toString())
        text.clear(); Selection.setSelection(text, 0); useEditor(InputType.TYPE_CLASS_TEXT)
        "teh".forEach { key(it.toString()) }; invoke("handleSpace")
        useEditor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        drainBackgroundEdits()
        assertEquals("teh ", text.toString())
    }
    @Test fun privateFieldUndoDoesNotPersistNegativeFeedback() {
        val profile = DictionaryManager.getInstance(service).personalProfile.apply { clear() }
        service.currentInputEditorInfo.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        type("teh "); invoke("handleDelete")
        assertEquals("teh", text.toString())
        assertEquals(0f, profile.rejectionPenalty("teh", "the"), 0f)
        invoke("handleSpace"); drainBackgroundEdits()
        assertEquals("teh ", text.toString())
    }

    @Test fun consecutiveCorrectionsKeepTheFollowingComposition() {
        type("finaly libary buisness!next")
        assertEquals("finally library business!next",text.toString())
        assertEquals(text.length-4,BaseInputConnection.getComposingSpanStart(text))
        assertEquals(text.length,BaseInputConnection.getComposingSpanEnd(text))
    }
    @Test fun cursorMovementAndSelectionCancelPendingWordChecks() {
        "teh".forEach { key(it.toString()) }; invoke("handleSpace")
        Selection.setSelection(text,0,2)
        service.onUpdateSelection(4,4,0,2,-1,-1)
        drainBackgroundEdits()
        assertEquals("teh ",text.toString())
        assertEquals(0,Selection.getSelectionStart(text))
        assertEquals(2,Selection.getSelectionEnd(text))
        assertFalse(PersonalTypingProfile.get(service).isTrusted("teh"))
    }
    @Test fun queuedOwnedSelectionUpdatesPreserveRapidTypingAndComposition() {
        "teh next".forEach { if(it==' ') invoke("handleSpace") else key(it.toString()) }
        // These are delayed acknowledgements of our earlier writes, not user cursor movements.
        service.onUpdateSelection(0,0,1,1,0,1)
        service.onUpdateSelection(1,1,4,4,-1,-1)
        service.onUpdateSelection(4,4,5,5,4,5)
        service.onUpdateSelection(5,5,8,8,4,8)
        drainBackgroundEdits()
        assertEquals("the next",text.toString())
        assertEquals(4,BaseInputConnection.getComposingSpanStart(text))
        assertEquals(8,BaseInputConnection.getComposingSpanEnd(text))
    }
    @Test fun actualCursorMovementAfterAcknowledgedTypingCancelsPendingCorrection() {
        "teh next".forEach { if(it==' ') invoke("handleSpace") else key(it.toString()) }
        service.onUpdateSelection(0,0,8,8,4,8)
        Selection.setSelection(text,2)
        service.onUpdateSelection(8,8,2,2,4,8)
        drainBackgroundEdits()
        assertEquals("teh next",text.toString())
        assertEquals(2,Selection.getSelectionEnd(text))
    }
    @Test fun externalEditsCancelUnsafeRangesAndDoNotTeachCancelledTypos() {
        "finaly".forEach { key(it.toString()) }; invoke("handleSpace")
        text.replace(0,6,"external"); Selection.setSelection(text,text.length)
        drainBackgroundEdits()
        assertEquals("external ",text.toString())
        assertFalse(PersonalTypingProfile.get(service).isTrusted("finaly"))
    }
    @Test fun identifiersAndDomainsStayLiteralInOrdinaryTextEditors() {
        type("@buisness teh.com ")
        assertEquals("@buisness teh.com ",text.toString())
    }
    @Test fun contextualCorrectionUsesAvailableFollowingTextAndPreservesIt() {
        text.append("go over  now"); Selection.setSelection(text,8)
        type("their ")
        assertEquals("go over there  now",text.toString())
        invoke("handleDelete")
        assertEquals("go over their now",text.toString())
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

    @Test fun basicCorrectionDoesNotSilentlyReplaceHeartWithEmoji() {
        val manager = OnDeviceNeuralPolishEngine.getInstance(service)
        val sample = "She has a kind heart."
        val proofread = manager.quickProofread(sample)
        assertFalse(proofread.contains("❤️"))
    }

    @Test fun testSpaceCursorScrollingMovesCursorWithoutTriggeringGlideAction() {
        text.clear()
        text.append("typing test")
        Selection.setSelection(text, 11)

        service.moveCursorLeft()
        service.moveCursorLeft()
        service.moveCursorLeft()
        service.moveCursorLeft()

        assertEquals("typing test", text.toString())

        service.moveCursorRight()
        assertEquals("typing test", text.toString())
    }
}
