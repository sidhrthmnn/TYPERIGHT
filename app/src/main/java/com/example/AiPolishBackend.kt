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

    val isCloudActive: Boolean get() = engine == ActiveAiEngine.ONLINE

    fun isGeminiConfigured(): Boolean = appContext?.let(CloudPolishEngine::isConfigured) ?: false

    val timeoutMillis: Long get() = if (appContext?.let { KeyboardSettings(it).cloudFallbackEnabled } == true) 240_000L else 185_000L

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

        if (engine == ActiveAiEngine.ONLINE) return CloudPolishEngine.polish(ctx, input, mode, context)
        try {
            return GgufPolishEngine.polish(ctx, input, mode, context, preferredModel)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) {
            // Credentials alone never authorize upload. Both fallback and a key must be configured.
            if (!KeyboardSettings(ctx).cloudFallbackEnabled || !CloudPolishEngine.isConfigured(ctx)) throw failure
            return CloudPolishEngine.polish(ctx, input, mode, context)
        }
    }

    fun streamPolish(
        input: String,
        mode: PolishMode,
        context: TextContext? = null,
        preferredModel: String? = null
    ): Flow<String> = flow {
        generatePolish(input, mode, context, preferredModel)?.let { emit(it) }
    }
}
