package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import android.content.SharedPreferences
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
    val aiPhraseCompletions: List<String> = emptyList()
)

class TypeRightKeyboardService : KeyboardService() {

    private lateinit var settings: KeyboardSettings
    private lateinit var dictionaryManager: DictionaryManager
    private lateinit var aiPolishManager: AiPolishManager
    private lateinit var clipboardRepository: ClipboardRepository
    private val localPredictor by lazy { LocalGrammarSpellPredictor(this) }

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
    private var lastCursorPosition = 0
    private var lastComposedStart = -1
    private var lastComposedEnd = -1
    
    // Voice Typing and AI Polish states
    private val isVoiceTypingActive = mutableStateOf(false)
    private val voiceTranscript = mutableStateOf("")
    private val voiceAudioLevel = mutableStateOf(0f)
    private val isAiPolishing = mutableStateOf(false)
    private val isAiRephrasing = mutableStateOf(false)
    private val aiRephraseSuggestions = androidx.compose.runtime.mutableStateListOf<String>()
    private val isMicPermissionGranted = mutableStateOf(false)
    private val showVoicePolishPrompt = mutableStateOf(false)
    private val pendingVoiceTranscript = mutableStateOf("")

    // Touch and machine-learning pattern tracking states
    private var lastTapX = 0.5f
    private var lastTapY = 0.5f

    // Speech recognizer and MediaRecorder
    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var audioRecord: android.media.AudioRecord? = null
    private var isAudioRecordActive = false

    // Voice Typing and STT Service
    private lateinit var voiceRecordingService: VoiceRecordingSttService

    private var audioManager: AudioManager? = null
    private var vibrator: Vibrator? = null

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
        
        try {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } catch (_: Exception) {}

        // Initialize on-device speech processing engine
        WhisperCppBrain.loadGGMLModel(this, "whisper-tiny")

        // Launch debounced asynchronous processing pipeline for text input buffer
        serviceScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            textBufferFlow
                .debounce(30L)
                .collectLatest { bufferState ->
                    val gboard = withContext(Dispatchers.Default) {
                        dictionaryManager.getGboardPredictions(
                            rawTyped = bufferState.activePrefix,
                            contextWords = bufferState.previousWords,
                            tapCoords = if (bufferState.tapCoords.isNotEmpty()) bufferState.tapCoords else null,
                            isSensitiveField = bufferState.isSensitive
                        )
                    }

                    val suggestionsList = withContext(Dispatchers.Default) {
                        if (bufferState.isUrl || bufferState.isEmail || bufferState.isSensitive) {
                            dictionaryManager.getSuggestionsForPrefix(
                                prefix = bufferState.activePrefix,
                                prevWord = bufferState.previousWord,
                                isUrlField = bufferState.isUrl,
                                isEmailField = bufferState.isEmail,
                                isSensitiveField = bufferState.isSensitive,
                                previousWords = bufferState.previousWords
                            )
                        } else {
                            listOf(gboard.leftCandidate, gboard.centerCandidate, gboard.rightCandidate)
                        }
                    }

                    val phrases = withContext(Dispatchers.Default) {
                        if (bufferState.activePrefix.isEmpty() && bufferState.previousWords.isNotEmpty()) {
                            dictionaryManager.localGrammarPredictor.predictPhraseCompletions(
                                previousWords = bufferState.previousWords,
                                prefix = bufferState.activePrefix
                            )
                        } else emptyList()
                    }

                    withContext(Dispatchers.Main) {
                        asyncPredictionsState.value = AsyncKeyboardPredictions(
                            gboardResult = gboard,
                            suggestions = suggestionsList,
                            aiPhraseCompletions = phrases
                        )
                    }
                }
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
        updatePreviousWord()

        // Capture new system clipboard content
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
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
        
        val isCursorInsideComposing = candidatesStart != -1 && candidatesEnd != -1 &&
                newSelStart >= candidatesStart && newSelStart <= candidatesEnd

        if (!isCursorInsideComposing || newSelStart != oldSelStart) {
            val expectedStep = lastCursorPosition + 1
            if (newSelStart != expectedStep || candidatesStart == -1) {
                if (currentTypedWord.value.isNotEmpty()) {
                    currentInputConnection?.finishComposingText()
                    currentTypedWord.value = ""
                    currentWordTapCoords.clear()
                }
            }
        }

        lastCursorPosition = newSelStart

