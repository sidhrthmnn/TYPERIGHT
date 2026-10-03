package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PointF
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.media.MediaRecorder
import java.io.File
import android.view.View
import android.view.Window
import android.view.ViewGroup
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.text.style.SuggestionSpan
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.composed
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import android.util.Log
import kotlin.random.Random
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.RevampedEmojiLayout
import com.example.giphy.GiphyMediaItem
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.catch
import java.util.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

data class TextInputBufferState(
    val typedWord: String = "",
    val activePrefix: String = "",
    val previousWord: String? = null,
    val previousWords: List<String> = emptyList(),
    val tapCoords: List<PointF> = emptyList(),
    val isUrl: Boolean = false,
    val isEmail: Boolean = false,
    val isSensitive: Boolean = false,
    val isSearch: Boolean = false,
    val isWeb: Boolean = false,
    val isChat: Boolean = false,
    val boxCategory: TextBoxCategory = TextBoxCategory.GENERAL,
    val timestamp: Long = System.currentTimeMillis()
)

data class AsyncKeyboardPredictions(
    val gboardResult: GboardSuggestionResult = GboardSuggestionResult("", "", "", false),
    val suggestions: List<String> = emptyList(),
    val aiPhraseCompletions: List<String> = emptyList(),
    val source: TextInputBufferState? = null
)

data class EditorTextSnapshot(val session: Long, val before: String, val selected: String?, val after: String) {
    val text: String get() = selected?.takeIf { it.isNotEmpty() } ?: (before + after)
}

class TypeRightKeyboardService : KeyboardService() {

    private lateinit var settings: KeyboardSettings
    private lateinit var dictionaryManager: DictionaryManager
    private lateinit var aiPolishManager: AiPolishManager
    private lateinit var clipboardRepository: ClipboardRepository
    private val localPredictor by lazy { LocalGrammarSpellPredictor(this) }
    private val manglishEngine by lazy { ManglishTransliterationEngine.getInstance(this) }
    private val predictiveService by lazy { PredictiveTextSuggestionService.getInstance(this) }
    private val userDictionaryRepo by lazy { UserDictionaryRepository.getInstance(this) }

    enum class FeedbackType {
        Standard, Space, Delete, Enter
    }

    private var composeSetup: ComposeSetup? = null

    // Asynchronous text input buffer and debouncing states
    private val textBufferFlow = MutableStateFlow(TextInputBufferState())
    val asyncPredictionsState = mutableStateOf(AsyncKeyboardPredictions())
    val currentTextBoxInfo = mutableStateOf(TextBoxClassifier.defaultClassification)

    fun getCurrentTextBoxInfo(): TextBoxClassification {
        val info = currentInputEditorInfo
        val current = currentTextBoxInfo.value
        if (info == null) return current
        return TextBoxClassifier.classify(info)
    }

    // Keyboard state
    private val isShiftActive = mutableStateOf(false)
    private val isCapsLockActive = mutableStateOf(false)
    private var lastShiftClickTime: Long = 0L
    private val isSymbolLayerActive = mutableStateOf(false)
    private val isEmojiLayerActive = mutableStateOf(false)
    private val isClipboardLayerActive = mutableStateOf(false)
    private val currentTypedWord = mutableStateOf("")
    private val wordUnderCursor = mutableStateOf("")
    private val previousWord = mutableStateOf<String?>(null)
    private val previousWord2 = mutableStateOf<String?>(null)
    private val previousWords = mutableStateOf<List<String>>(emptyList())
    private val currentWordTapCoords = mutableListOf<PointF>()
    
    // Instant autocorrect undo tracking state
    private var lastOriginalWord: String = ""
    private var lastCorrectedWord: String = ""
    private var justAutocorrected: Boolean = false
    private var lastCorrectedWasSpace: Boolean = false
    private var bufferGeneration = 0L
    private var editorSession = 0L
    private var lastSpaceTime = 0L
    private var suggestionSpacePending = false
    private var lastCursorPosition = 0
    private var lastComposedStart = -1
    private var lastComposedEnd = -1
    
    // Gesture swipe typing state tracking
    private var lastSwipeCommittedWord: String? = null
    private var lastSwipeCommittedHadSpace: Boolean = false
    private var lastSwipePath: List<PointF> = emptyList()
    
    // Voice Typing and AI Polish states
    private val isVoiceTypingActive = mutableStateOf(false)
    private val voiceTranscript = mutableStateOf("")
    private var voiceInsertionPrefix = ""
    private val voiceAudioLevel = mutableStateOf(0f)
    val isAiPolishing = mutableStateOf(false)
    val isProofreadSheetOpen = mutableStateOf(false)
    private var currentAiRequestId: Long = 0L
    private var currentAiJob: kotlinx.coroutines.Job? = null
    private var rephraseSnapshot: EditorTextSnapshot? = null
    lateinit var smartClipboard: SmartClipboardController
        private set
    private data class AcceptedEdit(val original: EditorTextSnapshot, val after: EditorTextSnapshot,
                                    val replacement: String, val receipt: List<String>)
    private var acceptedEdit: AcceptedEdit? = null
    private val isAiRephrasing = mutableStateOf(false)
    private val aiRephraseSuggestions = androidx.compose.runtime.mutableStateListOf<String>()
    private val isMicPermissionGranted = mutableStateOf(false)
    internal val pendingVoiceTranscript = mutableStateOf("")
    private var lastCommittedVoiceLength = 0

    // Ramble Mode states (Intent-based AI dictation)
    private val isRambleRecording = mutableStateOf(false)
    private val isRambleProcessing = mutableStateOf(false)
    private val rambleTranscript = mutableStateOf("")
    private var rambleRequestId = 0L
    private var ramblePolishJob: kotlinx.coroutines.Job? = null
    private val rambleAudioLevel = mutableStateOf(0f)

    // Touch and machine-learning pattern tracking states
    private var lastTapX = 0.5f
    private var lastTapY = 0.5f

    // Speech recognizer and MediaRecorder
    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var serviceJob = kotlinx.coroutines.SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var audioRecord: android.media.AudioRecord? = null
    private var isAudioRecordActive = false

    // Voice Typing and STT Service
    private lateinit var voiceRecordingService: VoiceRecordingSttService

    // Smart Select & On-Device Auto-Polish states
    val isSmartSelectOpen = mutableStateOf(false)
    val currentSmartSelectLevel = mutableStateOf(SmartSelectLevel.SENTENCE)
    val smartSelectFeedback = mutableStateOf<String?>(null)
    val showUndoAutoPolishPill = mutableStateOf(false)
    var undoAutoPolishSnapshot: UndoPolishData? = null

    private var audioManager: AudioManager? = null
    private var vibrator: Vibrator? = null

