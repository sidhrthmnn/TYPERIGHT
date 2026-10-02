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

    val label: String get() = appContext?.let { if (engine == ActiveAiEngine.OFFLINE) LocalGgufModel.label(it) else engine.title } ?: engine.title

    val isCloudActive: Boolean get() = false

    fun isGeminiConfigured(): Boolean = false

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

        // Local on-device GGUF model
        if (engine == ActiveAiEngine.OFFLINE) {
            return GgufPolishEngine.polish(ctx, input, mode, context, preferredModel)
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

        // Local on-device GGUF model
        if (engine == ActiveAiEngine.OFFLINE) {
            emit(GgufPolishEngine.polish(ctx, input, mode, context, preferredModel))
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