        // Zero-lag optimization: Only execute synchronous Binder IPC when cursor moves outside
        // composing text or candidate state changes, never during smooth sequential character typing.
        if (candidatesStart == -1 || !isCursorInsideComposing) {
            updatePreviousWord()
        }
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
        val buffer = TextInputBufferState(
            typedWord = currentTypedWord.value,
            activePrefix = active,
            previousWord = previousWord.value,
            previousWords = previousWords.value.toList(),
            tapCoords = currentWordTapCoords.toList(),
            isUrl = isUrlField(),
            isEmail = isEmailField(),
            isSensitive = isSensitiveField(),
            timestamp = System.currentTimeMillis()
        )
        textBufferFlow.value = buffer
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
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
            com.example.ui.theme.MyApplicationTheme(
                darkTheme = settings.isDarkMode,
                dynamicColor = settings.dynamicThemeEnabled
            ) {
                KeyboardLayout(
                    context = this,
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
                            polishAndPresentVoiceResult(textToPolish)
                        }
                    },
                    onRejectVoicePolish = {
                        showVoicePolishPrompt.value = false
                        pendingVoiceTranscript.value = ""
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

    private fun formatGrammarCheckedText(word: String): CharSequence {
        return word
    }

    private fun getAutoCorrectedWord(prefix: String): String? {
        val lower = prefix.lowercase().trim()
        if (!settings.autocorrectEnabled || prefix.isEmpty() || isSensitiveField()) return null

        // 1. Check if background prediction is already computed and ready
        val asyncResult = asyncPredictionsState.value.gboardResult
        if (asyncResult.isCenterAutocorrecting && asyncResult.debugTelemetry?.rawInput?.lowercase() == lower) {
            return asyncResult.centerCandidate
        }

        // 2. Local On-Device Grammar Check
        val grammarCorrection = localPredictor.checkGrammarDetailed(
            word = lower,
            previousWords = previousWords.value,
            sentenceContext = ""
        )
        if (grammarCorrection != null) {
            return restoreCasing(prefix, grammarCorrection.correctedWord)
        }

        // 3. Fast Gboard Prediction & Autocorrection Engine
        val gboardResult = dictionaryManager.getGboardPredictions(
            rawTyped = prefix,
            contextWords = previousWords.value,
            tapCoords = currentWordTapCoords.toList(),
            isSensitiveField = isSensitiveField()
        )
        if (gboardResult.isCenterAutocorrecting) {
            return gboardResult.centerCandidate
        }

        return null
    }

    private fun commitWordWithSmartCorrection(
        ic: InputConnection,
        prefix: String,
        trailingText: String = ""
    ) {
        val lower = prefix.lowercase().trim()
        if (!settings.autocorrectEnabled || prefix.isEmpty() || isSensitiveField()) {
            ic.commitText(prefix + trailingText, 1)
            learnWordAndContext(prefix)
            return
        }

        // 1. Deep Local Grammar & Contextual Agreement Check
        val grammarCorrection = localPredictor.checkGrammarDetailed(
            word = lower,
            previousWords = previousWords.value,
            sentenceContext = ""
        )

        if (grammarCorrection != null) {
            val correctedText = restoreCasing(prefix, grammarCorrection.correctedWord)
            if (grammarCorrection.tokensToReplaceCount == 2) {
                // Retroactively replace previous word as well (e.g. "your welcome" -> "you're welcome", "a apple" -> "an apple", "could of" -> "could have")
                val prevWord = previousWords.value.lastOrNull() ?: ""
                val textBefore = ic.getTextBeforeCursor(prevWord.length + 5, 0) ?: ""
                var deleteLen = 0
                if (textBefore.endsWith(" ") && prevWord.isNotEmpty()) {
                    val prevMatchIndex = textBefore.trimEnd().lastIndexOf(prevWord, ignoreCase = true)
                    if (prevMatchIndex != -1) {
                        deleteLen = textBefore.length - prevMatchIndex
                    }
                }
                if (deleteLen > 0) {
                    ic.deleteSurroundingText(deleteLen, 0)
                }
                ic.commitText(correctedText + trailingText, 1)
                lastOriginalWord = if (prevWord.isNotEmpty()) "$prevWord $prefix" else prefix
                lastCorrectedWord = correctedText
                justAutocorrected = true
                lastCorrectedWasSpace = trailingText == " "
                learnWordAndContext(correctedText)
                return
            } else {
                ic.commitText(correctedText + trailingText, 1)
                lastOriginalWord = prefix
                lastCorrectedWord = correctedText
                justAutocorrected = true
                lastCorrectedWasSpace = trailingText == " "
                learnWordAndContext(correctedText)
                return
            }
        }

        // 2. Check asyncPredictionsState result first for 0ms latency
        val asyncResult = asyncPredictionsState.value.gboardResult
        if (asyncResult.isCenterAutocorrecting && asyncResult.debugTelemetry?.rawInput?.lowercase() == lower) {
            val corrected = asyncResult.centerCandidate
            ic.commitText(corrected + trailingText, 1)
            lastOriginalWord = prefix
            lastCorrectedWord = corrected
            justAutocorrected = true
            lastCorrectedWasSpace = trailingText == " "
            learnWordAndContext(corrected)
            return
        }

        // 3. Gboard Posterior Prediction & Autocorrection
        val gboardResult = dictionaryManager.getGboardPredictions(
            rawTyped = prefix,
            contextWords = previousWords.value,
            tapCoords = currentWordTapCoords.toList(),
            isSensitiveField = isSensitiveField()
        )

        if (gboardResult.isCenterAutocorrecting) {
            val corrected = gboardResult.centerCandidate
            ic.commitText(corrected + trailingText, 1)
            lastOriginalWord = prefix
            lastCorrectedWord = corrected
            justAutocorrected = true
            lastCorrectedWasSpace = trailingText == " "
            learnWordAndContext(corrected)
            return
        }

        // 4. Literal typed word
        ic.commitText(prefix + trailingText, 1)
        justAutocorrected = false
        lastCorrectedWasSpace = false
        learnWordAndContext(prefix)
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
        if (isAiPolishing.value) return
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
            if (char.isSurrogate() || (!char.isLetterOrDigit() && char != ',' && char != '.' && char != '!' && char != '?' && char != '@' && char != '#' && char != '$')) {
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
                    if (before == " ") {
                        ic.deleteSurroundingText(1, 0) // Collapse space
                    }
                }
                // Commit punctuation followed by auto-inserted trailing space!
                ic.commitText("$char ", 1)
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
            if (char.lowercaseChar() in 'a'..'z') {
                dictionaryManager.learnTapPattern(char, lastTapX, lastTapY)
            }

            val wasEmpty = currentTypedWord.value.isEmpty()
            if (wasEmpty) {
                val wordBefore = getWordBeforeCursor(ic)
                if (wordBefore.isNotEmpty()) {
                    ic.deleteSurroundingText(wordBefore.length, 0)
                    currentTypedWord.value = wordBefore
                } else {
                    ic.finishComposingText()
                }
            }

            currentTypedWord.value += letter
            currentWordTapCoords.add(PointF(lastTapX, lastTapY))
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
        if (isAiPolishing.value) return
        showVoicePolishPrompt.value = false
        playFeedback(FeedbackType.Delete)
        if (isVoiceTypingActive.value) {
            stopVoiceTyping(shouldPolish = false)
        }
        val ic = currentInputConnection ?: return
        
        // Backspace clears any outstanding AI suggestions
        aiRephraseSuggestions.clear()
        
        // Instant undo check: if backspace immediately follows an autocorrect event
        if (justAutocorrected && lastOriginalWord.isNotEmpty() && lastCorrectedWord.isNotEmpty()) {
            val len = lastCorrectedWord.length + (if (lastCorrectedWasSpace) 1 else 0)
            ic.deleteSurroundingText(len, 0)
            
            // Restore original typed text
            currentTypedWord.value = lastOriginalWord
            ic.setComposingText(formatGrammarCheckedText(currentTypedWord.value), 1)
            
            // Adaptively learn NOT to autocorrect this word again
            dictionaryManager.suppressCorrection(lastOriginalWord, lastCorrectedWord)
            
            justAutocorrected = false
            updatePreviousWord()
            return
        }
        
        justAutocorrected = false

        if (currentTypedWord.value.isEmpty()) {
            val wordBefore = getWordBeforeCursor(ic)
            if (wordBefore.isNotEmpty()) {
                ic.deleteSurroundingText(wordBefore.length, 0)
                currentTypedWord.value = wordBefore
            }
        }

        if (currentTypedWord.value.isNotEmpty()) {
            currentTypedWord.value = currentTypedWord.value.dropLast(1)
            if (currentWordTapCoords.isNotEmpty()) {
                currentWordTapCoords.removeAt(currentWordTapCoords.size - 1)
            }
            if (currentTypedWord.value.isNotEmpty()) {
                ic.setComposingText(formatGrammarCheckedText(currentTypedWord.value), 1)
            } else {
                currentWordTapCoords.clear()
                ic.setComposingText("", 1)
                ic.finishComposingText()
            }
        } else {
            val selected = ic.getSelectedText(0)
            if (selected.isNullOrEmpty()) {
                ic.deleteSurroundingText(1, 0)
            } else {
                ic.commitText("", 1)
            }
        }
        updatePreviousWord()
    }

    private fun handleDeleteWord() {
        if (isAiPolishing.value) return
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
        if (isAiPolishing.value) return
        playFeedback(FeedbackType.Space)
        if (isVoiceTypingActive.value) {
            stopVoiceTyping(shouldPolish = true)
        }
        val ic = currentInputConnection ?: return
        val prefix = currentTypedWord.value

        if (prefix.isNotEmpty()) {
            commitWordWithSmartCorrection(ic, prefix, " ")
        } else {
            // Double-space-to-period check
            val before = ic.getTextBeforeCursor(2, 0) ?: ""
            val shouldConvertToPeriod = (before == " ") || (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit())
            if (shouldConvertToPeriod) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
            } else {
                ic.commitText(" ", 1)
            }
            justAutocorrected = false
            lastCorrectedWasSpace = false
        }
        currentTypedWord.value = ""
        currentWordTapCoords.clear()
        updatePreviousWord()
    }

