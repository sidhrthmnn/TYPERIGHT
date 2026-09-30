package com.example

import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class TypeRightApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AiPolishBackend.initialize(this)
    }
}

/** Single routing boundary shared by keyboard, playground, proofreading and dictation. */
object AiPolishBackend {
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun ensureInitialized(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    val context: Context? get() = appContext

    val engine: ActiveAiEngine get() {
        val ctx = appContext ?: return ActiveAiEngine.OFFLINE
        return KeyboardSettings(ctx).activeAiEngine
    }

    val label: String get() {
        return if (isCloudActive) "Gemini Flash AI" else engine.title
    }

    fun isGeminiConfigured(): Boolean {
        return try {
            val key = BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
            key != null && key.isNotBlank() && key != "MY_GEMINI_API_KEY"
        } catch (_: Exception) {
            false
        }
    }

    val isCloudActive: Boolean get() {
        return isGeminiConfigured() && engine != ActiveAiEngine.OFFLINE
    }

    val timeoutMillis: Long get() = 185_000L

    suspend fun generatePolish(input: String, mode: String): String? = generatePolish(input, PolishMode.fromString(mode))

    suspend fun generatePolish(
        input: String,
        mode: PolishMode,
        context: TextContext? = null,
        preferredModel: String? = null
    ): String? {
        if (input.isBlank()) return ""
        if (engine == ActiveAiEngine.NONE) return null
        val ctx = checkNotNull(appContext) { "Local GGUF engine requires initialized application context" }

        // If offline is selected, strictly require local GGUF model per app architecture
        if (engine == ActiveAiEngine.OFFLINE) {
            return GgufPolishEngine.polish(ctx, input, mode)
        }

        // Try Gemini Cloud AI if configured
        if (isGeminiConfigured()) {
            try {
                val cloudResult = GeminiApiClient.generatePolish(input, mode, context, preferredModel)
                if (!cloudResult.isNullOrBlank()) {
                    return cloudResult
                }
            } catch (e: Exception) {
                android.util.Log.w("AiPolishBackend", "Gemini cloud generate error: ${e.message}")
            }
        }

        // Fallback to local GGUF if ready
        if (LocalGgufModel.isReady(ctx) && GgufPolishEngine.isSupported()) {
            return GgufPolishEngine.polish(ctx, input, mode)
        }

        val tone = when (mode) {
            PolishMode.AUTO_FORMAT -> "auto_format"
            PolishMode.PROFESSIONAL -> "professional"
            PolishMode.CASUAL -> "casual"
            PolishMode.SHORTEN -> "concise"
            PolishMode.EXPAND -> "eloquent"
            PolishMode.REPHRASE -> "eloquent"
            PolishMode.VOICE_CLEANUP, PolishMode.RAMBLE -> "voice"
            else -> "proofread"
        }
        return OnDeviceNeuralPolishEngine.getInstance(ctx).polish(input, tone).polishedText
    }

    fun streamPolish(
        input: String,
        mode: PolishMode,
        context: TextContext? = null,
        preferredModel: String? = null
    ): Flow<String> = flow {
        if (engine == ActiveAiEngine.NONE) return@flow
        val ctx = checkNotNull(appContext) { "Local GGUF engine requires initialized application context" }

        // If offline is selected, strictly require local GGUF model per app architecture
        if (engine == ActiveAiEngine.OFFLINE) {
            emit(GgufPolishEngine.polish(ctx, input, mode))
            return@flow
        }

        // Try Gemini Cloud AI if configured
        if (isGeminiConfigured()) {
            var emittedAny = false
            try {
                GeminiApiClient.streamPolish(input, mode, context, preferredModel).collect { chunk ->
                    if (chunk.isNotBlank()) {
                        emittedAny = true
                        emit(chunk)
                    }
                }
                if (emittedAny) return@flow
            } catch (e: Exception) {
                android.util.Log.w("AiPolishBackend", "Gemini cloud stream error: ${e.message}")
            }
        }

        if (LocalGgufModel.isReady(ctx) && GgufPolishEngine.isSupported()) {
            emit(GgufPolishEngine.polish(ctx, input, mode))
            return@flow
        }

        val tone = when (mode) {
            PolishMode.AUTO_FORMAT -> "auto_format"
            PolishMode.PROFESSIONAL -> "professional"
            PolishMode.CASUAL -> "casual"
            PolishMode.SHORTEN -> "concise"
            PolishMode.EXPAND -> "eloquent"
            PolishMode.REPHRASE -> "eloquent"
            PolishMode.VOICE_CLEANUP, PolishMode.RAMBLE -> "voice"
            else -> "proofread"
        }
        val fallback = OnDeviceNeuralPolishEngine.getInstance(ctx).polish(input, tone).polishedText
        emit(fallback)
    }
}
