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
    val cursorPosition: Int
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
    object ModelNotDownloaded : PolishUiState
    data class PreparingModel(val backend: String) : PolishUiState
    data class Generating(val mode: PolishMode) : PolishUiState
    data class Ready(val result: PolishResult, val undoSnapshot: UndoSnapshot?) : PolishUiState
    data class Error(val message: String, val isRecoverable: Boolean = true) : PolishUiState
}

/**
 * Orchestrates on-device AI Polish requests, coordinates with the keyboard editor,
 * ensures truthful labeling ("Offline AI — Qwen3 1.7B" vs "Basic offline correction"),
 * performs output validation, and manages atomic Apply/Undo.
 */
class PolishCoordinator(
    private val context: Context,
    private val engine: ILiteRtPolishEngine = LiteRtPolishEngine.getInstance(context),
    private val repository: ModelRepository = ModelRepository.getInstance(context),
    private val basicPredictor: LocalGrammarSpellPredictor = LocalGrammarSpellPredictor(context)
) {
    companion object {
        private const val TAG = "PolishCoordinator"
        const val LABEL_OFFLINE_QWEN = "Offline AI — Qwen3 1.7B"
        const val LABEL_BASIC_OFFLINE = "Basic offline correction"

        @Volatile
        private var instance: PolishCoordinator? = null

        fun getInstance(context: Context): PolishCoordinator {
            return instance ?: synchronized(this) {
                instance ?: PolishCoordinator(context.applicationContext).also { instance = it }
            }
        }
    }

    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var polishJob: Job? = null

    private val _uiState = MutableStateFlow<PolishUiState>(PolishUiState.Idle)
    val uiState: StateFlow<PolishUiState> = _uiState.asStateFlow()

    private var lastUndoSnapshot: UndoSnapshot? = null

    fun cancelCurrent() {
        polishJob?.cancel()
        polishJob = null
        _uiState.value = PolishUiState.Idle
    }

    fun triggerPolish(
        snapshot: EditorSnapshot,
        mode: PolishMode,
        preferredBackend: ModelBackend = ModelBackend.AUTO,
        forceBasicOffline: Boolean = false
    ) {
        val originalText = snapshot.originalText.trim()
        if (originalText.isEmpty()) {
            _uiState.value = PolishUiState.Idle
            return
        }

        polishJob?.cancel()
        polishJob = coordinatorScope.launch {
            val startTime = System.currentTimeMillis()

            // Route 1: Forced or fallback to basic offline correction
            if (forceBasicOffline || !repository.isModelInstalled()) {
                if (!repository.isModelInstalled() && !forceBasicOffline) {
                    _uiState.value = PolishUiState.ModelNotDownloaded
                }

                val corrected = withContext(Dispatchers.Default) {
                    basicPredictor.polishSentenceLocally(originalText)
                }
                val duration = System.currentTimeMillis() - startTime
                val isChanged = corrected != originalText

                val undo = UndoSnapshot(
                    sessionId = snapshot.sessionId,
                    previousText = originalText,
                    replacementText = corrected,
                    startOffset = snapshot.startOffset,
                    endOffset = snapshot.endOffset
                )
                lastUndoSnapshot = undo

                _uiState.value = PolishUiState.Ready(
                    result = PolishResult(
                        text = corrected,
                        originalText = originalText,
                        modelLabel = LABEL_BASIC_OFFLINE,
                        backend = "Deterministic Engine",
                        mode = mode,
                        durationMs = duration,
                        isSuccess = true,
                        isValidated = true,
                        isChanged = isChanged
                    ),
                    undoSnapshot = undo
                )

                AiExecutionLogger.logAiAction(
                    context = context,
                    operation = "Polish (${mode.name})",
                    engine = LABEL_BASIC_OFFLINE,
                    input = originalText,
                    output = corrected,
                    durationMs = duration
                )
                return@launch
            }

            // Route 2: On-device LiteRT-LM Qwen3 1.7B
            try {
                _uiState.value = PolishUiState.PreparingModel(preferredBackend.name)
                _uiState.value = PolishUiState.Generating(mode)

                val engineResult = engine.generatePolish(originalText, mode, preferredBackend)

                if (!engineResult.isSuccess) {
                    Log.w(TAG, "LiteRT-LM inference unsuccessful: ${engineResult.errorMessage}. Falling back to basic offline.")
                    val fallbackText = withContext(Dispatchers.Default) {
                        basicPredictor.polishSentenceLocally(originalText)
                    }
                    val duration = System.currentTimeMillis() - startTime
                    val undo = UndoSnapshot(
                        sessionId = snapshot.sessionId,
                        previousText = originalText,
                        replacementText = fallbackText,
                        startOffset = snapshot.startOffset,
                        endOffset = snapshot.endOffset
                    )
                    lastUndoSnapshot = undo

                    _uiState.value = PolishUiState.Ready(
                        result = PolishResult(
                            text = fallbackText,
                            originalText = originalText,
                            modelLabel = "$LABEL_BASIC_OFFLINE (Fallback)",
                            backend = "Fallback",
                            mode = mode,
                            durationMs = duration,
                            isSuccess = true,
                            isValidated = true,
                            isChanged = fallbackText != originalText,
                            errorMessage = engineResult.errorMessage
                        ),
                        undoSnapshot = undo
                    )
                    return@launch
                }

                // Output validation
                val candidate = engineResult.text
                val isValid = AiOutputValidator.isValid(originalText, candidate, mode)
                val finalText = if (isValid) candidate else {
                    Log.w(TAG, "Candidate failed AI validation. Using basic offline correction.")
                    basicPredictor.polishSentenceLocally(originalText)
                }

                val duration = System.currentTimeMillis() - startTime
                val isChanged = finalText != originalText

                val undo = UndoSnapshot(
                    sessionId = snapshot.sessionId,
                    previousText = originalText,
                    replacementText = finalText,
                    startOffset = snapshot.startOffset,
                    endOffset = snapshot.endOffset
                )
                lastUndoSnapshot = undo

                val activeLabel = if (isValid) LABEL_OFFLINE_QWEN else "$LABEL_BASIC_OFFLINE (Validation fallback)"
                val activeBackend = if (isValid) engineResult.backendName else "Rule Engine"

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
                    _uiState.value = PolishUiState.Idle
                    return@launch
                }
                Log.e(TAG, "Error during Polish execution", e)
                _uiState.value = PolishUiState.Error(e.message ?: "Polish failed")
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