    private fun handleEnter() {
        if (isAiPolishing.value) return
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
        if (isAiPolishing.value) return
        playFeedback()
        val ic = currentInputConnection ?: return
        
        val rawTyped = currentTypedWord.value
        val isComposing = rawTyped.isNotEmpty() || (lastComposedStart != -1 && lastComposedEnd != -1)
        
        // If user selected their exact typed word (e.g. Left Slot), suppress auto-correction & save to local memory
        if (rawTyped.isNotEmpty() && word.lowercase() == rawTyped.lowercase()) {
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
            
            if (partBeforeLength > 0 || partAfterLength > 0) {
                ic.deleteSurroundingText(partBeforeLength, partAfterLength)
            }
            ic.commitText("$word ", 1)
        }
        
        learnWordAndContext(word)

        justAutocorrected = false
        currentTypedWord.value = ""
        currentWordTapCoords.clear()
        updatePreviousWord()
    }

    private fun learnWordAndContext(word: String) {
        if (!isSensitiveField() && word.isNotEmpty() && !word[0].isSurrogate()) {
            dictionaryManager.recordAcceptedWord(word)
            dictionaryManager.learnWord(word)
            val prevWords = previousWords.value
            val prev1 = prevWords.lastOrNull()
            val prev2 = if (prevWords.size >= 2) prevWords[prevWords.size - 2] else null
            val prev3 = if (prevWords.size >= 3) prevWords[prevWords.size - 3] else null
            if (!prev1.isNullOrEmpty()) {
                dictionaryManager.learnBigram(prev1, word)
                if (!prev2.isNullOrEmpty()) {
                    dictionaryManager.learnTrigram(prev2, prev1, word)
                    if (!prev3.isNullOrEmpty()) {
                        dictionaryManager.learnQuadgram(prev3, prev2, prev1, word)
                    }
                }
            }
        } else if (word.isNotEmpty()) {
            dictionaryManager.recordAcceptedWord(word)
        }
    }

