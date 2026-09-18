package com.example

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class ModelBackend {
    AUTO,
    GPU,
    CPU
}

data class EngineInferenceResult(
    val text: String,
    val modelId: String,
    val backendName: String,
    val durationMs: Long,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

/**
 * Low-level interface for local LLM text polishing.
 * Abstracted so test suites can run deterministic JVM tests with FakeLiteRtPolishEngine
 * while production uses the real LiteRT-LM runtime library.
 */
interface ILiteRtPolishEngine : AutoCloseable {
    fun isModelReady(): Boolean
    fun getActiveBackend(): String
    suspend fun generatePolish(
        inputText: String,
        mode: PolishMode,
        preferredBackend: ModelBackend = ModelBackend.AUTO
    ): EngineInferenceResult
}

/**
 * Production implementation of LiteRT-LM text polishing engine using Qwen3-1.7B INT4.
 * Safely serializes inference, handles GPU-to-CPU fallback, and closes conversations per request.
 */
class LiteRtPolishEngine(
    private val context: Context,
    private val modelRepository: ModelRepository = ModelRepository.getInstance(context)
) : ILiteRtPolishEngine {

    companion object {
        private const val TAG = "LiteRtPolishEngine"

        @Volatile
        private var instance: LiteRtPolishEngine? = null

        fun getInstance(context: Context): LiteRtPolishEngine {
            return instance ?: synchronized(this) {
                instance ?: LiteRtPolishEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private var engine: Engine? = null
    private var activeBackendName: String = "None"
    private var initializedModelPath: String? = null
    private val inferenceMutex = Mutex()

    override fun isModelReady(): Boolean {
        return modelRepository.isModelInstalled()
    }

    override fun getActiveBackend(): String = activeBackendName

    private suspend fun ensureEngineInitialized(preferredBackend: ModelBackend): Engine = withContext(Dispatchers.IO) {
        val modelFile = modelRepository.getModelFile()
        if (!modelFile.exists()) {
            throw IllegalStateException("Model not downloaded (expected at ${modelFile.name})")
        }

        val currentEngine = engine
        if (currentEngine != null && currentEngine.isInitialized() && initializedModelPath == modelFile.absolutePath) {
            return@withContext currentEngine
        }

        // Close previous if any
        try {
            currentEngine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing previous engine instance", e)
        }
        engine = null
        modelRepository.isModelLocked.set(true)

        val cacheDir = File(context.cacheDir, "litert_cache").apply { mkdirs() }

        when (preferredBackend) {
            ModelBackend.GPU -> {
                try {
                    Log.i(TAG, "Initializing LiteRT-LM with GPU backend...")
                    val gpuBackend = Backend.GPU()
                    val config = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = gpuBackend,
                        maxNumTokens = 512,
                        cacheDir = cacheDir.absolutePath
                    )
                    val newEngine = Engine(config)
                    newEngine.initialize()
                    engine = newEngine
                    activeBackendName = "GPU"
                    initializedModelPath = modelFile.absolutePath
                    Log.i(TAG, "LiteRT-LM GPU initialized successfully.")
                    return@withContext newEngine
                } catch (e: Throwable) {
                    Log.w(TAG, "GPU backend failed to initialize, falling back to CPU", e)
                    return@withContext initializeCpuBackend(modelFile, cacheDir)
                }
            }
            ModelBackend.CPU -> {
                return@withContext initializeCpuBackend(modelFile, cacheDir)
            }
            ModelBackend.AUTO -> {
                // Try GPU first; if unsupported/fails, fall back cleanly to CPU
                try {
                    Log.i(TAG, "Attempting GPU initialization (Auto mode)...")
                    val gpuBackend = Backend.GPU()
                    val config = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = gpuBackend,
                        maxNumTokens = 512,
                        cacheDir = cacheDir.absolutePath
                    )
                    val newEngine = Engine(config)
                    newEngine.initialize()
                    engine = newEngine
                    activeBackendName = "GPU"
                    initializedModelPath = modelFile.absolutePath
                    Log.i(TAG, "LiteRT-LM GPU initialized successfully in Auto mode.")
                    return@withContext newEngine
                } catch (e: Throwable) {
                    Log.i(TAG, "GPU unavailable or initialization failed, defaulting to CPU: ${e.message}")
                    return@withContext initializeCpuBackend(modelFile, cacheDir)
                }
            }
        }
    }

    private fun initializeCpuBackend(modelFile: File, cacheDir: File): Engine {
        Log.i(TAG, "Initializing LiteRT-LM with CPU backend (4 threads)...")
        val cpuBackend = Backend.CPU(threadCount = 4)
        val config = EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = cpuBackend,
            maxNumTokens = 512,
            cacheDir = cacheDir.absolutePath
        )
        val newEngine = Engine(config)
        newEngine.initialize()
        engine = newEngine
        activeBackendName = "CPU"
        initializedModelPath = modelFile.absolutePath
        Log.i(TAG, "LiteRT-LM CPU initialized successfully.")
        return newEngine
    }

    override suspend fun generatePolish(
        inputText: String,
        mode: PolishMode,
        preferredBackend: ModelBackend
    ): EngineInferenceResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val modelId = modelRepository.manifest.modelId

        if (inputText.isBlank()) {
            return@withContext EngineInferenceResult(
                text = inputText,
                modelId = modelId,
                backendName = activeBackendName,
                durationMs = 0,
                isSuccess = true
            )
        }

        inferenceMutex.withLock {
            var conversation: com.google.ai.edge.litertlm.Conversation? = null
            try {
                val activeEngine = ensureEngineInitialized(preferredBackend)

                val systemPrompt = PolishPromptFactory.getSystemInstruction(mode)
                val userMessage = PolishPromptFactory.buildUserMessage(inputText)

                val convConfig = ConversationConfig(
                    systemInstruction = Contents.of(systemPrompt),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.8, temperature = 0.2, seed = 42),
                    maxOutputToken = 384,
                    thinkingConfig = ThinkingConfig(false)
                )

                conversation = activeEngine.createConversation(convConfig)
                val responseMsg = conversation.sendMessage(userMessage)

                val outputBuilder = StringBuilder()
                val contentsList = responseMsg.contents.contents
                for (content in contentsList) {
                    if (content is Content.Text) {
                        outputBuilder.append(content.text)
                    }
                }

                val rawOutput = outputBuilder.toString()
                val duration = System.currentTimeMillis() - startTime

                // Sanitize via validator (strip quotes, markdown fences, AI commentary)
                val sanitized = AiOutputValidator.sanitize(rawOutput, inputText)

                EngineInferenceResult(
                    text = sanitized,
                    modelId = modelId,
                    backendName = activeBackendName,
                    durationMs = duration,
                    isSuccess = true
                )
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                val duration = System.currentTimeMillis() - startTime
                Log.e(TAG, "Inference error with LiteRT-LM", e)
                EngineInferenceResult(
                    text = inputText,
                    modelId = modelId,
                    backendName = activeBackendName,
                    durationMs = duration,
                    isSuccess = false,
                    errorMessage = e.message ?: "Inference failed"
                )
            } finally {
                try {
                    conversation?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing conversation", e)
                }
            }
        }
    }

    override fun close() {
        try {
            engine?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LiteRT engine", e)
        } finally {
            engine = null
            initializedModelPath = null
            activeBackendName = "None"
            modelRepository.isModelLocked.set(false)
        }
    }
}
