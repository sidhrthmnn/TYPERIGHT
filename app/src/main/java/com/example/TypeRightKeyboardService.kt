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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import android.util.Log
import kotlin.random.Random
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.RevampedEmojiLayout
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

    private enum class FeedbackType {
        Standard, Space, Delete, Enter
    }

    private var composeSetup: ComposeSetup? = null

    // Asynchronous text input buffer and debouncing states
    private val textBufferFlow = MutableStateFlow(TextInputBufferState())
    val asyncPredictionsState = mutableStateOf(AsyncKeyboardPredictions())

    // Keyboard state
    private val isShiftActive = mutableStateOf(false)
    private val isCapsLockActive = mutableStateOf(false)
    private var lastShiftClickTime: Long = 0L
    private val isSymbolLayerActive = mutableStateOf(false)
    private val isEmojiLayerActive = mutableStateOf(false)
    private val isClipboardLayerActive = mutableStateOf(false)
    private val isAssistantLayerActive = mutableStateOf(false)
    private val currentAiMode = mutableStateOf("formalize")
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
    
    // Voice Typing and AI Polish states
    private val isVoiceTypingActive = mutableStateOf(false)
    private val voiceTranscript = mutableStateOf("")
    private val voiceAudioLevel = mutableStateOf(0f)
    private val isAiPolishing = mutableStateOf(false)
    private var currentAiRequestId: Long = 0L
    private var currentAiJob: kotlinx.coroutines.Job? = null
    private var rephraseSnapshot: EditorTextSnapshot? = null
    private val isAiRephrasing = mutableStateOf(false)
    private val aiRephraseSuggestions = androidx.compose.runtime.mutableStateListOf<String>()
    private val isMicPermissionGranted = mutableStateOf(false)
    private val showVoicePolishPrompt = mutableStateOf(false)
    private val pendingVoiceTranscript = mutableStateOf("")
    private var lastCommittedVoiceLength = 0

    // Ramble Mode states (Intent-based AI dictation)
    private val isRambleRecording = mutableStateOf(false)
    private val isRambleProcessing = mutableStateOf(false)
    private val rambleTranscript = mutableStateOf("")
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
        settings = KeyboardSettings(this)
        dictionaryManager = DictionaryManager(this)
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

        // New input cancels the previous delay and computation. Results belong to one snapshot.
        serviceScope.launch {
            textBufferFlow.collectLatest { buffer ->
                try {
                    kotlinx.coroutines.delay(20L)
                    val result = withContext(Dispatchers.Default) {
                        if (buffer.isSensitive || buffer.isUrl || buffer.isEmail) {
                            AsyncKeyboardPredictions(source = buffer)
                        } else {
                            val gboard = dictionaryManager.getGboardPredictions(
                                buffer.activePrefix, buffer.previousWords, buffer.tapCoords.ifEmpty { null }, false)
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
                            AsyncKeyboardPredictions(finalResult, suggestions, emptyList(), buffer)
                        }
                    }
                    if (textBufferFlow.value == buffer) asyncPredictionsState.value = result
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w("TypeRight", "Prediction failed: ${failure.javaClass.simpleName}")
                    if (textBufferFlow.value == buffer) asyncPredictionsState.value = AsyncKeyboardPredictions(source = buffer)
                }
            }
        }
    }

    private fun cancelPendingPolish() {
        currentAiRequestId++
        currentAiJob?.cancel()
        currentAiJob = null
        rephraseSnapshot = null
        isAiPolishing.value = false
        isAiRephrasing.value = false
        aiRephraseSuggestions.clear()
    }

    private fun resetEditorState() {
        editorSession++
        if (::voiceRecordingService.isInitialized) voiceRecordingService.cancelRecording()
        isVoiceTypingActive.value = false
        isRambleRecording.value = false
        isRambleProcessing.value = false
        pendingVoiceTranscript.value = ""
        showVoicePolishPrompt.value = false
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
        lastSpaceTime = 0L
        suggestionSpacePending = false
        lastComposedStart = -1
        lastComposedEnd = -1
        asyncPredictionsState.value = AsyncKeyboardPredictions()
        textBufferFlow.value = TextInputBufferState(isSensitive = true, timestamp = ++bufferGeneration)
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
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
        if (!atComposingEnd) updatePreviousWord()
    }

    private fun isWordChar(c: Char): Boolean {
        return c.isLetterOrDigit() || c == '\'' || c == '-' || c == '_'
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
        if (!allowsTextAssistance()) {
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
        val after = ic.getTextAfterCursor(50, 0)?.toString() ?: ""

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
        previousWords.value = if (contextTokens.size > 3) contextTokens.takeLast(3) else contextTokens

        // 2. Calculate wordUnderCursor
        fun isCursorWordChar(c: Char): Boolean = c.isLetterOrDigit() || c in "._-/:@#$!*?"
        var wordStartIdx = before.length
        while (wordStartIdx > 0 && isCursorWordChar(before[wordStartIdx - 1])) {
            wordStartIdx--
        }
        val partBefore = before.substring(wordStartIdx)

        var wordEndIdx = 0
        while (wordEndIdx < after.length && isCursorWordChar(after[wordEndIdx])) {
            wordEndIdx++
        }
        val partAfter = after.substring(0, wordEndIdx)
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
        val active = if (currentTypedWord.value.isNotEmpty()) currentTypedWord.value else wordUnderCursor.value
        val buffer = if (!allowsTextAssistance()) TextInputBufferState(isSensitive = true, timestamp = ++bufferGeneration)
        else TextInputBufferState(
            typedWord = currentTypedWord.value, activePrefix = active,
            previousWord = previousWord.value, previousWords = previousWords.value.toList(),
            tapCoords = currentWordTapCoords.map { PointF(it.x, it.y) }, timestamp = ++bufferGeneration)
        asyncPredictionsState.value = AsyncKeyboardPredictions()
        textBufferFlow.value = buffer
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
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
        super.onDestroy()
        if (isVoiceTypingActive.value) {
            try {
                voiceRecordingService.stopRecording(serviceScope, shouldPolish = false) {}
            } catch (_: Exception) {}
        }
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
                    isAssistant = isAssistantLayerActive.value,
                    isVoiceTyping = isVoiceTypingActive.value,
                    voiceText = voiceTranscript.value,
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
                    onAssistantToggle = { toggleAssistant() },
                    currentAiMode = currentAiMode.value,
                    onTriggerAiAction = { mode -> triggerAiAction(mode) },
                    onVoiceTypingToggle = { toggleVoiceTyping() },
                    onAiPolishClick = { toggleAssistant() },
                    onProofreadClick = { performDirectLocalProofread() },
                    onSuggestionClick = { commitSuggestion(it) },
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
                    onSpaceSwipeLeft = { moveCursorLeft() },
                    onSpaceSwipeRight = { moveCursorRight() },
                    onUndo = { handleUndo() },
                    onRedo = { handleRedo() },
                    showVoicePolishPrompt = showVoicePolishPrompt.value,
                    onAcceptVoicePolish = {
                        showVoicePolishPrompt.value = false
                        val textToPolish = pendingVoiceTranscript.value
                        if (textToPolish.isNotBlank()) {
                            polishAndPresentVoiceResult(textToPolish, TranscriptionFormatStyle.SMART_CLEAN)
                        }
                    },
                    onFormatVoice = { style ->
                        showVoicePolishPrompt.value = false
                        val textToPolish = pendingVoiceTranscript.value
                        if (textToPolish.isNotBlank()) {
                            polishAndPresentVoiceResult(textToPolish, style)
                        }
                    },
                    onRejectVoicePolish = {
                        showVoicePolishPrompt.value = false
                        pendingVoiceTranscript.value = ""
                        lastCommittedVoiceLength = 0
                    }
                )
            }
        }
        return composeView
    }

    fun isUrlField(): Boolean {
        val info = currentInputEditorInfo ?: return false
        val inputType = info.inputType
        val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
        val isUriVariation = (inputType and EditorInfo.TYPE_CLASS_TEXT) != 0 && (
            variation == EditorInfo.TYPE_TEXT_VARIATION_URI
        )
        val packageName = (info.packageName ?: "").lowercase()
        val fieldName = (info.fieldName ?: "").lowercase()
        val isBrowserPackage = packageName.contains("chrome") || packageName.contains("browser") ||
                              packageName.contains("firefox") || packageName.contains("opera") ||
                              packageName.contains("edge") || packageName.contains("duckduckgo") ||
                              packageName.contains("samsung") || packageName.contains("brave") ||
                              packageName.contains("kiwi")
        val isUrlFieldHint = fieldName.contains("url") || fieldName.contains("address") ||
                             fieldName.contains("location") || fieldName.contains("omnibox") ||
                             fieldName.contains("url_bar") || fieldName.contains("address_bar")
        return isUriVariation || (isBrowserPackage && isUrlFieldHint)
    }

    fun isEmailField(): Boolean {
        val info = currentInputEditorInfo ?: return false
        val inputType = info.inputType
        val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
        return (inputType and EditorInfo.TYPE_CLASS_TEXT) != 0 && (
            variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )
    }

    fun isSensitiveField(): Boolean {
        val info = currentInputEditorInfo ?: return false
        val inputType = info.inputType
        val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
        val isPassword = (inputType and EditorInfo.TYPE_CLASS_TEXT) != 0 && (
            variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        val isNumberPassword = (inputType and EditorInfo.TYPE_CLASS_NUMBER) != 0 && (
            variation == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
        )
        return isPassword || isNumberPassword
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

    fun applyEditorReplacement(snapshot: EditorTextSnapshot?, replacement: String, mode: PolishMode): Boolean {
        if (snapshot == null || snapshot.session != editorSession || !allowsTextAssistance()) return false
        val ic = currentInputConnection ?: return false
        if (ic.getTextBeforeCursor(2000, 0)?.toString().orEmpty() != snapshot.before ||
            ic.getTextAfterCursor(2000, 0)?.toString().orEmpty() != snapshot.after ||
            ic.getSelectedText(0)?.toString() != snapshot.selected ||
            !AiOutputValidator.isValid(snapshot.text, replacement, mode)) return false
        ic.beginBatchEdit()
        try {
            ic.finishComposingText()
            if (snapshot.selected.isNullOrEmpty()) ic.deleteSurroundingText(snapshot.before.length, snapshot.after.length)
            ic.commitText(replacement, 1)
        } finally { ic.endBatchEdit() }
        justAutocorrected = false
        updatePreviousWord()
        return true
    }

    private fun formatGrammarCheckedText(word: String): CharSequence {
        return word
    }

    fun allowsTextAssistance(): Boolean {
        val info = currentInputEditorInfo ?: return false
        return !isSensitiveField() && !isUrlField() && !isEmailField() &&
            (info.inputType and android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_TEXT &&
            (info.inputType and android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0
    }

    private fun mayLearn(): Boolean = allowsTextAssistance() &&
        ((currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0

    private fun getAutoCorrectedWord(prefix: String): String? {
        if (!settings.autocorrectEnabled || prefix.isEmpty() || !allowsTextAssistance()) return null
        dictionaryManager.gboardEngine.immediateCorrection(prefix, dictionaryManager)?.let { return it }
        val prediction = asyncPredictionsState.value
        val source = prediction.source ?: return null
        if (source != textBufferFlow.value || source.activePrefix != prefix) return null
        return prediction.gboardResult.takeIf { it.isCenterAutocorrecting }?.centerCandidate
            ?.takeUnless { dictionaryManager.isCorrectionSuppressed(prefix, it) }
    }

    private fun commitWordWithSmartCorrection(ic: InputConnection, prefix: String, trailingText: String = "") {
        val corrected = getAutoCorrectedWord(prefix) ?: prefix
        ic.commitText(corrected + trailingText, 1)
        lastOriginalWord = prefix
        lastCorrectedWord = corrected
        justAutocorrected = corrected != prefix
        lastCorrectedWasSpace = trailingText == " "
        learnWordAndContext(corrected)
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
        cancelPendingPolish()
        lastSpaceTime = 0L
        showVoicePolishPrompt.value = false
        playFeedback()
        if (isVoiceTypingActive.value) {
            stopVoiceTyping(shouldPolish = true)
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
                    commitWordWithSmartCorrection(ic, prefix, "")
                    currentTypedWord.value = ""
                } else {
                    // Check if there is a trailing space before cursor
                    val before = ic.getTextBeforeCursor(1, 0) ?: ""
                    if (suggestionSpacePending && before == " ") {
                        ic.deleteSurroundingText(1, 0)
                    }
                }
                // Do not insert spaces inside URLs, decimals, or ellipses.
                ic.commitText(char.toString(), 1)
                suggestionSpacePending = false
                justAutocorrected = false
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
                ic.finishComposingText()
                currentWordTapCoords.clear()
            }

            currentTypedWord.value += letter
            val centroid = dictionaryManager.gboardEngine.spatialModel.getKeyCentroid(char)
            val tapX = if (lastTapX != 0.5f) lastTapX else (centroid?.x ?: 0.5f)
            val tapY = if (lastTapY != 0.5f) lastTapY else (centroid?.y ?: 0.5f)
            currentWordTapCoords.add(PointF(tapX, tapY))
            
            ic.setComposingText(currentTypedWord.value, 1)
            wordUnderCursor.value = currentTypedWord.value

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
        cancelPendingPolish()
        lastSpaceTime = 0L
        suggestionSpacePending = false
        showVoicePolishPrompt.value = false
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
                    ic.deleteSurroundingText(suffix.length, 0)
                    currentTypedWord.value = lastOriginalWord
                    currentWordTapCoords.clear()
                    ic.setComposingText(lastOriginalWord, 1)
                    dictionaryManager.suppressCorrection(lastOriginalWord, lastCorrectedWord)
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
        cancelPendingPolish()
        playFeedback(FeedbackType.Space)
        if (isVoiceTypingActive.value) stopVoiceTyping(shouldPolish = false)
        val ic = currentInputConnection ?: return
        val now = android.os.SystemClock.uptimeMillis()
        val prefix = currentTypedWord.value
        ic.beginBatchEdit()
        try {
            if (prefix.isNotEmpty()) {
                commitWordWithSmartCorrection(ic, prefix, " ")
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
        playFeedback(FeedbackType.Enter)
        if (isVoiceTypingActive.value) {
            stopVoiceTyping(shouldPolish = true)
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
        isAssistantLayerActive.value = false
    }

    private fun toggleEmojis() {
        playFeedback()
        isEmojiLayerActive.value = !isEmojiLayerActive.value
        isSymbolLayerActive.value = false
        isClipboardLayerActive.value = false
        isAssistantLayerActive.value = false
    }

    private fun toggleClipboard() {
        playFeedback()
        isClipboardLayerActive.value = !isClipboardLayerActive.value
        isEmojiLayerActive.value = false
        isSymbolLayerActive.value = false
        isAssistantLayerActive.value = false
    }

    private fun toggleAssistant() {
        playFeedback()
        isAssistantLayerActive.value = !isAssistantLayerActive.value
        isClipboardLayerActive.value = false
        isEmojiLayerActive.value = false
        isSymbolLayerActive.value = false
    }

    fun triggerAiAction(mode: String) {
        playFeedback()
        currentAiMode.value = mode
        isAssistantLayerActive.value = true
        isClipboardLayerActive.value = false
        isEmojiLayerActive.value = false
        isSymbolLayerActive.value = false
    }

    private fun commitSuggestion(word: String) {
        cancelPendingPolish()
        playFeedback()
        val ic = currentInputConnection ?: return
        
        val rawTyped = currentTypedWord.value
        val isComposing = rawTyped.isNotEmpty() || (lastComposedStart != -1 && lastComposedEnd != -1)
        val isExplicitRawAccept = rawTyped.isNotEmpty() && word.lowercase() == rawTyped.lowercase()
        
        // If user selected their exact typed word (e.g. Left Slot), suppress auto-correction & save to local memory
        if (isExplicitRawAccept) {
            val autoCorrect = getAutoCorrectedWord(rawTyped)
            if (autoCorrect != null) {
                dictionaryManager.suppressCorrection(rawTyped, autoCorrect)
            }
        }

        if (isComposing) {
            val after = ic.getTextAfterCursor(50, 0) ?: ""
            var wordEndIdx = 0
            while (wordEndIdx < after.length && (after[wordEndIdx].isLetterOrDigit() || after[wordEndIdx] == '\'')) {
                wordEndIdx++
            }
            if (wordEndIdx > 0) {
                ic.deleteSurroundingText(0, wordEndIdx)
            }
            ic.commitText("$word ", 1)
            lastComposedStart = -1
            lastComposedEnd = -1
        } else {
            val before = ic.getTextBeforeCursor(50, 0) ?: ""
            val after = ic.getTextAfterCursor(50, 0) ?: ""
            
            var wordStartIdx = before.length
            while (wordStartIdx > 0 && before[wordStartIdx - 1].isLetterOrDigit()) {
                wordStartIdx--
            }
            val partBeforeLength = before.length - wordStartIdx
            
            var wordEndIdx = 0
            while (wordEndIdx < after.length && after[wordEndIdx].isLetterOrDigit()) {
                wordEndIdx++
            }
            val partAfterLength = wordEndIdx
            
            if (partBeforeLength > 0) {
                ic.deleteSurroundingText(partBeforeLength, partAfterLength)
            }
            ic.commitText("$word ", 1)
        }
        
        suggestionSpacePending = true
        learnWordAndContext(word, explicit = isExplicitRawAccept)

        justAutocorrected = false
        currentTypedWord.value = ""
        currentWordTapCoords.clear()
        updatePreviousWord()
    }

    private fun learnWordAndContext(word: String, explicit: Boolean = false) {
        if (!mayLearn() || word.isEmpty() || word.any { !TypingPolicy.isWordCharacter(it) }) return
        // Only explicitly accepted words bypass the repeated-use learning threshold.
        if (explicit) dictionaryManager.recordAcceptedWord(word)
        dictionaryManager.learnWord(word, explicit = explicit)
        val context = previousWords.value.takeLast(3)
        context.lastOrNull()?.let { dictionaryManager.learnBigram(it, word) }
        if (context.size >= 2) dictionaryManager.learnTrigram(context[context.size - 2], context.last(), word)
        if (context.size == 3) dictionaryManager.learnQuadgram(context[0], context[1], context[2], word)
    }

    private fun launchSettingsActivity() {
        playFeedback()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
        if (!allowsTextAssistance()) return
        val session = editorSession
        if (!isMicPermissionGranted.value) {
            launchSettingsActivity()
            return
        }

        showVoicePolishPrompt.value = false
        pendingVoiceTranscript.value = ""
        isVoiceTypingActive.value = true
        voiceTranscript.value = ""
        voiceAudioLevel.value = 0.0f

        voiceRecordingService.startRecording(
            scope = serviceScope,
            onPartialText = { partial ->
                if (session != editorSession || !isVoiceTypingActive.value) return@startRecording
                voiceTranscript.value = partial
                currentInputConnection?.setComposingText(partial, 1)
            },
            onLevelChange = { level ->
                voiceAudioLevel.value = level
            }
        )
    }

    private fun stopVoiceTyping(shouldPolish: Boolean = false) {
        if (!isVoiceTypingActive.value) return
        isVoiceTypingActive.value = false
        val session = editorSession

        voiceRecordingService.stopRecording(
            scope = serviceScope,
            shouldPolish = false,
            onFinalTranscript = { rawText ->
                if (session != editorSession || !allowsTextAssistance()) return@stopRecording
                val cleanRaw = rawText.trim()
                if (cleanRaw.isNotEmpty()) {
                    currentInputConnection?.commitText(cleanRaw, 1)
                    pendingVoiceTranscript.value = cleanRaw
                    lastCommittedVoiceLength = cleanRaw.length
                    showVoicePolishPrompt.value = true
                } else {
                    currentInputConnection?.finishComposingText()
                    voiceTranscript.value = ""
                    lastCommittedVoiceLength = 0
                }
            }
        )
    }

    /**
     * Formats collected raw voice typing text into clean, structured output
     * according to the user's selected style (Smart Clean, Bullets, Numbered, Email, etc.).
     */
    private fun polishAndPresentVoiceResult(
        rawText: String,
        formatStyle: TranscriptionFormatStyle = TranscriptionFormatStyle.SMART_CLEAN
    ) {
        val ic = currentInputConnection ?: return
        val requestId = ++currentAiRequestId
        currentAiJob?.cancel()
        currentAiJob = serviceScope.launch {
            isAiPolishing.value = true
            voiceTranscript.value = "Formatting..."

            val formattedText = try {
                VoiceTranscriptionFormatter.formatTranscription(rawText, formatStyle)
            } catch (e: Exception) {
                Log.e("TypeRight", "Voice formatting error: ${e.message}")
                WhisperCppBrain.whisperCleanAndPolish(rawText)
            }

            if (requestId != currentAiRequestId) return@launch

            val finalOutput = if (formattedText.isNotBlank()) formattedText.trim() else rawText.trim()

            if (!allowsTextAssistance() || ic.getTextBeforeCursor(rawText.trim().length, 0)?.toString() != rawText.trim()) {
                isAiPolishing.value = false
                return@launch
            }
            if (lastCommittedVoiceLength > 0) {
                ic.deleteSurroundingText(lastCommittedVoiceLength, 0)
            } else if (rawText.isNotEmpty()) {
                ic.deleteSurroundingText(rawText.length, 0)
            }

            // Commit final formatted text with newlines and structural spacing preserved
            ic.commitText(finalOutput, 1)

            isAiPolishing.value = false
            voiceTranscript.value = ""
            pendingVoiceTranscript.value = ""
            lastCommittedVoiceLength = 0
            showVoicePolishPrompt.value = false
        }
    }

    /**
     * Starts Intent-based "Ramble Mode" Voice Input.
     * Buffers continuous speech without committing partial text to InputConnection.
     */
    private fun startRambleMode() {
        if (!allowsTextAssistance()) return
        if (!isMicPermissionGranted.value) {
            launchSettingsActivity()
            return
        }

        if (isVoiceTypingActive.value) {
            stopVoiceTyping(shouldPolish = false)
        }

        isRambleRecording.value = true
        isRambleProcessing.value = false
        rambleTranscript.value = ""
        rambleAudioLevel.value = 0f

        voiceRecordingService.startRecording(
            scope = serviceScope,
            onPartialText = { partial ->
                // Live preview in UI only - never commit directly to InputConnection
                rambleTranscript.value = partial
            },
            onLevelChange = { level ->
                rambleAudioLevel.value = level
            }
        )
    }

    /**
     * Confirms and finishes Ramble Mode dictation.
     * Triggers Stage 2 AI Intent Polishing (Gemini / Offline SLM) to strip disfluencies,
     * resolve self-corrections, process trailing directives, and atomically commits the finalized text.
     */
    private fun confirmRambleMode() {
        if (!isRambleRecording.value) return
        isRambleRecording.value = false
        isRambleProcessing.value = true
        val session = editorSession

        voiceRecordingService.stopRecording(
            scope = serviceScope,
            shouldPolish = false,
            onFinalTranscript = { rawSpeech ->
                if (session != editorSession || !allowsTextAssistance()) return@stopRecording
                val rawTrim = rawSpeech.trim()
                if (rawTrim.isBlank()) {
                    isRambleProcessing.value = false
                    rambleTranscript.value = ""
                    return@stopRecording
                }

                serviceScope.launch {
                    try {
                        val finalizedText = aiPolishManager.processRambleDictation(rawTrim)
                        val textToCommit = if (finalizedText.isNotBlank()) finalizedText.trim() else rawTrim
                        
                        // Atomically commit finalized text using InputConnection.commitText
                        if (session == editorSession && allowsTextAssistance()) currentInputConnection?.commitText(textToCommit, 1)
                    } catch (e: Exception) {
                        Log.e("TypeRight", "Ramble Mode AI processing error: ${e.message}")
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        if (session == editorSession && allowsTextAssistance()) currentInputConnection?.commitText(rawTrim, 1)
                    } finally {
                        isRambleProcessing.value = false
                        rambleTranscript.value = ""
                        rambleAudioLevel.value = 0f
                    }
                }
            }
        )
    }

    /**
     * Cancels Ramble Mode dictation immediately and discards buffered speech.
     */
    private fun cancelRambleMode() {
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
        toggleAssistant()
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

    private fun playFeedback(type: FeedbackType = FeedbackType.Standard) {
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

private data class WaveConfig(
    val amplitudeMult: Float,
    val frequencyMult: Float,
    val phaseOffset: Float,
    val alpha: Float
)

@Composable
fun VoiceWaveformVisualizer(
    audioLevel: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        
        val waves = listOf(
            WaveConfig(amplitudeMult = 1.0f, frequencyMult = 1.0f, phaseOffset = 0f, alpha = 0.8f),
            WaveConfig(amplitudeMult = 0.6f, frequencyMult = 1.5f, phaseOffset = (Math.PI * 0.5).toFloat(), alpha = 0.5f),
            WaveConfig(amplitudeMult = 0.3f, frequencyMult = 2.0f, phaseOffset = Math.PI.toFloat(), alpha = 0.3f)
        )

        waves.forEach { wave ->
            val path = Path()
            path.moveTo(0f, centerY)
            
            val baseAmplitude = (centerY * 0.8f) * (audioLevel + 0.05f).coerceAtMost(1f)
            
            for (x in 0..width.toInt() step 4) {
                val t = x.toFloat() / width
                val envelope = Math.sin(t.toDouble() * Math.PI).toFloat()
                
                val angle = (t * 2f * Math.PI.toFloat() * 2f * wave.frequencyMult) + phase + wave.phaseOffset
                val y = centerY + (baseAmplitude * wave.amplitudeMult * envelope * Math.sin(angle.toDouble()).toFloat())
                path.lineTo(x.toFloat(), y)
            }
            
            drawPath(
                path = path,
                color = accentColor.copy(alpha = wave.alpha),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
    }
}

private enum class KeyboardLayer {
    Qwerty, Symbols, Emojis, Clipboard, Assistant, ToolsDrawer, Proofread, TextEditing, WisprVoice
}

/**
 * Standard Jetpack Compose Keyboard Layout containing toolbar, suggestions, keys, and swipe trails.
 */
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
    isAssistant: Boolean = false,
    isVoiceTyping: Boolean,
    voiceText: String,
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
    onAssistantToggle: () -> Unit = {},
    currentAiMode: String = "formalize",
    onTriggerAiAction: (String) -> Unit = {},
    onVoiceTypingToggle: () -> Unit,
    onAiPolishClick: () -> Unit,
    onProofreadClick: () -> Unit = {},
    onSuggestionClick: (String) -> Unit,
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
    onRedo: () -> Unit = {},
    showVoicePolishPrompt: Boolean = false,
    onAcceptVoicePolish: () -> Unit = {},
    onFormatVoice: (TranscriptionFormatStyle) -> Unit = {},
    onRejectVoicePolish: () -> Unit = {}
) {
    val vibrator = remember(context) { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    val sharedPrefs = remember { context.getSharedPreferences("typeright_prefs", Context.MODE_PRIVATE) }
    
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

    val style = remember(themeState, isDarkState, dynamicThemeState, parsedAccentColor, keyBordersState, popupKeypressState) {
        val isDark = isDarkState
        val isDynamic = dynamicThemeState && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        val dynamicScheme = if (isDynamic) {
            if (isDark) androidx.compose.material3.dynamicDarkColorScheme(context) else androidx.compose.material3.dynamicLightColorScheme(context)
        } else null

        val currentKeyBorder = if (keyBordersState) {
            BorderStroke(1.dp, if (isDark) Color(0x35FFFFFF) else Color(0x22000000))
        } else null

        when {
            // 1. Dynamic Material You
            dynamicScheme != null -> {
                val bg = dynamicScheme.surface
                val normalBg = dynamicScheme.surfaceVariant
                val specialBg = dynamicScheme.secondaryContainer
                val textColor = dynamicScheme.onSurface
                val enterBg = dynamicScheme.primary
                val enterTextColor = dynamicScheme.onPrimary
                val accent = dynamicScheme.primary
                val shape = RoundedCornerShape(8.dp)

                KeyboardStyle(
                    theme = if (isDark) "Material You Dark" else "Material You Light",
                    isDark = isDark,
                    isRetro = false,
                    backgroundColor = bg,
                    normalKeyBg = normalBg,
                    specialKeyBg = specialBg,
                    keyTextColor = textColor,
                    accentColor = accent,
                    enterKeyBg = enterBg,
                    enterKeyTextColor = enterTextColor,
                    keyShape = shape,
                    keyBorder = currentKeyBorder,
                    showPressPopup = popupKeypressState,
                    scaleOnPress = true,
                    pressAnimationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                    keyBevelColor = Color.Transparent,
                    isMonospace = false
                )
            }

            // 2. Retro Beige (IBM Model M / Classic 1984) - DEFAULT RETRO THEME
            themeState == KeyboardSettings.THEME_RETRO_BEIGE -> {
                val bg = Color(0xFFDDD4C4) // Classic 1980s computer chassis putty/beige
                val normalBg = Color(0xFFF7F2EC) // Cream/eggshell alpha keys
                val specialBg = Color(0xFFC7BDAC) // Warm battleship gray modifier keys
                val textColor = Color(0xFF2B251D) // Deep vintage charcoal ink
                val enterBg = Color(0xFFD9532F) // Burnt orange terminal enter key
                val enterTextColor = Color(0xFFFFFFFF)
                val accent = Color(0xFFD9532F)
                val bevel = Color(0xFFA59A88) // Mechanical keycap bottom drop shadow
                val shape = RoundedCornerShape(6.dp)

                KeyboardStyle(
                    theme = KeyboardSettings.THEME_RETRO_BEIGE,
                    isDark = false,
                    isRetro = true,
                    backgroundColor = bg,
                    normalKeyBg = normalBg,
                    specialKeyBg = specialBg,
                    keyTextColor = textColor,
                    accentColor = accent,
                    enterKeyBg = enterBg,
                    enterKeyTextColor = enterTextColor,
                    keyShape = shape,
                    keyBorder = currentKeyBorder ?: BorderStroke(0.7.dp, Color(0x28000000)),
                    showPressPopup = popupKeypressState,
                    scaleOnPress = true,
                    pressAnimationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                    keyBevelColor = bevel,
                    isMonospace = true,
                    spacebarLineColor = Color(0x25000000),
                    toolbarBgColor = Color(0xFFD3C9B8),
                    chassisBorderColor = Color(0xFFB5A996)
                )
            }

            // 3. Retro CRT Terminal (Phosphor Green)
            themeState == KeyboardSettings.THEME_RETRO_CRT_GREEN || (isDark && themeState == KeyboardSettings.THEME_RETRO_BEIGE) -> {
                val bg = Color(0xFF0F1411) // Deep mainframe chassis
                val normalBg = Color(0xFF19241C) // Deep dark phosphor alpha keycap
                val specialBg = Color(0xFF121A14) // Darker modifier keycap
                val textColor = Color(0xFF39FF14) // Phosphor green
                val enterBg = Color(0xFF00E676)
                val enterTextColor = Color(0xFF002910)
                val accent = Color(0xFF39FF14)
                val bevel = Color(0xFF080D09)
                val shape = RoundedCornerShape(5.dp)

                KeyboardStyle(
                    theme = KeyboardSettings.THEME_RETRO_CRT_GREEN,
                    isDark = true,
                    isRetro = true,
                    backgroundColor = bg,
                    normalKeyBg = normalBg,
                    specialKeyBg = specialBg,
                    keyTextColor = textColor,
                    accentColor = accent,
                    enterKeyBg = enterBg,
                    enterKeyTextColor = enterTextColor,
                    keyShape = shape,
                    keyBorder = currentKeyBorder ?: BorderStroke(0.8.dp, Color(0x3539FF14)),
                    showPressPopup = popupKeypressState,
                    scaleOnPress = true,
                    pressAnimationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                    keyBevelColor = bevel,
                    isMonospace = true,
                    spacebarLineColor = Color(0x3039FF14),
                    toolbarBgColor = Color(0xFF0C100E),
                    chassisBorderColor = Color(0x4039FF14)
                )
            }

            // 4. Retro Amber Terminal
            themeState == KeyboardSettings.THEME_RETRO_AMBER -> {
                val bg = Color(0xFF16120C)
                val normalBg = Color(0xFF241D14)
                val specialBg = Color(0xFF1B150E)
                val textColor = Color(0xFFFFB000) // CRT Amber
                val enterBg = Color(0xFFFF9100)
                val enterTextColor = Color(0xFF261300)
                val accent = Color(0xFFFFB000)
                val bevel = Color(0xFF0D0A06)
                val shape = RoundedCornerShape(5.dp)

                KeyboardStyle(
                    theme = KeyboardSettings.THEME_RETRO_AMBER,
                    isDark = true,
                    isRetro = true,
                    backgroundColor = bg,
                    normalKeyBg = normalBg,
                    specialKeyBg = specialBg,
                    keyTextColor = textColor,
                    accentColor = accent,
                    enterKeyBg = enterBg,
                    enterKeyTextColor = enterTextColor,
                    keyShape = shape,
                    keyBorder = currentKeyBorder ?: BorderStroke(0.8.dp, Color(0x35FFB000)),
                    showPressPopup = popupKeypressState,
                    scaleOnPress = true,
                    pressAnimationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                    keyBevelColor = bevel,
                    isMonospace = true,
                    spacebarLineColor = Color(0x30FFB000),
                    toolbarBgColor = Color(0xFF120E09),
                    chassisBorderColor = Color(0x40FFB000)
                )
            }

            // 5. Retro 1984 Macintosh
            themeState == KeyboardSettings.THEME_RETRO_MAC1984 -> {
                val bg = Color(0xFFD2D5D6) // Platinum chassis
                val normalBg = Color(0xFFF1F3F4)
                val specialBg = Color(0xFFBCC0C3)
                val textColor = Color(0xFF1D2022)
                val enterBg = Color(0xFF5A6672)
                val enterTextColor = Color(0xFFFFFFFF)
                val accent = Color(0xFF2B6CB0)
                val bevel = Color(0xFF9EA3A7)
                val shape = RoundedCornerShape(6.dp)

                KeyboardStyle(
                    theme = KeyboardSettings.THEME_RETRO_MAC1984,
                    isDark = false,
                    isRetro = true,
                    backgroundColor = bg,
                    normalKeyBg = normalBg,
                    specialKeyBg = specialBg,
                    keyTextColor = textColor,
                    accentColor = accent,
                    enterKeyBg = enterBg,
                    enterKeyTextColor = enterTextColor,
                    keyShape = shape,
                    keyBorder = currentKeyBorder ?: BorderStroke(0.7.dp, Color(0x28000000)),
                    showPressPopup = popupKeypressState,
                    scaleOnPress = true,
                    pressAnimationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                    keyBevelColor = bevel,
                    isMonospace = true,
                    spacebarLineColor = Color(0x22000000),
                    toolbarBgColor = Color(0xFFC7CBCC),
                    chassisBorderColor = Color(0xFFABB0B3)
                )
            }

            // 6. Retro 80s Synthwave
            themeState == KeyboardSettings.THEME_RETRO_SYNTHWAVE -> {
                val bg = Color(0xFF140C24)
                val normalBg = Color(0xFF24153E)
                val specialBg = Color(0xFF1C0E32)
                val textColor = Color(0xFF00F0FF) // Neon Cyan
                val enterBg = Color(0xFFFF007F) // Neon Magenta
                val enterTextColor = Color(0xFFFFFFFF)
                val accent = Color(0xFFFF007F)
                val bevel = Color(0xFF0B0515)
                val shape = RoundedCornerShape(6.dp)

                KeyboardStyle(
                    theme = KeyboardSettings.THEME_RETRO_SYNTHWAVE,
                    isDark = true,
                    isRetro = true,
                    backgroundColor = bg,
                    normalKeyBg = normalBg,
                    specialKeyBg = specialBg,
                    keyTextColor = textColor,
                    accentColor = accent,
                    enterKeyBg = enterBg,
                    enterKeyTextColor = enterTextColor,
                    keyShape = shape,
                    keyBorder = currentKeyBorder ?: BorderStroke(0.8.dp, Color(0x4000F0FF)),
                    showPressPopup = popupKeypressState,
                    scaleOnPress = true,
                    pressAnimationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                    keyBevelColor = bevel,
                    isMonospace = true,
                    spacebarLineColor = Color(0x3500F0FF),
                    toolbarBgColor = Color(0xFF0E071A),
                    chassisBorderColor = Color(0x45FF007F)
                )
            }

            // 7. Dark Fallback
            isDark || themeState == KeyboardSettings.THEME_DARK || themeState == KeyboardSettings.THEME_AMOLED -> {
                val isOled = themeState == KeyboardSettings.THEME_AMOLED
                val bg = if (isOled) Color(0xFF000000) else Color(0xFF1E1F23)
                val normalBg = if (isOled) Color(0xFF121212) else Color(0xFF2B2D33)
                val specialBg = if (isOled) Color(0xFF1A1A1A) else Color(0xFF23252A)
                val textColor = Color(0xFFF1F3F5)
                val enterBg = parsedAccentColor
                val enterTextColor = Color(0xFF041E49)
                val shape = RoundedCornerShape(7.dp)

                KeyboardStyle(
                    theme = themeState,
                    isDark = true,
                    isRetro = false,
                    backgroundColor = bg,
                    normalKeyBg = normalBg,
                    specialKeyBg = specialBg,
                    keyTextColor = textColor,
                    accentColor = parsedAccentColor,
                    enterKeyBg = enterBg,
                    enterKeyTextColor = enterTextColor,
                    keyShape = shape,
                    keyBorder = currentKeyBorder,
                    showPressPopup = popupKeypressState,
                    scaleOnPress = true,
                    pressAnimationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
                )
            }

            // 8. Light Fallback
            else -> {
                val bg = Color(0xFFECEFF2)
                val normalBg = Color(0xFFFFFFFF)
                val specialBg = Color(0xFFDCE1E6)
                val textColor = Color(0xFF1D2024)
                val enterBg = parsedAccentColor
                val enterTextColor = Color(0xFFFFFFFF)
                val shape = RoundedCornerShape(7.dp)

                KeyboardStyle(
                    theme = themeState,
                    isDark = false,
                    isRetro = false,
                    backgroundColor = bg,
                    normalKeyBg = normalBg,
                    specialKeyBg = specialBg,
                    keyTextColor = textColor,
                    accentColor = parsedAccentColor,
                    enterKeyBg = enterBg,
                    enterKeyTextColor = enterTextColor,
                    keyShape = shape,
                    keyBorder = currentKeyBorder,
                    showPressPopup = popupKeypressState,
                    scaleOnPress = true,
                    pressAnimationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
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

    // Spacious key container height with comfortable proportions matching Gboard standard
    val keysHeight = when {
        isLandscape -> (screenHeight * 0.44f).coerceIn(135f, 180f).dp
        keyboardHeightState == KeyboardSettings.HEIGHT_SHORT -> (screenHeight * 0.23f).coerceIn(180f, 220f).dp
        keyboardHeightState == KeyboardSettings.HEIGHT_TALL -> (screenHeight * 0.34f).coerceIn(260f, 320f).dp
        keyboardHeightState == KeyboardSettings.HEIGHT_CUSTOM -> (screenHeight * (customKeyboardHeightPercent / 100f)).coerceIn(170f, 380f).dp
        else -> (screenHeight * 0.285f).coerceIn(210f, 260f).dp
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
    val isSensitiveInput = service != null && !service.allowsTextAssistance()
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
    val suggestions = remember(asyncPredictions, activePrefix, isSensitiveInput) {
        if (isSensitiveInput) {
            listOf("", "", "")
        } else if (asyncPredictions.suggestions.isNotEmpty() && asyncPredictions.suggestions.any { it.isNotBlank() }) {
            asyncPredictions.suggestions
        } else if (activePrefix.isNotEmpty()) {
            listOf(activePrefix, "", "")
        } else {
            listOf("", "", "")
        }
    }

    var isToolbarForceExpanded by remember { mutableStateOf(false) }
    var isToolsDrawerOpen by remember { mutableStateOf(false) }
    var isProofreadSheetOpen by remember { mutableStateOf(false) }
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
        val toolbarHeight = ((if (aiRephraseSuggestions.isNotEmpty()) 46f else 38f) * heightScaleFactor.coerceIn(0.88f, 1.15f)).dp
        val effectiveKeysHeight = if (isEmojis) keysHeight + toolbarHeight + 1.dp else keysHeight

        // --- TOOLBAR ROW ---
        if (!isEmojis) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(toolbarBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .height(toolbarHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                val toolbarMainMode = when {
                    isRephrasing -> 0
                    showVoicePolishPrompt -> 1
                    aiRephraseSuggestions.isNotEmpty() -> 2
                    isAssistant -> 3
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
                        1 -> {
                            val formatScrollState = rememberScrollState()
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(formatScrollState),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = accentColor.copy(alpha = 0.15f),
                                        modifier = Modifier.padding(end = 2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoFixHigh,
                                                contentDescription = "Format Transcription",
                                                tint = accentColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Format:",
                                                color = keyTextColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Format Style Action Chips
                                    val formatChips = listOf(
                                        Triple("✨ Polish", TranscriptionFormatStyle.SMART_CLEAN, "voice_format_clean"),
                                        Triple("• Bullets", TranscriptionFormatStyle.BULLETS, "voice_format_bullets"),
                                        Triple("1. Steps", TranscriptionFormatStyle.NUMBERED, "voice_format_numbered"),
                                        Triple("✉️ Email", TranscriptionFormatStyle.EMAIL, "voice_format_email"),
                                        Triple("👔 Formal", TranscriptionFormatStyle.EXECUTIVE, "voice_format_formal"),
                                        Triple("✂️ Concise", TranscriptionFormatStyle.CONCISE, "voice_format_concise"),
                                        Triple("☐ Checklist", TranscriptionFormatStyle.CHECKLIST, "voice_format_checklist")
                                    )

                                    formatChips.forEach { (label, style, tag) ->
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (style == TranscriptionFormatStyle.SMART_CLEAN) accentColor else keyTextColor.copy(alpha = 0.1f),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable {
                                                    onFormatVoice(style)
                                                }
                                                .testTag(tag)
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (style == TranscriptionFormatStyle.SMART_CLEAN) Color.White else keyTextColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                // Dismiss button
                                IconButton(
                                    onClick = onRejectVoicePolish,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("voice_polish_no_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = keyTextColor.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
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
                        3 -> {
                            // DEDICATED AI SCREEN TOOLBAR
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Left side: Back Button + AI Badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconButton(
                                        onClick = { onAssistantToggle() },
                                        modifier = Modifier.size(32.dp).testTag("ai_screen_back_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Back to Keyboard",
                                            tint = keyTextColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(accentColor)
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = "AI Active",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "AI Writer",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Right side: Clipboard Button + Sound Toggle + Settings
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { onClipboardToggle() },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(if (isClipboard) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                                            .testTag("ai_screen_clipboard_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Clipboard history",
                                            tint = if (isClipboard) accentColor else keyTextColor.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    var soundOn by remember { mutableStateOf(settings.soundEnabled) }
                                    IconButton(
                                        onClick = {
                                            settings.soundEnabled = !soundOn
                                            soundOn = !soundOn
                                        },
                                        modifier = Modifier.size(32.dp).testTag("ai_screen_sound_button")
                                    ) {
                                        Icon(
                                            imageVector = if (soundOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                            contentDescription = if (soundOn) "Mute sounds" else "Unmute sounds",
                                            tint = if (soundOn) accentColor else keyTextColor.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = onOpenSettings,
                                        modifier = Modifier.size(32.dp).testTag("ai_screen_settings_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Keyboard Settings",
                                            tint = keyTextColor.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
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
                                    "ramble_recording" -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            IconButton(
                                                onClick = onCancelRamble,
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFE53935).copy(alpha = 0.12f))
                                                    .testTag("cancel_ramble_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Cancel Ramble",
                                                    tint = Color(0xFFE53935),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFFE53935).copy(alpha = 0.12f))
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFE53935))
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Ramble",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFE53935)
                                                )
                                            }

                                            VoiceWaveformVisualizer(
                                                audioLevel = rambleAudioLevel,
                                                accentColor = Color(0xFFE53935),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .padding(vertical = 4.dp)
                                            )

                                            IconButton(
                                                onClick = onConfirmRamble,
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(accentColor)
                                                    .testTag("confirm_ramble_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Confirm Ramble",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                    "ramble_processing" -> {
                                        SlmRefiningShimmerStrip(
                                            accentColor = accentColor,
                                            keyTextColor = keyTextColor,
                                            keyColor = normalKeyBg,
                                            onCancel = onCancelRamble
                                        )
                                    }
                                    "voice_typing" -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            IconButton(
                                                onClick = onVoiceTypingToggle,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Red.copy(alpha = 0.12f))
                                                    .testTag("stop_recording_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Stop,
                                                    contentDescription = "Stop Voice Typing",
                                                    tint = Color.Red,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            VoiceWaveformVisualizer(
                                                audioLevel = audioLevel,
                                                accentColor = accentColor,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .padding(vertical = 4.dp)
                                            )
                                        }
                                    }
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
                                            if (activePrefix.isNotEmpty() || suggestions.any { it.isNotBlank() }) {
                                                // SUGGESTIONS MODE inside toolbar when typing
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
                                                        modifier = Modifier.size(32.dp).testTag("expand_toolbar_options_button")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Apps,
                                                            contentDescription = "Gboard Quick Tools",
                                                            tint = if (isToolsDrawerOpen) accentColor else keyTextColor.copy(alpha = 0.8f),
                                                            modifier = Modifier.size(19.dp)
                                                        )
                                                    }
                                                    
                                                    // Suggestions list with smooth animated morphing
                                                    Row(
                                                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        suggestions.take(3).forEachIndexed { index, word ->
                                                            val middleWord = suggestions.getOrNull(1) ?: ""
                                                            val isMiddleAutoCorrecting = gboardResult.isCenterAutocorrecting || (
                                                                activePrefix.isNotEmpty() &&
                                                                middleWord.isNotEmpty() &&
                                                                middleWord.lowercase() != activePrefix.lowercase()
                                                            )

                                                            val isCorrectionActive = activePrefix.isNotEmpty() && (
                                                                (index == 1 && (isMiddleAutoCorrecting || suggestions.size == 1)) ||
                                                                (index == 1 && dictionaryManager.isSpellingCorrection(activePrefix, word, previousWord)) ||
                                                                (activePrefix.lowercase() == "i" && word == "I" && index == 1)
                                                            )

                                                            val isLiteralRawTyped = index == 0 &&
                                                                currentTypedWord.isNotEmpty() &&
                                                                word.lowercase() == currentTypedWord.lowercase()

                                                            val textWeight = if (isCorrectionActive) FontWeight.Bold else FontWeight.Medium
                                                            val textColorValue = if (isCorrectionActive) Color.White else keyTextColor

                                                            val chipInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                                            val chipPressed by chipInteraction.collectIsPressedAsState()
                                                            val chipScale by animateFloatAsState(
                                                                targetValue = if (chipPressed) 0.93f else 1.0f,
                                                                animationSpec = tween(60, easing = FastOutSlowInEasing),
                                                                label = "chip_press_scale"
                                                            )
                                                            val chipBgColor by animateColorAsState(
                                                                targetValue = if (isCorrectionActive) accentColor
                                                                    else if (chipPressed) keyTextColor.copy(alpha = 0.12f)
                                                                    else keyTextColor.copy(alpha = 0.05f),
                                                                animationSpec = tween(120),
                                                                label = "chip_bg_color"
                                                            )
                                                            val chipBorderColor by animateColorAsState(
                                                                targetValue = if (isCorrectionActive) accentColor.copy(alpha = 0.85f)
                                                                    else keyTextColor.copy(alpha = 0.12f),
                                                                animationSpec = tween(120),
                                                                label = "chip_border_color"
                                                            )

                                                            Box(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .padding(horizontal = 4.dp)
                                                                    .graphicsLayer {
                                                                        scaleX = chipScale
                                                                        scaleY = chipScale
                                                                    }
                                                                    .clip(if (style.isRetro) RoundedCornerShape(5.dp) else RoundedCornerShape(20.dp))
                                                                    .background(chipBgColor)
                                                                    .border(
                                                                        border = androidx.compose.foundation.BorderStroke(
                                                                            width = if (isCorrectionActive) 1.5.dp else (if (style.isRetro) 0.8.dp else 0.5.dp),
                                                                            color = chipBorderColor
                                                                        ),
                                                                        shape = if (style.isRetro) RoundedCornerShape(5.dp) else RoundedCornerShape(20.dp)
                                                                    )
                                                                    .clickable(
                                                                        interactionSource = chipInteraction,
                                                                        indication = null
                                                                    ) {
                                                                        onSuggestionClick(word)
                                                                    }
                                                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                                                                    .testTag("suggestion_item_$word"),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.Center
                                                                ) {
                                                                    if (isCorrectionActive) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.AutoFixHigh,
                                                                            contentDescription = "Auto-correct suggestion",
                                                                            tint = Color.White,
                                                                            modifier = Modifier.size(12.dp).padding(end = 2.dp)
                                                                        )
                                                                    }
                                                                    AnimatedContent(
                                                                        targetState = if (isLiteralRawTyped) "\"$word\"" else word,
                                                                        transitionSpec = {
                                                                            fadeIn(animationSpec = tween(110, easing = LinearOutSlowInEasing)) togetherWith
                                                                            fadeOut(animationSpec = tween(70, easing = FastOutLinearInEasing))
                                                                        },
                                                                        label = "suggestion_word_anim"
                                                                    ) { displayWord ->
                                                                        Text(
                                                                            text = displayWord,
                                                                            color = textColorValue,
                                                                            fontSize = 13.sp,
                                                                            fontWeight = textWeight,
                                                                            fontFamily = if (style.isMonospace) FontFamily.Monospace else FontFamily.SansSerif,
                                                                            textAlign = TextAlign.Center,
                                                                            maxLines = 1
                                                                        )
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
                                                        modifier = Modifier.size(36.dp).testTag("mic_button")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Mic,
                                                            contentDescription = "Voice Dictation",
                                                            tint = if (isVoiceTyping) Color.Red else keyTextColor.copy(alpha = 0.75f),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            } else {
                                                // IDLE QUICK TOOLS TOOLBAR (Image 2 style: 6 evenly spaced tools or smart clipboard paste)
                                                val clipManager = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager }
                                                val recentClipText = remember(clipManager, isClipboard) {
                                                    try {
                                                        if (clipManager?.hasPrimaryClip() == true) {
                                                            val txt = clipManager.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                                                            if (txt.isNotBlank() && txt.length <= 120) txt else null
                                                        } else null
                                                    } catch (e: Exception) { null }
                                                }

                                                if (recentClipText != null) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(horizontal = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        IconButton(
                                                            onClick = {
                                                                isToolsDrawerOpen = !isToolsDrawerOpen
                                                                isProofreadSheetOpen = false
                                                                isTextEditingOpen = false
                                                            },
                                                            modifier = Modifier.size(36.dp).testTag("expand_toolbar_options_button")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Apps,
                                                                contentDescription = "Gboard Quick Tools",
                                                                tint = if (isToolsDrawerOpen) accentColor else keyTextColor.copy(alpha = 0.8f),
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }

                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .padding(horizontal = 6.dp)
                                                                .clip(RoundedCornerShape(18.dp))
                                                                .background(accentColor.copy(alpha = 0.18f))
                                                                .border(0.5.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                                                                .clickable {
                                                                    (context as? TypeRightKeyboardService)?.currentInputConnection?.commitText(recentClipText, 1)
                                                                }
                                                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                                                .testTag("toolbar_quick_paste_chip"),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.ContentPaste,
                                                                    contentDescription = "Quick Paste",
                                                                    tint = accentColor,
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                                Text(
                                                                    text = "Paste: \"${if (recentClipText.length > 20) recentClipText.take(18) + "..." else recentClipText}\"",
                                                                    color = keyTextColor,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Medium,
                                                                    maxLines = 1
                                                                )
                                                            }
                                                        }

                                                        IconButton(
                                                            onClick = onVoiceTypingToggle,
                                                            modifier = Modifier.size(36.dp).testTag("mic_button")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Mic,
                                                                contentDescription = "Voice Dictation",
                                                                tint = if (isVoiceTyping) Color.Red else keyTextColor.copy(alpha = 0.8f),
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                } else {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    // 1. Apps / Grid menu
                                                    IconButton(
                                                        onClick = {
                                                            isToolsDrawerOpen = !isToolsDrawerOpen
                                                            isProofreadSheetOpen = false
                                                            isTextEditingOpen = false
                                                        },
                                                        modifier = Modifier.size(36.dp).testTag("expand_toolbar_options_button")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Apps,
                                                            contentDescription = "Gboard Quick Tools",
                                                            tint = if (isToolsDrawerOpen) accentColor else keyTextColor.copy(alpha = 0.8f),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }

                                                    // 2. Clipboard
                                                    IconButton(
                                                        onClick = onClipboardToggle,
                                                        modifier = Modifier.size(36.dp).testTag("clipboard_button")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ContentPaste,
                                                            contentDescription = "Clipboard history",
                                                            tint = if (isClipboard) accentColor else keyTextColor.copy(alpha = 0.8f),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }

                                                    // 3. AI Polish Pill button
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(18.dp))
                                                            .background(accentColor.copy(alpha = 0.22f))
                                                            .clickable {
                                                                isProofreadSheetOpen = true
                                                                isToolsDrawerOpen = false
                                                            }
                                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                                            .testTag("toolbar_proofread_pill"),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.AutoAwesome,
                                                                contentDescription = "AI Polish",
                                                                tint = accentColor,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Text(
                                                                text = "AI Polish",
                                                                color = accentColor,
                                                                fontSize = 11.5.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                    }

                                                    // 4. GIF badge / button
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable { onEmojiToggle() }
                                                            .testTag("toolbar_gif_button"),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "GIF",
                                                            color = keyTextColor.copy(alpha = 0.8f),
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }

                                                    // 5. AI Polish & Rephrase Modes
                                                    IconButton(
                                                        onClick = { onAiPolishClick() },
                                                        modifier = Modifier.size(36.dp).testTag("ai_polish_button")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.AutoFixHigh,
                                                            contentDescription = "AI Polish Modes",
                                                            tint = if (isAssistant) accentColor else keyTextColor.copy(alpha = 0.8f),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }

                                                    // 6. Mic Voice Dictation
                                                    IconButton(
                                                        onClick = onVoiceTypingToggle,
                                                        modifier = Modifier.size(36.dp).testTag("mic_button")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Mic,
                                                            contentDescription = "Voice Dictation",
                                                            tint = if (isVoiceTyping) Color.Red else keyTextColor.copy(alpha = 0.8f),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
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
                                                        .size(32.dp)
                                                        .testTag("collapse_toolbar_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "Back to suggestions",
                                                        tint = keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                AiEngineIndicatorBadge(
                                                    activeEngine = activeAiEngineState,
                                                    accentColor = accentColor,
                                                    keyTextColor = keyTextColor,
                                                    onClick = {
                                                        val next = when (activeAiEngineState) {
                                                            ActiveAiEngine.BOTH -> ActiveAiEngine.OFFLINE
                                                            ActiveAiEngine.OFFLINE -> ActiveAiEngine.ONLINE
                                                            ActiveAiEngine.ONLINE, ActiveAiEngine.NEMOTRON -> ActiveAiEngine.NONE
                                                            ActiveAiEngine.NONE -> ActiveAiEngine.BOTH
                                                        }
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
                                                        .size(34.dp)
                                                        .testTag("undo_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                                        contentDescription = "Undo",
                                                        tint = keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = onRedo,
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .testTag("redo_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.Redo,
                                                        contentDescription = "Redo",
                                                        tint = keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = onClipboardToggle,
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .testTag("clipboard_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentPaste,
                                                        contentDescription = "Clipboard history",
                                                        tint = if (isClipboard) accentColor else keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = onVoiceTypingToggle,
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .testTag("mic_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Mic,
                                                        contentDescription = "Voice Dictation",
                                                        tint = if (isVoiceTyping) Color.Red else keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { onProofreadClick() },
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .testTag("proofread_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Spellcheck,
                                                        contentDescription = "Direct Proofread",
                                                        tint = keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { onAiPolishClick() },
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .testTag("ai_polish_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AutoAwesome,
                                                        contentDescription = "AI Polish",
                                                        tint = if (isAssistant) accentColor else keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = onOpenSettings,
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .testTag("settings_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Settings,
                                                        contentDescription = "Settings",
                                                        tint = keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(20.dp)
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

        HorizontalDivider(color = keyTextColor.copy(alpha = 0.12f), thickness = 1.dp)

        // --- KEYBOARD KEYS CONTAINER ---
        val currentLayer = when {
            isWisprVoiceOpen -> KeyboardLayer.WisprVoice
            isProofreadSheetOpen -> KeyboardLayer.Proofread
            isToolsDrawerOpen -> KeyboardLayer.ToolsDrawer
            isTextEditingOpen -> KeyboardLayer.TextEditing
            isAssistant -> KeyboardLayer.Assistant
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
                            onClose = { isProofreadSheetOpen = false }
                        )
                    }
                    KeyboardLayer.ToolsDrawer -> {
                        GboardToolsDrawer(
                            keyTextColor = keyTextColor,
                            accentColor = accentColor,
                            keyColor = normalKeyBg,
                            heightPercent = customKeyboardHeightPercent,
                            onHeightPercentChange = { percent ->
                                settings.height = KeyboardSettings.HEIGHT_CUSTOM
                                settings.customKeyboardHeightPercent = percent
                                keyboardHeightState = KeyboardSettings.HEIGHT_CUSTOM
                                customKeyboardHeightPercent = percent
                            },
                            onToolClick = { tool ->
                                when (tool) {
                                    GboardTool.AI_POLISH,
                                    GboardTool.PROOFREAD -> {
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
                                        val nextTheme = if (isDark) KeyboardSettings.THEME_LIGHT else KeyboardSettings.THEME_DARK
                                        settings.theme = nextTheme
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
                    KeyboardLayer.Assistant -> {
                        AiAssistantPanel(
                            initialMode = currentAiMode,
                            keyTextColor = keyTextColor,
                            accentColor = accentColor,
                            keyColor = normalKeyBg,
                            onApplyText = { _, _ -> onAssistantToggle() },
                            onClose = onAssistantToggle
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
                        RevampedEmojiLayout(
                            keyColor = normalKeyBg,
                            textColor = keyTextColor,
                            accentColor = accentColor,
                            onKeyClick = onKeyClick,
                            onEmojiToggle = onEmojiToggle,
                            onDelete = onDelete
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
                            onSwipeWord = onSuggestionClick,
                            dictionaryManager = dictionaryManager,
                            onVoiceTypingToggle = onVoiceTypingToggle,
                            onTapCoordinates = onTapCoordinates,
                            onSpaceSwipeLeft = onSpaceSwipeLeft,
                            onSpaceSwipeRight = onSpaceSwipeRight,
                            spacebarLabel = spacebarLabel,
                            onSpaceLongClick = onToggleLanguage
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

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: Dismiss / Collapse Keyboard button
                    IconButton(
                        onClick = {
                            (context as? InputMethodService)?.requestHideSelf(0)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("dismiss_keyboard_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Dismiss Keyboard",
                            tint = keyTextColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Center: Spacious clearance for Android system navigation gesture bar / 3-button pill
                    Spacer(modifier = Modifier.weight(1f))

                    // Right side: Dedicated clearance for Android system IME switcher button (the globe icon)
                    Spacer(modifier = Modifier.width(42.dp))
                }
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
    onSwipeWord: (String) -> Unit,
    dictionaryManager: DictionaryManager,
    onVoiceTypingToggle: () -> Unit,
    onTapCoordinates: (Float, Float) -> Unit = { _, _ -> },
    onSpaceSwipeLeft: (() -> Unit)? = null,
    onSpaceSwipeRight: (() -> Unit)? = null,
    showNumberRow: Boolean = false,
    spacebarLabel: String = "English",
    onSpaceLongClick: (() -> Unit)? = null
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

    val trailAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    val trailElasticity = remember { androidx.compose.animation.core.Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { columnSize = it.size }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            val down = event.changes.firstOrNull() ?: continue
                            val startPosition = down.position
                            val activePointerId = down.id

                            // Record touch down coordinate for the typing offset ML predictor
                            val colW = columnSize.width
                            val colH = columnSize.height
                            if (colW > 0 && colH > 0) {
                                val tx = (startPosition.x / colW).coerceIn(0f, 1f)
                                val ty = (startPosition.y / (colH * 0.75f)).coerceIn(0f, 1f)
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

                                    // Keep up to 100 points for a smooth, extended swipe trail effect
                                    if (pendingPoints.size > 100) {
                                        pendingPoints.removeAt(0)
                                    }

                                    val dist = (currentPos - startPosition).getDistance()
                                    if (!detectedSwipe && dist > 20.dp.toPx()) {
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
                                        if (w > 0 && h > 0) {
                                            pendingPoints.forEach { pt ->
                                                val nx = (pt.x / w).coerceIn(0f, 1f)
                                                val ny = (pt.y / (h * 0.75f)).coerceIn(0f, 1f)
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
                                        if (w > 0 && h > 0) {
                                            val nx = (currentPos.x / w).coerceIn(0f, 1f)
                                            val ny = (currentPos.y / (h * 0.75f)).coerceIn(0f, 1f)
                                            val lastPt = normalizedPath.lastOrNull()
                                            if (lastPt == null || lastPt.x != nx || lastPt.y != ny) {
                                                normalizedPath.add(android.graphics.PointF(nx, ny))
                                            }
                                        }
                                    }
                                } else {
                                    if (detectedSwipe) {
                                        change.consume()

                                        val decoded = dictionaryManager.decodeSwipePath(normalizedPath.toList())
                                        if (decoded.isNotEmpty()) {
                                            val bestWord = decoded.first()
                                            dictionaryManager.learnSwipePattern(bestWord, normalizedPath.toList())
                                            val formattedWord = if (isShift) {
                                                bestWord.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                                            } else {
                                                bestWord
                                            }
                                            onSwipeWord(formattedWord)
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
                    .testTag("space_key"),
                keyBg = keyColor,
                textColor = textColor.copy(alpha = 0.65f),
                onLongClick = onSpaceLongClick,
                onSwipeLeft = onSpaceSwipeLeft,
                onSwipeRight = onSpaceSwipeRight
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
    if ((isSwiping || trailAlpha.value > 0.01f) && currentSwipePoints.size > 1) {
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
                        var dragAccumulatedX = 0f
                        var isDragging = false
                        var lastX = down.position.x
                        
                        do {
                            val event = awaitPointerEvent()
                            val dragEvent = event.changes.firstOrNull()
                            if (dragEvent != null && dragEvent.pressed) {
                                val currentX = dragEvent.position.x
                                val diffX = currentX - lastX
                                lastX = currentX
                                
                                if (!isDragging && kotlin.math.abs(dragAccumulatedX + diffX) > swipeThresholdPx) {
                                    isDragging = true
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
                        
                        if (!isDragging) {
                            onClick()
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
fun AiAssistantPanel(
    initialMode: String = "formalize",
    keyTextColor: Color,
    accentColor: Color,
    keyColor: Color,
    onApplyText: (appliedText: String, isSelection: Boolean) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val keyboardSettings = remember { KeyboardSettings(context) }
    val coroutineScope = rememberCoroutineScope()
    val editorService = context as? TypeRightKeyboardService
    val snapshot = remember { editorService?.captureEditorText() }

    // Fetch the text to process: either selected text or the entire text field content.
    var originalText by remember { mutableStateOf("") }
    var isSelectionActive by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(initialMode) }
    var generatedText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val modes = listOf(
        "proofread" to "✨ Proofread",
        "polish" to "💎 Polish",
        "formalize" to "👔 Professional",
        "casual" to "😊 Casual",
        "rephrase" to "🔄 Rephrase",
        "shorten" to "⚡ Shorten",
        "expand" to "📝 Expand"
    )

    fun runPolish(targetText: String, modeId: String) {
        if (targetText.isEmpty()) return
        activeJob?.cancel()
        isLoading = true
        activeJob = coroutineScope.launch {
            try {
                val polishMode = PolishMode.fromString(modeId)
                val textContext = TextContext(mode = polishMode)
                val result = AiPolishManager(context).polishText(targetText, polishMode, textContext)
                generatedText = result
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelled due to user switching modes
            } catch (e: Exception) {
                generatedText = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(initialMode) {
        selectedMode = initialMode
        originalText = snapshot?.text.orEmpty()
        isSelectionActive = !snapshot?.selected.isNullOrEmpty()

        if (originalText.isNotEmpty()) {
            runPolish(originalText, selectedMode)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        // Mode Selection Row + Dismiss Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                modes.forEach { (modeId, modeLabel) ->
                    val isSelected = selectedMode == modeId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) accentColor else keyTextColor.copy(alpha = 0.08f)
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) accentColor else keyTextColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                selectedMode = modeId
                                runPolish(originalText, modeId)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("assistant_mode_$modeId")
                    ) {
                        Text(
                            text = modeLabel,
                            color = if (isSelected) Color.White else keyTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(28.dp)
                    .padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = keyTextColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Main Result Box - Tapping directly replaces / inserts text into the field
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(keyTextColor.copy(alpha = 0.05f))
                .border(1.dp, keyTextColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .clickable(
                    enabled = generatedText.isNotEmpty() && !isLoading && !generatedText.startsWith("Error"),
                    onClick = {
                        if (editorService?.applyEditorReplacement(snapshot, generatedText, PolishMode.fromString(selectedMode)) == true) {
                            onApplyText(generatedText, isSelectionActive)
                        }
                    }
                )
                .padding(10.dp)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = accentColor,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Polishing text...",
                        color = keyTextColor.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            } else {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = if (generatedText.isNotEmpty()) generatedText else "Select a mode above to polish or rephrase text.",
                            color = if (generatedText.isNotEmpty()) keyTextColor else keyTextColor.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            fontWeight = if (generatedText.isNotEmpty()) FontWeight.Medium else FontWeight.Normal,
                            lineHeight = 17.sp
                        )
                    }

                    if (generatedText.isNotEmpty() && !generatedText.startsWith("Error")) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tap text to insert / replace ↵",
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
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
    val bgColor = when (activeEngine) {
        ActiveAiEngine.BOTH -> accentColor.copy(alpha = 0.16f)
        ActiveAiEngine.OFFLINE -> Color(0xFF10B981).copy(alpha = 0.16f)
        ActiveAiEngine.ONLINE, ActiveAiEngine.NEMOTRON -> Color(0xFF3B82F6).copy(alpha = 0.16f)
        ActiveAiEngine.NONE -> keyTextColor.copy(alpha = 0.06f)
    }

    val borderColor = when (activeEngine) {
        ActiveAiEngine.BOTH -> accentColor.copy(alpha = 0.50f)
        ActiveAiEngine.OFFLINE -> Color(0xFF10B981).copy(alpha = 0.50f)
        ActiveAiEngine.ONLINE, ActiveAiEngine.NEMOTRON -> Color(0xFF3B82F6).copy(alpha = 0.50f)
        ActiveAiEngine.NONE -> keyTextColor.copy(alpha = 0.18f)
    }

    val contentColor = when (activeEngine) {
        ActiveAiEngine.BOTH -> accentColor
        ActiveAiEngine.OFFLINE -> Color(0xFF10B981)
        ActiveAiEngine.ONLINE, ActiveAiEngine.NEMOTRON -> Color(0xFF3B82F6)
        ActiveAiEngine.NONE -> keyTextColor.copy(alpha = 0.55f)
    }

    val icon = when (activeEngine) {
        ActiveAiEngine.BOTH -> Icons.Default.AutoAwesome
        ActiveAiEngine.OFFLINE -> Icons.Default.Bolt
        ActiveAiEngine.ONLINE, ActiveAiEngine.NEMOTRON -> Icons.Default.Cloud
        ActiveAiEngine.NONE -> Icons.Default.CloudOff
    }

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
    AI_POLISH("AI Polish", Icons.Default.AutoAwesome),
    PROOFREAD("AI Polish", Icons.Default.AutoAwesome),
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
    heightPercent: Float,
    onHeightPercentChange: (Float) -> Unit,
    onToolClick: (GboardTool) -> Unit,
    onClose: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.toFloat().coerceAtLeast(1f)
    val currentHeight by rememberUpdatedState(heightPercent)
    val resize by rememberUpdatedState(onHeightPercentChange)
    var dragHeight by remember { mutableStateOf(heightPercent) }
    val scale = LocalKeyboardScale.current

    // Keep this surface focused on the actions people need while typing. Less-used
    // settings remain available from the settings screen and the toolbar overflow.
    val tools = listOf(
        GboardTool.AI_POLISH,
        GboardTool.CLIPBOARD,
        GboardTool.TEXT_EDIT,
        GboardTool.ONE_HANDED
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
                .padding(bottom = 4.dp),
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
                    text = "Keyboard tools",
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

        // Direct-manipulation resize control with presets and slider
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = keyColor.copy(alpha = 0.92f),
            border = BorderStroke(0.5.dp, keyTextColor.copy(alpha = 0.14f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AspectRatio, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Keyboard Height", color = keyTextColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${heightPercent.toInt()}%", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Quick size preset buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "Short" to 22f,
                        "Normal" to 28.5f,
                        "Tall" to 34f,
                        "Extra" to 40f
                    ).forEach { (label, preset) ->
                        val isSel = kotlin.math.abs(heightPercent - preset) < 2.5f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) accentColor.copy(alpha = 0.22f) else keyTextColor.copy(alpha = 0.07f))
                                .border(0.5.dp, if (isSel) accentColor else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { resize(preset) }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) accentColor else keyTextColor.copy(alpha = 0.85f),
                                fontSize = 10.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Interactive slider and drag handle
                Slider(
                    value = heightPercent.coerceIn(20f, 42f),
                    onValueChange = { resize(it) },
                    valueRange = 20f..42f,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = keyTextColor.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .testTag("keyboard_resize_slider")
                )

                // Drag handle area for direct gesture resize
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .semantics {
                            contentDescription = "Keyboard height. Drag up to enlarge or down to shrink"
                            progressBarRangeInfo = ProgressBarRangeInfo(heightPercent, 20f..42f)
                            setProgress { resize(it.coerceIn(20f, 42f)); true }
                        }
                        .pointerInput(density, screenHeightDp) {
                            detectDragGestures(onDragStart = { dragHeight = currentHeight }) { change, delta ->
                                change.consume()
                                dragHeight = (dragHeight - delta.y / density / screenHeightDp * 100f).coerceIn(20f, 42f)
                                resize(dragHeight)
                            }
                        }
                        .testTag("keyboard_resize_handle"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DragHandle, contentDescription = null, tint = accentColor.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Drag handle to resize", color = keyTextColor.copy(alpha = 0.6f), fontSize = 10.sp)
                    }
                }
            }
        }

        // Proportional, Gboard-style tool grid that fills remaining height without overflow
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val chunkedTools = tools.chunked(2)
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
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.title,
                                    tint = if (tool == GboardTool.AI_POLISH || tool == GboardTool.PROOFREAD) accentColor else keyTextColor,
                                    modifier = Modifier.size((18f * scale).coerceIn(15f, 24f).dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tool.title,
                                    color = keyTextColor,
                                    fontSize = (9.5f * scale).coerceIn(8.5f, 12f).sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GboardProofreadPanel(
    keyTextColor: Color,
    accentColor: Color,
    keyColor: Color,
    onApplyText: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val editorService = context as? TypeRightKeyboardService
    val snapshot = remember { editorService?.captureEditorText() }
    var correctionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    var originalText by remember { mutableStateOf("") }
    var correctedText by remember { mutableStateOf("") }
    var slmResult by remember { mutableStateOf<SlmProofreadEngine.SlmProofreadResult?>(null) }
    var neuralResult by remember { mutableStateOf<OnDeviceNeuralPolishEngine.NeuralPolishResult?>(null) }
    var selectedEngineIndex by remember { mutableStateOf(0) }
    val engines = listOf("⚡ Neural Polish", "🧠 AICore Nano", "🤖 SLM Syntactic")
    var isLoading by remember { mutableStateOf(true) }
    var selectedTone by remember { mutableStateOf("Proofread") }

    val tones = listOf(
        "Proofread" to "✨ Fix All",
        "Formal" to "👔 Professional",
        "Casual" to "💬 Casual",
        "Concise" to "⚡ Concise",
        "Eloquent" to "🌟 Eloquent",
        "Bullets" to "📝 Bullets"
    )

    fun runCorrection(text: String, tone: String, engineIdx: Int = selectedEngineIndex) {
        if (text.isBlank()) {
            isLoading = false
            return
        }
        isLoading = true
        correctionJob?.cancel()
        correctionJob = coroutineScope.launch {
            try {
                when (engineIdx) {
                    0 -> { // ⚡ Neural Polish (On-Device)
                        val engine = OnDeviceNeuralPolishEngine.getInstance(context)
                        val res = withContext(Dispatchers.Default) { engine.polish(text, tone) }
                        neuralResult = res
                        correctedText = res.polishedText
                    }
                    1 -> { // 🧠 AICore (Gemini Nano)
                        val slmEngine = SlmProofreadEngine.getInstance(context)
                        val detailed = withContext(Dispatchers.Default) { slmEngine.proofread(text, tone) }
                        slmResult = detailed
                        if (tone.equals("Proofread", ignoreCase = true)) {
                            correctedText = detailed.proofreadText
                        } else {
                            val result = DeviceAiCoreEngine.getInstance(context).proofread(detailed.proofreadText, tone)
                            correctedText = result.correctedText
                        }
                    }
                    else -> { // 🤖 SLM Syntactic
                        val slmEngine = SlmProofreadEngine.getInstance(context)
                        val detailed = withContext(Dispatchers.Default) { slmEngine.proofread(text, tone) }
                        slmResult = detailed
                        correctedText = detailed.proofreadText
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                correctedText = text
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        originalText = snapshot?.text.orEmpty()
        if (originalText.isNotEmpty()) {
            runCorrection(originalText, "Proofread", 0)
        } else {
            isLoading = false
        }
    }

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
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "AI Polish",
                    color = keyTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    modifier = Modifier.clickable {
                        selectedEngineIndex = (selectedEngineIndex + 1) % engines.size
                        runCorrection(originalText, selectedTone, selectedEngineIndex)
                    }
                ) {
                    Text(
                        text = engines[selectedEngineIndex] + " ▾",
                        color = Color(0xFF10B981),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
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

        // Tone chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tones.forEach { (toneId, toneLabel) ->
                val isSelected = selectedTone == toneId
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) accentColor else keyColor,
                    border = BorderStroke(0.5.dp, if (isSelected) accentColor else keyTextColor.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .clickable {
                            selectedTone = toneId
                            runCorrection(originalText, toneId, selectedEngineIndex)
                        }
                        .testTag("proofread_tone_$toneId")
                ) {
                    Text(
                        text = toneLabel,
                        color = if (isSelected) Color.White else keyTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Diff / Suggestion Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = keyColor.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, if (correctedText.isNotEmpty() && correctedText != originalText) accentColor.copy(alpha = 0.5f) else keyTextColor.copy(alpha = 0.12f)),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = accentColor, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Analyzing with ${engines[selectedEngineIndex]}...",
                            color = keyTextColor.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else if (originalText.isBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Type some text in any input field to proofread.",
                        color = keyTextColor.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (selectedEngineIndex == 0) {
                            val nRes = neuralResult
                            if (nRes != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = accentColor.copy(alpha = 0.15f),
                                    border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                ) {
                                    Text(
                                        text = "${nRes.toneSummary} • ${nRes.latencyMs}ms local inference • ${nRes.appliedEdits.size} improvements",
                                        color = accentColor,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                if (nRes.appliedEdits.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        for (edit in nRes.appliedEdits) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = keyTextColor.copy(alpha = 0.08f),
                                                border = BorderStroke(0.5.dp, keyTextColor.copy(alpha = 0.18f))
                                            ) {
                                                Text(
                                                    text = "${edit.original} → ${edit.replacement}",
                                                    color = keyTextColor,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val result = slmResult
                            if (result != null) {
                                if (result.corrections.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = accentColor.copy(alpha = 0.15f),
                                        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                    ) {
                                        Text(
                                            text = result.summaryMessage,
                                            color = accentColor,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        for (cor in result.corrections) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = keyTextColor.copy(alpha = 0.08f),
                                                border = BorderStroke(0.5.dp, keyTextColor.copy(alpha = 0.18f))
                                            ) {
                                                Text(
                                                    text = "${cor.original} → ${cor.replacement}",
                                                    color = keyTextColor,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (correctedText != originalText && correctedText.isNotBlank()) {
                            Text(
                                text = "Original: $originalText",
                                color = keyTextColor.copy(alpha = 0.45f),
                                fontSize = 11.5.sp,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text(
                            text = if (correctedText.isNotBlank()) correctedText else originalText,
                            color = keyTextColor,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp
                        )
                    }

                    Button(
                        onClick = {
                            val textToApply = if (correctedText.isNotBlank()) correctedText else originalText
                            if (editorService?.applyEditorReplacement(snapshot, textToApply, PolishMode.fromString(selectedTone)) == true) {
                                onApplyText(textToApply)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("apply_proofread_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (correctedText != originalText && correctedText.isNotBlank()) "Apply Fix (${selectedTone})" else "Keep as is",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
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

@Composable
fun SlmRefiningShimmerStrip(
    accentColor: Color,
    keyTextColor: Color,
    keyColor: Color,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "slm_shimmer_transition")

    // Smooth horizontal sweeping shimmer effect
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_sweep"
    )

    // Pulsing sparkle icon scale
    val sparkleScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle_scale"
    )

    // Gentle alpha breathing for placeholder pills
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.12f * pulseAlpha),
            accentColor.copy(alpha = 0.45f * pulseAlpha),
            Color.White.copy(alpha = 0.35f * pulseAlpha),
            accentColor.copy(alpha = 0.12f * pulseAlpha)
        ),
        start = Offset(shimmerTranslate - 260f, 0f),
        end = Offset(shimmerTranslate, 0f)
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("slm_refining_shimmer_strip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Leading Animated Sparkle & Local SLM Chip
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = accentColor.copy(alpha = 0.12f),
            border = BorderStroke(0.75.dp, accentColor.copy(alpha = 0.35f)),
            modifier = Modifier.height(30.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "SLM refining",
                    tint = accentColor,
                    modifier = Modifier
                        .size(13.dp)
                        .graphicsLayer {
                            scaleX = sparkleScale
                            scaleY = sparkleScale
                        }
                )
                Text(
                    text = "SLM",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Shimmering Token 1 (Left Placeholder Chip)
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(keyColor.copy(alpha = 0.7f))
                .background(shimmerBrush)
                .border(0.5.dp, keyTextColor.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
        )

        // Shimmering Central Intent & Refine Label (Center Main Chip)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(keyColor.copy(alpha = 0.7f))
                .background(shimmerBrush)
                .border(0.75.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Text(
                    text = "Refining transcript...",
                    color = keyTextColor.copy(alpha = 0.88f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        // Shimmering Token 3 (Right Placeholder Chip)
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(keyColor.copy(alpha = 0.7f))
                .background(shimmerBrush)
                .border(0.5.dp, keyTextColor.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
        )

        // Cancel / Dismiss button
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .size(28.dp)
                .testTag("cancel_slm_refining_button")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel refinement",
                tint = keyTextColor.copy(alpha = 0.55f),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