    private val dictUpdateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DictionaryUpdateService.ACTION_DICTIONARY_UPDATED) {
                Log.d("TypeRight", "Received ACTION_DICTIONARY_UPDATED broadcast, reloading SLM vocab...")
                dictionaryManager.reloadFromDatabase()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.init(this)
        AiPolishBackend.initialize(this)
        settings = KeyboardSettings(this)
        dictionaryManager = DictionaryManager.getInstance(this)
        dictionaryManager.gboardEngine
        dictionaryManager.correctionPipeline
        dictionaryManager.refreshContactWords()
        smartClipboard = SmartClipboardController(this, serviceScope) {
            settings.clipboardEnabled && currentInputEditorInfo != null && !isSensitiveField()
        }
        aiPolishManager = AiPolishManager(this)
        voiceRecordingService = VoiceRecordingSttService(this)
        composeSetup = ComposeSetup()
        val database = AppDatabase.getDatabase(this)
        clipboardRepository = ClipboardRepository(database.clipboardDao())

        // Register receiver for background SLM vocabulary updates
        try {
            val filter = android.content.IntentFilter(DictionaryUpdateService.ACTION_DICTIONARY_UPDATED)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(dictUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(dictUpdateReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w("TypeRight", "Failed to register dictUpdateReceiver: ${e.message}")
        }

        // Schedule periodic background dictionary update job
        DictionaryUpdateScheduler.schedulePeriodicUpdate(this)
        
        try {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } catch (_: Exception) {}

        // Initialize on-device speech processing engine
        WhisperCppBrain.loadGGMLModel(this, "whisper-tiny")

        // Warm up Room custom dictionary and frequent words caches
        serviceScope.launch {
            userDictionaryRepo.warmUpCaches(dictionaryManager)
        }

        startContextCorrections()
        // New input cancels the previous delay and computation. Results belong to one snapshot.
        serviceScope.launch {
            textBufferFlow.collectLatest { buffer ->
                try {
                    kotlinx.coroutines.delay(20L)
                    val result = withContext(Dispatchers.Default) {
                        if (buffer.isSensitive) {
                            AsyncKeyboardPredictions(source = buffer)
                        } else {
                            val pred = predictiveService.fetchSuggestions(buffer)
                            val gboard = GboardSuggestionResult(
                                leftCandidate = pred.leftCandidate,
                                centerCandidate = pred.centerCandidate,
                                rightCandidate = pred.rightCandidate,
                                isCenterAutocorrecting = pred.isCenterAutocorrecting
                            )
                            val malayalam = settings.keyboardLanguage.contains("Malayalam", ignoreCase = true)
                            val transliterations = if (buffer.activePrefix.isNotEmpty() &&
                                (malayalam || settings.manglishTransliterationEnabled)) {
                                manglishEngine.getTransliterationCandidates(buffer.activePrefix)
                            } else emptyList()
                            val finalResult = if (malayalam && transliterations.isNotEmpty()) gboard.copy(
                                leftCandidate = transliterations.getOrElse(1) { buffer.activePrefix },
                                centerCandidate = transliterations[0],
                                rightCandidate = transliterations.getOrElse(2) { "" },
                                isCenterAutocorrecting = true
                            ) else gboard
                            val suggestions = listOf(finalResult.leftCandidate, finalResult.centerCandidate,
                                if (!malayalam && transliterations.isNotEmpty()) transliterations[0] else finalResult.rightCandidate)

                            AsyncKeyboardPredictions(finalResult, suggestions, pred.phraseCompletions, buffer)
                        }
                    }
                    if (textBufferFlow.value == buffer && completedCorrection == null) asyncPredictionsState.value = result
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w("TypeRight", "Prediction failed: ${failure.javaClass.simpleName}")
                    if (textBufferFlow.value == buffer) asyncPredictionsState.value = AsyncKeyboardPredictions(source = buffer)
                }
            }
        }
    }

    data class ContextReview(val session: Long, val before: String, val after: String, val original: String, val replacement: String)
    val pendingContextReview = mutableStateOf<ContextReview?>(null)
    private var lastContextAttempt = ""

    private fun startContextCorrections() {
        serviceScope.launch {
            textBufferFlow.collectLatest { buffer ->
                pendingContextReview.value = null
                if (buffer.isSensitive || buffer.isUrl || buffer.isEmail || !settings.contextualCorrectionEnabled || !allowsAutocorrect()) return@collectLatest
                // Only sentence/word pauses. Candidate generation above never calls this engine.
                kotlinx.coroutines.delay(if (buffer.typedWord.isEmpty()) 650L else 1200L)
                if (!settings.contextualCorrectionEnabled || !allowsAutocorrect() || isVoiceTypingActive.value || isAiPolishing.value) return@collectLatest
                val ic = currentInputConnection ?: return@collectLatest
                if (!ic.getSelectedText(0).isNullOrEmpty()) return@collectLatest
                val before = ic.getTextBeforeCursor(20000, 0)?.toString().orEmpty()
                val after = ic.getTextAfterCursor(20000, 0)?.toString().orEmpty()
                if (after.isNotEmpty()) return@collectLatest
                val trimmed = before.trimEnd()
                val sentenceEnd = trimmed.trimEnd('.', '!', '?')
                val split = sentenceEnd.indexOfLast { it in ".!?\n" }
                val sentence = trimmed.substring(split + 1).trimStart()
                if (sentence.length !in 8..320 || sentence.count(Char::isWhitespace) < 2 || sentence == lastContextAttempt) return@collectLatest
                val session = editorSession
                lastContextAttempt = sentence
                try {
                    val result = withContext(Dispatchers.Default) {
                        NeuralCorrectionEngine.getInstance(applicationContext).contextualCorrection(sentence, settings.contextualModelId)
                    } ?: return@collectLatest
                    if (textBufferFlow.value == buffer && editorSession == session && currentInputConnection === ic &&
                        settings.contextualCorrectionEnabled && allowsAutocorrect() &&
                        ic.getTextBeforeCursor(20000, 0)?.toString() == before && ic.getTextAfterCursor(20000, 0)?.toString() == after) {
                        pendingContextReview.value = ContextReview(session, before, after, sentence, result)
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (failure: Exception) { Log.d("TypeRight", "Context correction unavailable: ${failure.javaClass.simpleName}") }
            }
        }
    }

    fun acceptContextCorrection(): Boolean {
        val review = pendingContextReview.value ?: return false
        pendingContextReview.value = null
        val trailing = review.before.takeLastWhile(Char::isWhitespace)
        val prefix = review.before.removeSuffix(trailing).removeSuffix(review.original)
        return applyEditorReplacement(EditorTextSnapshot(review.session, review.before, null, review.after),
            prefix + review.replacement + trailing + review.after, PolishMode.PROOFREAD)
    }
    fun dismissContextCorrection() { pendingContextReview.value = null }

    private fun cancelPendingPolish() {
        currentAiRequestId++
        currentAiJob?.cancel()
        currentAiJob = null
        rephraseSnapshot = null
        isAiPolishing.value = false
        isAiRephrasing.value = false
        isProofreadSheetOpen.value = false
        aiRephraseSuggestions.clear()
        try {
            PolishCoordinator.getInstance(this).cancelCurrent()
        } catch (_: Exception) {}
    }

    private fun resetEditorState() {
        editorSession++
        rambleRequestId++
        ramblePolishJob?.cancel()
        if (::voiceRecordingService.isInitialized) voiceRecordingService.cancelRecording()
        isVoiceTypingActive.value = false
        isRambleRecording.value = false
        isRambleProcessing.value = false
        pendingVoiceTranscript.value = ""
        cancelPendingPolish()
        currentTypedWord.value = ""
        wordUnderCursor.value = ""
        previousWord.value = null
        previousWord2.value = null
        previousWords.value = emptyList()
        currentWordTapCoords.clear()
        justAutocorrected = false
        lastOriginalWord = ""
        lastCorrectedWord = ""
        lastSwipeCommittedWord = null
        lastSpaceTime = 0L
        suggestionSpacePending = false
        lastComposedStart = -1
        lastComposedEnd = -1
        isSmartSelectOpen.value = false
        showUndoAutoPolishPill.value = false
        undoAutoPolishSnapshot = null
        asyncPredictionsState.value = AsyncKeyboardPredictions()
        val classification = getCurrentTextBoxInfo()
        textBufferFlow.value = TextInputBufferState(
            isSensitive = classification.isSensitive,
            isUrl = classification.isUrl,
            isEmail = classification.isEmail,
            isSearch = classification.isSearch,
            isWeb = classification.isWeb,
            isChat = classification.isChat,
            boxCategory = classification.category,
            timestamp = ++bufferGeneration
        )
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        if (::smartClipboard.isInitialized) smartClipboard.stop()
        acceptedEdit = null
        cancelWordCorrection()
        super.onStartInput(info, restarting)
        currentTextBoxInfo.value = TextBoxClassifier.classify(info)
        pendingContextReview.value = null
        lastContextAttempt = ""
        resetEditorState()
    }

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        try {
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setGravity(Gravity.BOTTOM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                win.isNavigationBarContrastEnforced = false
            }
        } catch (e: Exception) {
            Log.w("TypeRight", "onConfigureWindow note: ${e.message}")
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isProofreadSheetOpen.value = false
        try {
            PolishCoordinator.getInstance(this).cancelCurrent()
        } catch (_: Exception) {}
        currentTextBoxInfo.value = TextBoxClassifier.classify(info)
        val setup = composeSetup ?: ComposeSetup().also { composeSetup = it }
        setup.start()

        try {
            window?.window?.decorView?.let { decorView ->
                decorView.setViewTreeLifecycleOwner(setup)
                decorView.setViewTreeViewModelStoreOwner(setup)
                decorView.setViewTreeSavedStateRegistryOwner(setup)
            }
        } catch (e: Exception) {
            Log.w("TypeRight", "onStartInputView decorView warning: ${e.message}")
        }

        // Refresh microphone permission state
        isMicPermissionGranted.value = checkMicrophonePermission()
        currentTypedWord.value = ""
        asyncPredictionsState.value = AsyncKeyboardPredictions()
        updatePreviousWord()

        dictionaryManager.refreshContactWords()
        smartClipboard.start()

        // Capture new system clipboard content
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            if (!isSensitiveField() && settings.clipboardEnabled && clipboard != null && clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0 &&
                    clipData.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) != true) {
                    val text = clipData.getItemAt(0).text?.toString()
                    if (!text.isNullOrBlank()) {
                        serviceScope.launch {
                            clipboardRepository.insert(text)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TypeRight", "Failed to capture clipboard content", e)
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        
        val atComposingEnd = candidatesStart >= 0 && newSelStart == newSelEnd && newSelEnd == candidatesEnd
        if (currentTypedWord.value.isNotEmpty() && !atComposingEnd) {
            currentInputConnection?.finishComposingText()
            currentTypedWord.value = ""
            currentWordTapCoords.clear()
            justAutocorrected = false
        }
        lastCursorPosition = newSelStart
        lastComposedStart = candidatesStart
        lastComposedEnd = candidatesEnd
        if (!atComposingEnd) {
            updatePreviousWord()
        } else {
            // Even when at composing end, ensure surrounding word tokens and buffer reflect current word
            notifyTextBufferChanged()
        }
    }

    private fun isWordChar(c: Char): Boolean {
        return c.isLetterOrDigit() || c == '\'' || c == '-' || c == '_'
    }

    /**
     * Extracts the surrounding word tokens before and after the cursor.
     * Returns Pair(partBeforeCursor, partAfterCursor).
     * The full word at the cursor is partBeforeCursor + partAfterCursor.
     */
    private fun getSurroundingWordTokens(ic: InputConnection): Pair<String, String> {
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(64, 0)?.toString() ?: ""

        var startIdx = before.length
        while (startIdx > 0 && isWordChar(before[startIdx - 1])) {
            startIdx--
        }
        val partBefore = before.substring(startIdx)

        var endIdx = 0
        while (endIdx < after.length && isWordChar(after[endIdx])) {
            endIdx++
        }
        val partAfter = after.substring(0, endIdx)

        return Pair(partBefore, partAfter)
    }

    private fun getWordBeforeCursor(ic: InputConnection): String {
        val before = ic.getTextBeforeCursor(50, 0) ?: return ""
        if (before.isEmpty()) return ""
        var i = before.length - 1
        if (!isWordChar(before[i])) return ""
        while (i >= 0 && isWordChar(before[i])) {
            i--
        }
        return before.substring(i + 1).toString()
    }

    private fun updatePreviousWord() {
        if (isSensitiveField()) {
            // Do not read surrounding text from password editors or retain it in state.
            previousWord.value = null
            previousWord2.value = null
            previousWords.value = emptyList()
            wordUnderCursor.value = ""
            notifyTextBufferChanged()
            return
        }
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(150, 0)?.toString() ?: ""

        // 1. Calculate previousWord & previousThreeWords from single IPC fetch
        val trimmed = before.trim()
        val lastSpace = trimmed.lastIndexOf(' ')
        previousWord.value = if (lastSpace >= 0) {
            trimmed.substring(lastSpace + 1)
        } else if (trimmed.isNotEmpty()) {
            trimmed
        } else null

        val tokens = ArrayList<String>(4)
        val sb = java.lang.StringBuilder()
        for (c in before) {
            if (c.isLetterOrDigit() || c == '\'') {
                sb.append(c)
            } else {
                if (sb.isNotEmpty()) {
                    tokens.add(sb.toString())
                    sb.setLength(0)
                }
            }
        }
        if (sb.isNotEmpty()) {
            tokens.add(sb.toString())
        }
        val endsWithSpace = before.isNotEmpty() && before.last().isWhitespace()
        val contextTokens = if (endsWithSpace) tokens else if (tokens.isNotEmpty()) tokens.dropLast(1) else emptyList()
        previousWords.value = contextTokens.takeLast(5)

        // 2. Calculate full word under cursor (combining text before and after cursor)
        val (partBefore, partAfter) = getSurroundingWordTokens(ic)
        wordUnderCursor.value = partBefore + partAfter

        if (currentTypedWord.value.isEmpty()) {
            if (lastComposedStart != -1 || lastComposedEnd != -1) {
                lastComposedStart = -1
                lastComposedEnd = -1
                ic.finishComposingText()
            }
            if (!isCapsLockActive.value) {
                val shortBefore = if (before.length > 4) before.takeLast(4) else before
                if (shortBefore.isEmpty() || shortBefore.endsWith("\n")) {
                    isShiftActive.value = true
                } else {
                    val trimmedShort = shortBefore.trimEnd()
                    if (trimmedShort.isNotEmpty() && (trimmedShort.endsWith('.') || trimmedShort.endsWith('!') || trimmedShort.endsWith('?'))) {
                        if (shortBefore.endsWith(' ')) {
                            isShiftActive.value = true
                        }
                    }
                }
            }
        }

        notifyTextBufferChanged()
    }

    fun notifyTextBufferChanged() {
        val ic = currentInputConnection
        val (partBefore, partAfter) = if (ic != null) getSurroundingWordTokens(ic) else Pair(currentTypedWord.value, "")
        val fullWord = (partBefore + partAfter).trim()
        val active = if (fullWord.isNotEmpty()) fullWord else currentTypedWord.value.ifEmpty { wordUnderCursor.value }
        wordUnderCursor.value = active

        val boxInfo = getCurrentTextBoxInfo()
        val buffer = if (boxInfo.isSensitive) {
            TextInputBufferState(
                isSensitive = true,
                boxCategory = TextBoxCategory.PASSWORD,
                timestamp = ++bufferGeneration
            )
        } else {
            TextInputBufferState(
                typedWord = currentTypedWord.value,
                activePrefix = active,
                previousWord = previousWord.value,
                previousWords = previousWords.value.toList(),
                tapCoords = currentWordTapCoords.map { PointF(it.x, it.y) },
                isUrl = boxInfo.isUrl,
                isEmail = boxInfo.isEmail,
                isSensitive = false,
                isSearch = boxInfo.isSearch,
                isWeb = boxInfo.isWeb,
                isChat = boxInfo.isChat,
                boxCategory = boxInfo.category,
                timestamp = ++bufferGeneration
            )
        }
        pendingContextReview.value = null
        textBufferFlow.value = buffer
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        smartClipboard.stop()
        cancelWordCorrection()
        super.onFinishInputView(finishingInput)
        isProofreadSheetOpen.value = false
        try {
            PolishCoordinator.getInstance(this).cancelCurrent()
        } catch (_: Exception) {}
        resetEditorState()
        if (isVoiceTypingActive.value) {
            stopVoiceTyping(shouldPolish = false)
        }
        try {
            composeSetup?.stop()
        } catch (e: Exception) {
            Log.w("TypeRight", "onFinishInputView stop warning: ${e.message}")
        }
    }

    override fun onDestroy() {
        if (::smartClipboard.isInitialized) smartClipboard.stop()
        super.onDestroy()
        if (::voiceRecordingService.isInitialized) voiceRecordingService.cancelRecording()
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        try {
            unregisterReceiver(dictUpdateReceiver)
        } catch (_: Exception) {}
        try {
            composeSetup?.destroy()
        } catch (e: Exception) {
            Log.w("TypeRight", "onDestroy destroy warning: ${e.message}")
        }
        composeSetup = null
        serviceJob.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onKeyText(text: String) {
        handleKeyPress(text)
        notifyTextInput(text)
    }

    override fun onKeyDelete() {
        handleDelete()
        notifyDelete()
    }

    override fun onKeyEnter() {
        handleEnter()
        notifyEnter()
    }

    override fun onKeySpace() {
        handleSpace()
        notifySpace()
    }

    fun pasteSmartSuggestion(item: SmartClipSuggestion) {
        if (!settings.clipboardEnabled || isSensitiveField()) return
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        val session = editorSession
        val lifetime = if (item.code != null) SmartClipboardPolicy.OTP_LIFETIME else SmartClipboardPolicy.SCREENSHOT_LIFETIME
        if (!SmartClipboardPolicy.recent(item.timestamp, System.currentTimeMillis(), lifetime)) {
            smartClipboard.refresh(); return
        }
        if (item.code != null) {
            ic.finishComposingText()
            if (ic.commitText(item.code, 1)) {
                currentTypedWord.value = ""
                currentWordTapCoords.clear()
                smartClipboard.dismiss(item)
                updatePreviousWord()
            }
            return
        }
        if (!ScreenshotPaste.supported(info, item.mimeType)) {
            android.widget.Toast.makeText(this, "This app does not accept pasted images", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        serviceScope.launch {
            runCatching {
                val uri = ScreenshotPaste.prepare(this@TypeRightKeyboardService, item)
                if (session != editorSession || currentInputConnection !== ic || isSensitiveField() || !settings.clipboardEnabled) return@launch
                val content = androidx.core.view.inputmethod.InputContentInfoCompat(uri,
                    android.content.ClipDescription("Screenshot", arrayOf(item.mimeType)), null)
                val flag = if (Build.VERSION.SDK_INT >= 25) androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION else 0
                if (Build.VERSION.SDK_INT < 25 && !info.packageName.isNullOrBlank()) grantUriPermission(info.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (androidx.core.view.inputmethod.InputConnectionCompat.commitContent(ic, info, content, flag, null)) smartClipboard.dismiss(item)
                else android.widget.Toast.makeText(this@TypeRightKeyboardService, "This app could not paste the screenshot", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                android.widget.Toast.makeText(this@TypeRightKeyboardService, "Screenshot is no longer available", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun commitRichMedia(item: GiphyMediaItem) {
        serviceScope.launch {
            com.example.giphy.MediaCommitHelper.commitMedia(
                context = this@TypeRightKeyboardService,
                inputConnection = currentInputConnection,
                editorInfo = currentInputEditorInfo,
                item = item
            )
        }
    }

    override fun onCreateInputView(): View {
        val setup = composeSetup ?: ComposeSetup().also { composeSetup = it }
        setup.start()

        try {
            window?.window?.decorView?.let { decorView ->
                decorView.setViewTreeLifecycleOwner(setup)
                decorView.setViewTreeViewModelStoreOwner(setup)
                decorView.setViewTreeSavedStateRegistryOwner(setup)
            }
        } catch (e: Exception) {
            Log.w("TypeRight", "onCreateInputView decorView warning: ${e.message}")
        }

        val composeView = ComposeView(this)
        composeView.setViewTreeLifecycleOwner(setup)
        composeView.setViewTreeViewModelStoreOwner(setup)
        composeView.setViewTreeSavedStateRegistryOwner(setup)
        
        composeView.setViewCompositionStrategy(
            androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
        )

        composeView.setContent {
            val voiceFinishing by voiceRecordingService.isFinishing.collectAsState()
            val userPrefs by settings.dataStore.userPreferencesFlow.collectAsState(initial = settings.dataStore.currentSnapshot())
            val isDark = userPrefs.isDarkMode
            val isDynamic = userPrefs.dynamicThemeEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val colorScheme = if (isDynamic) {
                if (isDark) dynamicDarkColorScheme(this@TypeRightKeyboardService) else dynamicLightColorScheme(this@TypeRightKeyboardService)
            } else {
                if (isDark) darkColorScheme() else lightColorScheme()
            }
            MaterialTheme(colorScheme = colorScheme) {
                KeyboardLayout(
                    context = this@TypeRightKeyboardService,
                    settings = settings,
                    dictionaryManager = dictionaryManager,
                    isShift = isShiftActive.value,
                    isCapsLock = isCapsLockActive.value,
                    isSymbols = isSymbolLayerActive.value,
                    isEmojis = isEmojiLayerActive.value,
                    isClipboard = isClipboardLayerActive.value,
                    isVoiceTyping = isVoiceTypingActive.value,
                    isVoiceFinishing = voiceFinishing,
                    audioLevel = voiceAudioLevel.value,
                    isRambleRecording = isRambleRecording.value,
                    isRambleProcessing = isRambleProcessing.value,
                    rambleTranscript = rambleTranscript.value,
                    rambleAudioLevel = rambleAudioLevel.value,
                    onRambleToggle = { toggleRambleMode() },
                    onConfirmRamble = { confirmRambleMode() },
                    onCancelRamble = { cancelRambleMode() },
                    isPolishing = isAiPolishing.value,
                    micPermission = isMicPermissionGranted.value,
                    currentTypedWord = currentTypedWord.value,
                    wordUnderCursor = wordUnderCursor.value,
                    previousWord = previousWord.value,
                    previousWords = previousWords.value,
                    clipboardRepository = clipboardRepository,
                    onKeyClick = { handleKeyPress(it) },
                    onDelete = { handleDelete() },
                    onDeleteWord = { handleDeleteWord() },
                    onSpace = { handleSpace() },
                    onEnter = { handleEnter() },
                    onShiftToggle = { toggleShift() },
                    onSymbolsToggle = { toggleSymbols() },
                    onEmojiToggle = { toggleEmojis() },
                    onClipboardToggle = { toggleClipboard() },
                    onVoiceTypingToggle = { toggleVoiceTyping() },
                    onVoiceCancel = { cancelVoiceTyping() },
                    onAiPolishClick = { performDirectAiPolish() },
                    onProofreadClick = { performDirectLocalProofread() },
                    onSuggestionClick = { commitSuggestion(it) },
                    onRemoveSuggestion = { removeSuggestion(it) },
                    onOpenSettings = { launchSettingsActivity() },
                    isRephrasing = isAiPolishing.value,
                    aiRephraseSuggestions = aiRephraseSuggestions,
                    onAiRephraseClick = { },
                    onRephraseSuggestionClick = { commitRephraseSuggestion(it) },
                    onClearRephrasings = { aiRephraseSuggestions.clear() },
                    onTapCoordinates = { x, y ->
                        lastTapX = x
                        lastTapY = y
                    },
                    onSpaceSwipeLeft = {
                        if (isSmartSelectOpen.value) {
                            cycleSmartSelection(forward = false)
                        } else {
                            moveCursorLeft()
                        }
                    },
                    onSpaceSwipeRight = {
                        if (isSmartSelectOpen.value) {
                            cycleSmartSelection(forward = true)
                        } else {
                            moveCursorRight()
                        }
                    },
                    onUndo = { handleUndo() },
                    onRedo = { handleRedo() },
                    onAutoFormatClick = { performDirectAutoFormat() }
                )
            }
        }
        return composeView
    }

    fun isUrlField(): Boolean {
        val info = currentInputEditorInfo ?: return false
        return TextBoxClassifier.isUrlField(info)
    }

    fun isEmailField(): Boolean {
        val info = currentInputEditorInfo ?: return false
        return TextBoxClassifier.isEmailField(info)
    }

    fun isSensitiveField(): Boolean {
        val info = currentInputEditorInfo ?: return false
        return TextBoxClassifier.isSensitiveField(info)
    }

    fun isSearchField(): Boolean {
        val info = currentInputEditorInfo ?: return false
        return TextBoxClassifier.isSearchField(info)
    }

    fun isWebField(): Boolean {
        val info = currentInputEditorInfo ?: return false
        return TextBoxClassifier.isWebField(info)
    }

    fun isChatField(): Boolean {
        val info = currentInputEditorInfo ?: return false
        return TextBoxClassifier.isChatField(info)
    }

    fun captureEditorText(): EditorTextSnapshot? {
        if (!allowsTextAssistance()) return null
        val ic = currentInputConnection ?: return null
        ic.finishComposingText()
        currentTypedWord.value = ""
        currentWordTapCoords.clear()
        wordUnderCursor.value = ""
        notifyTextBufferChanged()
        return EditorTextSnapshot(editorSession, ic.getTextBeforeCursor(2000, 0)?.toString().orEmpty(),
            ic.getSelectedText(0)?.toString(), ic.getTextAfterCursor(2000, 0)?.toString().orEmpty())
    }

    fun captureFullEditorText(): EditorTextSnapshot? {
        if (!allowsTextAssistance()) return null
        val ic = currentInputConnection ?: return null
        ic.finishComposingText()
        currentTypedWord.value = ""
        currentWordTapCoords.clear()
        wordUnderCursor.value = ""
        notifyTextBufferChanged()

        var fullText = ""
        try {
            val req = android.view.inputmethod.ExtractedTextRequest().apply {
                flags = 0
                hintMaxChars = 100000
                hintMaxLines = 10000
            }
            val ext = ic.getExtractedText(req, 0)
            if (ext?.text != null && ext.text.isNotEmpty()) {
                fullText = ext.text.toString()
            }
        } catch (e: Exception) {
            Log.d("TypeRight", "getExtractedText error: ${e.message}")
        }

        val before = ic.getTextBeforeCursor(20000, 0)?.toString().orEmpty()
        val selected = ic.getSelectedText(0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(20000, 0)?.toString().orEmpty()

        if (fullText.isEmpty()) {
            fullText = if (selected.isNotEmpty()) {
                before + selected + after
            } else {
                before + after
            }
        }

        if (fullText.isBlank() && selected.isNotBlank()) {
            fullText = selected
        }

        // Select all text in editor so user sees full selection and replacement is atomic
        try {
            ic.setSelection(0, fullText.length)
            ic.performContextMenuAction(android.R.id.selectAll)
        } catch (_: Exception) {}

        return EditorTextSnapshot(
            session = editorSession,
            before = "",
            selected = fullText,
            after = ""
        )
    }

    fun applyEditorReplacement(snapshot: EditorTextSnapshot?, replacement: String, mode: PolishMode): Boolean {
        if (snapshot == null || snapshot.session != editorSession || !allowsTextAssistance()) return false
        if (acceptedEdit?.original == snapshot && replacement == snapshot.text) return undoEditorReplacement(snapshot)
        val ic = currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(20000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(20000, 0)?.toString().orEmpty()
        val selected = ic.getSelectedText(0)?.toString().orEmpty()
        val full = !snapshot.selected.isNullOrEmpty() && snapshot.before.isEmpty() && snapshot.after.isEmpty()
        if (full) {
            if (before + selected + after != snapshot.text) return false
        } else if (before != snapshot.before || after != snapshot.after || selected != snapshot.selected.orEmpty()) return false
        if (!AiOutputValidator.isValid(snapshot.text, replacement, mode)) return false
        val clean = AiOutputValidator.sanitize(replacement, snapshot.text)
        if (clean.isEmpty() || clean == snapshot.text) return false
        var committed = false
        ic.beginBatchEdit()
        try {
            ic.finishComposingText()
            if (full) {
                ic.performContextMenuAction(android.R.id.selectAll)
                if (ic.getSelectedText(0)?.toString() == snapshot.text) {
                    committed = ic.commitText(clean, 1)
                } else if (ic.getTextBeforeCursor(20000, 0)?.toString().orEmpty() +
                    ic.getSelectedText(0)?.toString().orEmpty() + ic.getTextAfterCursor(20000, 0)?.toString().orEmpty() == snapshot.text) {
                    if (ic.getSelectedText(0).isNullOrEmpty() && ic.deleteSurroundingText(before.length, after.length)) committed = ic.commitText(clean, 1)
                }
            } else if (!snapshot.selected.isNullOrEmpty()) committed = ic.commitText(clean, 1)
            else if (ic.deleteSurroundingText(snapshot.before.length, snapshot.after.length)) committed = ic.commitText(clean, 1)
        } finally { ic.endBatchEdit() }
        if (!committed) return false
        val receipt = if (mayLearn()) dictionaryManager.personalProfile.acceptPolish(snapshot.text, clean) {
            dictionaryManager.isRecognizedInAnyLanguage(it) && !dictionaryManager.isBlocked(it) && !dictionaryManager.gboardEngine.isKnownTypo(it)
        } else emptyList()
        acceptedEdit = AcceptedEdit(snapshot, EditorTextSnapshot(editorSession,
            ic.getTextBeforeCursor(20000, 0)?.toString().orEmpty(), ic.getSelectedText(0)?.toString(),
            ic.getTextAfterCursor(20000, 0)?.toString().orEmpty()), clean, receipt)
        justAutocorrected = false
        updatePreviousWord()
        return true
    }

    fun undoEditorReplacement(snapshot: EditorTextSnapshot?): Boolean {
        val edit = acceptedEdit ?: return false
        if (snapshot == null || snapshot != edit.original || snapshot.session != editorSession) return false
        val ic = currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(20000, 0)?.toString().orEmpty()
        if (before != edit.after.before || ic.getTextAfterCursor(20000, 0)?.toString().orEmpty() != edit.after.after ||
            ic.getSelectedText(0)?.toString().orEmpty() != edit.after.selected.orEmpty() || !before.endsWith(edit.replacement)) return false
        var restored = false
        ic.beginBatchEdit()
        try {
            ic.finishComposingText()
            if (ic.deleteSurroundingText(edit.replacement.length, 0)) restored = ic.commitText(snapshot.text, 1)
        } finally { ic.endBatchEdit() }
        if (restored) {
            dictionaryManager.personalProfile.retract(edit.receipt)
            acceptedEdit = null
            updatePreviousWord()
        }
        return restored
    }

    // --- SMART SELECT & ON-DEVICE AUTO-POLISH ENGINE ---

    fun computeSmartSelection(level: SmartSelectLevel): SmartSelectionBounds? {
        val ic = currentInputConnection ?: return null
        var fullText = ""
        var cursorPosition = 0

        // Strategy 1: Attempt getExtractedText for accurate global cursor & text
        try {
            val req = android.view.inputmethod.ExtractedTextRequest()
            val ext = ic.getExtractedText(req, 0)
            if (ext?.text != null) {
                fullText = ext.text.toString()
                val selStart = ext.selectionStart.coerceIn(0, fullText.length)
                val selEnd = ext.selectionEnd.coerceIn(0, fullText.length)
                cursorPosition = minOf(selStart, selEnd)
            }
        } catch (e: Exception) {
            Log.d("TypeRight", "getExtractedText fallback: ${e.message}")
        }

        // Strategy 2: Fallback to surrounding text
        if (fullText.isEmpty()) {
            val before = ic.getTextBeforeCursor(4000, 0)?.toString().orEmpty()
            val selected = ic.getSelectedText(0)?.toString().orEmpty()
            val after = ic.getTextAfterCursor(4000, 0)?.toString().orEmpty()
            fullText = before + selected + after
            cursorPosition = before.length
        }

        if (fullText.isEmpty()) return null
        cursorPosition = cursorPosition.coerceIn(0, fullText.length)

        val (start, end) = when (level) {
            SmartSelectLevel.ALL -> 0 to fullText.length
            SmartSelectLevel.PARAGRAPH -> {
                var s = cursorPosition
                while (s > 0 && fullText[s - 1] != '\n') {
                    s--
                }
                var e = cursorPosition
                while (e < fullText.length && fullText[e] != '\n') {
                    e++
                }
                while (s < e && fullText[s].isWhitespace() && fullText[s] != '\n') s++
                while (e > s && fullText[e - 1].isWhitespace() && fullText[e - 1] != '\n') e--
                s to e
            }
            SmartSelectLevel.SENTENCE -> {
                var s = cursorPosition
                while (s > 0) {
                    val prev = fullText[s - 1]
                    if (prev == '\n') break
                    if (prev in ".!?" && (s >= fullText.length || fullText[s].isWhitespace())) {
                        break
                    }
                    s--
                }
                while (s < fullText.length && fullText[s].isWhitespace() && fullText[s] != '\n') {
                    s++
                }
                var e = cursorPosition
                while (e < fullText.length) {
                    val ch = fullText[e]
                    if (ch == '\n') break
                    if (ch in ".!?") {
                        e++
                        break
                    }
                    e++
                }
                if (e < s) e = s
                s to e
            }
            SmartSelectLevel.WORD -> {
                var s = cursorPosition
                while (s > 0 && !fullText[s - 1].isWhitespace() && fullText[s - 1] !in ".,!?;:\"'()[]{}<>\n") {
                    s--
                }
                var e = cursorPosition
                while (e < fullText.length && !fullText[e].isWhitespace() && fullText[e] !in ".,!?;:\"'()[]{}<>\n") {
                    e++
                }
                s to e
            }
        }

        val safeStart = start.coerceIn(0, fullText.length)
        val safeEnd = end.coerceIn(safeStart, fullText.length)
        val selectedText = if (safeEnd > safeStart) fullText.substring(safeStart, safeEnd) else ""
        return SmartSelectionBounds(fullText, safeStart, safeEnd, selectedText, level)
    }

    fun applySmartSelection(level: SmartSelectLevel): SmartSelectionBounds? {
        val ic = currentInputConnection ?: return null
        val bounds = computeSmartSelection(level) ?: return null

        try {
            ic.setSelection(bounds.startIndex, bounds.endIndex)
        } catch (e: Exception) {
            Log.w("TypeRight", "setSelection failed: ${e.message}")
        }
        if (level == SmartSelectLevel.ALL) {
            try {
                ic.performContextMenuAction(android.R.id.selectAll)
            } catch (_: Exception) {}
        }

        currentSmartSelectLevel.value = level
        playFeedback(FeedbackType.Standard)
        val charCount = bounds.selectedText.length
        val preview = if (bounds.selectedText.length > 25) bounds.selectedText.take(22) + "..." else bounds.selectedText
        smartSelectFeedback.value = "${level.label} selected ($charCount chars): \"$preview\""
        return bounds
    }

    fun cycleSmartSelection(forward: Boolean = true): SmartSelectionBounds? {
        val levels = SmartSelectLevel.values()
        val currentIndex = levels.indexOf(currentSmartSelectLevel.value)
        val nextIndex = if (forward) {
            (currentIndex + 1).coerceAtMost(levels.size - 1)
        } else {
            (currentIndex - 1).coerceAtLeast(0)
        }
        val nextLevel = levels[nextIndex]
        return applySmartSelection(nextLevel)
    }

    fun autoPolishWithGemini(targetScope: SmartSelectLevel? = null) {
        if (!allowsTextAssistance()) return
        val ic = currentInputConnection ?: return
        val requestId = ++currentAiRequestId
        val before = ic.getTextBeforeCursor(10000, 0)?.toString()
        val after = ic.getTextAfterCursor(10000, 0)?.toString()
        val selection = ic.getSelectedText(0)?.toString()
        ic.finishComposingText()

        // Always target the entire text in the field to format, edit, spell-check, and proofread fully
        val bounds = computeSmartSelection(SmartSelectLevel.ALL)
        val textToPolish = bounds?.selectedText?.trim() ?: ""
        if (textToPolish.isBlank()) {
            smartSelectFeedback.value = "Type some text first to polish"
            playFeedback(FeedbackType.Standard)
            return
        }

        isAiPolishing.value = true
        smartSelectFeedback.value = "Polishing with ${AiPolishBackend.label}..."
        playFeedback(FeedbackType.Standard)

        currentAiJob?.cancel()
        currentAiJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            var polishedResult: String? = null
            try {
                kotlinx.coroutines.withTimeoutOrNull(AiPolishBackend.timeoutMillis) {
                    AiPolishBackend.generatePolish(
                        input = textToPolish,
                        mode = PolishMode.PROOFREAD
                    )
                }?.let { polishedResult = it }
            } catch (e: Exception) {
                isAiPolishing.value = false
                if (e is kotlinx.coroutines.CancellationException) throw e
                smartSelectFeedback.value = e.message ?: "Local polish failed"
                return@launch
            }

            val finalPolished = if (!polishedResult.isNullOrBlank()) {
                AiOutputValidator.sanitize(polishedResult!!, textToPolish)
            } else {
                withContext(Dispatchers.Default) {
                    try {
                        OnDeviceNeuralPolishEngine.getInstance(applicationContext).quickProofread(textToPolish)
                    } catch (e: Exception) {
                        DeviceAiCoreEngine.getInstance(applicationContext).proofread(textToPolish, "Proofread").correctedText
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (requestId != currentAiRequestId) return@withContext
                isAiPolishing.value = false
                if (!allowsTextAssistance() || currentInputConnection != ic ||
                    ic.getTextBeforeCursor(10000, 0)?.toString() != before ||
                    ic.getTextAfterCursor(10000, 0)?.toString() != after ||
                    ic.getSelectedText(0)?.toString() != selection) {
                    smartSelectFeedback.value = "Text changed; polish again"
                    return@withContext
                }
                if (finalPolished.isNotBlank() && finalPolished != textToPolish) {
                    ic.beginBatchEdit()
                    try {
                        try {
                            ic.performContextMenuAction(android.R.id.selectAll)
                        } catch (_: Exception) {}
                        ic.commitText(finalPolished, 1)
                    } finally {
                        ic.endBatchEdit()
                    }

                    val durationMs = System.currentTimeMillis() - startTime
                    AiExecutionLogger.logAiAction(
                        context = applicationContext,
                        operation = "AI Auto-Polish",
                        engine = if (!polishedResult.isNullOrBlank()) AiPolishBackend.label else "Basic offline correction",
                        input = textToPolish,
                        output = finalPolished,
                        durationMs = durationMs
                    )

                    undoAutoPolishSnapshot = UndoPolishData(
                        originalText = textToPolish,
                        replacedText = finalPolished,
                        isSelection = false,
                        selectionStart = 0,
                        selectionEnd = textToPolish.length,
                        timestamp = System.currentTimeMillis()
                    )
                    showUndoAutoPolishPill.value = true
                    smartSelectFeedback.value = "Polished with ${AiPolishBackend.label} ($durationMs ms)"
                    playFeedback(FeedbackType.Standard)
                } else {
                    smartSelectFeedback.value = "Text is already clear and polished!"
                    playFeedback(FeedbackType.Standard)
                }
            }
        }
    }

    fun undoGeminiAutoPolish() {
        val snapshot = undoAutoPolishSnapshot ?: return
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        try {
            ic.deleteSurroundingText(snapshot.replacedText.length, 0)
            ic.commitText(snapshot.originalText, 1)
        } finally {
            ic.endBatchEdit()
        }
        undoAutoPolishSnapshot = null
        showUndoAutoPolishPill.value = false
        smartSelectFeedback.value = "Reverted to original text"
        playFeedback(FeedbackType.Standard)
    }

    private fun formatGrammarCheckedText(word: String): CharSequence {
        return word
    }

    fun allowsTextAssistance(): Boolean {
        val info = currentInputEditorInfo ?: return false
        return !isSensitiveField()
    }

    fun allowsAutocorrect(): Boolean {
        val info = currentInputEditorInfo ?: return false
        return TextBoxClassifier.classify(info).allowsAutocorrect
    }

    fun allowsAiPolish(): Boolean {
        val info = currentInputEditorInfo ?: return false
        return TextBoxClassifier.classify(info).allowsAiPolish
    }

    private fun mayLearn(): Boolean = settings.personalizedLearningEnabled &&
        ((currentInputEditorInfo?.inputType ?: 0) and android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_TEXT && allowsTextAssistance() && !isUrlField() && !isEmailField() &&
        ((currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0

    // IME thread: only read an already ranked result; never build indexes or run ranking/inference here.
    private fun getAutoCorrectedWord(prefix: String): String? {
        if (!settings.autocorrectEnabled || !allowsAutocorrect()) return null
        return dictionaryManager.correctionPipeline.cached(prefix, previousWords.value,
            currentWordTapCoords.toList())?.automatic
    }

    private var boundaryJob: Job? = null
    private var unlearnedBoundary: Pair<String, List<String>>? = null
    private var boundaryGeneration = 0L
    private data class CompletedCorrection(val original: String, val trailing: String, val before: String,
                                           val context: List<String>, val ranked: RankedCorrection)
    private var completedCorrection: CompletedCorrection? = null
    private data class AutoCorrectionEvent(val original: String, val replacement: String, val context: List<String>, val at: Long)
    private var lastAutoCorrection: AutoCorrectionEvent? = null

    private fun cancelWordCorrection() {
        unlearnedBoundary?.let { (word, prior) -> learnWordAndContext(word, contextOverride = prior) }
        unlearnedBoundary = null
        boundaryGeneration++
        boundaryJob?.cancel()
        completedCorrection = null
    }

    private fun observeManualReversal(word: String, context: List<String>) {
        val event = lastAutoCorrection ?: return
        if (mayLearn() && android.os.SystemClock.uptimeMillis() - event.at < 8000 &&
            word.equals(event.original, true) && context.takeLast(2) == event.context.takeLast(2)) {
            dictionaryManager.suppressCorrection(event.original, event.replacement)
            lastAutoCorrection = null
        }
    }

    private fun rankCompletedWord(original: String, trailing: String, context: List<String>, taps: List<PointF>) {
        if (!settings.autocorrectEnabled || !allowsAutocorrect()) { learnWordAndContext(original); return }
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(20000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(20000, 0)?.toString().orEmpty()
        val suffix = original + trailing
        if (!before.endsWith(suffix)) return
        val session = editorSession
        val request = ++boundaryGeneration
        boundaryJob?.cancel()
        unlearnedBoundary = original to context
        boundaryJob = serviceScope.launch {
            val result = withContext(Dispatchers.Default) { dictionaryManager.correctionPipeline.rank(original, context, taps) }
            if (request != boundaryGeneration || session != editorSession || currentInputConnection !== ic ||
                !allowsAutocorrect() || !settings.autocorrectEnabled || currentTypedWord.value.isNotEmpty() ||
                ic.getTextBeforeCursor(20000, 0)?.toString().orEmpty() != before ||
                ic.getTextAfterCursor(20000, 0)?.toString().orEmpty() != after || !ic.getSelectedText(0).isNullOrEmpty()) return@launch
            val correction = result.automatic
            if (correction != null) {
                ic.beginBatchEdit()
                try {
                    if (!ic.deleteSurroundingText(suffix.length, 0) || !ic.commitText(correction + trailing, 1)) return@launch
                } finally { ic.endBatchEdit() }
                AutocorrectMetrics.recordApplied()
                lastOriginalWord = original
                lastCorrectedWord = correction + trailing
                lastCorrectedWasSpace = false
                justAutocorrected = true
                lastAutoCorrection = AutoCorrectionEvent(original, correction, context, android.os.SystemClock.uptimeMillis())
                unlearnedBoundary = null
                learnWordAndContext(correction, contextOverride = context)
                updatePreviousWord()
            } else {
                unlearnedBoundary = null
                learnWordAndContext(original, contextOverride = context)
                if (result.tier == ConfidenceTier.MEDIUM) {
                    completedCorrection = CompletedCorrection(original, trailing, before, context, result)
                    val proposal = result.suggestion
                    asyncPredictionsState.value = AsyncKeyboardPredictions(
                        gboardResult = GboardSuggestionResult(original, proposal, "", false),
                        suggestions = listOf(original, proposal, ""), source = textBufferFlow.value)
                }
            }
        }
    }

    private fun commitWordWithSmartCorrection(ic: InputConnection, prefix: String, trailingText: String = "") {
        val context = previousWords.value.toList()
        val taps = currentWordTapCoords.toList()
        observeManualReversal(prefix, context)
        val corrected = getAutoCorrectedWord(prefix) ?: prefix
        if (!ic.commitText(corrected + trailingText, 1)) return
        lastOriginalWord = prefix
        lastCorrectedWord = corrected + trailingText
        justAutocorrected = corrected != prefix
        lastCorrectedWasSpace = false
        if (justAutocorrected) {
            AutocorrectMetrics.recordApplied()
            lastAutoCorrection = AutoCorrectionEvent(prefix, corrected, context, android.os.SystemClock.uptimeMillis())
            learnWordAndContext(corrected)
        } else rankCompletedWord(prefix, trailingText, context, taps)
    }

    private fun restoreCasing(original: String, target: String): String {
        if (original.isEmpty() || target.isEmpty()) return target
        if (original.all { it.isUpperCase() }) {
            return target.uppercase()
        }
        if (original[0].isUpperCase()) {
            return target.replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() }
        }
        return target
    }

    private fun handleKeyPress(text: String) {
        cancelWordCorrection()
        cancelPendingPolish()
        lastSpaceTime = 0L
        lastSwipeCommittedWord = null
        playFeedback()
        if (isVoiceTypingActive.value) {
            stopVoiceTyping(shouldPolish = false)
        }
        val ic = currentInputConnection ?: return
        
        // Typing clears any outstanding AI suggestions
        aiRephraseSuggestions.clear()
        
        // Typing any letter clears the instant undo window
        justAutocorrected = false
        
        // If it's a string representing emojis (e.g. contains surrogate pairs) or is more than 1 character and contains no letter, or is specifically an emoji:
        if (text.length > 1 && (text.any { it.isSurrogate() } || !text.any { it.isLetterOrDigit() })) {
            // Commit any existing word first
            if (currentTypedWord.value.isNotEmpty()) {
                val prefix = currentTypedWord.value
                val corrected = getAutoCorrectedWord(prefix)
                ic.commitText(corrected ?: prefix, 1)
                currentTypedWord.value = ""
            }
            ic.commitText(text, 1)
            updatePreviousWord()
            return
        }
        
        // If it's a single character:
        if (text.length == 1) {
            val char = text[0]
            
            // If it is a surrogate char or is not standard letter/digit (like some emojis that are single char):
            if (char.isSurrogate() || (!TypingPolicy.isWordCharacter(char) && char != ',' && char != '.' && char != '!' && char != '?' && char != '@' && char != '#' && char != '$')) {
                if (currentTypedWord.value.isNotEmpty()) {
                    val prefix = currentTypedWord.value
                    commitWordWithSmartCorrection(ic, prefix, "")
                    currentTypedWord.value = ""
                }
                ic.commitText(text, 1)
                updatePreviousWord()
                return
            }
            
            // If it's a standard grammatical punctuation, apply Gboard-style smart spacing & auto-correction
            if (char == ',' || char == '.' || char == '!' || char == '?') {
                if (currentTypedWord.value.isNotEmpty()) {
                    val prefix = currentTypedWord.value
                    commitWordWithSmartCorrection(ic, prefix, char.toString())
                    currentTypedWord.value = ""
                } else {
                    // Check if there is a trailing space before cursor
                    val before = ic.getTextBeforeCursor(1, 0) ?: ""
                    if (suggestionSpacePending && before == " ") {
                        ic.deleteSurroundingText(1, 0)
                    }
                    ic.commitText(char.toString(), 1)
                    justAutocorrected = false
                }
                suggestionSpacePending = false
                updatePreviousWord()
                return
            }

            // For other symbols (@, #, $), commit the current word, then commit the symbol literally
            if (char == '@' || char == '#' || char == '$') {
                if (currentTypedWord.value.isNotEmpty()) {
                    val prefix = currentTypedWord.value
                    commitWordWithSmartCorrection(ic, prefix, "")
                    currentTypedWord.value = ""
                }
                ic.commitText(char.toString(), 1)
                updatePreviousWord()
                return
            }

            val letter = if (isShiftActive.value) char.uppercaseChar().toString() else char.toString()
            
            // Train the typing offset ML model for alphabetical characters
            if (mayLearn() && char.lowercaseChar() in 'a'..'z') {
                dictionaryManager.learnTapPattern(char, lastTapX, lastTapY)
            }

            val wasEmpty = currentTypedWord.value.isEmpty()
            if (wasEmpty) {
                // If cursor is within an already typed word, adopt the prefix but remove from editor to prevent doubling
                val (partBefore, partAfter) = getSurroundingWordTokens(ic)
                if (partBefore.isNotEmpty() || partAfter.isNotEmpty()) {
                    if (partBefore.isNotEmpty()) {
                        ic.deleteSurroundingText(partBefore.length, 0)
                    }
                    currentTypedWord.value = partBefore
                    currentWordTapCoords.clear()
                } else {
                    ic.finishComposingText()
                    currentWordTapCoords.clear()
                }
            }

            currentTypedWord.value += letter
            val centroid = dictionaryManager.gboardEngine.spatialModel.getKeyCentroid(char)
            val tapX = if (lastTapX != 0.5f) lastTapX else (centroid?.x ?: 0.5f)
            val tapY = if (lastTapY != 0.5f) lastTapY else (centroid?.y ?: 0.5f)
            currentWordTapCoords.add(PointF(tapX, tapY))
            
            ic.setComposingText(currentTypedWord.value, 1)
            val (partB, partA) = getSurroundingWordTokens(ic)
            val fullCurrentWord = (partB + partA).ifEmpty { currentTypedWord.value }
            wordUnderCursor.value = fullCurrentWord

            notifyTextBufferChanged()

            // Auto-disable shift if it wasn't caps locked
            if (isShiftActive.value && !isCapsLockActive.value) {
                isShiftActive.value = false
            }
        } else {
            // Fallback for any other multi-character input
            if (currentTypedWord.value.isNotEmpty()) {
                val prefix = currentTypedWord.value
                val corrected = getAutoCorrectedWord(prefix)
                ic.commitText(corrected ?: prefix, 1)
                currentTypedWord.value = ""
            }
            ic.commitText(text, 1)
            updatePreviousWord()
        }
    }

    private fun handleDelete() {
        cancelWordCorrection()
        cancelPendingPolish()
        lastSpaceTime = 0L
        lastSwipeCommittedWord = null
        suggestionSpacePending = false
        playFeedback(FeedbackType.Delete)
        if (isVoiceTypingActive.value) stopVoiceTyping(shouldPolish = false)
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        try {
            if (!ic.getSelectedText(0).isNullOrEmpty()) {
                ic.commitText("", 1)
                currentTypedWord.value = ""
                currentWordTapCoords.clear()
                justAutocorrected = false
            } else {
                val suffix = lastCorrectedWord + if (lastCorrectedWasSpace) " " else ""
                val canUndo = allowsTextAssistance() && justAutocorrected && lastCorrectedWord.isNotEmpty() &&
                    ic.getTextBeforeCursor(suffix.length, 0)?.toString() == suffix
                if (canUndo) {
                    AutocorrectMetrics.recordUndo()
                    ic.deleteSurroundingText(suffix.length, 0)
                    currentTypedWord.value = lastOriginalWord
                    currentWordTapCoords.clear()
                    ic.setComposingText(lastOriginalWord, 1)
                    dictionaryManager.suppressCorrection(lastOriginalWord, lastCorrectedWord.trim().trimEnd(',', '.', '!', '?'), persistFeedback = mayLearn())
                } else if (currentTypedWord.value.isNotEmpty()) {
                    currentTypedWord.value = currentTypedWord.value.dropLast(TypingPolicy.lastCharacterLength(currentTypedWord.value))
                    currentWordTapCoords.clear()
                    ic.setComposingText(currentTypedWord.value, 1)
                    if (currentTypedWord.value.isEmpty()) ic.finishComposingText()
                } else {
                    val before = ic.getTextBeforeCursor(128, 0)?.toString().orEmpty()
                    if (before.isNotEmpty()) ic.deleteSurroundingText(TypingPolicy.lastCharacterLength(before), 0)
                }
                justAutocorrected = false
            }
        } finally { ic.endBatchEdit() }
        updatePreviousWord()
    }

    private fun handleDeleteWord() {
        cancelPendingPolish()
        playFeedback(FeedbackType.Delete)
        val ic = currentInputConnection ?: return
        
        // Word delete clears any outstanding AI suggestions
        aiRephraseSuggestions.clear()
        
        justAutocorrected = false
        
        // 1. If we are currently composing a word, clear it!
        if (currentTypedWord.value.isNotEmpty()) {
            currentTypedWord.value = ""
            ic.setComposingText("", 1)
            ic.finishComposingText()
            updatePreviousWord()
            return
        }
        
        // 2. Otherwise, look at the text before the cursor to find the previous word boundaries
        val textBefore = ic.getTextBeforeCursor(100, 0) ?: ""
        if (textBefore.isEmpty()) {
            return
        }
        
        var i = textBefore.length - 1
        
        // First, skip any trailing whitespaces or punctuation
        while (i >= 0 && !textBefore[i].isLetterOrDigit()) {
            i--
        }
        
        // Then, skip the word (letter or digit characters)
        while (i >= 0 && textBefore[i].isLetterOrDigit()) {
            i--
        }
        
        val charsToDelete = textBefore.length - (i + 1)
        if (charsToDelete > 0) {
            ic.deleteSurroundingText(charsToDelete, 0)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        updatePreviousWord()
    }

    private fun handleSpace() {
        cancelWordCorrection()
        cancelPendingPolish()
        lastSwipeCommittedWord = null
        playFeedback(FeedbackType.Space)
        if (isVoiceTypingActive.value) stopVoiceTyping(shouldPolish = false)
        val ic = currentInputConnection ?: return
        val now = android.os.SystemClock.uptimeMillis()
        ic.finishComposingText()

        val (partBefore, partAfter) = getSurroundingWordTokens(ic)
        val fullWord = partBefore + partAfter

        ic.beginBatchEdit()
        try {
            if (fullWord.isNotEmpty()) {
                val context = previousWords.value.toList()
                val taps = currentWordTapCoords.toList()
                observeManualReversal(fullWord, context)
                val corrected = getAutoCorrectedWord(fullWord)
                if (corrected != null && !corrected.equals(fullWord, ignoreCase = true)) {
                    ic.deleteSurroundingText(partBefore.length, partAfter.length)
                    ic.commitText("$corrected ", 1)
                    lastOriginalWord = fullWord
                    lastCorrectedWord = "$corrected "
                    justAutocorrected = true
                    lastCorrectedWasSpace = false
                    AutocorrectMetrics.recordApplied()
                    lastAutoCorrection = AutoCorrectionEvent(fullWord, corrected, context, android.os.SystemClock.uptimeMillis())
                    learnWordAndContext(corrected)
                } else {
                    ic.commitText(" ", 1)
                    rankCompletedWord(fullWord, " ", context, taps)
                    justAutocorrected = false
                    lastCorrectedWasSpace = false
                }
            } else {
                val before = if (allowsTextAssistance()) ic.getTextBeforeCursor(2, 0)?.toString().orEmpty() else ""
                if (lastSpaceTime != 0L && TypingPolicy.shouldInsertPeriod(before, settings.doubleSpacePeriod, now - lastSpaceTime)) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                } else ic.commitText(" ", 1)
                justAutocorrected = false
                lastCorrectedWasSpace = false
            }
        } finally { ic.endBatchEdit() }
        lastSpaceTime = now
        suggestionSpacePending = false
        currentTypedWord.value = ""
        currentWordTapCoords.clear()
        updatePreviousWord()
    }

    private fun handleEnter() {
        cancelPendingPolish()
        lastSwipeCommittedWord = null
        playFeedback(FeedbackType.Enter)
        if (isVoiceTypingActive.value) {
            stopVoiceTyping(shouldPolish = false)
        }
        val ic = currentInputConnection ?: return
        
        if (currentTypedWord.value.isNotEmpty()) {
            val prefix = currentTypedWord.value
            commitWordWithSmartCorrection(ic, prefix, "")
            currentTypedWord.value = ""
        } else {
            justAutocorrected = false
        }

        val info = currentInputEditorInfo ?: return
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION

        when (action) {
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND -> {
                ic.performEditorAction(action)
            }
            else -> {
                ic.commitText("\n", 1)
            }
        }
        updatePreviousWord()
    }

    override fun undoText() {
        handleUndo()
    }

    override fun redoText() {
        handleRedo()
    }

    private fun handleUndo() {
        playFeedback()
        if (currentTypedWord.value.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            currentTypedWord.value = ""
            currentWordTapCoords.clear()
        }
        val ic = currentInputConnection ?: return
        val success = ic.performContextMenuAction(android.R.id.undo)
        if (!success) {
            val now = android.os.SystemClock.uptimeMillis()
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON))
        }
        updatePreviousWord()
    }

    private fun handleRedo() {
        playFeedback()
        if (currentTypedWord.value.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            currentTypedWord.value = ""
            currentWordTapCoords.clear()
        }
        val ic = currentInputConnection ?: return
        val success = ic.performContextMenuAction(android.R.id.redo)
        if (!success) {
            val now = android.os.SystemClock.uptimeMillis()
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Y, 0, KeyEvent.META_CTRL_ON))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Y, 0, KeyEvent.META_CTRL_ON))
        }
        updatePreviousWord()
    }

    fun moveCursorLeft() {
        if (currentTypedWord.value.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            currentTypedWord.value = ""
            currentWordTapCoords.clear()
        }
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
        playFeedback(FeedbackType.Standard)
    }

    fun moveCursorRight() {
        if (currentTypedWord.value.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            currentTypedWord.value = ""
            currentWordTapCoords.clear()
        }
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
        playFeedback(FeedbackType.Standard)
    }

    private fun toggleShift() {
        playFeedback()
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastShiftClickTime < 350) {
            // Double tap - activate Caps Lock!
            isCapsLockActive.value = true
            isShiftActive.value = true
        } else {
            // Single tap - toggle normal shift
            if (isCapsLockActive.value) {
                isCapsLockActive.value = false
                isShiftActive.value = false
            } else {
                isShiftActive.value = !isShiftActive.value
            }
        }
        lastShiftClickTime = currentTime
    }

    private fun toggleSymbols() {
        playFeedback()
        isSymbolLayerActive.value = !isSymbolLayerActive.value
        isEmojiLayerActive.value = false
        isClipboardLayerActive.value = false
    }

    private fun toggleEmojis() {
        playFeedback()
        isEmojiLayerActive.value = !isEmojiLayerActive.value
        isSymbolLayerActive.value = false
        isClipboardLayerActive.value = false
    }

    private fun toggleClipboard() {
        playFeedback()
        isClipboardLayerActive.value = !isClipboardLayerActive.value
        isEmojiLayerActive.value = false
        isSymbolLayerActive.value = false
    }

    fun removeSuggestion(word: String) {
        val clean = word.trim()
        if (clean.isBlank()) return
        cancelPendingPolish()
        playFeedback()
        dictionaryManager.blockSuggestion(clean)

        // Immediately filter out the removed word from current active prediction state
        val current = asyncPredictionsState.value
        val updatedSuggestions = current.suggestions.map { if (it.equals(clean, ignoreCase = true)) "" else it }
        val updatedGboard = current.gboardResult.copy(
            leftCandidate = if (current.gboardResult.leftCandidate.equals(clean, ignoreCase = true)) "" else current.gboardResult.leftCandidate,
            centerCandidate = if (current.gboardResult.centerCandidate.equals(clean, ignoreCase = true)) "" else current.gboardResult.centerCandidate,
            rightCandidate = if (current.gboardResult.rightCandidate.equals(clean, ignoreCase = true)) "" else current.gboardResult.rightCandidate
        )
        asyncPredictionsState.value = current.copy(gboardResult = updatedGboard, suggestions = updatedSuggestions)

        // Force refresh predictions for current active input buffer
        val buffer = textBufferFlow.value
        textBufferFlow.value = buffer.copy()
    }

    private fun commitSuggestion(word: String) {
        cancelPendingPolish()
        playFeedback()
        val ic = currentInputConnection ?: return
        ic.finishComposingText()

        completedCorrection?.let { pending ->
            if (word in listOf(pending.original, pending.ranked.suggestion) &&
                ic.getTextBeforeCursor(20000, 0)?.toString() == pending.before) {
                if (word != pending.original) {
                    val suffix = pending.original + pending.trailing
                    ic.beginBatchEdit()
                    try {
                        if (!ic.deleteSurroundingText(suffix.length, 0) || !ic.commitText(word + pending.trailing, 1)) return
                    } finally { ic.endBatchEdit() }
                    if (mayLearn()) dictionaryManager.personalProfile.acceptPolish(
                        pending.context.joinToString(" ") + " " + pending.original,
                        pending.context.joinToString(" ") + " " + word) { dictionaryManager.isRecognizedInAnyLanguage(it) }
                } else if (mayLearn()) {
                    dictionaryManager.suppressCorrection(pending.original, pending.ranked.suggestion)
                }
                completedCorrection = null
                updatePreviousWord()
                return
            }
        }

        // 1. One-tap replacement for recent swipe candidate alternatives
        if (lastSwipeCommittedWord != null) {
            val prevSwipeWord = lastSwipeCommittedWord!!
            val hadSpace = lastSwipeCommittedHadSpace
            val suffix = if (hadSpace) "$prevSwipeWord " else prevSwipeWord
            val before = ic.getTextBeforeCursor(suffix.length, 0)?.toString()
            if (before == suffix) {
                ic.beginBatchEdit()
                try {
                    ic.deleteSurroundingText(suffix.length, 0)
                    val boxInfo = getCurrentTextBoxInfo()
                    val shouldAppendSpace = !boxInfo.isUrl && !boxInfo.isEmail && !word.startsWith(".") && !word.startsWith("@")
                    val commitStr = if (shouldAppendSpace) "$word " else word
                    ic.commitText(commitStr, 1)
                    lastComposedStart = -1
                    lastComposedEnd = -1
                    lastSwipeCommittedWord = word
                    lastSwipeCommittedHadSpace = shouldAppendSpace
                } finally {
                    ic.endBatchEdit()
                }
                learnWordAndContext(word, explicit = true)
                if (mayLearn() && lastSwipePath.isNotEmpty()) {
                    dictionaryManager.learnSwipePattern(word, lastSwipePath)
                }
                justAutocorrected = false
                currentTypedWord.value = ""
                currentWordTapCoords.clear()
                updatePreviousWord()
                return
            }
        }

        val (partBefore, partAfter) = getSurroundingWordTokens(ic)
        val deleteAfterLen = if (partBefore.isNotEmpty()) partAfter.length else 0
        val rawTyped = (partBefore + if (deleteAfterLen > 0) partAfter else "").ifEmpty { currentTypedWord.value }
        val isExplicitRawAccept = rawTyped.isNotEmpty() && word.lowercase() == rawTyped.lowercase()

        // If user selected their exact typed word (e.g. Left Slot), suppress auto-correction & save to local memory
        if (isExplicitRawAccept) {
            val autoCorrect = getAutoCorrectedWord(rawTyped)
            if (autoCorrect != null) {
                dictionaryManager.suppressCorrection(rawTyped, autoCorrect)
            }
        }

        ic.beginBatchEdit()
        try {
            if (partBefore.isNotEmpty() || deleteAfterLen > 0) {
                ic.deleteSurroundingText(partBefore.length, deleteAfterLen)
            }
            val boxInfo = getCurrentTextBoxInfo()
            val shouldAppendSpace = !boxInfo.isUrl && !boxInfo.isEmail && !word.startsWith(".") && !word.startsWith("@")
            val commitStr = if (shouldAppendSpace) "$word " else word
            ic.commitText(commitStr, 1)
            lastComposedStart = -1
            lastComposedEnd = -1
        } finally {
            ic.endBatchEdit()
        }

        val boxInfo = getCurrentTextBoxInfo()
        val shouldAppendSpace = !boxInfo.isUrl && !boxInfo.isEmail && !word.startsWith(".") && !word.startsWith("@")
        suggestionSpacePending = shouldAppendSpace
        learnWordAndContext(word, explicit = isExplicitRawAccept)

        justAutocorrected = false
        currentTypedWord.value = ""
        currentWordTapCoords.clear()
        updatePreviousWord()
    }

    internal fun handleSwipeResult(decodedWord: String, decodedCandidates: List<String>, path: List<PointF>) {
        val topWord = TypingPolicy.swipeCase(decodedWord, isCapsLockActive.value)
        val candidates = decodedCandidates.map { TypingPolicy.swipeCase(it, isCapsLockActive.value) }
        if (!isCapsLockActive.value) isShiftActive.value = false
        cancelPendingPolish()
        playFeedback()
        val ic = currentInputConnection ?: return
        ic.finishComposingText()

        val boxInfo = getCurrentTextBoxInfo()
        val shouldAppendSpace = !boxInfo.isUrl && !boxInfo.isEmail && !topWord.startsWith(".") && !topWord.startsWith("@")
        val commitStr = if (shouldAppendSpace) "$topWord " else topWord

        ic.beginBatchEdit()
        try {
            ic.commitText(commitStr, 1)
            lastComposedStart = -1
            lastComposedEnd = -1
        } finally {
            ic.endBatchEdit()
        }

        suggestionSpacePending = shouldAppendSpace
        lastSwipeCommittedWord = topWord
        lastSwipeCommittedHadSpace = shouldAppendSpace
        lastSwipePath = path
        learnWordAndContext(topWord, explicit = false)

        justAutocorrected = false
        currentTypedWord.value = ""
        currentWordTapCoords.clear()
        updatePreviousWord()

        // Populate alternative candidates into the suggestion strip
        val suggestionList = if (candidates.size > 1) {
            val alts = candidates.filter { it.isNotBlank() }
            when {
                alts.size == 2 -> listOf(alts[1], alts[0], "")
                alts.size >= 3 -> listOf(alts[1], alts[0], alts[2])
                else -> listOf(alts[0], "", "")
            }
        } else {
            listOf(topWord, "", "")
        }

        val gboardResult = GboardSuggestionResult(
            leftCandidate = suggestionList.getOrElse(0) { "" },
            centerCandidate = suggestionList.getOrElse(1) { "" },
            rightCandidate = suggestionList.getOrElse(2) { "" },
            isCenterAutocorrecting = false
        )
        asyncPredictionsState.value = AsyncKeyboardPredictions(
            gboardResult = gboardResult,
            suggestions = suggestionList,
            aiPhraseCompletions = emptyList(),
            source = textBufferFlow.value
        )
    }

    private fun learnWordAndContext(word: String, explicit: Boolean = false, contextOverride: List<String>? = null) {
        if (!mayLearn() || word.isEmpty() || word.any { !TypingPolicy.isWordCharacter(it) }) return
        // A cancelled boundary check must not teach the frequency store a curated typo.
        // Explicit literal acceptance and undo still establish intentional vocabulary.
        if (!explicit && dictionaryManager.gboardEngine.isKnownTypo(word.lowercase(java.util.Locale.ROOT))) return
        // Only explicitly accepted words bypass the repeated-use learning threshold.
        if (explicit) dictionaryManager.recordAcceptedWord(word)
        dictionaryManager.learnWord(word, explicit = explicit)
        val context = (contextOverride ?: previousWords.value).takeLast(5)
        dictionaryManager.personalProfile.observe(word, context)
        context.lastOrNull()?.let { dictionaryManager.learnBigram(it, word) }
        if (context.size >= 2) dictionaryManager.learnTrigram(context[context.size - 2], context.last(), word)
        if (context.size >= 3) dictionaryManager.learnQuadgram(context[context.size - 3], context[context.size - 2], context.last(), word)
        dictionaryManager.nGramModel.observeHigherOrder(word, context)

        // Asynchronously persist word frequency to Room database for predictive typing
        serviceScope.launch(Dispatchers.IO) {
            userDictionaryRepo.recordWordUsage(word, dictionaryManager)
        }
    }

    private fun launchSettingsActivity(requestMic: Boolean = false) {
        playFeedback()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (requestMic) {
                putExtra("request_mic_permission", true)
            }
        }
        startActivity(intent)
    }

    /**
     * Toggles Voice Typing: captures voice, transcribes it, and in the end asks user if it needs polish.
     */
    private fun toggleVoiceTyping() {
        if (isVoiceTypingActive.value) {
            stopVoiceTyping(shouldPolish = false)
        } else {
            startVoiceTyping()
        }
    }

    private var recordingJob: Job? = null

    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    private fun startVoiceTyping() {
        val hasMic = MicrophonePermissionHelper.hasMicrophonePermission(this)
        isMicPermissionGranted.value = hasMic
        if (!hasMic) {
            android.widget.Toast.makeText(this, "Microphone permission required for voice input", android.widget.Toast.LENGTH_SHORT).show()
            launchSettingsActivity(requestMic = true)
            return
        }

        val ic = currentInputConnection
        if (ic == null) {
            Log.w("TypeRight", "Cannot start voice typing: InputConnection is null")
            return
        }

        if (isRambleRecording.value || isRambleProcessing.value) cancelRambleMode()
        playFeedback(FeedbackType.Standard)
        pendingVoiceTranscript.value = ""
        isVoiceTypingActive.value = true
        voiceTranscript.value = ""
        ic.finishComposingText()
        voiceInsertionPrefix = if (ic.getSelectedText(0).isNullOrEmpty() &&
            ic.getTextBeforeCursor(1, 0)?.lastOrNull()?.isLetterOrDigit() == true) " " else ""
        voiceAudioLevel.value = 0.0f

        voiceRecordingService.startRecording(
            scope = serviceScope,
            onPartialText = { partial ->
                if (!isVoiceTypingActive.value) return@startRecording
                voiceTranscript.value = partial
                try {
                    ic.setComposingText(voiceInsertionPrefix + partial, 1)
                } catch (e: Exception) {
                    Log.w("TypeRight", "Failed to setComposingText: ${e.message}")
                }
            },
            onLevelChange = { level -> voiceAudioLevel.value = level },
            onError = { message ->
                isVoiceTypingActive.value = false
                ic.finishComposingText()
                voiceAudioLevel.value = 0f
                android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun stopVoiceTyping(shouldPolish: Boolean = false, onFinished: (() -> Unit)? = null) {
        if (!isVoiceTypingActive.value) return
        val editor = currentInputConnection
        val session = editorSession

        voiceRecordingService.stopRecording(
            scope = serviceScope,
            shouldPolish = false,
            onFinalTranscript = { rawText ->
                if (session != editorSession) return@stopRecording
                isVoiceTypingActive.value = false
                val cleanRaw = rawText.trim()
                val ic = editor
                if (cleanRaw.isNotEmpty()) {
                    try {
                        ic?.commitText(voiceInsertionPrefix + cleanRaw, 1)
                        ic?.finishComposingText()
                    } catch (e: Exception) {
                        Log.e("TypeRight", "Failed to commit voice text", e)
                    }
                    pendingVoiceTranscript.value = ""
                    lastCommittedVoiceLength = voiceInsertionPrefix.length + cleanRaw.length
                } else {
                    try {
                        ic?.finishComposingText()
                    } catch (_: Exception) {}
                    lastCommittedVoiceLength = 0
                }
                voiceTranscript.value = ""
                onFinished?.invoke()
            }
        )
    }

    private fun cancelVoiceTyping() {
        voiceRecordingService.cancelRecording()
        if (voiceTranscript.value.isNotBlank()) currentInputConnection?.setComposingText("", 1)
        currentInputConnection?.finishComposingText()
        isVoiceTypingActive.value = false
        voiceTranscript.value = ""
        voiceAudioLevel.value = 0f
        voiceInsertionPrefix = ""
    }

    /**
     * Starts Intent-based "Ramble Mode" Voice Input.
     * Buffers continuous speech without committing partial text to InputConnection.
     */
    private fun startRambleMode() {
        val hasMic = MicrophonePermissionHelper.hasMicrophonePermission(this)
        isMicPermissionGranted.value = hasMic
        if (!hasMic) {
            android.widget.Toast.makeText(this, "Microphone permission required for voice input", android.widget.Toast.LENGTH_SHORT).show()
            launchSettingsActivity(requestMic = true)
            return
        }

        if (isVoiceTypingActive.value) {
            // Keep the recognizer alive for the final correction before switching modes.
            stopVoiceTyping(onFinished = { startRambleMode() })
            return
        }

        rambleRequestId++
        ramblePolishJob?.cancel()
        playFeedback(FeedbackType.Standard)
        isRambleRecording.value = true
        isRambleProcessing.value = false
        rambleTranscript.value = ""
        rambleAudioLevel.value = 0f

        voiceRecordingService.startRecording(
            scope = serviceScope,
            onPartialText = { partial -> rambleTranscript.value = partial },
            onLevelChange = { level -> rambleAudioLevel.value = level },
            onError = { message ->
                isRambleRecording.value = false
                rambleAudioLevel.value = 0f
                android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
            }
        )
    }

    /**
     * Confirms and finishes Ramble Mode dictation.
     * Triggers Stage 2 AI Intent Polishing (local model) to strip disfluencies,
     * resolve self-corrections, process trailing directives, and atomically commits the finalized text.
     */
    private fun confirmRambleMode() {
        if (!isRambleRecording.value) return
        isRambleRecording.value = false
        isRambleProcessing.value = true
        val request = rambleRequestId
        val session = editorSession
        val editor = currentInputConnection

        voiceRecordingService.stopRecording(
            scope = serviceScope,
            shouldPolish = false,
            onFinalTranscript = { rawSpeech ->
                if (request != rambleRequestId || session != editorSession) return@stopRecording
                val rawTrim = rawSpeech.trim()
                if (rawTrim.isBlank()) {
                    isRambleProcessing.value = false
                    rambleTranscript.value = ""
                    return@stopRecording
                }

                ramblePolishJob = serviceScope.launch {
                    try {
                        val finalizedText = aiPolishManager.processRambleDictation(rawTrim)
                        if (request != rambleRequestId || session != editorSession) return@launch
                        val textToCommit = if (finalizedText.isNotBlank()) finalizedText.trim() else rawTrim
                        editor?.commitText(textToCommit, 1)
                    } catch (e: Exception) {
                        Log.e("TypeRight", "Ramble Mode AI processing error: ${e.message}")
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        if (request == rambleRequestId && session == editorSession) editor?.commitText(rawTrim, 1)
                    } finally {
                        if (request == rambleRequestId) {
                            isRambleProcessing.value = false
                            rambleTranscript.value = ""
                            rambleAudioLevel.value = 0f
                        }
                    }
                }
            }
        )
    }

    /**
     * Cancels Ramble Mode dictation immediately and discards buffered speech.
     */
    private fun cancelRambleMode() {
        rambleRequestId++
        ramblePolishJob?.cancel()
        ramblePolishJob = null
        isRambleRecording.value = false
        isRambleProcessing.value = false
        rambleTranscript.value = ""
        rambleAudioLevel.value = 0f
        voiceRecordingService.cancelRecording()
    }

    private fun toggleRambleMode() {
        if (isRambleRecording.value) {
            confirmRambleMode()
        } else {
            startRambleMode()
        }
    }

    /**
     * Executes AI Rephrase/Suggest improvements using local LLM
     */
    private fun commitRephraseSuggestion(suggestion: String) {
        playFeedback()
        if (applyEditorReplacement(rephraseSnapshot, suggestion, PolishMode.REPHRASE)) {
            aiRephraseSuggestions.clear()
            rephraseSnapshot = null
        }
    }

    private fun handleAiPolishButtonClick() {
        playFeedback()
        performDirectAiPolish()
    }

    /**
     * Automatically proofreads the entire text to correct all grammatical errors,
     * spelling mistakes, and typos without going to any writing screen.
     */
    private fun performDirectAiPolish() {
        if (!allowsTextAssistance()) return
        val ic = currentInputConnection ?: return
        cancelPendingPolish()

        if (currentTypedWord.value.isNotEmpty()) {
            ic.finishComposingText()
            currentTypedWord.value = ""
            currentWordTapCoords.clear()
        }

        val requestId = ++currentAiRequestId
        currentAiJob?.cancel()
        currentAiJob = serviceScope.launch {
            val selectedText = ic.getSelectedText(0)?.toString()
            val textToProofread: String
            val isSelection: Boolean

            var extracted = ""
            try {
                val req = android.view.inputmethod.ExtractedTextRequest().apply {
                    flags = 0
                    hintMaxChars = 100000
                    hintMaxLines = 10000
                }
                val ext = ic.getExtractedText(req, 0)
                if (ext?.text != null && ext.text.isNotEmpty()) {
                    extracted = ext.text.toString()
                }
            } catch (_: Exception) {}

            val before = ic.getTextBeforeCursor(20000, 0)?.toString() ?: ""
            val after = ic.getTextAfterCursor(20000, 0)?.toString() ?: ""

            if (!selectedText.isNullOrEmpty()) {
                textToProofread = selectedText
                isSelection = true
            } else if (extracted.isNotBlank()) {
                textToProofread = extracted
                isSelection = false
            } else {
                textToProofread = before + after
                isSelection = false
            }

            if (textToProofread.isBlank()) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(applicationContext, "Type or select text to polish", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            isAiPolishing.value = true

            try {
                val candidateResult = withContext(Dispatchers.Default) {
                    var modelResult: String? = null
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(AiPolishBackend.timeoutMillis) {
                            AiPolishBackend.generatePolish(textToProofread, PolishMode.PROOFREAD,
                                if (isSelection) TextContext(textBeforeCursor = before.takeLast(300), textAfterCursor = after.take(160), selectedText = selectedText) else null)
                        }?.let { modelResult = it }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.w("TypeRight", "Model polish fallback: ${e.message}")
                    }

                    if (!modelResult.isNullOrBlank()) {
                        AiOutputValidator.sanitize(modelResult!!, textToProofread)
                    } else {
                        OnDeviceNeuralPolishEngine.getInstance(applicationContext).quickProofread(textToProofread)
                    }
                }

                if (requestId != currentAiRequestId) return@launch

                withContext(Dispatchers.Main) {
                    if (requestId != currentAiRequestId || !allowsTextAssistance()) return@withContext
                    if (!AiOutputValidator.isValid(textToProofread, candidateResult, PolishMode.PROOFREAD)) return@withContext

                    val finalOutput = if (candidateResult.isNotBlank()) candidateResult else textToProofread

                    ic.beginBatchEdit()
                    try {
                        ic.finishComposingText()
                        if (isSelection) {
                            ic.commitText(finalOutput, 1)
                        } else {
                            try {
                                ic.setSelection(0, 50000)
                                ic.performContextMenuAction(android.R.id.selectAll)
                            } catch (_: Exception) {}
                            val sel = ic.getSelectedText(0)?.toString().orEmpty()
                            if (sel.isNotEmpty()) {
                                ic.commitText(finalOutput, 1)
                            } else {
                                val curBefore = ic.getTextBeforeCursor(50000, 0)?.toString() ?: ""
                                val curAfter = ic.getTextAfterCursor(50000, 0)?.toString() ?: ""
                                if (curBefore.isNotEmpty() || curAfter.isNotEmpty()) {
                                    ic.deleteSurroundingText(curBefore.length, curAfter.length)
                                }
                                ic.commitText(finalOutput, 1)
                            }
                        }
                    } finally {
                        ic.endBatchEdit()
                    }

                    currentTypedWord.value = ""
                    wordUnderCursor.value = ""
                    updatePreviousWord()

                    if (finalOutput != textToProofread) {
                        android.widget.Toast.makeText(applicationContext, "✨ AI Polish: Typos, grammar & spelling corrected", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(applicationContext, "✨ AI Polish: Text is already clean & error-free", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(applicationContext, e.message ?: "Polish failed", android.widget.Toast.LENGTH_LONG).show()
                }
                Log.e("TypeRight", "Direct AI polish error: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    if (requestId == currentAiRequestId) {
                        isAiPolishing.value = false
                    }
                }
            }
        }
    }

    private fun performDirectLocalProofread() {
        if (!allowsTextAssistance()) return
        val ic = currentInputConnection ?: return
        cancelPendingPolish()

        if (currentTypedWord.value.isNotEmpty()) {
            ic.finishComposingText()
            currentTypedWord.value = ""
            currentWordTapCoords.clear()
        }

        val requestId = ++currentAiRequestId
        currentAiJob?.cancel()
        currentAiJob = serviceScope.launch {
            val selectedText = ic.getSelectedText(0)?.toString()
            val textToProofread: String
            val isSelection: Boolean
            val before = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
            val after = ic.getTextAfterCursor(2000, 0)?.toString() ?: ""

            if (!selectedText.isNullOrEmpty()) {
                textToProofread = selectedText
                isSelection = true
            } else {
                textToProofread = before + after
                isSelection = false
            }

            if (textToProofread.isBlank()) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(applicationContext, "Type or select text to proofread", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            isAiPolishing.value = true

            try {
                val proofreadResult = withContext(Dispatchers.Default) {
                    DeviceAiCoreEngine.getInstance(applicationContext).proofread(textToProofread, "Proofread").correctedText
                }

                if (requestId != currentAiRequestId) return@launch

                withContext(Dispatchers.Main) {
                    if (requestId != currentAiRequestId || !allowsTextAssistance()) return@withContext
                    if (ic.getTextBeforeCursor(2000, 0)?.toString().orEmpty() != before ||
                        ic.getTextAfterCursor(2000, 0)?.toString().orEmpty() != after ||
                        ic.getSelectedText(0)?.toString() != selectedText ||
                        !AiOutputValidator.isValid(textToProofread, proofreadResult, PolishMode.PROOFREAD)) return@withContext
                    ic.beginBatchEdit()
                    try {
                        ic.finishComposingText()
                        if (isSelection) {
                            ic.commitText(proofreadResult, 1)
                        } else {
                            val curBefore = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
                            val curAfter = ic.getTextAfterCursor(2000, 0)?.toString() ?: ""
                            if (curBefore.isNotEmpty() || curAfter.isNotEmpty()) {
                                ic.deleteSurroundingText(curBefore.length, curAfter.length)
                            }
                            ic.commitText(proofreadResult, 1)
                        }
                    } finally {
                        ic.endBatchEdit()
                    }
                    currentTypedWord.value = ""
                    wordUnderCursor.value = ""
                    updatePreviousWord()
                    if (proofreadResult != textToProofread) {
                        android.widget.Toast.makeText(applicationContext, "⚡ Fixed with On-Device AICore", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(applicationContext, "⚡ Checked with On-Device AICore (No errors)", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("TypeRight", "Direct local proofread error: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    if (requestId == currentAiRequestId) {
                        isAiPolishing.value = false
                    }
                }
            }
        }
    }

    /**
     * Auto Format: understands context, formats structure (bullets/paragraphs), and auto-corrects text.
     */
    private fun performDirectAutoFormat() {
        if (!allowsTextAssistance()) return
        val ic = currentInputConnection ?: return
        cancelPendingPolish()

        if (currentTypedWord.value.isNotEmpty()) {
            ic.finishComposingText()
            currentTypedWord.value = ""
            currentWordTapCoords.clear()
        }

        val requestId = ++currentAiRequestId
        currentAiJob?.cancel()
        currentAiJob = serviceScope.launch {
            val selectedText = ic.getSelectedText(0)?.toString()
            val textToFormat: String
            val isSelection: Boolean
            val before = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
            val after = ic.getTextAfterCursor(2000, 0)?.toString() ?: ""

            if (!selectedText.isNullOrEmpty()) {
                textToFormat = selectedText
                isSelection = true
            } else {
                textToFormat = before + after
                isSelection = false
            }

            if (textToFormat.isBlank()) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(applicationContext, "Type or select text to auto-format", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            isAiPolishing.value = true

            try {
                val formattedResult = withContext(Dispatchers.Default) {
                    var candidate: String? = null
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(AiPolishBackend.timeoutMillis) {
                            AiPolishBackend.generatePolish(textToFormat, PolishMode.AUTO_FORMAT)
                        }?.let { candidate = it }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException || AiPolishBackend.engine == ActiveAiEngine.OFFLINE) throw e
                    }
                    if (!candidate.isNullOrBlank()) {
                        AiOutputValidator.sanitize(candidate!!, textToFormat)
                    } else {
                        OnDeviceNeuralPolishEngine.getInstance(applicationContext).autoFormatAndCorrect(textToFormat)
                    }
                }

                if (requestId != currentAiRequestId) return@launch

                withContext(Dispatchers.Main) {
                    if (requestId != currentAiRequestId || !allowsTextAssistance()) return@withContext
                    if (ic.getTextBeforeCursor(2000, 0)?.toString().orEmpty() != before ||
                        ic.getTextAfterCursor(2000, 0)?.toString().orEmpty() != after ||
                        ic.getSelectedText(0)?.toString() != selectedText) return@withContext
                    ic.beginBatchEdit()
                    try {
                        ic.finishComposingText()
                        if (isSelection) {
                            ic.commitText(formattedResult, 1)
                        } else {
                            val curBefore = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
                            val curAfter = ic.getTextAfterCursor(2000, 0)?.toString() ?: ""
                            if (curBefore.isNotEmpty() || curAfter.isNotEmpty()) {
                                ic.deleteSurroundingText(curBefore.length, curAfter.length)
                            }
                            ic.commitText(formattedResult, 1)
                        }
                    } finally {
                        ic.endBatchEdit()
                    }
                    currentTypedWord.value = ""
                    wordUnderCursor.value = ""
                    updatePreviousWord()
                    android.widget.Toast.makeText(applicationContext, "✨ Auto Formatted & Corrected", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(applicationContext, e.message ?: "Polish failed", android.widget.Toast.LENGTH_LONG).show()
                }
                Log.e("TypeRight", "Direct auto-format error: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    if (requestId == currentAiRequestId) {
                        isAiPolishing.value = false
                    }
                }
            }
        }
    }

    /**
     * Executes AI Polish on-device to suggest professional, casual, or concise rewrites.
     */
    private fun performAiPolish() {
        if (!allowsTextAssistance() || settings.supportTier == KeyboardSettings.TIER_3) return
        cancelPendingPolish()
        playFeedback()
        val snapshot = captureEditorText() ?: return
        if (snapshot.text.isBlank()) return
        rephraseSnapshot = snapshot
        val requestId = currentAiRequestId
        currentAiJob = serviceScope.launch {
            isAiPolishing.value = true
            try {
                aiPolishManager.suggestImprovements(snapshot.text).collect { suggestions ->
                    if (requestId == currentAiRequestId && snapshot.session == editorSession) {
                        aiRephraseSuggestions.clear()
                        aiRephraseSuggestions.addAll(suggestions.filter {
                            AiOutputValidator.isValid(snapshot.text, it, PolishMode.REPHRASE)
                        })
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("TypeRight", "Polish failed: ${failure.javaClass.simpleName}")
            } finally {
                if (requestId == currentAiRequestId) isAiPolishing.value = false
            }
        }
    }

    fun playFeedback(type: FeedbackType = FeedbackType.Standard) {
        if (isVoiceTypingActive.value) return
        try {
            if (settings.soundEnabled) {
                val soundEffect = when (type) {
                    FeedbackType.Space -> AudioManager.FX_KEYPRESS_SPACEBAR
                    FeedbackType.Delete -> AudioManager.FX_KEYPRESS_DELETE
                    FeedbackType.Enter -> AudioManager.FX_KEYPRESS_RETURN
                    else -> AudioManager.FX_KEYPRESS_STANDARD
                }
                audioManager?.playSoundEffect(soundEffect)
            }
        } catch (_: Exception) {}

        try {
            if (settings.hapticEnabled) {
                val vib = vibrator
                if (vib != null && vib.hasVibrator()) {
                    val duration = if (settings.mechanicalSoundEnabled) 22L else 18L
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vib.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(duration)
                    }
                }
            }
        } catch (_: Exception) {}
    }
}

data class KeyboardStyle(
    val theme: String,
    val isDark: Boolean,
    val backgroundColor: Color,
    val normalKeyBg: Color,
    val specialKeyBg: Color,
    val keyTextColor: Color,
    val accentColor: Color,
    val enterKeyBg: Color,
    val enterKeyTextColor: Color,
    val keyShape: androidx.compose.ui.graphics.Shape,
    val keyBorder: BorderStroke?,
    val showPressPopup: Boolean,
    val scaleOnPress: Boolean,
    val pressAnimationSpec: AnimationSpec<Float>?,
    val keyBevelColor: Color = Color.Transparent,
    val isRetro: Boolean = false,
    val isMonospace: Boolean = false,
    val spacebarLineColor: Color = Color.Transparent,
    val toolbarBgColor: Color = backgroundColor,
    val chassisBorderColor: Color = keyTextColor.copy(alpha = 0.2f)
)

val LocalKeyboardStyle = staticCompositionLocalOf<KeyboardStyle> {
    error("No KeyboardStyle provided")
}

val LocalKeyboardScale = staticCompositionLocalOf<Float> {
    1.0f
}

@Composable
fun VoiceWaveformVisualizer(
    audioLevel: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_recording_anim")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    val animatedAudioLevel by androidx.compose.animation.core.animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy
        ),
        label = "audio_level_spring"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val micCenterX = width / 2f

        val baseRadius = height * 0.28f
        val maxPulseRadius = height * 0.48f

        // 1. Expanding outer halo ring 1
        val currentPulseRadius = baseRadius + (maxPulseRadius - baseRadius) * pulseProgress
        val pulseAlpha = (1f - pulseProgress) * 0.40f * (0.35f + 0.65f * animatedAudioLevel)
        drawCircle(
            color = accentColor.copy(alpha = pulseAlpha),
            radius = currentPulseRadius,
            center = Offset(micCenterX, centerY)
        )

        // 2. Phased secondary ripple ring 2
        val progress2 = (pulseProgress + 0.5f) % 1f
        val currentPulseRadius2 = baseRadius + (maxPulseRadius - baseRadius) * progress2
        val pulseAlpha2 = (1f - progress2) * 0.30f * (0.35f + 0.65f * animatedAudioLevel)
        drawCircle(
            color = accentColor.copy(alpha = pulseAlpha2),
            radius = currentPulseRadius2,
            center = Offset(micCenterX, centerY)
        )

        // 3. Audio-reactive inner halo
        val voiceReactRadius = baseRadius + (height * 0.12f * animatedAudioLevel)
        drawCircle(
            color = accentColor.copy(alpha = 0.20f + 0.35f * animatedAudioLevel),
            radius = voiceReactRadius,
            center = Offset(micCenterX, centerY)
        )

        // 4. Core mic circle badge
        drawCircle(
            color = accentColor,
            radius = baseRadius,
            center = Offset(micCenterX, centerY)
        )

        // Draw microphone body inside core circle
        val micW = 3.5.dp.toPx()
        val micH = 7.dp.toPx()
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(micCenterX - micW / 2f, centerY - micH / 2f - 1.dp.toPx()),
            size = Size(micW, micH),
            cornerRadius = CornerRadius(micW / 2f, micW / 2f)
        )
        // Mic base stand
        drawRect(
            color = Color.White,
            topLeft = Offset(micCenterX - 0.75.dp.toPx(), centerY + micH / 2f - 1.dp.toPx()),
            size = Size(1.5.dp.toPx(), 2.5.dp.toPx())
        )
        drawRect(
            color = Color.White,
            topLeft = Offset(micCenterX - 2.5.dp.toPx(), centerY + micH / 2f + 1.5.dp.toPx()),
            size = Size(5.dp.toPx(), 1.dp.toPx())
        )
    }
}

private enum class KeyboardLayer {
    Qwerty, Symbols, Emojis, Clipboard, ToolsDrawer, Proofread, TextEditing, WisprVoice
}

/**
 * Truncates text in the middle with an ellipsis ("...") if it exceeds the available pixel width,
 * displaying the beginning of the text, "...", and the ending of the text as per Gboard design.
 */
fun formatMiddleEllipsis(
    text: String,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    maxPixelWidth: Float
): String {
    val cleanText = text.trim()
    if (cleanText.isEmpty() || maxPixelWidth <= 0f) return cleanText

    val fullWidth = textMeasurer.measure(cleanText, textStyle).size.width
    if (fullWidth <= maxPixelWidth) {
        return cleanText
    }

    val ellipsis = "..."
    val ellipsisWidth = textMeasurer.measure(ellipsis, textStyle).size.width
    if (ellipsisWidth >= maxPixelWidth) {
        return ellipsis
    }

    if (cleanText.length <= 4) {
        return if (cleanText.length > 2) cleanText.take(1) + ellipsis + cleanText.takeLast(1) else cleanText
    }

    var low = 2
    var high = cleanText.length - 1
    var bestCandidate = cleanText.take(1) + ellipsis + cleanText.takeLast(1)

    while (low <= high) {
        val mid = (low + high) / 2
        val prefixLen = (mid + 1) / 2
        val suffixLen = mid / 2

        if (prefixLen + suffixLen >= cleanText.length) {
            high = mid - 1
            continue
        }

        val candidate = cleanText.take(prefixLen) + ellipsis + cleanText.takeLast(suffixLen)
        val measuredWidth = textMeasurer.measure(candidate, textStyle).size.width
        if (measuredWidth <= maxPixelWidth) {
            bestCandidate = candidate
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    return bestCandidate
}

/**
 * Standard Jetpack Compose Keyboard Layout containing toolbar, suggestions, keys, and swipe trails.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun KeyboardLayout(
    context: Context,
    settings: KeyboardSettings,
    dictionaryManager: DictionaryManager,
    isShift: Boolean,
    isCapsLock: Boolean,
    isSymbols: Boolean,
    isEmojis: Boolean,
    isClipboard: Boolean = false,
    isVoiceTyping: Boolean,
    isVoiceFinishing: Boolean = false,
    audioLevel: Float,
    isRambleRecording: Boolean = false,
    isRambleProcessing: Boolean = false,
    rambleTranscript: String = "",
    rambleAudioLevel: Float = 0f,
    onRambleToggle: () -> Unit = {},
    onConfirmRamble: () -> Unit = {},
    onCancelRamble: () -> Unit = {},
    isPolishing: Boolean,
    micPermission: Boolean,
    currentTypedWord: String,
    wordUnderCursor: String,
    previousWord: String?,
    previousWords: List<String> = emptyList(),
    clipboardRepository: ClipboardRepository? = null,
    onKeyClick: (String) -> Unit,
    onDelete: () -> Unit,
    onDeleteWord: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onShiftToggle: () -> Unit,
    onSymbolsToggle: () -> Unit,
    onEmojiToggle: () -> Unit,
    onClipboardToggle: () -> Unit = {},
    onVoiceTypingToggle: () -> Unit,
    onVoiceCancel: () -> Unit = onVoiceTypingToggle,
    onAiPolishClick: () -> Unit,
    onProofreadClick: () -> Unit = {},
    onAutoFormatClick: () -> Unit = {},
    onSuggestionClick: (String) -> Unit,
    onRemoveSuggestion: (String) -> Unit = {},
    onOpenSettings: () -> Unit,
    isRephrasing: Boolean = false,
    aiRephraseSuggestions: List<String> = emptyList(),
    onAiRephraseClick: () -> Unit = {},
    onRephraseSuggestionClick: (String) -> Unit = {},
    onClearRephrasings: () -> Unit = {},
    onTapCoordinates: (Float, Float) -> Unit = { _, _ -> },
    onSpaceSwipeLeft: () -> Unit = {},
    onSpaceSwipeRight: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {}
) {
    val vibrator = remember(context) { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    val sharedPrefs = remember { context.getSharedPreferences("typeright_prefs", Context.MODE_PRIVATE) }

    var wordPendingRemoval by remember { mutableStateOf<String?>(null) }
    var removedWordNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(removedWordNotice) {
        if (removedWordNotice != null) {
            kotlinx.coroutines.delay(2000L)
            removedWordNotice = null
        }
    }
    LaunchedEffect(currentTypedWord, wordUnderCursor) {
        wordPendingRemoval = null
    }
    
    var themeState by remember { mutableStateOf(settings.theme) }
    var isDarkState by remember { mutableStateOf(settings.isDarkMode) }
    var dynamicThemeState by remember { mutableStateOf(settings.dynamicThemeEnabled) }
    var accentColorHexState by remember { mutableStateOf(settings.accentColor) }
    var keyboardHeightState by remember { mutableStateOf(settings.height) }
    var customKeyboardHeightPercent by remember { mutableStateOf(settings.customKeyboardHeightPercent) }
    var numberRowEnabledState by remember { mutableStateOf(settings.numberRowEnabled) }
    var keyboardLanguageState by remember { mutableStateOf(settings.keyboardLanguage) }
    var keyBordersState by remember { mutableStateOf(settings.keyBordersEnabled) }
    var popupKeypressState by remember { mutableStateOf(settings.popupOnKeypress) }
    var oneHandedModeState by remember { mutableStateOf(settings.oneHandedMode) }
    var emojiSuggestionsState by remember { mutableStateOf(settings.emojiSuggestionsEnabled) }
    var spaceSwipeState by remember { mutableStateOf(settings.spaceSwipeEnabled) }
    var swipeEnabledState by remember { mutableStateOf(settings.swipeEnabled) }
    var navBarClearanceState by remember { mutableStateOf(settings.navBarClearance) }

    DisposableEffect(sharedPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KeyboardSettings.KEY_THEME -> themeState = settings.theme
                KeyboardSettings.KEY_DARK_MODE -> isDarkState = settings.isDarkMode
                KeyboardSettings.KEY_DYNAMIC_THEME_ENABLED -> dynamicThemeState = settings.dynamicThemeEnabled
                KeyboardSettings.KEY_ACCENT_COLOR -> accentColorHexState = settings.accentColor
                KeyboardSettings.KEY_HEIGHT -> keyboardHeightState = settings.height
                KeyboardSettings.KEY_CUSTOM_KEYBOARD_HEIGHT_PERCENT -> customKeyboardHeightPercent = settings.customKeyboardHeightPercent
                KeyboardSettings.KEY_NUMBER_ROW_ENABLED -> numberRowEnabledState = settings.numberRowEnabled
                KeyboardSettings.KEY_KEYBOARD_LANGUAGE -> keyboardLanguageState = settings.keyboardLanguage
                KeyboardSettings.KEY_KEY_BORDERS_ENABLED -> keyBordersState = settings.keyBordersEnabled
                KeyboardSettings.KEY_POPUP_ON_KEYPRESS -> popupKeypressState = settings.popupOnKeypress
                KeyboardSettings.KEY_ONE_HANDED_MODE -> oneHandedModeState = settings.oneHandedMode
                KeyboardSettings.KEY_EMOJI_SUGGESTIONS -> emojiSuggestionsState = settings.emojiSuggestionsEnabled
                KeyboardSettings.KEY_SPACE_SWIPE_ENABLED -> spaceSwipeState = settings.spaceSwipeEnabled
                KeyboardSettings.KEY_SWIPE_ENABLED -> swipeEnabledState = settings.swipeEnabled
                KeyboardSettings.KEY_NAV_BAR_CLEARANCE -> navBarClearanceState = settings.navBarClearance
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val isMalayalamKeyboard = keyboardLanguageState.contains("Malayalam", ignoreCase = true)
    val spacebarLabel = if (isMalayalamKeyboard) "മലയാളം (Manglish)" else "English"
    val onToggleLanguage: () -> Unit = {
        val next = if (isMalayalamKeyboard) "English" else "മലയാളം (Manglish)"
        settings.keyboardLanguage = next
        keyboardLanguageState = next
        (context as? TypeRightKeyboardService)?.notifyTextBufferChanged()
        Unit
    }

    val parsedAccentColor = remember(accentColorHexState) {
        try {
            Color(android.graphics.Color.parseColor(accentColorHexState))
        } catch (e: Exception) {
            Color(0xFF70C7C1)
        }
    }

    val style = remember(themeState, keyBordersState, popupKeypressState) {
        val isLight = themeState.equals(KeyboardSettings.THEME_LIGHT, ignoreCase = true) ||
                themeState.equals("Light", ignoreCase = true) ||
                themeState.equals("Light Arrangement", ignoreCase = true)
        val isNight = themeState.equals(KeyboardSettings.THEME_NIGHT, ignoreCase = true) ||
                themeState.equals("Night", ignoreCase = true) ||
                themeState.equals("AMOLED Black", ignoreCase = true)

        val currentKeyBorder = if (keyBordersState) {
            when {
                isNight -> BorderStroke(0.8.dp, Color(0x3544474E))
                isLight -> BorderStroke(0.8.dp, Color(0x1F000000))
                else -> BorderStroke(0.8.dp, Color(0x4044474E))
            }
        } else null

        when {
            isLight -> {
                // Light Arrangement: Clean, high-contrast Material 3 Light keyboard
                KeyboardStyle(
                    theme = KeyboardSettings.THEME_LIGHT,
                    isDark = false,
                    isRetro = false,
                    backgroundColor = Color(0xFFF1F4F9),
                    normalKeyBg = Color(0xFFFFFFFF),
                    specialKeyBg = Color(0xFFE2E7ED),
                    keyTextColor = Color(0xFF191C20),
                    accentColor = Color(0xFF0B57D0),
                    enterKeyBg = Color(0xFF0B57D0),
                    enterKeyTextColor = Color(0xFFFFFFFF),
                    keyShape = RoundedCornerShape(8.dp),
                    keyBorder = currentKeyBorder ?: BorderStroke(0.5.dp, Color(0x12000000)),
                    showPressPopup = popupKeypressState,
                    scaleOnPress = true,
                    pressAnimationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                    keyBevelColor = Color(0x12000000),
                    isMonospace = false,
                    spacebarLineColor = Color(0x300B57D0),
                    toolbarBgColor = Color(0xFFF1F4F9),
                    chassisBorderColor = Color(0x15000000)
                )
            }
            isNight -> {
                // Night Theme: Pure AMOLED Deep Black
                KeyboardStyle(
                    theme = KeyboardSettings.THEME_NIGHT,
                    isDark = true,
                    isRetro = false,
                    backgroundColor = Color(0xFF000000),
                    normalKeyBg = Color(0xFF1C1D22),
                    specialKeyBg = Color(0xFF141518),
                    keyTextColor = Color(0xFFE2E2E6),
                    accentColor = Color(0xFFA8C7FA),
                    enterKeyBg = Color(0xFFA8C7FA),
                    enterKeyTextColor = Color(0xFF041E49),
                    keyShape = RoundedCornerShape(8.dp),
                    keyBorder = currentKeyBorder,
                    showPressPopup = popupKeypressState,
                    scaleOnPress = true,
                    pressAnimationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                    keyBevelColor = Color.Transparent,
                    isMonospace = false,
                    spacebarLineColor = Color(0x25A8C7FA),
                    toolbarBgColor = Color(0xFF000000),
                    chassisBorderColor = Color(0x20FFFFFF)
                )
            }
            else -> {
                // Dark Theme: Default Standard Charcoal Dark
                KeyboardStyle(
                    theme = KeyboardSettings.THEME_DARK,
                    isDark = true,
                    isRetro = false,
                    backgroundColor = Color(0xFF1B1B1F),
                    normalKeyBg = Color(0xFF2E3137),
                    specialKeyBg = Color(0xFF24262B),
                    keyTextColor = Color(0xFFE2E2E6),
                    accentColor = Color(0xFFA8C7FA),
                    enterKeyBg = Color(0xFFA8C7FA),
                    enterKeyTextColor = Color(0xFF041E49),
                    keyShape = RoundedCornerShape(8.dp),
                    keyBorder = currentKeyBorder,
                    showPressPopup = popupKeypressState,
                    scaleOnPress = true,
                    pressAnimationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                    keyBevelColor = Color.Transparent,
                    isMonospace = false,
                    spacebarLineColor = Color(0x30A8C7FA),
                    toolbarBgColor = Color(0xFF1B1B1F),
                    chassisBorderColor = Color(0x2544474E)
                )
            }
        }
    }

    val isDark = style.isDark
    val backgroundColor = style.backgroundColor
    val normalKeyBg = style.normalKeyBg
    val specialKeyBg = style.specialKeyBg
    val keyTextColor = style.keyTextColor
    val accentColor = style.accentColor
    val enterKeyBg = style.enterKeyBg
    val enterKeyTextColor = style.enterKeyTextColor
    val toolbarBg = backgroundColor

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val screenHeight = configuration.screenHeightDp

    // Locked strictly to short keyboard length; resizing disabled per user request
    val keysHeight = if (isLandscape) {
        (screenHeight * 0.44f).coerceIn(135f, 180f).dp
    } else {
        (screenHeight * 0.23f).coerceIn(180f, 220f).dp
    }

    val heightScaleFactor = (keysHeight.value / 225f).coerceIn(0.72f, 1.45f)

    val navBarsInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val resourceNavHeight = remember(context) {
        try {
            val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resId > 0) {
                val px = context.resources.getDimensionPixelSize(resId)
                val density = context.resources.displayMetrics.density
                if (density > 0 && px > 0) (px / density).dp else 48.dp
            } else {
                48.dp
            }
        } catch (_: Exception) {
            48.dp
        }
    }

    // Default smart clearance elevated to at least 48dp so the keys never collide with the system nav bar / gesture bar / IME switcher
    val smartAutoClearance = remember(resourceNavHeight, navBarsInset) {
        val detected = maxOf(navBarsInset, resourceNavHeight)
        if (detected >= 44.dp) detected.coerceAtMost(64.dp) else 48.dp
    }

    val bottomClearanceHeight = when {
        isLandscape -> 12.dp
        navBarClearanceState == "extra" -> 64.dp
        navBarClearanceState == "3button" -> 56.dp
        navBarClearanceState == "gesture" -> 48.dp
        navBarClearanceState == "compact" -> 32.dp
        navBarClearanceState == "none" -> 16.dp
        else -> smartAutoClearance // "auto" default: 48.dp to 54.dp
    }

    val activePrefix = if (currentTypedWord.isNotEmpty()) currentTypedWord else wordUnderCursor

    val service = context as? TypeRightKeyboardService
    val boxInfo = remember(service?.currentInputEditorInfo) {
        service?.getCurrentTextBoxInfo() ?: TextBoxClassifier.defaultClassification
    }
    val isSensitiveInput = boxInfo.isSensitive
    val asyncPredictions = service?.asyncPredictionsState?.value ?: AsyncKeyboardPredictions()
    val gboardResult = asyncPredictions.gboardResult

    val imeAction = service?.currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_UNSPECIFIED
    val enterIcon = when (imeAction) {
        EditorInfo.IME_ACTION_SEARCH -> Icons.Default.Search
        EditorInfo.IME_ACTION_SEND -> Icons.AutoMirrored.Filled.Send
        EditorInfo.IME_ACTION_GO,
        EditorInfo.IME_ACTION_NEXT -> Icons.AutoMirrored.Filled.ArrowForward
        EditorInfo.IME_ACTION_DONE -> Icons.Default.Check
        else -> Icons.AutoMirrored.Filled.KeyboardReturn
    }

    // The UI only reads completed results; dictionary searches run on the worker.
    val suggestions = remember(asyncPredictions, activePrefix, isSensitiveInput, boxInfo) {
        if (isSensitiveInput) {
            listOf("", "", "")
        } else if (asyncPredictions.suggestions.isNotEmpty() && asyncPredictions.suggestions.any { it.isNotBlank() }) {
            asyncPredictions.suggestions.filter { !dictionaryManager.isBlocked(it) }
        } else if (activePrefix.isNotEmpty()) {
            if (!dictionaryManager.isBlocked(activePrefix)) listOf(activePrefix, "", "") else listOf("", "", "")
        } else {
            boxInfo.defaultEmptySuggestions.filter { !dictionaryManager.isBlocked(it) }
        }
    }

    var isToolbarForceExpanded by remember { mutableStateOf(false) }
    var isToolsDrawerOpen by remember { mutableStateOf(false) }
    val proofreadSheetState = service?.isProofreadSheetOpen ?: remember { mutableStateOf(false) }
    var isProofreadSheetOpen by proofreadSheetState
    var isTextEditingOpen by remember { mutableStateOf(false) }
    var isWisprVoiceOpen by remember { mutableStateOf(false) }
    var activeAiEngineState by remember { mutableStateOf(settings.activeAiEngine) }

    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KeyboardSettings.KEY_OFFLINE_AI_ENABLED || key == KeyboardSettings.KEY_GEMINI_AI_ENABLED) {
                activeAiEngineState = settings.activeAiEngine
            }
        }
        val prefs = (context.applicationContext ?: context).getSharedPreferences("typeright_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    LaunchedEffect(currentTypedWord, wordUnderCursor) {
        if (currentTypedWord.isNotEmpty() || wordUnderCursor.isNotEmpty()) {
            isToolbarForceExpanded = false
        }
    }

    CompositionLocalProvider(
        LocalKeyboardStyle provides style,
        LocalKeyboardScale provides heightScaleFactor
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 660.dp)
            ) {
        val toolbarHeight = ((if (aiRephraseSuggestions.isNotEmpty()) 52f else 48f) * heightScaleFactor.coerceIn(0.94f, 1.15f)).dp
        val effectiveKeysHeight = if (isEmojis) keysHeight + toolbarHeight + 8.dp else if (isProofreadSheetOpen) keysHeight + toolbarHeight + 36.dp else keysHeight

        // --- TOOLBAR ROW ---
        if (!isEmojis && !isProofreadSheetOpen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(toolbarBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .height(toolbarHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                val showUndoPill = service?.showUndoAutoPolishPill?.value ?: false
                val isSmartSelectOpen = service?.isSmartSelectOpen?.value ?: false

                val toolbarMainMode = when {
                    isVoiceTyping || isVoiceFinishing || isRambleRecording || isRambleProcessing -> 4
                    service?.pendingContextReview?.value != null -> 7
                    showUndoPill -> 6
                    isSmartSelectOpen -> 5
                    isRephrasing -> 0
                    aiRephraseSuggestions.isNotEmpty() -> 2
                    else -> 4
                }

                AnimatedContent(
                    targetState = toolbarMainMode,
                    transitionSpec = {
                        (slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { -it / 3 } +
                         fadeIn(animationSpec = tween(200, easing = LinearOutSlowInEasing))) togetherWith
                        (slideOutVertically(animationSpec = tween(180, easing = FastOutLinearInEasing)) { -it / 3 } +
                         fadeOut(animationSpec = tween(160)))
                    },
                    label = "main_toolbar_mode_transition",
                    modifier = Modifier.fillMaxSize()
                ) { mode ->
                    when (mode) {
                        0 -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "rephrase_pulse")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "pulse_alpha"
                                )
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Suggestions Loading",
                                    tint = accentColor.copy(alpha = alpha),
                                    modifier = Modifier.size(18.dp).padding(end = 6.dp)
                                )
                                Text(
                                    text = "Generating rewrites...",
                                    color = keyTextColor.copy(alpha = alpha),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        2 -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Suggestions",
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp).padding(end = 4.dp)
                                )
                                
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    aiRephraseSuggestions.forEachIndexed { index, suggestion ->
                                        val label = when (index) {
                                            0 -> "👔 Professional"
                                            1 -> "😊 Casual"
                                            2 -> "⚡ Concise"
                                            else -> "✨ Alternate"
                                        }
                                        
                                        Column(
                                            modifier = Modifier
                                                .width(200.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(accentColor.copy(alpha = 0.08f))
                                                .border(
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        width = 1.dp,
                                                        color = accentColor.copy(alpha = 0.25f)
                                                    ),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    onRephraseSuggestionClick(suggestion)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                                .testTag("ai_rephrase_suggestion_$label"),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = accentColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = suggestion,
                                                color = keyTextColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Normal,
                                                maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                lineHeight = 12.sp
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = onClearRephrasings,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("close_ai_suggestions_button")
                                 ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close suggestions",
                                        tint = keyTextColor.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        5 -> {
                            // SMART SELECT TOOLBAR ROW
                            val currentLevel = service?.currentSmartSelectLevel?.value ?: SmartSelectLevel.SENTENCE
                            val isPolishing = service?.isAiPolishing?.value ?: false
                            var scrubAccumulator by remember { mutableFloatStateOf(0f) }

                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 1. Close Button
                                IconButton(
                                    onClick = {
                                        service?.isSmartSelectOpen?.value = false
                                    },
                                    modifier = Modifier.size(32.dp).testTag("smart_select_close_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Smart Select",
                                        tint = keyTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // 2. Scrollable Scope Selector & Gesture Scrubber
                                androidx.compose.foundation.lazy.LazyRow(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Level Pills
                                    items(SmartSelectLevel.values()) { level ->
                                        val isSelected = currentLevel == level
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isSelected) accentColor.copy(alpha = 0.28f) else keyTextColor.copy(alpha = 0.08f),
                                            border = if (isSelected) BorderStroke(1.dp, accentColor) else null,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable {
                                                    service?.applySmartSelection(level)
                                                }
                                                .testTag("smart_select_scope_${level.name.lowercase()}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                val icon = when (level) {
                                                    SmartSelectLevel.WORD -> Icons.Default.TextFields
                                                    SmartSelectLevel.SENTENCE -> Icons.Default.ShortText
                                                    SmartSelectLevel.PARAGRAPH -> Icons.Default.Subject
                                                    SmartSelectLevel.ALL -> Icons.Default.SelectAll
                                                }
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = level.label,
                                                    tint = if (isSelected) accentColor else keyTextColor.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = level.label,
                                                    color = if (isSelected) accentColor else keyTextColor.copy(alpha = 0.85f),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    // Interactive Gesture Scrubber Pill
                                    item {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = accentColor.copy(alpha = 0.12f),
                                            border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.4f)),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .pointerInput(Unit) {
                                                    detectHorizontalDragGestures(
                                                        onDragEnd = { scrubAccumulator = 0f },
                                                        onHorizontalDrag = { change, dragAmount ->
                                                            change.consume()
                                                            scrubAccumulator += dragAmount
                                                            if (scrubAccumulator > 30f) {
                                                                scrubAccumulator = 0f
                                                                service?.cycleSmartSelection(forward = true)
                                                            } else if (scrubAccumulator < -30f) {
                                                                scrubAccumulator = 0f
                                                                service?.cycleSmartSelection(forward = false)
                                                            }
                                                        }
                                                    )
                                                }
                                                .testTag("smart_select_gesture_scrubber")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.SwapHoriz,
                                                    contentDescription = "Scrub Selection",
                                                    tint = accentColor,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "↔ Scrub",
                                                    color = accentColor,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }

                                // 3. Fast Auto-Polish with Gemini Action Button
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = accentColor,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable(enabled = !isPolishing) {
                                            service?.autoPolishWithGemini(currentLevel)
                                        }
                                        .testTag("smart_select_gemini_polish_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (isPolishing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(13.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = "Polishing...",
                                                color = Color.White,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = "AI Auto-Polish",
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "Auto Polish",
                                                color = Color.White,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        7 -> {
                            var reviewOpen by remember { mutableStateOf(false) }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { reviewOpen = true }, modifier = Modifier.weight(1f).testTag("context_review_button")) {
                                    Icon(Icons.Default.Spellcheck, null); Spacer(Modifier.width(8.dp)); Text("Review sentence correction")
                                }
                                IconButton(onClick = { service?.dismissContextCorrection() }, Modifier.testTag("dismiss_context_correction")) {
                                    Icon(Icons.Default.Close, "Dismiss correction")
                                }
                            }
                            if (reviewOpen) service?.pendingContextReview?.value?.let { review ->
                                AlertDialog(onDismissRequest = { reviewOpen = false }, title = { Text("Sentence correction") },
                                    text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(review.original, style = MaterialTheme.typography.bodyMedium)
                                        Text(review.replacement, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                    } }, confirmButton = { TextButton(onClick = { service.acceptContextCorrection(); reviewOpen = false }, Modifier.testTag("accept_context_correction")) { Text("Accept") } },
                                    dismissButton = { TextButton(onClick = { service.dismissContextCorrection(); reviewOpen = false }) { Text("Keep original") } })
                            }
                        }
                        6 -> {
                            // UNDO GEMINI AUTO-POLISH TOOLBAR ROW
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(accentColor.copy(alpha = 0.25f))
                                        .clickable {
                                            service?.undoGeminiAutoPolish()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("undo_auto_polish_pill"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = "Undo polish",
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Polished • Tap to Undo",
                                        color = keyTextColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        service?.showUndoAutoPolishPill?.value = false
                                    },
                                    modifier = Modifier.size(32.dp).testTag("dismiss_undo_auto_polish_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss Undo",
                                        tint = keyTextColor.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        else -> {
                            val toolbarVoiceState = when {
                                isRambleRecording -> "ramble_recording"
                                isRambleProcessing -> "ramble_processing"
                                isVoiceTyping -> "voice_typing"
                                else -> "normal"
                            }
                            AnimatedContent(
                                targetState = toolbarVoiceState,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(180, easing = LinearOutSlowInEasing)) +
                                     scaleIn(initialScale = 0.95f, animationSpec = tween(180))) togetherWith
                                    (fadeOut(animationSpec = tween(140, easing = FastOutLinearInEasing)) +
                                     scaleOut(targetScale = 0.95f, animationSpec = tween(140)))
                                },
                                label = "voice_typing_toolbar_transition",
                                modifier = Modifier.fillMaxSize()
                            ) { activeVoiceState ->
                                when (activeVoiceState) {
                                    "ramble_recording" -> VoiceInputToolbar(
                                        audioLevel = rambleAudioLevel, accentColor = accentColor, keyTextColor = keyTextColor,
                                        onCancel = onCancelRamble, onDone = onConfirmRamble
                                    )
                                    "ramble_processing" -> VoiceInputToolbar(
                                        audioLevel = 0f, accentColor = accentColor, keyTextColor = keyTextColor,
                                        onCancel = onCancelRamble, onDone = {}, processing = true
                                    )
                                    "voice_typing" -> VoiceInputToolbar(
                                        audioLevel = audioLevel, accentColor = accentColor, keyTextColor = keyTextColor,
                                        onCancel = onVoiceCancel, onDone = onVoiceTypingToggle,
                                        processing = isVoiceFinishing, processingDescription = "Finishing dictation"
                                    )
                                    else -> {
                                    val showSuggestionsInToolbar = !isToolbarForceExpanded

                                    AnimatedContent(
                                        targetState = showSuggestionsInToolbar,
                                        transitionSpec = {
                                            if (targetState) {
                                                (slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { width -> -width / 4 } +
                                                 fadeIn(animationSpec = tween(200, easing = LinearOutSlowInEasing))) togetherWith
                                                (slideOutHorizontally(animationSpec = tween(180, easing = FastOutLinearInEasing)) { width -> width / 4 } +
                                                 fadeOut(animationSpec = tween(150)))
                                            } else {
                                                (slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { width -> width / 4 } +
                                                 fadeIn(animationSpec = tween(200, easing = LinearOutSlowInEasing))) togetherWith
                                                (slideOutHorizontally(animationSpec = tween(180, easing = FastOutLinearInEasing)) { width -> -width / 4 } +
                                                 fadeOut(animationSpec = tween(150)))
                                            }
                                        },
                                        label = "toolbar_mode_transition"
                                    ) { showSuggestions ->
                                        if (showSuggestions) {
                                            // STABLE SUGGESTIONS MODE inside toolbar - rock solid with zero flickering
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        isToolsDrawerOpen = !isToolsDrawerOpen
                                                        isProofreadSheetOpen = false
                                                        isTextEditingOpen = false
                                                    },
                                                    modifier = Modifier.size(42.dp).testTag("expand_toolbar_options_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Apps,
                                                        contentDescription = "Gboard Quick Tools",
                                                        tint = if (isToolsDrawerOpen) accentColor else keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(25.dp)
                                                    )
                                                }

                                                val keyboardService = context as? TypeRightKeyboardService
                                                val smartItems by (keyboardService?.smartClipboard?.suggestions
                                                    ?: remember { MutableStateFlow(emptyList<SmartClipSuggestion>()) }).collectAsState()
                                                if (!isSensitiveInput && settings.clipboardEnabled && smartItems.isNotEmpty() && activePrefix.isEmpty()) {
                                                    SmartClipboardChips(smartItems, accentColor, keyTextColor,
                                                        onPaste = { keyboardService?.pasteSmartSuggestion(it) },
                                                        onDismiss = { keyboardService?.smartClipboard?.dismiss(it) },
                                                        modifier = Modifier.weight(1f))
                                                } else if (isSensitiveInput) {
                                                    // Clarify that predictions & corrections are not available in password/sensitive text fields
                                                    Row(
                                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Lock,
                                                            contentDescription = "Password field",
                                                            tint = keyTextColor.copy(alpha = 0.5f),
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "Password field • Privacy protected",
                                                            color = keyTextColor.copy(alpha = 0.55f),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Normal,
                                                            maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                        )
                                                    }
                                                } else {
                                                    // Open Gboard-style suggestions row with clean vertical dividers and middle-truncation
                                                    val rawItems = suggestions.take(3)
                                                    val suggestionSlots = remember(rawItems) {
                                                        val list = rawItems.toMutableList()
                                                        while (list.size < 3) {
                                                            list.add("")
                                                        }
                                                        list
                                                    }
                                                    val textMeasurer = rememberTextMeasurer()
                                                    val hasAnySuggestion = suggestionSlots.any { it.isNotBlank() }

                                                    if (removedWordNotice != null) {
                                                        // Brief removal confirmation notice
                                                        Row(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight()
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(accentColor.copy(alpha = 0.16f))
                                                                .padding(horizontal = 10.dp)
                                                                .testTag("suggestion_removed_notice"),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = null,
                                                                tint = accentColor,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = "\"$removedWordNotice\" removed from suggestions",
                                                                color = keyTextColor,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                maxLines = 1,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    } else if (wordPendingRemoval != null) {
                                                        val targetWord = wordPendingRemoval!!
                                                        // The Bin appears where the user can remove the suggested word
                                                        Row(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight()
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f))
                                                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                                                .padding(horizontal = 6.dp)
                                                                .testTag("suggestion_removal_bin_bar"),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .clickable {
                                                                        onRemoveSuggestion(targetWord)
                                                                        removedWordNotice = targetWord
                                                                        wordPendingRemoval = null
                                                                    },
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                // Bin Icon
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(32.dp)
                                                                        .clip(CircleShape)
                                                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.20f))
                                                                        .clickable {
                                                                            onRemoveSuggestion(targetWord)
                                                                            removedWordNotice = targetWord
                                                                            wordPendingRemoval = null
                                                                        }
                                                                        .testTag("suggestion_bin_icon"),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Delete,
                                                                        contentDescription = "Remove suggestion",
                                                                        tint = MaterialTheme.colorScheme.error,
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }

                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(
                                                                        text = "Remove \"$targetWord\"?",
                                                                        style = TextStyle(
                                                                            fontSize = 12.5.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = MaterialTheme.colorScheme.onErrorContainer
                                                                        ),
                                                                        maxLines = 1,
                                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                                    )
                                                                    Text(
                                                                        text = "Won't be suggested again",
                                                                        style = TextStyle(
                                                                            fontSize = 10.sp,
                                                                            fontWeight = FontWeight.Normal,
                                                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                                                        ),
                                                                        maxLines = 1
                                                                    )
                                                                }
                                                            }

                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                // Remove Button
                                                                Button(
                                                                    onClick = {
                                                                        onRemoveSuggestion(targetWord)
                                                                        removedWordNotice = targetWord
                                                                        wordPendingRemoval = null
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(
                                                                        containerColor = MaterialTheme.colorScheme.error,
                                                                        contentColor = MaterialTheme.colorScheme.onError
                                                                    ),
                                                                    shape = RoundedCornerShape(14.dp),
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                                    modifier = Modifier
                                                                        .height(28.dp)
                                                                        .testTag("remove_suggestion_button")
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Delete,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(13.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(3.dp))
                                                                    Text("Remove", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                                }

                                                                // Cancel Button
                                                                IconButton(
                                                                    onClick = { wordPendingRemoval = null },
                                                                    modifier = Modifier
                                                                        .size(28.dp)
                                                                        .testTag("cancel_remove_suggestion_button")
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Close,
                                                                        contentDescription = "Cancel",
                                                                        tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        Row(
                                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            suggestionSlots.forEachIndexed { index, word ->
                                                                val isCenter = index == 1
                                                                val itemStyle = TextStyle(
                                                                    fontSize = if (isCenter) 17.sp else 16.sp,
                                                                    fontWeight = if (isCenter) FontWeight.SemiBold else FontWeight.Medium,
                                                                    fontFamily = if (style.isMonospace) FontFamily.Monospace else FontFamily.SansSerif,
                                                                    color = keyTextColor
                                                                )

                                                                BoxWithConstraints(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .fillMaxHeight()
                                                                        .clip(RoundedCornerShape(4.dp))
                                                                        .combinedClickable(
                                                                            enabled = word.isNotBlank(),
                                                                            onClick = {
                                                                                wordPendingRemoval = null
                                                                                onSuggestionClick(word)
                                                                            },
                                                                            onLongClick = {
                                                                                if (word.isNotBlank()) {
                                                                                    vibrator?.let { v ->
                                                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                                                            v.vibrate(VibrationEffect.createOneShot(55, VibrationEffect.DEFAULT_AMPLITUDE))
                                                                                        } else {
                                                                                            v.vibrate(55)
                                                                                        }
                                                                                    }
                                                                                    wordPendingRemoval = word
                                                                                }
                                                                            }
                                                                        )
                                                                        .padding(horizontal = 4.dp)
                                                                        .testTag(if (word.isNotBlank()) "suggestion_item_$word" else "suggestion_item_empty_$index"),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    if (word.isNotBlank()) {
                                                                        val density = androidx.compose.ui.platform.LocalDensity.current
                                                                        val horizontalPaddingPx = with(density) { 8.dp.toPx() }
                                                                        val maxAvailablePx = (constraints.maxWidth.toFloat() - horizontalPaddingPx).coerceAtLeast(0f)

                                                                        val formattedWord = remember(word, maxAvailablePx, itemStyle) {
                                                                            formatMiddleEllipsis(word, textMeasurer, itemStyle, maxAvailablePx)
                                                                        }

                                                                        AnimatedContent(
                                                                            targetState = formattedWord,
                                                                            transitionSpec = {
                                                                                fadeIn(animationSpec = tween(90, easing = LinearOutSlowInEasing)) togetherWith
                                                                                fadeOut(animationSpec = tween(60, easing = FastOutLinearInEasing))
                                                                            },
                                                                            label = "suggestion_word_crossfade"
                                                                        ) { targetWord ->
                                                                            Text(
                                                                                text = targetWord,
                                                                                style = itemStyle,
                                                                                textAlign = TextAlign.Center,
                                                                                maxLines = 1,
                                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Clip
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                        if (emojiSuggestionsState && activePrefix.isNotEmpty()) {
                                                            val smartEmoji = when (activePrefix.lowercase().trim()) {
                                                                "love" -> "❤️"
                                                                "fire", "lit" -> "🔥"
                                                                "happy", "smile" -> "😊"
                                                                "laugh", "lol", "haha" -> "😂"
                                                                "cool" -> "😎"
                                                                "sad", "cry" -> "😢"
                                                                "party" -> "🎉"
                                                                "clap" -> "👏"
                                                                "ok", "okay" -> "👍"
                                                                "coffee", "tea" -> "☕"
                                                                "car" -> "🚗"
                                                                "heart" -> "💖"
                                                                "dog" -> "🐶"
                                                                "cat" -> "🐱"
                                                                "star" -> "⭐"
                                                                "yes", "check" -> "✅"
                                                                "no" -> "❌"
                                                                "pray", "thanks" -> "🙏"
                                                                "100", "hundred" -> "💯"
                                                                else -> null
                                                            }
                                                            if (smartEmoji != null) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .padding(horizontal = 3.dp)
                                                                        .clip(RoundedCornerShape(18.dp))
                                                                        .background(accentColor.copy(alpha = 0.16f))
                                                                        .clickable { onSuggestionClick(smartEmoji) }
                                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                                        .testTag("smart_emoji_suggestion"),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(text = smartEmoji, fontSize = 16.sp)
                                                                }
                                                            }
                                                        }
                                                    }

                                                IconButton(
                                                    onClick = {
                                                        onVoiceTypingToggle()
                                                    },
                                                    modifier = Modifier.size(42.dp).testTag("mic_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Mic,
                                                        contentDescription = "Voice Dictation",
                                                        tint = if (isVoiceTyping) Color.Red else keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(25.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            // FULL TOOLBAR MODE
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceEvenly
                                            ) {
                                                IconButton(
                                                    onClick = { isToolbarForceExpanded = false },
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .testTag("collapse_toolbar_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "Back to suggestions",
                                                        tint = keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                AiEngineIndicatorBadge(
                                                    activeEngine = activeAiEngineState,
                                                    accentColor = accentColor,
                                                    keyTextColor = keyTextColor,
                                                    onClick = {
                                                        val next = if (activeAiEngineState == ActiveAiEngine.OFFLINE) ActiveAiEngine.NONE else ActiveAiEngine.OFFLINE
                                                        settings.setActiveAiEngine(next)
                                                        activeAiEngineState = next
                                                        try {
                                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                                vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                                                            } else {
                                                                @Suppress("DEPRECATION")
                                                                vibrator?.vibrate(20)
                                                            }
                                                        } catch (_: Exception) {}
                                                    }
                                                )

                                                IconButton(
                                                    onClick = onUndo,
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .testTag("undo_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                                        contentDescription = "Undo",
                                                        tint = keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = onRedo,
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .testTag("redo_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.Redo,
                                                        contentDescription = "Redo",
                                                        tint = keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = onClipboardToggle,
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .testTag("clipboard_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentPaste,
                                                        contentDescription = "Clipboard history",
                                                        tint = if (isClipboard) accentColor else keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = onVoiceTypingToggle,
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .testTag("mic_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Mic,
                                                        contentDescription = "Voice Dictation",
                                                        tint = if (isVoiceTyping) Color.Red else keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { onProofreadClick() },
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .testTag("proofread_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Spellcheck,
                                                        contentDescription = "Direct Proofread",
                                                        tint = keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { onAutoFormatClick() },
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .testTag("auto_format_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AutoFixNormal,
                                                        contentDescription = "Auto Format",
                                                        tint = keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { isProofreadSheetOpen = true },
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .testTag("writing_tool_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AutoFixHigh,
                                                        contentDescription = "Writing tools",
                                                        tint = keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = onOpenSettings,
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .testTag("settings_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Settings,
                                                        contentDescription = "Settings",
                                                        tint = keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // --- KEYBOARD KEYS CONTAINER ---
        val currentLayer = when {
            isWisprVoiceOpen -> KeyboardLayer.WisprVoice
            isProofreadSheetOpen -> KeyboardLayer.Proofread
            isToolsDrawerOpen -> KeyboardLayer.ToolsDrawer
            isTextEditingOpen -> KeyboardLayer.TextEditing
            isClipboard -> KeyboardLayer.Clipboard
            isEmojis -> KeyboardLayer.Emojis
            isSymbols -> KeyboardLayer.Symbols
            else -> KeyboardLayer.Qwerty
        }

        val isOneHanded = oneHandedModeState != "off" &&
                currentLayer != KeyboardLayer.WisprVoice &&
                currentLayer != KeyboardLayer.ToolsDrawer

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(effectiveKeysHeight)
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOneHanded && oneHandedModeState == "right") {
                OneHandedSideRail(
                    modifier = Modifier
                        .weight(0.14f)
                        .fillMaxHeight(),
                    keyTextColor = keyTextColor,
                    accentColor = accentColor,
                    keyColor = specialKeyBg,
                    currentMode = "right",
                    onSwitchSide = {
                        settings.oneHandedMode = "left"
                        oneHandedModeState = "left"
                    },
                    onExpand = {
                        settings.oneHandedMode = "off"
                        oneHandedModeState = "off"
                    },
                    onSettings = onOpenSettings
                )
            }

            Box(
                modifier = Modifier
                    .weight(if (isOneHanded) 0.86f else 1.0f)
                    .fillMaxHeight()
            ) {
                AnimatedContent(
                    targetState = currentLayer,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(180, easing = LinearOutSlowInEasing)) +
                         scaleIn(initialScale = 0.97f, animationSpec = tween(180, easing = FastOutSlowInEasing))) togetherWith
                        (fadeOut(animationSpec = tween(140, easing = FastOutLinearInEasing)) +
                         scaleOut(targetScale = 1.02f, animationSpec = tween(140)))
                    },
                    label = "keyboard_layer_transition",
                    modifier = Modifier.fillMaxSize()
                ) { layer ->
                when (layer) {
                    KeyboardLayer.WisprVoice -> {
                        var polishedText by remember(rambleTranscript) { mutableStateOf("") }
                        val wisprEngine = remember { WisprFlowEngine.getInstance(context) }
                        var activeWisprMode by remember { mutableStateOf(settings.wisprFlowMode) }

                        LaunchedEffect(rambleTranscript, activeWisprMode) {
                            if (rambleTranscript.isNotBlank()) {
                                val res = wisprEngine.processVoiceTranscript(rambleTranscript, activeWisprMode)
                                polishedText = res.polishedText
                            } else {
                                polishedText = ""
                            }
                        }

                        WisprFlowVoicePanel(
                            isRecording = isRambleRecording,
                            audioLevel = rambleAudioLevel,
                            rawTranscript = rambleTranscript,
                            polishedTranscript = polishedText,
                            currentMode = activeWisprMode,
                            accentColor = accentColor,
                            keyTextColor = keyTextColor,
                            keyColor = normalKeyBg,
                            onModeSelect = { mode ->
                                activeWisprMode = mode
                                settings.wisprFlowMode = mode
                            },
                            onToggleRecording = onRambleToggle,
                            onCommitText = { textToCommit ->
                                val ic = (context as? TypeRightKeyboardService)?.currentInputConnection
                                ic?.commitText(textToCommit, 1)
                                isWisprVoiceOpen = false
                                onCancelRamble()
                            },
                            onCancel = {
                                onCancelRamble()
                                isWisprVoiceOpen = false
                            },
                            onSwitchToKeyboard = {
                                isWisprVoiceOpen = false
                            }
                        )
                    }
                    KeyboardLayer.Proofread -> {
                        GboardProofreadPanel(
                            keyTextColor = keyTextColor,
                            accentColor = accentColor,
                            keyColor = normalKeyBg,
                            onApplyText = { isProofreadSheetOpen = false },
                            onClose = { isProofreadSheetOpen = false },
                            onToggleLanguage = onToggleLanguage
                        )
                    }
                    KeyboardLayer.ToolsDrawer -> {
                        GboardToolsDrawer(
                            keyTextColor = keyTextColor,
                            accentColor = accentColor,
                            keyColor = normalKeyBg,
                            onToolClick = { tool ->
                                when (tool) {
                                    GboardTool.SMART_SELECT -> {
                                        isToolsDrawerOpen = false
                                        service?.isSmartSelectOpen?.value = true
                                        service?.applySmartSelection(SmartSelectLevel.SENTENCE)
                                    }
                                    GboardTool.AI_POLISH,
                                    GboardTool.PROOFREAD,
                                    GboardTool.OFFLINE_AI -> {
                                        isToolsDrawerOpen = false
                                        isProofreadSheetOpen = true
                                    }
                                    GboardTool.RAMBLE -> {
                                        isToolsDrawerOpen = false
                                        onVoiceTypingToggle()
                                    }
                                    GboardTool.CLIPBOARD -> {
                                        isToolsDrawerOpen = false
                                        onClipboardToggle()
                                    }
                                    GboardTool.THEMES -> {
                                        val nextTheme = when {
                                            themeState.equals(KeyboardSettings.THEME_LIGHT, ignoreCase = true) || themeState.equals("Light Arrangement", ignoreCase = true) -> KeyboardSettings.THEME_DARK
                                            themeState.equals(KeyboardSettings.THEME_DARK, ignoreCase = true) -> KeyboardSettings.THEME_NIGHT
                                            else -> KeyboardSettings.THEME_LIGHT
                                        }
                                        settings.theme = nextTheme
                                        themeState = nextTheme
                                    }
                                    GboardTool.TRANSLATE -> {
                                        isToolsDrawerOpen = false
                                        onAiPolishClick()
                                    }
                                    GboardTool.TEXT_EDIT -> {
                                        isToolsDrawerOpen = false
                                        isTextEditingOpen = true
                                    }
                                    GboardTool.ONE_HANDED -> {
                                        val nextMode = when (oneHandedModeState) {
                                            "off" -> "right"
                                            "right" -> "left"
                                            else -> "off"
                                        }
                                        settings.oneHandedMode = nextMode
                                        oneHandedModeState = nextMode
                                        isToolsDrawerOpen = false
                                    }
                                    GboardTool.SETTINGS -> {
                                        onOpenSettings()
                                    }
                                    GboardTool.LANGUAGE -> {
                                        isToolsDrawerOpen = false
                                        onToggleLanguage()
                                    }
                                }
                            },
                            onClose = { isToolsDrawerOpen = false }
                        )
                    }
                    KeyboardLayer.TextEditing -> {
                        TextEditingPanel(
                            keyTextColor = keyTextColor,
                            accentColor = accentColor,
                            keyColor = normalKeyBg,
                            specialKeyBg = specialKeyBg,
                            onNavigate = { keyCode ->
                                val ic = (context as? TypeRightKeyboardService)?.currentInputConnection
                                ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                                ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                            },
                            onSelectAll = {
                                val ic = (context as? TypeRightKeyboardService)?.currentInputConnection
                                ic?.performContextMenuAction(android.R.id.selectAll)
                            },
                            onCut = {
                                val ic = (context as? TypeRightKeyboardService)?.currentInputConnection
                                ic?.performContextMenuAction(android.R.id.cut)
                            },
                            onCopy = {
                                val ic = (context as? TypeRightKeyboardService)?.currentInputConnection
                                ic?.performContextMenuAction(android.R.id.copy)
                            },
                            onPaste = {
                                val ic = (context as? TypeRightKeyboardService)?.currentInputConnection
                                ic?.performContextMenuAction(android.R.id.paste)
                            },
                            onClose = { isTextEditingOpen = false }
                        )
                    }
                    KeyboardLayer.Clipboard -> {
                        ClipboardPanel(
                            clipboardRepository = clipboardRepository,
                            keyTextColor = keyTextColor,
                            accentColor = accentColor,
                            keyColor = normalKeyBg,
                            onPasteText = { pastedText ->
                                val ic = (context as? TypeRightKeyboardService)?.currentInputConnection
                                ic?.commitText(pastedText, 1)
                            },
                            onClose = onClipboardToggle
                        )
                    }
                    KeyboardLayer.Emojis -> {
                        val ic = (context as? TypeRightKeyboardService)?.currentInputConnection
                        val textBefore = try { ic?.getTextBeforeCursor(150, 0)?.toString().orEmpty() } catch (_: Exception) { "" }
                        val activeText = when {
                            currentTypedWord.isNotBlank() -> currentTypedWord
                            wordUnderCursor.isNotBlank() -> wordUnderCursor
                            textBefore.isNotBlank() -> textBefore
                            !previousWord.isNullOrBlank() -> previousWord
                            else -> ""
                        }
                        val service = context as? TypeRightKeyboardService
                        RevampedEmojiLayout(
                            keyColor = normalKeyBg,
                            textColor = keyTextColor,
                            accentColor = accentColor,
                            typedText = activeText,
                            onKeyClick = onKeyClick,
                            onEmojiToggle = onEmojiToggle,
                            onDelete = onDelete,
                            onMediaCommit = { mediaItem ->
                                service?.commitRichMedia(mediaItem)
                            }
                        )
                    }
                    KeyboardLayer.Symbols -> {
                        SymbolLayout(
                            keyColor = normalKeyBg,
                            textColor = keyTextColor,
                            accentColor = accentColor,
                            specialKeyBg = specialKeyBg,
                            enterKeyBg = enterKeyBg,
                            enterKeyTextColor = enterKeyTextColor,
                            enterIcon = enterIcon,
                            onKeyClick = onKeyClick,
                            onDelete = onDelete,
                            onDeleteWord = onDeleteWord,
                            onSymbolsToggle = onSymbolsToggle,
                            onSpaceClick = onSpace,
                            onEnterClick = onEnter,
                            onVoiceTypingToggle = onVoiceTypingToggle,
                            onSpaceSwipeLeft = onSpaceSwipeLeft,
                            onSpaceSwipeRight = onSpaceSwipeRight,
                            spacebarLabel = spacebarLabel,
                            onSpaceLongClick = onToggleLanguage
                        )
                    }
                    KeyboardLayer.Qwerty -> {
                        QwertyLayout(
                            keyColor = normalKeyBg,
                            textColor = keyTextColor,
                            accentColor = accentColor,
                            specialKeyBg = specialKeyBg,
                            enterKeyBg = enterKeyBg,
                            enterKeyTextColor = enterKeyTextColor,
                            enterIcon = enterIcon,
                            isShift = isShift,
                            isCapsLock = isCapsLock,
                            showNumberRow = numberRowEnabledState,
                            onKeyClick = onKeyClick,
                            onDelete = onDelete,
                            onDeleteWord = onDeleteWord,
                            onShiftToggle = onShiftToggle,
                            onSymbolsToggle = onSymbolsToggle,
                            onEmojiToggle = onEmojiToggle,
                            onSpaceClick = onSpace,
                            onEnterClick = onEnter,
                            onSwipeResult = { topWord, candidates, path ->
                                service?.handleSwipeResult(topWord, candidates, path)
                            },
                            dictionaryManager = dictionaryManager,
                            onVoiceTypingToggle = onVoiceTypingToggle,
                            onTapCoordinates = onTapCoordinates,
                            onSpaceSwipeLeft = onSpaceSwipeLeft,
                            onSpaceSwipeRight = onSpaceSwipeRight,
                            spacebarLabel = spacebarLabel,
                            onSpaceLongClick = onToggleLanguage,
                            prevWord = previousWords.lastOrNull(),
                            swipeEnabled = swipeEnabledState
                        )
                    }
                }
            }
        }

            if (isOneHanded && oneHandedModeState == "left") {
                OneHandedSideRail(
                    modifier = Modifier
                        .weight(0.14f)
                        .fillMaxHeight(),
                    keyTextColor = keyTextColor,
                    accentColor = accentColor,
                    keyColor = specialKeyBg,
                    currentMode = "left",
                    onSwitchSide = {
                        settings.oneHandedMode = "right"
                        oneHandedModeState = "right"
                    },
                    onExpand = {
                        settings.oneHandedMode = "off"
                        oneHandedModeState = "off"
                    },
                    onSettings = onOpenSettings
                )
            }
        }

        // --- DEDICATED SYSTEM NAVIGATION CLEARANCE LAYER ---
        // Lifts the keys above the Android gesture navigation handle and IME switcher
        if (bottomClearanceHeight > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomClearanceHeight)
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                HorizontalDivider(
                    color = keyTextColor.copy(alpha = 0.08f),
                    thickness = 0.5.dp,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // Clean empty clearance layer reserved for system navigation bar and system IME switcher
                Spacer(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
}
}

/**
 * Standard QWERTY character keys styled in dynamic Material You flat rounded capsules.
 */
@Composable
fun QwertyLayout(
    keyColor: Color,
    textColor: Color,
    accentColor: Color,
    specialKeyBg: Color,
    enterKeyBg: Color,
    enterKeyTextColor: Color,
    enterIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
    isShift: Boolean,
    isCapsLock: Boolean,
    onKeyClick: (String) -> Unit,
    onDelete: () -> Unit,
    onDeleteWord: () -> Unit,
    onShiftToggle: () -> Unit,
    onSymbolsToggle: () -> Unit,
    onEmojiToggle: () -> Unit,
    onSpaceClick: () -> Unit,
    onEnterClick: () -> Unit,
    onSwipeResult: (String, List<String>, List<android.graphics.PointF>) -> Unit,
    dictionaryManager: DictionaryManager,
    onVoiceTypingToggle: () -> Unit,
    onTapCoordinates: (Float, Float) -> Unit = { _, _ -> },
    onSpaceSwipeLeft: (() -> Unit)? = null,
    onSpaceSwipeRight: (() -> Unit)? = null,
    showNumberRow: Boolean = false,
    spacebarLabel: String = "English",
    onSpaceLongClick: (() -> Unit)? = null,
    prevWord: String? = null,
    swipeEnabled: Boolean = true
) {
    val numberRow = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')
    val row1 = listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p')
    val row2 = listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l')
    val row3 = listOf('z', 'x', 'c', 'v', 'b', 'n', 'm')

    val secondaryMap = mapOf(
        'q' to '1', 'w' to '2', 'e' to '3', 'r' to '4', 't' to '5',
        'y' to '6', 'u' to '7', 'i' to '8', 'o' to '9', 'p' to '0',
        'a' to '@', 's' to '#', 'd' to '$', 'f' to '%', 'g' to '&',
        'h' to '*', 'j' to '-', 'k' to '+', 'l' to '=',
        'z' to '_', 'x' to '"', 'c' to '\'', 'v' to ':', 'b' to ';',
        'n' to '/', 'm' to '?'
    )

    val swipePoints = remember { androidx.compose.runtime.mutableStateListOf<Offset>() }
    val normalizedPath = remember { androidx.compose.runtime.mutableStateListOf<android.graphics.PointF>() }
    val scale = LocalKeyboardScale.current
    val rowSpacing = (6.5f * scale).coerceIn(3f, 8.5f).dp
    val keySpacing = (4.5f * scale.coerceAtMost(1.15f)).coerceIn(2.5f, 6f).dp
    var isSwiping by remember { androidx.compose.runtime.mutableStateOf(false) }
    var columnSize by remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var columnLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var spaceBarCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var spaceBarBounds by remember { mutableStateOf<Rect?>(null) }
    var isSpaceTouching by remember { mutableStateOf(false) }
    var isSpaceScrolling by remember { mutableStateOf(false) }

    fun updateSpaceBarBounds() {
        val parent = columnLayoutCoordinates
        val coords = spaceBarCoords
        if (parent != null && parent.isAttached && coords != null && coords.isAttached) {
            val topLeft = parent.localPositionOf(coords, Offset.Zero)
            spaceBarBounds = Rect(
                topLeft,
                Size(coords.size.width.toFloat(), coords.size.height.toFloat())
            )
        }
    }

    val trailAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    val trailElasticity = remember { androidx.compose.animation.core.Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    columnSize = it.size
                    columnLayoutCoordinates = it
                    updateSpaceBarBounds()
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            val down = event.changes.firstOrNull() ?: continue
                            val startPosition = down.position
                            val activePointerId = down.id

                            val colW = columnSize.width
                            val colH = columnSize.height

                            // 1. Check if touch down originated on or near the spacebar / bottom control row
                            val bottomRowStartY = if (showNumberRow) (3.65f / 4.85f) * colH else 0.70f * colH
                            val isTouchOnSpace = if (spaceBarBounds != null) {
                                spaceBarBounds!!.inflate(36f).contains(startPosition)
                            } else {
                                val isInBottomRow = colH > 0 && startPosition.y >= (bottomRowStartY - 12f)
                                val isInSpaceX = colW > 0 && startPosition.x >= (colW * 0.20f) && startPosition.x <= (colW * 0.88f)
                                isInBottomRow && isInSpaceX
                            }

                            // 2. Gesture-based typing should NEVER originate from the bottom control row (?123, comma, emoji, space, period, enter)
                            val isTouchInBottomRow = colH > 0 && startPosition.y >= (bottomRowStartY - 8f)

                            if (!swipeEnabled || isTouchOnSpace || isTouchInBottomRow || isSpaceScrolling || isSpaceTouching) {
                                // Touch originated on the space button or bottom control row, or swipe is disabled.
                                // Glide action MUST NOT be enabled, no trail should be drawn, and no swipe word decoded.
                                // We leave pointer events unconsumed so the spacebar's onSwipeLeft/onSwipeRight
                                // cursor scrolling operates with maximum smoothness.
                                while (true) {
                                    val nextEvent = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    val change = nextEvent.changes.firstOrNull { it.id == activePointerId } ?: break
                                    if (!change.pressed) break
                                }
                                continue
                            }

                            // Record touch down coordinate for the typing offset ML predictor
                            if (colW > 0 && colH > 0) {
                                val tx = (startPosition.x / colW).coerceIn(0f, 1f)
                                val letterStartY = if (showNumberRow) (0.85f / 4.85f) * colH else 0f
                                val letterHeight = if (showNumberRow) (3.0f / 4.85f) * colH else 0.75f * colH
                                val ty = if (letterHeight > 0f) ((startPosition.y - letterStartY) / letterHeight).coerceIn(0f, 1f) else 0f
                                onTapCoordinates(tx, ty)
                            }

                            val pendingPoints = mutableListOf<Offset>()
                            pendingPoints.add(startPosition)

                            swipePoints.clear()
                            normalizedPath.clear()

                            var detectedSwipe = false

                            while (true) {
                                val moveEvent = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                val change = moveEvent.changes.firstOrNull { it.id == activePointerId } ?: break

                                if (change.pressed) {
                                    if (isSpaceScrolling || isSpaceTouching) {
                                        // Space cursor scrolling or space touch was activated; immediately abort glide typing
                                        detectedSwipe = false
                                        isSwiping = false
                                        swipePoints.clear()
                                        normalizedPath.clear()
                                        // Drain all remaining events for this pointer until release so no glide action can retrigger
                                        while (true) {
                                            val nextEvent = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                            val nextChange = nextEvent.changes.firstOrNull { it.id == activePointerId } ?: break
                                            if (!nextChange.pressed) break
                                        }
                                        break
                                    }
                                    val currentPos = change.position
                                    // Elastic spring physics interpolation towards raw touch coordinate
                                    if (pendingPoints.size > 1) {
                                        val prev = pendingPoints.last()
                                        val elasticPos = Offset(
                                            prev.x + (currentPos.x - prev.x) * 0.85f,
                                            prev.y + (currentPos.y - prev.y) * 0.85f
                                        )
                                        pendingPoints.add(elasticPos)
                                    } else {
                                        pendingPoints.add(currentPos)
                                    }

                                    val dist = (currentPos - startPosition).getDistance()
                                    if (!detectedSwipe && swipeEnabled && dist > 32.dp.toPx() && pendingPoints.size >= 4) {
                                        detectedSwipe = true
                                        isSwiping = true
                                        coroutineScope.launch {
                                            trailAlpha.snapTo(1f)
                                            trailElasticity.snapTo(1f)
                                        }
                                        swipePoints.clear()
                                        swipePoints.addAll(pendingPoints)

                                        val w = columnSize.width
                                        val h = columnSize.height
                                        val letterStartY = if (showNumberRow) (0.85f / 4.85f) * h else 0f
                                        val letterHeight = if (showNumberRow) (3.0f / 4.85f) * h else 0.75f * h
                                        if (w > 0 && h > 0 && letterHeight > 0f) {
                                            pendingPoints.forEach { pt ->
                                                val nx = (pt.x / w).coerceIn(0f, 1f)
                                                val ny = ((pt.y - letterStartY) / letterHeight).coerceIn(0f, 1f)
                                                normalizedPath.add(android.graphics.PointF(nx, ny))
                                            }
                                        }
                                    }

                                    if (detectedSwipe) {
                                        change.consume()
                                        if (swipePoints.isEmpty() || swipePoints.last() != currentPos) {
                                            swipePoints.add(currentPos)
                                        }
                                        val w = columnSize.width
                                        val h = columnSize.height
                                        val letterStartY = if (showNumberRow) (0.85f / 4.85f) * h else 0f
                                        val letterHeight = if (showNumberRow) (3.0f / 4.85f) * h else 0.75f * h
                                        if (w > 0 && h > 0 && letterHeight > 0f) {
                                            val nx = (currentPos.x / w).coerceIn(0f, 1f)
                                            val ny = ((currentPos.y - letterStartY) / letterHeight).coerceIn(0f, 1f)
                                            val lastPt = normalizedPath.lastOrNull()
                                            if (lastPt == null || lastPt.x != nx || lastPt.y != ny) {
                                                normalizedPath.add(android.graphics.PointF(nx, ny))
                                            }
                                        }
                                    }
                                } else {
                                    if (detectedSwipe && !isSpaceScrolling && !isSpaceTouching) {
                                        change.consume()

                                        val decoded = dictionaryManager.decodeSwipePath(normalizedPath.toList(), prevWord)
                                        if (decoded.isNotEmpty()) {
                                            val formattedCandidates = decoded.map { word ->
                                                TypingPolicy.swipeCase(word, isCapsLock)
                                            }
                                            val bestWord = formattedCandidates.first()
                                            onSwipeResult(bestWord, formattedCandidates, normalizedPath.toList())
                                        }

                                        isSwiping = false
                                        // Animate subtle fade-out animation and elastic spring physics contraction
                                        coroutineScope.launch {
                                            launch {
                                                trailElasticity.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    )
                                                )
                                            }
                                            trailAlpha.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing)
                                            )
                                            swipePoints.clear()
                                            normalizedPath.clear()
                                        }
                                    } else {
                                        swipePoints.clear()
                                        pendingPoints.clear()
                                        normalizedPath.clear()
                                        isSwiping = false
                                    }
                                    break
                                }
                            }
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
        if (showNumberRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.85f),
                horizontalArrangement = Arrangement.spacedBy(keySpacing)
            ) {
                numberRow.forEach { char ->
                    KeyButton(
                        text = char.toString(),
                        modifier = Modifier.weight(1.0f),
                        keyBg = keyColor,
                        textColor = textColor,
                        onLongClick = null
                    ) {
                        onKeyClick(char.toString())
                    }
                }
            }
        }

        // Row 1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            horizontalArrangement = Arrangement.spacedBy(keySpacing)
        ) {
            row1.forEach { char ->
                val dispChar = if (isShift) char.uppercaseChar() else char
                val secChar = if (showNumberRow) null else secondaryMap[char]
                KeyButton(
                    text = dispChar.toString(),
                    secondaryText = secChar?.toString(),
                    modifier = Modifier.weight(1.0f),
                    keyBg = keyColor,
                    textColor = textColor,
                    onLongClick = if (secChar != null) { { onKeyClick(secChar.toString()) } } else null
                ) {
                    onKeyClick(dispChar.toString())
                }
            }
        }

        // Row 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            horizontalArrangement = Arrangement.spacedBy(keySpacing)
        ) {
            Spacer(modifier = Modifier.weight(0.5f))
            row2.forEach { char ->
                val dispChar = if (isShift) char.uppercaseChar() else char
                KeyButton(
                    text = dispChar.toString(),
                    modifier = Modifier.weight(1.0f),
                    keyBg = keyColor,
                    textColor = textColor,
                    onLongClick = { secondaryMap[char]?.let { sec -> onKeyClick(sec.toString()) } }
                ) {
                    onKeyClick(dispChar.toString())
                }
            }
            Spacer(modifier = Modifier.weight(0.5f))
        }

        // Row 3
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            horizontalArrangement = Arrangement.spacedBy(keySpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shift Key
            val shiftIconColor = if (isCapsLock) accentColor else if (isShift) accentColor.copy(alpha = 0.85f) else textColor
            IconButtonKey(
                icon = Icons.Default.ArrowUpward,
                modifier = Modifier.weight(1.4f),
                keyBg = if (isCapsLock || isShift) accentColor.copy(alpha = 0.2f) else specialKeyBg,
                tint = shiftIconColor,
                onClick = onShiftToggle
            )

            row3.forEach { char ->
                val dispChar = if (isShift) char.uppercaseChar() else char
                KeyButton(
                    text = dispChar.toString(),
                    modifier = Modifier.weight(1.0f),
                    keyBg = keyColor,
                    textColor = textColor,
                    onLongClick = { secondaryMap[char]?.let { sec -> onKeyClick(sec.toString()) } }
                ) {
                    onKeyClick(dispChar.toString())
                }
            }

            // Backspace key
            IconButtonKey(
                icon = Icons.Default.Backspace,
                modifier = Modifier
                    .weight(1.4f)
                    .testTag("delete_key"),
                keyBg = specialKeyBg,
                tint = textColor,
                onClick = onDelete,
                onHold = onDeleteWord,
                onSwipeLeft = onDeleteWord
            )
        }

        // Row 4: Minimal, spacious, ergonomic bottom row (?123, Comma, Emoji, Spacebar, Period, Enter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            horizontalArrangement = Arrangement.spacedBy(keySpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. ?123 Symbol Toggle
            KeyButton(
                text = "?123",
                modifier = Modifier.weight(1.35f),
                keyBg = specialKeyBg,
                textColor = textColor
            ) {
                onSymbolsToggle()
            }

            // 2. Comma "," Key
            KeyButton(
                text = ",",
                secondaryText = "!",
                modifier = Modifier.weight(1.0f),
                keyBg = specialKeyBg,
                textColor = textColor,
                onLongClick = { onKeyClick("!") }
            ) {
                onKeyClick(",")
            }

            // 3. Emoji Key
            IconButtonKey(
                icon = Icons.Default.Mood,
                modifier = Modifier
                    .weight(1.0f)
                    .testTag("bottom_emoji_button"),
                keyBg = specialKeyBg,
                tint = textColor.copy(alpha = 0.85f),
                onClick = onEmojiToggle
            )

            // 4. Spacious Spacebar (Flawless thumb reach & smooth swipe glide)
            KeyButton(
                text = spacebarLabel,
                modifier = Modifier
                    .weight(4.8f)
                    .testTag("space_key")
                    .onGloballyPositioned { coords ->
                        spaceBarCoords = coords
                        updateSpaceBarBounds()
                    },
                keyBg = keyColor,
                textColor = textColor.copy(alpha = 0.65f),
                onLongClick = onSpaceLongClick,
                onSwipeLeft = onSpaceSwipeLeft,
                onSwipeRight = onSpaceSwipeRight,
                onTouchStateChange = { touching ->
                    isSpaceTouching = touching
                    if (touching) {
                        isSwiping = false
                        swipePoints.clear()
                        normalizedPath.clear()
                    }
                },
                onDragStateChange = { dragging ->
                    isSpaceScrolling = dragging
                    if (dragging) {
                        isSwiping = false
                        swipePoints.clear()
                        normalizedPath.clear()
                    }
                }
            ) {
                onSpaceClick()
            }

            // 5. Period "." Key (with secondary question hint & long-press)
            KeyButton(
                text = ".",
                secondaryText = "?",
                modifier = Modifier.weight(1.0f),
                keyBg = specialKeyBg,
                textColor = textColor,
                onLongClick = { onKeyClick("?") }
            ) {
                onKeyClick(".")
            }

            // 6. Enter / Action Key (Themed action capsule)
            IconButtonKey(
                icon = enterIcon,
                modifier = Modifier
                    .weight(1.35f)
                    .testTag("enter_key"),
                keyBg = enterKeyBg,
                tint = enterKeyTextColor,
                onClick = onEnterClick
            )
        }
    }

    val currentSwipePoints = swipePoints.toList()
    if ((isSwiping || trailAlpha.value > 0.01f) && currentSwipePoints.size > 1 && !isSpaceScrolling && !isSpaceTouching) {
        val alphaScale = trailAlpha.value
        val elasticityScale = trailElasticity.value
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val start = currentSwipePoints.firstOrNull() ?: return@Canvas
            val path = Path()
            path.moveTo(start.x, start.y)

            for (i in 1 until currentSwipePoints.size) {
                val p1 = currentSwipePoints[i - 1]
                val p2 = currentSwipePoints[i]
                val midX = (p1.x + p2.x) / 2f
                val midY = (p1.y + p2.y) / 2f
                path.quadraticTo(p1.x, p1.y, midX, midY)
            }

            // 1. Bottom neon glow layer (wide, soft alpha with elastic spring scaling)
            drawPath(
                path = path,
                color = accentColor.copy(alpha = 0.3f * alphaScale),
                style = Stroke(
                    width = 14.dp.toPx() * alphaScale * (0.3f + 0.7f * elasticityScale),
                    cap = StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )

            // 2. Middle vibrant accent color layer
            drawPath(
                path = path,
                color = accentColor.copy(alpha = 0.9f * alphaScale),
                style = Stroke(
                    width = 6.dp.toPx() * alphaScale * (0.3f + 0.7f * elasticityScale),
                    cap = StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )

            // 3. Top core highlight layer (bright white)
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.95f * alphaScale),
                style = Stroke(
                    width = 2.5.dp.toPx() * alphaScale * (0.3f + 0.7f * elasticityScale),
                    cap = StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )

            // Draw the glowing comet tip only if we are actively swiping
            if (isSwiping) {
                val tip = swipePoints.last()
                drawCircle(
                    color = accentColor.copy(alpha = 0.25f * alphaScale * elasticityScale),
                    radius = 18.dp.toPx() * alphaScale * elasticityScale,
                    center = tip
                )
                drawCircle(
                    color = accentColor.copy(alpha = alphaScale * elasticityScale),
                    radius = 9.dp.toPx() * alphaScale * elasticityScale,
                    center = tip
                )
                drawCircle(
                    color = Color.White.copy(alpha = alphaScale * elasticityScale),
                    radius = 4.5.dp.toPx() * alphaScale * elasticityScale,
                    center = tip
                )
            }
        }
    }
}
}

/**
 * Symbol and Numbers Keyboard Layout fully functional with bottom Row 4
 */
@Composable
fun SymbolLayout(
    keyColor: Color,
    textColor: Color,
    accentColor: Color,
    specialKeyBg: Color,
    enterKeyBg: Color,
    enterKeyTextColor: Color,
    enterIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
    onKeyClick: (String) -> Unit,
    onDelete: () -> Unit,
    onDeleteWord: () -> Unit,
    onSymbolsToggle: () -> Unit,
    onSpaceClick: () -> Unit,
    onEnterClick: () -> Unit,
    onVoiceTypingToggle: () -> Unit,
    onSpaceSwipeLeft: (() -> Unit)? = null,
    onSpaceSwipeRight: (() -> Unit)? = null,
    spacebarLabel: String = "English",
    onSpaceLongClick: (() -> Unit)? = null
) {
    var isSecondarySymbols by remember { mutableStateOf(false) }

    // Primary Symbol Page (Image 2)
    val page1Row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val page1Row2 = listOf("@", "#", "£", "_", "&", "-", "+", "(", ")", "/")
    val page1Row3 = listOf("*", "\"", "'", ":", ";", "!", "?")

    // Secondary Symbol Page
    val page2Row1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆")
    val page2Row2 = listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\")
    val page2Row3 = listOf("%", "©", "®", "™", "✓", "[", "]")

    val activeRow1 = if (isSecondarySymbols) page2Row1 else page1Row1
    val activeRow2 = if (isSecondarySymbols) page2Row2 else page1Row2
    val activeRow3 = if (isSecondarySymbols) page2Row3 else page1Row3

    val scale = LocalKeyboardScale.current
    val rowSpacing = (6.5f * scale).coerceIn(3f, 8.5f).dp
    val keySpacing = (4.5f * scale.coerceAtMost(1.15f)).coerceIn(2.5f, 6f).dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(rowSpacing)
    ) {
        // Row 1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            horizontalArrangement = Arrangement.spacedBy(keySpacing)
        ) {
            activeRow1.forEach { char ->
                KeyButton(text = char, modifier = Modifier.weight(1.0f), keyBg = keyColor, textColor = textColor) {
                    onKeyClick(char)
                }
            }
        }

        // Row 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            horizontalArrangement = Arrangement.spacedBy(keySpacing)
        ) {
            activeRow2.forEach { char ->
                KeyButton(text = char, modifier = Modifier.weight(1.0f), keyBg = keyColor, textColor = textColor) {
                    onKeyClick(char)
                }
            }
        }

        // Row 3 (=\< toggle on left, 7 symbols in middle, Backspace on right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            horizontalArrangement = Arrangement.spacedBy(keySpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(
                text = if (isSecondarySymbols) "1/2" else "=\\<",
                modifier = Modifier.weight(1.4f),
                keyBg = if (isSecondarySymbols) accentColor.copy(alpha = 0.18f) else specialKeyBg,
                textColor = if (isSecondarySymbols) accentColor else textColor
            ) {
                isSecondarySymbols = !isSecondarySymbols
            }

            activeRow3.forEach { char ->
                KeyButton(text = char, modifier = Modifier.weight(1.0f), keyBg = keyColor, textColor = textColor) {
                    onKeyClick(char)
                }
            }

            IconButtonKey(
                icon = Icons.Default.Backspace,
                modifier = Modifier.weight(1.4f),
                keyBg = specialKeyBg,
                tint = textColor,
                onClick = onDelete,
                onHold = onDeleteWord,
                onSwipeLeft = onDeleteWord
            )
        }

        // Row 4 (ABC, Comma, Emoji, English Spacebar, Period, Enter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            horizontalArrangement = Arrangement.spacedBy(keySpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(text = "ABC", modifier = Modifier.weight(1.35f), keyBg = specialKeyBg, textColor = textColor) {
                onSymbolsToggle()
            }

            KeyButton(
                text = ",",
                secondaryText = if (isSecondarySymbols) "1/2" else null,
                modifier = Modifier.weight(1.0f),
                keyBg = specialKeyBg,
                textColor = textColor,
                onLongClick = { isSecondarySymbols = !isSecondarySymbols }
            ) {
                onKeyClick(",")
            }

            IconButtonKey(
                icon = Icons.Default.Mood,
                modifier = Modifier
                    .weight(1.0f)
                    .testTag("symbol_bottom_emoji_button"),
                keyBg = specialKeyBg,
                tint = textColor.copy(alpha = 0.85f),
                onClick = onSymbolsToggle
            )

            KeyButton(
                text = spacebarLabel,
                modifier = Modifier.weight(4.8f),
                keyBg = keyColor,
                textColor = textColor.copy(alpha = 0.65f),
                onLongClick = onSpaceLongClick,
                onSwipeLeft = onSpaceSwipeLeft,
                onSwipeRight = onSpaceSwipeRight
            ) {
                onSpaceClick()
            }

            KeyButton(text = ".", modifier = Modifier.weight(1.0f), keyBg = specialKeyBg, textColor = textColor) {
                onKeyClick(".")
            }

            IconButtonKey(
                icon = enterIcon,
                modifier = Modifier
                    .weight(1.35f)
                    .testTag("symbol_enter_key"),
                keyBg = enterKeyBg,
                tint = enterKeyTextColor,
                onClick = onEnterClick
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RowScope.KeyButton(
    text: String,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    keyBg: Color,
    textColor: Color,
    onLongClick: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    onDragStateChange: ((Boolean) -> Unit)? = null,
    onTouchStateChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
) {
    val style = LocalKeyboardStyle.current
    val scale = LocalKeyboardScale.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.935f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(40, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "key_press_scale"
    )

    val effectiveKeyBg = if (isPressed) keyBg.copy(alpha = 0.78f) else keyBg
    val effectiveTextColor = if (isPressed) textColor.copy(alpha = 0.88f) else textColor

    val currentShape = style.keyShape
    val currentBorder = style.keyBorder

    val density = androidx.compose.ui.platform.LocalDensity.current
    val swipeThresholdPx = with(density) { 14.dp.toPx() }

    val hasBevel = style.isRetro && style.keyBevelColor != Color.Transparent && keyBg != Color.Transparent

    val baseModifier = modifier
        .padding(vertical = 1.dp, horizontal = 0.5.dp)
        .fillMaxHeight()
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
        .shadow(
            elevation = if (keyBg != Color.Transparent) (if (hasBevel) 1.5.dp else 0.8.dp) else 0.dp,
            shape = currentShape,
            clip = false
        )
        .clip(currentShape)
        .background(if (hasBevel) style.keyBevelColor else effectiveKeyBg)
        .run {
            if (hasBevel) {
                this.padding(bottom = if (isPressed) 0.8.dp else 2.5.dp)
                    .clip(currentShape)
                    .background(effectiveKeyBg)
            } else {
                this
            }
        }
        .run {
            val subtleBorder = currentBorder ?: if (keyBg != Color.Transparent) {
                BorderStroke(0.5.dp, if (style.isDark) Color(0x1AFFFFFF) else Color(0x14000000))
            } else null

            if (subtleBorder != null && keyBg != Color.Transparent) {
                this.border(subtleBorder, currentShape)
            } else {
                this
            }
        }

    val interactiveModifier = if (onSwipeLeft != null || onSwipeRight != null) {
        baseModifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onTouchStateChange?.invoke(true)
                        var dragAccumulatedX = 0f
                        var isDragging = false
                        var lastX = down.position.x
                        
                        try {
                            do {
                                val event = awaitPointerEvent()
                                val dragEvent = event.changes.firstOrNull()
                                if (dragEvent != null && dragEvent.pressed) {
                                    val currentX = dragEvent.position.x
                                    val diffX = currentX - lastX
                                    lastX = currentX
                                    
                                    if (!isDragging && kotlin.math.abs(dragAccumulatedX + diffX) > swipeThresholdPx) {
                                        isDragging = true
                                        onDragStateChange?.invoke(true)
                                    }
                                    
                                    if (isDragging) {
                                        dragEvent.consume()
                                        dragAccumulatedX += diffX
                                        val step = swipeThresholdPx
                                        while (dragAccumulatedX >= step) {
                                            onSwipeRight?.invoke()
                                            dragAccumulatedX -= step
                                        }
                                        while (dragAccumulatedX <= -step) {
                                            onSwipeLeft?.invoke()
                                            dragAccumulatedX += step
                                        }
                                    } else {
                                        dragAccumulatedX += diffX
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                            
                            if (isDragging) {
                                onDragStateChange?.invoke(false)
                            } else {
                                onClick()
                            }
                        } finally {
                            onTouchStateChange?.invoke(false)
                            if (isDragging) {
                                onDragStateChange?.invoke(false)
                            }
                        }
                    }
                }
            }
            .testTag("key_$text")
    } else {
        baseModifier
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick
            )
            .testTag("key_$text")
    }

    Box(
        modifier = interactiveModifier,
        contentAlignment = Alignment.Center
    ) {
        if (style.showPressPopup && isPressed && text.isNotEmpty() && text.length == 1) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopCenter,
                offset = androidx.compose.ui.unit.IntOffset(0, (-80 * scale).toInt())
            ) {
                Box(
                    modifier = Modifier
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp), clip = false)
                        .background(
                            if (style.isRetro) (if (style.isDark) Color(0xFF18221B) else Color(0xFFF7F2EC))
                            else if (style.isDark) Color(0xFF2E2E33) else Color(0xFFFFFFFF),
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (style.isRetro) style.accentColor.copy(alpha = 0.6f)
                            else if (style.isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.12f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = (16f * scale).coerceIn(12f, 20f).dp, vertical = (10f * scale).coerceIn(8f, 14f).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text,
                        color = if (style.isRetro) style.keyTextColor else if (style.isDark) Color.White else Color(0xFF1D2024),
                        fontSize = (24f * scale).coerceIn(18f, 32f).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (style.isMonospace) FontFamily.Monospace else FontFamily.SansSerif
                    )
                }
            }
        }

        if (secondaryText != null && text.length == 1) {
            Text(
                text = secondaryText,
                color = effectiveTextColor.copy(alpha = 0.38f),
                fontSize = (8.5f * scale).coerceIn(6.5f, 11f).sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = if (style.isMonospace) FontFamily.Monospace else FontFamily.SansSerif,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = (1.5f * scale).coerceIn(1f, 3f).dp, end = (3f * scale).coerceIn(2f, 5f).dp)
            )
        }

        if (style.isRetro && style.spacebarLineColor != Color.Transparent && (text.contains("English") || text.contains("Manglish"))) {
            Box(
                modifier = Modifier
                    .width((42f * scale).coerceIn(30f, 60f).dp)
                    .height(2.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(1.dp))
                    .background(style.spacebarLineColor)
            )
        }

        val isMultiChar = text.length > 1
        val calculatedFontSize = if (isMultiChar) ((13.5f * scale).coerceIn(10.5f, 17f).sp) else ((21f * scale).coerceIn(15f, 27f).sp)
        Text(
            text = text,
            color = effectiveTextColor,
            fontSize = calculatedFontSize,
            fontWeight = if (isMultiChar) FontWeight.Medium else (if (style.isRetro) FontWeight.Bold else FontWeight.Normal),
            fontFamily = if (style.isMonospace) FontFamily.Monospace else FontFamily.SansSerif,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip
        )
    }
}

fun Modifier.repeatingClickable(
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource,
    enabled: Boolean = true,
    initialDelayMillis: Long = 350,
    delayMillis: Long = 70,
    onClick: () -> Unit,
    onHold: () -> Unit
): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnHold by rememberUpdatedState(onHold)
    
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pressInteraction = androidx.compose.foundation.interaction.PressInteraction.Press(down.position)
                
                launch {
                    interactionSource.emit(pressInteraction)
                }
                
                var holdJob: Job? = null
                
                holdJob = launch {
                    delay(initialDelayMillis)
                    while (isActive) {
                        currentOnHold()
                        delay(delayMillis)
                    }
                }
                
                val up = waitForUpOrCancellation()
                holdJob.cancel()
                
                launch {
                    if (up != null) {
                        interactionSource.emit(androidx.compose.foundation.interaction.PressInteraction.Release(pressInteraction))
                    } else {
                        interactionSource.emit(androidx.compose.foundation.interaction.PressInteraction.Cancel(pressInteraction))
                    }
                }
                
                if (up != null) {
                    up.consume()
                    val duration = up.uptimeMillis - down.uptimeMillis
                    if (duration < initialDelayMillis) {
                        currentOnClick()
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.IconButtonKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    keyBg: Color,
    tint: Color,
    onClick: () -> Unit,
    onHold: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null
) {
    val style = LocalKeyboardStyle.current
    val scale = LocalKeyboardScale.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.935f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(40, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "icon_key_press_scale"
    )

    val effectiveKeyBg = if (isPressed) keyBg.copy(alpha = 0.78f) else keyBg
    val effectiveTint = if (isPressed) tint.copy(alpha = 0.88f) else tint

    val currentShape = style.keyShape
    val currentBorder = style.keyBorder

    val hasBevel = style.isRetro && style.keyBevelColor != Color.Transparent && keyBg != Color.Transparent

    val baseModifier = modifier
        .padding(vertical = 1.dp, horizontal = 0.5.dp)
        .fillMaxHeight()
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
        .shadow(
            elevation = if (keyBg != Color.Transparent) (if (hasBevel) 1.5.dp else 0.8.dp) else 0.dp,
            shape = currentShape,
            clip = false
        )
        .clip(currentShape)
        .background(if (hasBevel) style.keyBevelColor else effectiveKeyBg)
        .run {
            if (hasBevel) {
                this.padding(bottom = if (isPressed) 0.8.dp else 2.5.dp)
                    .clip(currentShape)
                    .background(effectiveKeyBg)
            } else {
                this
            }
        }
        .run {
            val subtleBorder = currentBorder ?: if (keyBg != Color.Transparent) {
                BorderStroke(0.5.dp, if (style.isDark) Color(0x1AFFFFFF) else Color(0x14000000))
            } else null

            if (subtleBorder != null && keyBg != Color.Transparent) {
                this.border(subtleBorder, currentShape)
            } else {
                this
            }
        }

    val gestureModifier = if (onSwipeLeft != null) {
        baseModifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {},
                onHorizontalDrag = { change, dragAmount ->
                    if (dragAmount < -15f) {
                        change.consume()
                        onSwipeLeft()
                    }
                }
            )
        }
    } else {
        baseModifier
    }

    val finalModifier = if (onHold != null) {
        gestureModifier.repeatingClickable(
            interactionSource = interactionSource,
            onClick = onClick,
            onHold = onHold
        )
    } else {
        gestureModifier.clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current
        ) { onClick() }
    }

    val iconSize = (21f * scale).coerceIn(15f, 28f).dp
    Box(
        modifier = finalModifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = effectiveTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * A beautiful, highly-functional on-device Clipboard History panel styled with Material 3.
 * Supports pinning, deleting, clearing unpinned, and direct pasting.
 */
@Composable
fun ClipboardPanel(
    clipboardRepository: ClipboardRepository?,
    keyTextColor: Color,
    accentColor: Color,
    keyColor: Color,
    onPasteText: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Collect clipboard items dynamically
    val clipboardItems by if (clipboardRepository != null) {
        clipboardRepository.allItems.collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        // Clipboard Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = keyTextColor
                    )
                }
                Text(
                    text = "Clipboard History",
                    color = keyTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (clipboardItems.any { !it.isPinned }) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            clipboardRepository?.clearUnpinned()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = "Clear Temp",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // List of Clipboard Items
        if (clipboardItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = keyColor.copy(alpha = 0.6f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = BorderStroke(1.dp, keyTextColor.copy(alpha = 0.12f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                        ) {
                            Box(
                                modifier = Modifier.padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Empty Clipboard",
                                    tint = accentColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Text(
                            text = "Clipboard is empty",
                            color = keyTextColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Text and clips you copy on your device will save here automatically for fast pasting.",
                            color = keyTextColor.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = keyTextColor.copy(alpha = 0.06f),
                            border = BorderStroke(0.5.dp, keyTextColor.copy(alpha = 0.12f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pin tip",
                                    tint = accentColor,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "Tip: Pin important clips to keep them permanently",
                                    color = keyTextColor.copy(alpha = 0.8f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
            ) {
                items(clipboardItems, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                onPasteText(item.text)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = keyColor.copy(alpha = 0.95f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (item.isPinned) accentColor.copy(alpha = 0.7f) else keyTextColor.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (item.isPinned) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = accentColor.copy(alpha = 0.2f),
                                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = "Pinned",
                                                    tint = accentColor,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Text(
                                                    text = "PINNED",
                                                    color = accentColor,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "${item.text.length} chars",
                                            color = keyTextColor.copy(alpha = 0.4f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // Pin/Unpin Button
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                clipboardRepository?.togglePin(item)
                                            }
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = if (item.isPinned) "Unpin" else "Pin",
                                            tint = if (item.isPinned) accentColor else keyTextColor.copy(alpha = 0.35f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    // Copy to system clip button
                                    IconButton(
                                        onClick = {
                                            try {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("TypeRight Copy", item.text)
                                                clipboard?.setPrimaryClip(clip)
                                                android.widget.Toast.makeText(context, "Copied to system clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Log.e("TypeRight", "Copy to system clipboard failed", e)
                                            }
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy text",
                                            tint = keyTextColor.copy(alpha = 0.5f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    // Delete Button
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                clipboardRepository?.delete(item)
                                            }
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.Red.copy(alpha = 0.6f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = item.text,
                                color = keyTextColor,
                                fontSize = 13.sp,
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                lineHeight = 17.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiEngineIndicatorBadge(
    activeEngine: ActiveAiEngine,
    accentColor: Color,
    keyTextColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val isEngineOn = activeEngine == ActiveAiEngine.ONLINE || activeEngine == ActiveAiEngine.BOTH || activeEngine == ActiveAiEngine.OFFLINE || activeEngine == ActiveAiEngine.NEMOTRON
    val bgColor = if (isEngineOn) Color(0xFF3B82F6).copy(alpha = 0.16f) else keyTextColor.copy(alpha = 0.06f)
    val borderColor = if (isEngineOn) Color(0xFF3B82F6).copy(alpha = 0.50f) else keyTextColor.copy(alpha = 0.18f)
    val contentColor = if (isEngineOn) Color(0xFF3B82F6) else keyTextColor.copy(alpha = 0.55f)
    val icon = if (activeEngine == ActiveAiEngine.OFFLINE) Icons.Default.PhoneAndroid else if (isEngineOn) Icons.Default.Cloud else Icons.Default.CloudOff

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = tween(70),
        label = "ai_indicator_scale"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("ai_engine_indicator")
            .testTag("ai_engine_indicator_${activeEngine.shortLabel.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 5.dp else 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Active AI Engine: ${activeEngine.title}",
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
            if (!compact) {
                Text(
                    text = activeEngine.shortLabel,
                    color = contentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun OneHandedSideRail(
    modifier: Modifier = Modifier,
    keyTextColor: Color,
    accentColor: Color,
    keyColor: Color,
    currentMode: String,
    onSwitchSide: () -> Unit,
    onExpand: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        androidx.compose.material3.FilledTonalIconButton(
            onClick = onSwitchSide,
            modifier = Modifier.size(42.dp),
            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = keyColor,
                contentColor = keyTextColor
            )
        ) {
            Icon(
                imageVector = if (currentMode == "left") Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Switch Hand",
                modifier = Modifier.size(20.dp)
            )
        }

        androidx.compose.material3.FilledTonalIconButton(
            onClick = onExpand,
            modifier = Modifier.size(42.dp),
            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = keyColor,
                contentColor = keyTextColor
            )
        ) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Expand to Full Width",
                modifier = Modifier.size(22.dp)
            )
        }

        androidx.compose.material3.FilledTonalIconButton(
            onClick = onSettings,
            modifier = Modifier.size(42.dp),
            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = keyColor,
                contentColor = keyTextColor
            )
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

enum class GboardTool(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SMART_SELECT("Smart Select", Icons.Default.SelectAll),
    AI_POLISH("Writing tools", Icons.Default.AutoAwesome),
    OFFLINE_AI("Offline AI", Icons.Default.Memory),
    PROOFREAD("Writing tools", Icons.Default.AutoAwesome),
    RAMBLE("Voice Dictation", Icons.Default.Mic),
    CLIPBOARD("Clipboard", Icons.Default.ContentPaste),
    THEMES("Theme", Icons.Default.Palette),
    LANGUAGE("Language", Icons.Default.Language),
    TRANSLATE("Rephrase", Icons.Default.AutoFixHigh),
    TEXT_EDIT("Text Editing", Icons.Default.Keyboard),
    ONE_HANDED("One-Handed", Icons.Default.PhoneAndroid),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun GboardToolsDrawer(
    keyTextColor: Color,
    accentColor: Color,
    keyColor: Color,
    onToolClick: (GboardTool) -> Unit,
    onClose: () -> Unit
) {
    val scale = LocalKeyboardScale.current

    val tools = listOf(
        GboardTool.AI_POLISH,
        GboardTool.CLIPBOARD,
        GboardTool.THEMES,
        GboardTool.TEXT_EDIT,
        GboardTool.ONE_HANDED,
        GboardTool.SETTINGS
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Keyboard Options & Tools",
                    color = keyTextColor,
                    fontSize = (13f * scale).coerceIn(11f, 15f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = keyTextColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Spacious, compact Gboard-style tool grid filling the short keyboard height
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val chunkedTools = tools.chunked(3)
            chunkedTools.forEach { rowTools ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowTools.forEach { tool ->
                        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val toolAnimScale by animateFloatAsState(
                            targetValue = if (isPressed) 0.93f else 1.0f,
                            animationSpec = tween(70),
                            label = "tool_scale"
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = keyColor.copy(alpha = 0.85f),
                            border = BorderStroke(0.5.dp, keyTextColor.copy(alpha = 0.12f)),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    scaleX = toolAnimScale
                                    scaleY = toolAnimScale
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) { onToolClick(tool) }
                                .testTag("gboard_tool_${tool.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (tool == GboardTool.AI_POLISH) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_pen_sparkle),
                                        contentDescription = tool.title,
                                        tint = accentColor,
                                        modifier = Modifier.size((20f * scale).coerceIn(16f, 24f).dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = tool.icon,
                                        contentDescription = tool.title,
                                        tint = if (tool == GboardTool.SMART_SELECT || tool == GboardTool.PROOFREAD || tool == GboardTool.OFFLINE_AI) accentColor else keyTextColor,
                                        modifier = Modifier.size((20f * scale).coerceIn(16f, 24f).dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = tool.title,
                                    color = keyTextColor,
                                    fontSize = (10f * scale).coerceIn(9f, 12f).sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper to compute token-level diff and highlight changes like in the reference image
private fun buildHighlightedDiffText(
    original: String,
    revised: String,
    textColor: Color,
    highlightColor: Color
): AnnotatedString {
    if (original == revised || original.isBlank() || revised.isBlank()) {
        return AnnotatedString(revised)
    }

    // Split into tokens preserving all whitespace, words, and individual punctuation marks
    val tokenRegex = Regex("""\w+|[^\w\s]|\s+""")
    val origTokens = tokenRegex.findAll(original).map { it.value }.toList()
    val revTokens = tokenRegex.findAll(revised).map { it.value }.toList()

    if (origTokens.isEmpty()) return AnnotatedString(revised)

    val n = origTokens.size
    val m = revTokens.size

    // Guard against excessively large text
    if (n > 250 || m > 250) {
        return AnnotatedString(revised)
    }

    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in 0 until n) {
        for (j in 0 until m) {
            if (origTokens[i] == revTokens[j]) {
                dp[i + 1][j + 1] = dp[i][j] + 1
            } else {
                dp[i + 1][j + 1] = maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
    }

    // Backtrack to identify matched tokens in the revised text
    val matchedInRev = BooleanArray(m)
    var i = n
    var j = m
    while (i > 0 && j > 0) {
        if (origTokens[i - 1] == revTokens[j - 1]) {
            matchedInRev[j - 1] = true
            i--
            j--
        } else if (dp[i - 1][j] >= dp[i][j - 1]) {
            i--
        } else {
            j--
        }
    }

    val builder = AnnotatedString.Builder()
    for (idx in revTokens.indices) {
        val token = revTokens[idx]
        val isAddedOrChanged = !matchedInRev[idx] && token.isNotBlank()
        if (isAddedOrChanged) {
            builder.pushStyle(
                SpanStyle(
                    background = highlightColor,
                    color = textColor
                )
            )
            builder.append(token)
            builder.pop()
        } else {
            builder.append(token)
        }
    }
    return builder.toAnnotatedString()
}

@Composable
fun GboardProofreadPanel(
    keyTextColor: Color,
    accentColor: Color,
    keyColor: Color,
    onApplyText: (String) -> Unit,
    onClose: () -> Unit,
    onToggleLanguage: () -> Unit = {}
) {
    val context = LocalContext.current
    val editorService = context as? TypeRightKeyboardService
    val coordinator = remember { PolishCoordinator.getInstance(context) }
    val settings = remember { KeyboardSettings(context) }

    // Always capture the entire text for Writing Tools freshly when panel opens
    val snapshot = remember(Unit) {
        editorService?.captureFullEditorText()
    }

    val editorSnapshot = remember(snapshot) {
        if (snapshot != null) {
            EditorSnapshot(
                sessionId = snapshot.session,
                originalText = snapshot.text,
                startOffset = 0,
                endOffset = snapshot.text.length,
                cursorPosition = snapshot.text.length
            )
        } else null
    }

    var selectedTone by remember { mutableStateOf(PolishMode.PROOFREAD) }
    var hasApplied by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var currentEngine by remember { mutableStateOf(settings.activeAiEngine) }

    DisposableEffect(settings) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            currentEngine = settings.activeAiEngine
        }
        settings.sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val uiState by coordinator.uiState.collectAsState()

    val tones = listOf(
        Triple(PolishMode.PROOFREAD, "Proofread", Icons.Default.Spellcheck),
        Triple(PolishMode.AUTO_FORMAT, "Auto Format", Icons.Default.AutoFixNormal),
        Triple(PolishMode.REPHRASE, "Rephrase", Icons.AutoMirrored.Filled.Subject),
        Triple(PolishMode.PROFESSIONAL, "Professional", Icons.Default.Work),
        Triple(PolishMode.CASUAL, "Friendly", Icons.Default.ChatBubbleOutline),
        Triple(PolishMode.SHORTEN, "Concise", Icons.Default.Bolt),
        Triple(PolishMode.EXPAND, "Elaborate", Icons.Default.Notes)
    )

    fun runPolish(mode: PolishMode = selectedTone) {
        val snap = editorSnapshot ?: return
        hasApplied = false
        coordinator.triggerPolish(
            snapshot = snap,
            mode = mode,
            forceBasicOffline = false
        )
    }

    DisposableEffect(Unit) {
        val currentSess = snapshot?.session ?: System.currentTimeMillis()
        coordinator.resetForSession(currentSess)
        if (editorSnapshot != null && editorSnapshot.originalText.isNotBlank()) {
            runPolish(selectedTone)
        }
        onDispose {
            coordinator.cancelCurrent()
            editorService?.isProofreadSheetOpen?.value = false
        }
    }

    val style = LocalKeyboardStyle.current
    val panelBg = if (style.isDark) {
        if (style.theme == KeyboardSettings.THEME_NIGHT) Color(0xFF000000) else Color(0xFF1B1B1F)
    } else Color(0xFFF1F4F9)
    val cardBg = if (style.isDark) Color(0xFF2E3137) else Color(0xFFFFFFFF)
    val elementBg = if (style.isDark) Color(0xFF24262B) else Color(0xFFE2E7ED)
    val titleAndIconColor = if (style.isDark) Color(0xFFE2E2E6) else Color(0xFF191C20)
    val activePillBg = style.accentColor
    val activePillContent = style.enterKeyTextColor
    val highlightColor = if (style.isDark) Color(0xFF505A6B).copy(alpha = 0.65f) else Color(0xFFD3E3FD).copy(alpha = 0.75f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(panelBg)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // --- 1. TOP HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(elementBg)
                        .clickable {
                            coordinator.cancelCurrent()
                            editorService?.isProofreadSheetOpen?.value = false
                            onClose()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = titleAndIconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Writing tools",
                    color = titleAndIconColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiEngineIndicatorBadge(
                    activeEngine = currentEngine,
                    accentColor = activePillBg,
                    keyTextColor = titleAndIconColor,
                    compact = false,
                    onClick = {
                        val next = if (currentEngine == ActiveAiEngine.OFFLINE) ActiveAiEngine.NONE else ActiveAiEngine.OFFLINE
                        settings.setActiveAiEngine(next)
                        currentEngine = next
                        runPolish(selectedTone)
                    }
                )

                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(elementBg)
                            .clickable { showOverflowMenu = !showOverflowMenu },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = titleAndIconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("On-Device AI") },
                            leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                settings.setActiveAiEngine(ActiveAiEngine.OFFLINE)
                                currentEngine = ActiveAiEngine.OFFLINE
                                runPolish(selectedTone)
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Copy to clipboard") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                val ready = uiState as? PolishUiState.Ready
                                val streaming = uiState as? PolishUiState.Streaming
                                val textToCopy = ready?.result?.text ?: streaming?.partialText ?: editorSnapshot?.originalText.orEmpty()
                                if (textToCopy.isNotEmpty()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Writing Tools Text", textToCopy))
                                }
                            }
                        )
                        if (hasApplied) {
                            DropdownMenuItem(
                                text = { Text("Undo replacement") },
                                leadingIcon = { Icon(Icons.Default.Undo, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    if (editorService?.undoEditorReplacement(snapshot) == true) {
                                        hasApplied = false
                                    }
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Revert to original") },
                            leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                val orig = editorSnapshot?.originalText.orEmpty()
                                if (orig.isNotEmpty()) {
                                    editorService?.applyEditorReplacement(snapshot, orig, PolishMode.PROOFREAD)
                                    hasApplied = false
                                }
                            }
                        )
                    }
                }
            }
        }

        // --- 2. MAIN SPACIOUS CONTENT CARD (Tapping box automatically applies text) ---
        val original = editorSnapshot?.originalText.orEmpty()

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cardBg,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .clickable(enabled = original.isNotBlank() && ((uiState is PolishUiState.Ready && (uiState as PolishUiState.Ready).result.isChanged) || uiState is PolishUiState.Streaming)) {
                    val textToApply = when (val s = uiState) {
                        is PolishUiState.Ready -> if (s.result.text.isNotBlank() && s.result.isChanged) s.result.text else null
                        is PolishUiState.Streaming -> if (s.partialText.isNotBlank()) s.partialText else null
                        else -> null
                    }
                    if (textToApply != null && editorService?.applyEditorReplacement(snapshot, textToApply, selectedTone) == true) {
                        hasApplied = true
                        editorService?.playFeedback()
                        coordinator.cancelCurrent()
                        editorService?.isProofreadSheetOpen?.value = false
                        onApplyText(textToApply)
                    }
                }
                .testTag("proofread_text_card")
        ) {
            if (original.isBlank()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Type or select text, then open Writing tools to polish and rephrase.",
                        color = titleAndIconColor.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                when (val state = uiState) {
                    is PolishUiState.Idle -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = activePillBg, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        }
                    }
                    is PolishUiState.Generating, is PolishUiState.PreparingModel -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(color = activePillBg, modifier = Modifier.size(32.dp), strokeWidth = 3.5.dp)
                                Text(
                                    text = "Polishing text...",
                                    color = titleAndIconColor.copy(alpha = 0.8f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal
                                 )
                            }
                        }
                    }
                    is PolishUiState.TermsRequired -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = activePillBg,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Accept Gemma Terms of Use",
                                    color = titleAndIconColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Review the selected model's terms in AI polish settings before using it.",
                                    color = titleAndIconColor.copy(alpha = 0.75f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            LocalGgufModel.acceptTerms(context, true)
                                            runPolish(selectedTone)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = activePillBg),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.testTag("sheet_accept_gemma_terms_button")
                                    ) {
                                        Text("Accept Terms", color = activePillContent, fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://ai.google.dev/gemma/terms")).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        },
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text("View Terms", color = titleAndIconColor, fontSize = 12.sp)
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        val snap = editorSnapshot
                                        if (snap != null) {
                                            coordinator.triggerPolish(snap, selectedTone, forceBasicOffline = true)
                                        }
                                    }
                                ) {
                                    Text("Use Quick Offline Polish instead", color = activePillBg, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    is PolishUiState.ModelNotDownloaded -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = activePillBg,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Local Model Not Downloaded",
                                    color = titleAndIconColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "The selected model is not downloaded. Open AI polish settings to choose and download a model.",
                                    color = titleAndIconColor.copy(alpha = 0.75f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(context, MainActivity::class.java).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                                    putExtra("target_tab", 2)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = activePillBg),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text("Download in Settings", color = activePillContent, fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val snap = editorSnapshot
                                            if (snap != null) {
                                                coordinator.triggerPolish(snap, selectedTone, forceBasicOffline = true)
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text("Quick Polish", color = titleAndIconColor, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    is PolishUiState.Streaming -> {
                        val partial = state.partialText
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 22.dp, vertical = 20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = partial,
                                    color = titleAndIconColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = 26.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = activePillBg.copy(alpha = 0.15f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 2.dp,
                                                color = activePillBg
                                            )
                                            Text(
                                                text = "Streaming...",
                                                color = activePillBg,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is PolishUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { runPolish(selectedTone) },
                                        colors = ButtonDefaults.buttonColors(containerColor = activePillBg),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text("Retry", color = activePillContent, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(context, MainActivity::class.java).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                                    putExtra("target_tab", 2)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = elementBg),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text("Open Settings", color = titleAndIconColor, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    is PolishUiState.Ready -> {
                        val result = state.result
                        val displayText = if (result.text.isNotBlank()) result.text else original
                        val annotatedText = remember(original, displayText) {
                            buildHighlightedDiffText(
                                original = original,
                                revised = displayText,
                                textColor = titleAndIconColor,
                                highlightColor = highlightColor
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 22.dp, vertical = 20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = annotatedText,
                                    color = titleAndIconColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = 26.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!result.isChanged) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = if (selectedTone == PolishMode.PROOFREAD) "No errors found — already clean!" else "Text already fits style",
                                                    color = Color(0xFF10B981),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = activePillBg.copy(alpha = 0.15f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = activePillBg,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = "Tap card to apply",
                                                    color = activePillBg,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is PolishUiState.Idle -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Ready to polish text", color = titleAndIconColor.copy(alpha = 0.5f), fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // --- 3. MODE PILLS ROW (Below Card) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tones.forEach { (toneMode, toneLabel, toneIcon) ->
                val isSelected = selectedTone == toneMode
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) activePillBg else elementBg,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            selectedTone = toneMode
                            runPolish(toneMode)
                        }
                        .testTag("proofread_tone_${toneMode.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = toneIcon,
                            contentDescription = null,
                            tint = if (isSelected) activePillContent else titleAndIconColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = toneLabel,
                            color = if (isSelected) activePillContent else titleAndIconColor,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TextEditingPanel(
    keyTextColor: Color,
    accentColor: Color,
    keyColor: Color,
    specialKeyBg: Color,
    onNavigate: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Text Editing",
                    color = keyTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = keyTextColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Action buttons on the left
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = specialKeyBg,
                        border = BorderStroke(0.5.dp, keyTextColor.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { onSelectAll() }
                            .testTag("text_edit_select_all")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Select All", color = keyTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = specialKeyBg,
                        border = BorderStroke(0.5.dp, keyTextColor.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { onCut() }
                            .testTag("text_edit_cut")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Cut", color = keyTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = specialKeyBg,
                        border = BorderStroke(0.5.dp, keyTextColor.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { onCopy() }
                            .testTag("text_edit_copy")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Copy", color = keyTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = specialKeyBg,
                        border = BorderStroke(0.5.dp, keyTextColor.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { onPaste() }
                            .testTag("text_edit_paste")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Paste", color = keyTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // D-Pad on the right
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // UP
                IconButton(
                    onClick = { onNavigate(KeyEvent.KEYCODE_DPAD_UP) },
                    modifier = Modifier.size(36.dp).background(keyColor, CircleShape)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = keyTextColor)
                }

                // LEFT, CENTER, RIGHT
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onNavigate(KeyEvent.KEYCODE_DPAD_LEFT) },
                        modifier = Modifier.size(36.dp).background(keyColor, CircleShape)
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Left", tint = keyTextColor)
                    }

                    IconButton(
                        onClick = { onNavigate(KeyEvent.KEYCODE_DPAD_RIGHT) },
                        modifier = Modifier.size(36.dp).background(keyColor, CircleShape)
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Right", tint = keyTextColor)
                    }
                }

                // DOWN
                IconButton(
                    onClick = { onNavigate(KeyEvent.KEYCODE_DPAD_DOWN) },
                    modifier = Modifier.size(36.dp).background(keyColor, CircleShape)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = keyTextColor)
                }
            }
        }
    }
}