    private fun launchSettingsActivity() {
        playFeedback()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    /**
     * Toggles WisprFlow style Voice Typing using offline android speech recognizer or gorgeous real simulation
     */
    private fun toggleVoiceTyping() {
        if (isVoiceTypingActive.value) {
            stopVoiceTyping(shouldPolish = true)
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

        voiceRecordingService.stopRecording(
            scope = serviceScope,
            shouldPolish = false,
            onFinalTranscript = { rawText ->
                val cleanRaw = rawText.trim()
                if (cleanRaw.isNotEmpty()) {
                    currentInputConnection?.commitText(cleanRaw, 1)
                    pendingVoiceTranscript.value = cleanRaw
                    showVoicePolishPrompt.value = true
                } else {
                    currentInputConnection?.finishComposingText()
                    voiceTranscript.value = ""
                }
            }
        )
    }

    /**
     * Polishes collected raw voice typing text using AI system (Gemini API / Local AI model)
     * removing fillers, stutters, and formatting into clean proper sentences.
     */
    private fun polishAndPresentVoiceResult(rawText: String) {
        val ic = currentInputConnection ?: return
        serviceScope.launch {
            isAiPolishing.value = true
            voiceTranscript.value = "AI Polishing..."

            val polishedText = try {
                aiPolishManager.proofreadText(rawText)
            } catch (e: Exception) {
                Log.e("TypeRight", "Voice AI polish error: ${e.message}")
                WhisperCppBrain.whisperCleanAndPolish(rawText)
            }

            val finalOutput = if (polishedText.isNotBlank()) polishedText.trim() else rawText.trim()

            if (rawText.isNotEmpty()) {
                ic.deleteSurroundingText(rawText.length, 0)
            }

            // Present the polished output word-by-word into the composing field
            val words = finalOutput.split(" ")
            val currentBuild = StringBuilder()
            for (i in words.indices) {
                if (i > 0) currentBuild.append(" ")
                currentBuild.append(words[i])
                ic.setComposingText(currentBuild.toString(), 1)
                delay(30)
            }

            ic.finishComposingText()
            isAiPolishing.value = false
            voiceTranscript.value = ""
            pendingVoiceTranscript.value = ""
            showVoicePolishPrompt.value = false
        }
    }

    /**
     * Executes AI Rephrase/Suggest improvements using local LLM
     */
    private fun commitRephraseSuggestion(suggestion: String) {
        playFeedback()
        val ic = currentInputConnection ?: return
        serviceScope.launch {
            val selectedText = ic.getSelectedText(0)?.toString()
            if (!selectedText.isNullOrEmpty()) {
                ic.commitText(suggestion, 1)
                // Deselect and place cursor at the end of replacement
                val et = ic.getExtractedText(ExtractedTextRequest(), 0)
                val len = et?.text?.length ?: 0
                ic.setSelection(len, len)
            } else {
                // Replace entire text
                val et = ic.getExtractedText(ExtractedTextRequest(), 0)
                val totalLen = et?.text?.length ?: 0
                ic.setSelection(0, totalLen)
                ic.commitText(suggestion, 1)
            }
            aiRephraseSuggestions.clear()
        }
    }

    private fun handleAiPolishButtonClick() {
        playFeedback()
        toggleAssistant()
    }

    private fun performDirectLocalProofread() {
        val ic = currentInputConnection ?: return
        if (isAiPolishing.value) return

        if (currentTypedWord.value.isNotEmpty()) {
            ic.finishComposingText()
            currentTypedWord.value = ""
            currentWordTapCoords.clear()
        }

        serviceScope.launch {
            val selectedText = ic.getSelectedText(0)?.toString()
            val textToProofread: String
            val isSelection: Boolean

            if (!selectedText.isNullOrEmpty()) {
                textToProofread = selectedText
                isSelection = true
            } else {
                val et = ic.getExtractedText(ExtractedTextRequest(), 0)
                val fullText = et?.text?.toString()
                val before = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
                val after = ic.getTextAfterCursor(2000, 0)?.toString() ?: ""
                
                textToProofread = when {
                    !fullText.isNullOrBlank() -> fullText
                    (before + after).isNotBlank() -> (before + after)
                    else -> ""
                }
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
                    AiPolishManager(this@TypeRightKeyboardService).proofreadText(textToProofread)
                }

                withContext(Dispatchers.Main) {
                    if (proofreadResult.isNotBlank() && proofreadResult != textToProofread) {
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
                        currentTypedWord.value = ""
                        wordUnderCursor.value = ""
                        updatePreviousWord()
                    } else {
                        android.widget.Toast.makeText(applicationContext, "Text is already proofread & well formatted!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("TypeRight", "Direct local proofread error: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    isAiPolishing.value = false
                }
            }
        }
    }

    /**
     * Executes AI Polish on-device to suggest professional, casual, or concise rewrites.
     */
    private fun performAiPolish() {
        playFeedback()
        val ic = currentInputConnection ?: return

        // Tier Check - Graceful fallback
        if (settings.supportTier == KeyboardSettings.TIER_3) {
            return
        }

        serviceScope.launch {
            val selectedText = ic.getSelectedText(0)?.toString()
            val textToPolish: String

            if (!selectedText.isNullOrEmpty()) {
                textToPolish = selectedText
            } else {
                val et = ic.getExtractedText(ExtractedTextRequest(), 0)
                val fullText = et?.text?.toString()
                if (!fullText.isNullOrBlank()) {
                    textToPolish = fullText
                } else {
                    val before = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
                    val after = ic.getTextAfterCursor(2000, 0)?.toString() ?: ""
                    textToPolish = (before + after).trim()
                }
            }

            if (textToPolish.isBlank()) {
                withContext(Dispatchers.Main) {
                    toggleAssistant()
                }
                return@launch
            }

            isAiPolishing.value = true
            aiRephraseSuggestions.clear()

            aiPolishManager.suggestImprovements(textToPolish)
                .catch { e ->
                    Log.e("TypeRight", "AI Polish error: ${e.message}")
                    isAiPolishing.value = false
                }
                .collect { suggestions ->
                    aiRephraseSuggestions.clear()
                    aiRephraseSuggestions.addAll(suggestions)
                    isAiPolishing.value = false
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vib.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(18)
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
    val pressAnimationSpec: AnimationSpec<Float>?
)

val LocalKeyboardStyle = staticCompositionLocalOf<KeyboardStyle> {
    error("No KeyboardStyle provided")
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
    Qwerty, Symbols, Emojis, Clipboard, Assistant
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
    onRejectVoicePolish: () -> Unit = {}
) {
    val sharedPrefs = remember { context.getSharedPreferences("typeright_prefs", Context.MODE_PRIVATE) }
    
    var themeState by remember { mutableStateOf(settings.theme) }
    var isDarkState by remember { mutableStateOf(settings.isDarkMode) }
    var dynamicThemeState by remember { mutableStateOf(settings.dynamicThemeEnabled) }
    var accentColorHexState by remember { mutableStateOf(settings.accentColor) }
    var keyboardHeightState by remember { mutableStateOf(settings.height) }
    var numberRowEnabledState by remember { mutableStateOf(settings.numberRowEnabled) }
    
    DisposableEffect(sharedPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KeyboardSettings.KEY_THEME -> themeState = settings.theme
                KeyboardSettings.KEY_DARK_MODE -> isDarkState = settings.isDarkMode
                KeyboardSettings.KEY_DYNAMIC_THEME_ENABLED -> dynamicThemeState = settings.dynamicThemeEnabled
                KeyboardSettings.KEY_ACCENT_COLOR -> accentColorHexState = settings.accentColor
                KeyboardSettings.KEY_HEIGHT -> keyboardHeightState = settings.height
                KeyboardSettings.KEY_NUMBER_ROW_ENABLED -> numberRowEnabledState = settings.numberRowEnabled
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val parsedAccentColor = remember(accentColorHexState) {
        try {
            Color(android.graphics.Color.parseColor(accentColorHexState))
        } catch (e: Exception) {
            Color(0xFF70C7C1)
        }
    }

    val style = remember(themeState, isDarkState, dynamicThemeState, parsedAccentColor) {
        val isDark = isDarkState
        val isDynamic = dynamicThemeState && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        val dynamicScheme = if (isDynamic) {
            com.example.ui.theme.getAppColorScheme(context, isDark, dynamicColor = true)
        } else null

        if (dynamicScheme != null) {
            // Material You Dynamic Palette (Adapts to system wallpaper & Material You tokens)
            val bg = dynamicScheme.surface
            val normalBg = dynamicScheme.surfaceVariant
            val specialBg = dynamicScheme.secondaryContainer
            val textColor = dynamicScheme.onSurface
            val enterBg = dynamicScheme.primary
            val enterTextColor = dynamicScheme.onPrimary
            val accent = dynamicScheme.primary
            val shape = RoundedCornerShape(7.dp)

            KeyboardStyle(
                theme = if (isDark) "Material You Dark" else "Material You Light",
                isDark = isDark,
                backgroundColor = bg,
                normalKeyBg = normalBg,
                specialKeyBg = specialBg,
                keyTextColor = textColor,
                accentColor = accent,
                enterKeyBg = enterBg,
                enterKeyTextColor = enterTextColor,
                keyShape = shape,
                keyBorder = null,
                showPressPopup = true,
                scaleOnPress = true,
                pressAnimationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
            )
        } else if (isDark) {
            // Modern Minimal Dark Theme
            val bg = Color(0xFF17181A)
            val normalBg = Color(0xFF2C2E33)
            val specialBg = Color(0xFF212326)
            val textColor = Color(0xFFF1F3F5)
            val enterBg = parsedAccentColor
            val enterTextColor = Color(0xFFFFFFFF)
            val shape = RoundedCornerShape(7.dp)

            KeyboardStyle(
                theme = KeyboardSettings.THEME_DARK,
                isDark = true,
                backgroundColor = bg,
                normalKeyBg = normalBg,
                specialKeyBg = specialBg,
                keyTextColor = textColor,
                accentColor = parsedAccentColor,
                enterKeyBg = enterBg,
                enterKeyTextColor = enterTextColor,
                keyShape = shape,
                keyBorder = null,
                showPressPopup = true,
                scaleOnPress = true,
                pressAnimationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
            )
        } else {
            // Modern Minimal Light Theme
            val bg = Color(0xFFECEFF2)
            val normalBg = Color(0xFFFFFFFF)
            val specialBg = Color(0xFFD6DBE0)
            val textColor = Color(0xFF1D2024)
            val enterBg = parsedAccentColor
            val enterTextColor = Color(0xFFFFFFFF)
            val shape = RoundedCornerShape(7.dp)

            KeyboardStyle(
                theme = KeyboardSettings.THEME_LIGHT,
                isDark = false,
                backgroundColor = bg,
                normalKeyBg = normalBg,
                specialKeyBg = specialBg,
                keyTextColor = textColor,
                accentColor = parsedAccentColor,
                enterKeyBg = enterBg,
                enterKeyTextColor = enterTextColor,
                keyShape = shape,
                keyBorder = null,
                showPressPopup = true,
                scaleOnPress = true,
                pressAnimationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
            )
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

    // Minimal and well-structured height calculation: less taller buttons, balanced key proportions
    val keysHeight = when {
        isLandscape -> (screenHeight * 0.44f).coerceIn(135f, 170f).dp
        keyboardHeightState == KeyboardSettings.HEIGHT_SHORT -> (screenHeight * 0.23f).coerceIn(175f, 195f).dp
        keyboardHeightState == KeyboardSettings.HEIGHT_TALL -> (screenHeight * 0.30f).coerceIn(225f, 250f).dp
        else -> (screenHeight * 0.265f).coerceIn(195f, 218f).dp
    }

    val navBarsInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val systemBottomPadding = if (navBarsInset > 0.dp) {
        navBarsInset.coerceAtLeast(if (isLandscape) 4.dp else 12.dp)
    } else {
        if (isLandscape) 4.dp else 16.dp
    }

    val activePrefix = if (currentTypedWord.isNotEmpty()) currentTypedWord else wordUnderCursor

    val service = context as? TypeRightKeyboardService
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

    // Lightweight immediate prefix completion fallback while debounced worker computes suggestions
    val instantFallback = remember(activePrefix) {
        if (activePrefix.isNotEmpty()) {
            dictionaryManager.findWordsWithPrefix(activePrefix, 3)
        } else emptyList<String>()
    }

    val suggestions = remember(asyncPredictions, activePrefix, instantFallback) {
        if (asyncPredictions.suggestions.isNotEmpty() && asyncPredictions.suggestions.any { it.isNotBlank() }) {
            asyncPredictions.suggestions
        } else if (instantFallback.isNotEmpty()) {
            instantFallback
        } else if (activePrefix.isNotEmpty()) {
            listOf(activePrefix, "", "")
        } else {
            listOf("", "", "")
        }
    }

    var isToolbarForceExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(currentTypedWord, wordUnderCursor) {
        if (currentTypedWord.isNotEmpty() || wordUnderCursor.isNotEmpty()) {
            isToolbarForceExpanded = false
        }
    }

    CompositionLocalProvider(LocalKeyboardStyle provides style) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(bottom = systemBottomPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 660.dp)
            ) {
        val toolbarHeight = if (aiRephraseSuggestions.isNotEmpty()) 46.dp else 38.dp
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
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = "Polish Prompt",
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Polish?",
                                        color = keyTextColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // YES BUTTON
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = accentColor,
                                        modifier = Modifier
                                            .clickable { onAcceptVoicePolish() }
                                            .testTag("voice_polish_yes_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "Yes",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // NO BUTTON
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = keyTextColor.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, keyTextColor.copy(alpha = 0.2f)),
                                        modifier = Modifier
                                            .clickable { onRejectVoicePolish() }
                                            .testTag("voice_polish_no_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "No",
                                                color = keyTextColor.copy(alpha = 0.85f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
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
                            AnimatedContent(
                                targetState = isVoiceTyping,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(180, easing = LinearOutSlowInEasing)) +
                                     scaleIn(initialScale = 0.95f, animationSpec = tween(180))) togetherWith
                                    (fadeOut(animationSpec = tween(140, easing = FastOutLinearInEasing)) +
                                     scaleOut(targetScale = 0.95f, animationSpec = tween(140)))
                                },
                                label = "voice_typing_toolbar_transition",
                                modifier = Modifier.fillMaxSize()
                            ) { voiceActive ->
                                if (voiceActive) {
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
                                } else {
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
                                            // SUGGESTIONS MODE inside toolbar
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        isToolbarForceExpanded = true
                                                    },
                                                    modifier = Modifier.size(36.dp).testTag("expand_toolbar_options_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ChevronRight,
                                                        contentDescription = "Toolbar options",
                                                        tint = keyTextColor.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(24.dp)
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
                                                                .clip(RoundedCornerShape(20.dp))
                                                                .background(chipBgColor)
                                                                .border(
                                                                    border = androidx.compose.foundation.BorderStroke(
                                                                        width = if (isCorrectionActive) 1.5.dp else 0.5.dp,
                                                                        color = chipBorderColor
                                                                    ),
                                                                    shape = RoundedCornerShape(20.dp)
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
                                                                        textAlign = TextAlign.Center,
                                                                        maxLines = 1
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                
                                                IconButton(
                                                    onClick = onVoiceTypingToggle,
                                                    modifier = Modifier.size(36.dp).testTag("mic_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Mic,
                                                        contentDescription = "Voice dictation",
                                                        tint = keyTextColor.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(20.dp)
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
                                                        .size(34.dp)
                                                        .testTag("collapse_toolbar_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "Back to suggestions",
                                                        tint = keyTextColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

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
                                                        contentDescription = "Voice dictation",
                                                        tint = if (isVoiceTyping) accentColor else keyTextColor.copy(alpha = 0.85f),
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
                                                        imageVector = Icons.Default.AutoFixHigh,
                                                        contentDescription = "AI Polish Options",
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

        HorizontalDivider(color = keyTextColor.copy(alpha = 0.12f), thickness = 1.dp)

        // --- KEYBOARD KEYS CONTAINER ---
        val currentLayer = when {
            isAssistant -> KeyboardLayer.Assistant
            isClipboard -> KeyboardLayer.Clipboard
            isEmojis -> KeyboardLayer.Emojis
            isSymbols -> KeyboardLayer.Symbols
            else -> KeyboardLayer.Qwerty
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(effectiveKeysHeight)
                .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 4.dp)
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    when (layer) {
                        KeyboardLayer.Assistant -> {
                            AiAssistantPanel(
                                initialMode = currentAiMode,
                                keyTextColor = keyTextColor,
                                accentColor = accentColor,
                                keyColor = normalKeyBg,
                                onApplyText = { appliedText, isSelection ->
                                    val ic = (context as? TypeRightKeyboardService)?.currentInputConnection
                                    if (ic != null) {
                                        if (isSelection) {
                                            ic.commitText(appliedText, 1)
                                        } else {
                                            val before = ic.getTextBeforeCursor(1000, 0)?.toString() ?: ""
                                            val after = ic.getTextAfterCursor(1000, 0)?.toString() ?: ""
                                            if (before.isNotEmpty() || after.isNotEmpty()) {
                                                ic.deleteSurroundingText(before.length, after.length)
                                            }
                                            ic.commitText(appliedText, 1)
                                        }
                                    }
                                    onAssistantToggle()
                                },
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
                                onSpaceSwipeRight = onSpaceSwipeRight
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
                                onSpaceSwipeRight = onSpaceSwipeRight
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
    showNumberRow: Boolean = false
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
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
        if (showNumberRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.85f),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
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
            horizontalArrangement = Arrangement.spacedBy(3.dp)
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
                    onKeyClick(char.toString())
                }
            }
        }

        // Row 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
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
                    onKeyClick(char.toString())
                }
            }
            Spacer(modifier = Modifier.weight(0.5f))
        }

        // Row 3
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shift Key
            val shiftIconColor = if (isCapsLock) accentColor else if (isShift) accentColor.copy(alpha = 0.8f) else textColor
            IconButtonKey(
                icon = Icons.Default.ArrowUpward,
                modifier = Modifier.weight(1.5f),
                keyBg = specialKeyBg,
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
                    onKeyClick(char.toString())
                }
            }

            // Backspace key
            IconButtonKey(
                icon = Icons.Default.Backspace,
                modifier = Modifier
                    .weight(1.5f)
                    .testTag("delete_key"),
                keyBg = specialKeyBg,
                tint = textColor,
                onClick = onDelete,
                onHold = onDeleteWord
            )
        }

        // Row 4 (Image 3: ?123, comma with smiley, globe, English space, period, teal enter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. ?123
            KeyButton(
                text = "?123",
                modifier = Modifier.weight(1.3f),
                keyBg = specialKeyBg,
                textColor = textColor
            ) {
                onSymbolsToggle()
            }

            // 2. Comma
            KeyButton(
                text = ",",
                modifier = Modifier.weight(1.0f),
                keyBg = specialKeyBg,
                textColor = textColor
            ) {
                onKeyClick(",")
            }

            // 3. Emoji button (next to space bar)
            IconButtonKey(
                icon = Icons.Default.Mood,
                modifier = Modifier
                    .weight(1.0f)
                    .testTag("bottom_emoji_button"),
                keyBg = specialKeyBg,
                tint = textColor,
                onClick = onEmojiToggle
            )

            // 4. English Spacebar
            KeyButton(
                text = "English",
                modifier = Modifier
                    .weight(4.5f)
                    .testTag("space_key"),
                keyBg = keyColor,
                textColor = textColor.copy(alpha = 0.6f),
                onSwipeLeft = onSpaceSwipeLeft,
                onSwipeRight = onSpaceSwipeRight
            ) {
                onSpaceClick()
            }

            // 5. Period "." Key
            KeyButton(
                text = ".",
                modifier = Modifier.weight(1.0f),
                keyBg = specialKeyBg,
                textColor = textColor
            ) {
                onKeyClick(".")
            }

            // 6. Enter key (Adaptive action pill with white icon)
            IconButtonKey(
                icon = enterIcon,
                modifier = Modifier
                    .weight(1.5f)
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
    onSpaceSwipeRight: (() -> Unit)? = null
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Row 1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
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
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            activeRow2.forEach { char ->
                KeyButton(text = char, modifier = Modifier.weight(1.0f), keyBg = keyColor, textColor = textColor) {
                    onKeyClick(char)
                }
            }
        }

        // Row 3 (Image 2: =< on left, 7 symbols in middle, Backspace on right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(
                text = if (isSecondarySymbols) "12\n34" else "=\\<",
                modifier = Modifier.weight(1.3f),
                keyBg = specialKeyBg,
                textColor = textColor
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
                onHold = onDeleteWord
            )
        }

        // Row 4 (Image 2: ABC, comma, 12/34, English Spacebar, dot, Teal Action Arrow)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(text = "ABC", modifier = Modifier.weight(1.4f), keyBg = specialKeyBg, textColor = textColor) {
                onSymbolsToggle()
            }

            KeyButton(text = ",", modifier = Modifier.weight(1.0f), keyBg = specialKeyBg, textColor = textColor) {
                onKeyClick(",")
            }

            KeyButton(
                text = "12\n34",
                modifier = Modifier.weight(1.1f),
                keyBg = specialKeyBg,
                textColor = textColor
            ) {
                isSecondarySymbols = !isSecondarySymbols
            }

            KeyButton(
                text = "English",
                modifier = Modifier.weight(5.0f),
                keyBg = keyColor,
                textColor = textColor.copy(alpha = 0.6f),
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
                    .weight(1.5f)
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
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val effectiveKeyBg = if (isPressed) keyBg.copy(alpha = 0.75f) else keyBg
    val effectiveTextColor = if (isPressed) textColor.copy(alpha = 0.85f) else textColor

    val currentShape = style.keyShape
    val currentBorder = style.keyBorder

    val density = androidx.compose.ui.platform.LocalDensity.current
    val swipeThresholdPx = with(density) { 14.dp.toPx() }

    val baseModifier = modifier
        .padding(vertical = 1.5.dp, horizontal = 0.5.dp)
        .fillMaxHeight()
        .graphicsLayer {
            if (isPressed) {
                scaleX = 0.95f
                scaleY = 0.95f
            }
        }
        .shadow(
            elevation = if (keyBg != Color.Transparent) 0.75.dp else 0.dp,
            shape = currentShape,
            clip = false
        )
        .clip(currentShape)
        .background(effectiveKeyBg)
        .run {
            val subtleBorder = currentBorder ?: if (keyBg != Color.Transparent) {
                BorderStroke(0.5.dp, if (style.isDark) Color(0x18FFFFFF) else Color(0x12000000))
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
                offset = androidx.compose.ui.unit.IntOffset(0, -140)
            ) {
                Box(
                    modifier = Modifier
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp), clip = false)
                        .background(
                            if (style.isDark) Color(0xFF2E2E2E) else Color(0xFFFFFFFF),
                            RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, if (style.isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text,
                        color = if (style.isDark) Color.White else Color.Black,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (secondaryText != null && text.length == 1) {
            Text(
                text = secondaryText,
                color = effectiveTextColor.copy(alpha = 0.38f),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.5.dp, end = 3.dp)
            )
        }

        val isMultiChar = text.length > 1
        Text(
            text = text,
            color = effectiveTextColor,
            fontSize = if (isMultiChar) 12.5.sp else 18.sp,
            fontWeight = if (isMultiChar) FontWeight.Medium else FontWeight.Normal,
            fontFamily = FontFamily.SansSerif,
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
    onHold: (() -> Unit)? = null
) {
    val style = LocalKeyboardStyle.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val effectiveKeyBg = if (isPressed) keyBg.copy(alpha = 0.75f) else keyBg
    val effectiveTint = if (isPressed) tint.copy(alpha = 0.85f) else tint

    val currentShape = style.keyShape
    val currentBorder = style.keyBorder

    val baseModifier = modifier
        .padding(vertical = 1.5.dp, horizontal = 0.5.dp)
        .fillMaxHeight()
        .graphicsLayer {
            if (isPressed) {
                scaleX = 0.95f
                scaleY = 0.95f
            }
        }
        .shadow(
            elevation = if (keyBg != Color.Transparent) 0.75.dp else 0.dp,
            shape = currentShape,
            clip = false
        )
        .clip(currentShape)
        .background(effectiveKeyBg)
        .run {
            val subtleBorder = currentBorder ?: if (keyBg != Color.Transparent) {
                BorderStroke(0.5.dp, if (style.isDark) Color(0x18FFFFFF) else Color(0x12000000))
            } else null

            if (subtleBorder != null && keyBg != Color.Transparent) {
                this.border(subtleBorder, currentShape)
            } else {
                this
            }
        }

    val finalModifier = if (onHold != null) {
        baseModifier.repeatingClickable(
            interactionSource = interactionSource,
            onClick = onClick,
            onHold = onHold
        )
    } else {
        baseModifier.clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current
        ) { onClick() }
    }

    Box(
        modifier = finalModifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = effectiveTint,
            modifier = Modifier.size(19.dp)
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
    val ic = (context as? TypeRightKeyboardService)?.currentInputConnection

    // Fetch the text to process: either selected text or the entire text field content.
    var originalText by remember { mutableStateOf("") }
    var isSelectionActive by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(initialMode) }
    var generatedText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(initialMode) {
        selectedMode = if (initialMode == "proofread") "formalize" else initialMode
        val selected = ic?.getSelectedText(0)?.toString()
        if (!selected.isNullOrEmpty()) {
            originalText = selected
            isSelectionActive = true
        } else {
            val et = ic?.getExtractedText(ExtractedTextRequest(), 0)
            val fullText = et?.text?.toString()
            if (!fullText.isNullOrEmpty()) {
                originalText = fullText
            } else {
                val beforeCursor = ic?.getTextBeforeCursor(500, 0)?.toString() ?: ""
                val afterCursor = ic?.getTextAfterCursor(500, 0)?.toString() ?: ""
                originalText = (beforeCursor + afterCursor).trim()
            }
            isSelectionActive = false
        }

        if (originalText.isNotEmpty()) {
            isLoading = true
            try {
                val result = AiPolishManager(context).polishText(originalText, selectedMode)
                generatedText = result
            } catch (e: Exception) {
                generatedText = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    val modes = listOf(
        "formalize" to "👔 Formal",
        "casual" to "😊 Casual",
        "rephrase" to "🔄 Rephrase",
        "shorten" to "⚡ Shorten",
        "expand" to "📝 Expand"
    )

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
                                isLoading = true
                                coroutineScope.launch {
                                    try {
                                        val result = AiPolishManager(context).polishText(originalText, modeId)
                                        generatedText = result
                                    } catch (e: Exception) {
                                        generatedText = "Error: ${e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
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
                        onApplyText(generatedText, isSelectionActive)
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
