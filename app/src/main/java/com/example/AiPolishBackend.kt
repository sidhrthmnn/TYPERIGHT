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
        val ctx = appContext ?: return ActiveAiEngine.ONLINE
        return KeyboardSettings(ctx).activeAiEngine
    }

    val label: String get() = engine.title
    val timeoutMillis: Long get() = if (engine == ActiveAiEngine.OFFLINE) 125_000L else 5_000L

    suspend fun generatePolish(input: String, mode: String): String? = generatePolish(input, PolishMode.fromString(mode))

    suspend fun generatePolish(input: String, mode: PolishMode, context: TextContext? = null,
                               preferredModel: String? = null): String? {
        if (input.isBlank()) return ""
        return when (engine) {
            ActiveAiEngine.OFFLINE -> {
                val ctx = checkNotNull(appContext) { "Local GGUF engine requires initialized application context" }
                GgufPolishEngine.polish(ctx, input, mode)
            }
            ActiveAiEngine.NONE -> null
            else -> GeminiApiClient.generatePolish(input, mode, context, preferredModel)
        }
    }

    fun streamPolish(input: String, mode: PolishMode, context: TextContext? = null,
                     preferredModel: String? = null): Flow<String> = flow {
        when (engine) {
            ActiveAiEngine.OFFLINE -> {
                val ctx = checkNotNull(appContext) { "Local GGUF engine requires initialized application context" }
                emit(GgufPolishEngine.polish(ctx, input, mode))
            }
            ActiveAiEngine.NONE -> Unit
            else -> emitAll(GeminiApiClient.streamPolish(input, mode, context, preferredModel))
        }
    }
}
