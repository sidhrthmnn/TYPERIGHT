package com.example

import android.content.Context
import android.util.Log
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorSnapshot(
    val sessionId: Long,
    val originalText: String,
    val startOffset: Int,
    val endOffset: Int,
    val cursorPosition: Int,
    val textContext: TextContext? = null
)

data class UndoSnapshot(
    val sessionId: Long,
    val previousText: String,
    val replacementText: String,
    val startOffset: Int,
    val endOffset: Int
)

data class PolishResult(
    val text: String,
    val originalText: String,
    val modelLabel: String,
    val backend: String,
    val mode: PolishMode,
    val durationMs: Long,
    val isSuccess: Boolean,
    val isValidated: Boolean,
    val isChanged: Boolean,
    val errorMessage: String? = null
)

sealed interface PolishUiState {
    object Idle : PolishUiState
    object TermsRequired : PolishUiState
    object ModelNotDownloaded : PolishUiState
    data class PreparingModel(val backend: String) : PolishUiState
    data class Generating(val mode: PolishMode) : PolishUiState
    data class Streaming(val partialText: String, val mode: PolishMode, val sessionId: Long) : PolishUiState
    data class Ready(val result: PolishResult, val undoSnapshot: UndoSnapshot?) : PolishUiState
    data class Error(val message: String, val isRecoverable: Boolean = true) : PolishUiState
}

/**
 * Orchestrates on-device AI Polish requests, coordinates with the keyboard editor,
 * ensures truthful labeling ("Local GGUF · selected model" vs "Basic offline correction"),
 * performs output validation, and manages atomic Apply/Undo.
 */
class PolishCoordinator(
    private val context: Context,
    private val basicPredictor: LocalGrammarSpellPredictor = LocalGrammarSpellPredictor(context)
) {
    companion object {
        private const val TAG = "PolishCoordinator"
        const val LABEL_OFFLINE_MODEL = LocalGgufModel.LABEL
        const val LABEL_BASIC_OFFLINE = "Basic offline correction"

        @Volatile
        private var instance: PolishCoordinator? = null

        fun cancelIfCreated() { instance?.cancelCurrent() }

        fun getInstance(context: Context): PolishCoordinator {
            return instance ?: synchronized(this) {
                instance ?: PolishCoordinator(context.applicationContext).also { instance = it }
            }
        }
    }

    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var polishJob: Job? = null
    private var currentSessionId: Long = -1L

    private val _uiState = MutableStateFlow<PolishUiState>(PolishUiState.Idle)
    val uiState: StateFlow<PolishUiState> = _uiState.asStateFlow()

    private var lastUndoSnapshot: UndoSnapshot? = null

    fun resetForSession(sessionId: Long = -1L) {
        polishJob?.cancel()
        polishJob = null
        currentSessionId = sessionId
        _uiState.value = PolishUiState.Idle
    }

    fun cancelCurrent() {
        polishJob?.cancel()
        polishJob = null
        currentSessionId = -1L
        _uiState.value = PolishUiState.Idle
    }

    fun triggerPolish(
        snapshot: EditorSnapshot,
        mode: PolishMode,
        forceBasicOffline: Boolean = false
    ) {
        val originalText = snapshot.originalText.trim()
        if (originalText.isEmpty()) {
            _uiState.value = PolishUiState.Idle
            return
        }

        polishJob?.cancel()
        currentSessionId = snapshot.sessionId
        val thisSessionId = snapshot.sessionId

        polishJob = coordinatorScope.launch {
            val startTime = System.currentTimeMillis()
            _uiState.value = PolishUiState.Generating(mode)

            try {
                var lastStreamedText: String? = null
                var isFromModel = false
                val selectedEngine = AiPolishBackend.engine
                val hasCloud = AiPolishBackend.isCloudActive

                if (!forceBasicOffline && selectedEngine == ActiveAiEngine.OFFLINE) {
                    if (!LocalGgufModel.termsAccepted(context)) {
                        _uiState.value = PolishUiState.TermsRequired
                        return@launch
                    }
                    if (!LocalGgufModel.isReady(context) && !hasCloud) {
                        _uiState.value = PolishUiState.ModelNotDownloaded
                        return@launch
                    }
                }

                if (!forceBasicOffline) {
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(AiPolishBackend.timeoutMillis) {
                            AiPolishBackend.streamPolish(originalText, mode, snapshot.textContext).collect { chunk ->
                                if (currentSessionId == thisSessionId && chunk.isNotBlank()) {
                                    lastStreamedText = chunk
                                    isFromModel = true
                                    _uiState.value = PolishUiState.Streaming(chunk, mode, thisSessionId)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Polish streaming error: ${e.message}")
                    }
                }

                if (currentSessionId != thisSessionId) return@launch

                val candidateText = if (isFromModel && !lastStreamedText.isNullOrBlank()) {
                    AiOutputValidator.sanitize(lastStreamedText!!, originalText)
                } else {
                    withContext(Dispatchers.Default) {
                        try {
                            val tone = when (mode) {
                                PolishMode.AUTO_FORMAT -> "auto_format"
                                PolishMode.PROFESSIONAL -> "professional"
                                PolishMode.CASUAL -> "casual"
                                PolishMode.SHORTEN -> "concise"
                                PolishMode.EXPAND -> "eloquent"
                                PolishMode.REPHRASE -> "eloquent"
                                else -> "proofread"
                            }
                            val localResult = if (tone == "auto_format") {
                                OnDeviceNeuralPolishEngine.getInstance(context).autoFormatAndCorrect(originalText)
                            } else if (tone == "proofread") {
                                OnDeviceNeuralPolishEngine.getInstance(context).quickProofread(originalText)
                            } else {
                                OnDeviceNeuralPolishEngine.getInstance(context).polish(originalText, tone).polishedText
                            }
                            if (localResult == originalText) {
                                basicPredictor.polishSentenceLocally(originalText)
                            } else {
                                localResult
                            }
                        } catch (e: Exception) {
                            basicPredictor.polishSentenceLocally(originalText)
                        }
                    }
                }

                if (currentSessionId != thisSessionId) return@launch

                val isValid = AiOutputValidator.isValid(originalText, candidateText, mode)
                val finalText = if (candidateText.isNotBlank() && isValid) {
                    candidateText
                } else {
                    withContext(Dispatchers.Default) {
                        OnDeviceNeuralPolishEngine.getInstance(context).quickProofread(originalText)
                    }
                }

                if (currentSessionId != thisSessionId) return@launch

                // If not streamed from Gemini, stream the on-device result word-by-word for responsive UI
                if (!isFromModel && finalText.isNotBlank() && finalText != originalText) {
                    val words = finalText.split(Regex("\\s+"))
                    val streamSb = StringBuilder()
                    for (i in words.indices) {
                        if (currentSessionId != thisSessionId) return@launch
                        if (i > 0) streamSb.append(" ")
                        streamSb.append(words[i])
                        _uiState.value = PolishUiState.Streaming(streamSb.toString(), mode, thisSessionId)
                        kotlinx.coroutines.delay(20L)
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                val isChanged = finalText.trim() != originalText.trim()

                val undo = UndoSnapshot(
                    sessionId = snapshot.sessionId,
                    previousText = originalText,
                    replacementText = finalText,
                    startOffset = snapshot.startOffset,
                    endOffset = snapshot.endOffset
                )
                lastUndoSnapshot = undo

                val activeLabel = if (isFromModel) AiPolishBackend.label else LABEL_BASIC_OFFLINE
                val activeBackend = if (isFromModel) (if (AiPolishBackend.isCloudActive) "Gemini Cloud" else selectedEngine.shortLabel) else "Local rules"

                _uiState.value = PolishUiState.Ready(
                    result = PolishResult(
                        text = finalText,
                        originalText = originalText,
                        modelLabel = activeLabel,
                        backend = activeBackend,
                        mode = mode,
                        durationMs = duration,
                        isSuccess = true,
                        isValidated = isValid,
                        isChanged = isChanged
                    ),
                    undoSnapshot = undo
                )

                AiExecutionLogger.logAiAction(
                    context = context,
                    operation = "Polish (${mode.name})",
                    engine = activeLabel,
                    input = originalText,
                    output = finalText,
                    durationMs = duration
                )
            } catch (e: Exception) {
                if (e is CancellationException) {
                    if (currentSessionId == thisSessionId) {
                        _uiState.value = PolishUiState.Idle
                    }
                    return@launch
                }
                Log.e(TAG, "Error during Polish execution", e)
                if (currentSessionId == thisSessionId) {
                    _uiState.value = PolishUiState.Error(e.message ?: "Polish failed")
                }
            }
        }
    }

    /**
     * Applies polished text atomically to the editor using InputConnection batch edit.
     */
    fun applyResult(
        inputConnection: InputConnection?,
        snapshot: EditorSnapshot,
        newText: String
    ): Boolean {
        if (inputConnection == null) return false
        return try {
            inputConnection.beginBatchEdit()
            val replaceLength = snapshot.endOffset - snapshot.startOffset
            if (replaceLength > 0) {
                inputConnection.setSelection(snapshot.startOffset, snapshot.endOffset)
                inputConnection.commitText(newText, 1)
            } else {
                inputConnection.commitText(newText, 1)
            }
            inputConnection.endBatchEdit()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply text to editor", e)
            try { inputConnection.endBatchEdit() } catch (_: Exception) {}
            false
        }
    }

    /**
     * Reverts text to previous text using UndoSnapshot.
     */
    fun undo(inputConnection: InputConnection?): Boolean {
        val undo = lastUndoSnapshot ?: return false
        if (inputConnection == null) return false

        return try {
            inputConnection.beginBatchEdit()
            val newEnd = undo.startOffset + undo.replacementText.length
            inputConnection.setSelection(undo.startOffset, newEnd)
            inputConnection.commitText(undo.previousText, 1)
            inputConnection.endBatchEdit()
            lastUndoSnapshot = null
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to undo text modification", e)
            try { inputConnection.endBatchEdit() } catch (_: Exception) {}
            false
        }
    }
}
