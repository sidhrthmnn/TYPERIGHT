package com.example

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class GgufCancellation(private val job: Job?) {
    @androidx.annotation.Keep
    fun isCancelled(): Boolean = job?.isActive == false
}

@androidx.annotation.Keep
internal object GgufNative {
    private var isLoaded = false
    private var loadError: Throwable? = null

    init {
        try {
            System.loadLibrary("typeright_gguf")
            isLoaded = true
        } catch (t: Throwable) {
            loadError = t
        }
    }

    fun ensureLoaded() {
        if (!isLoaded) {
            throw IllegalStateException("Local GGUF runtime is unavailable on this device", loadError)
        }
    }

    external fun generate(path: ByteArray, prompt: ByteArray, cancellation: GgufCancellation): ByteArray
}

object GgufPolishEngine {
    private val mutex = Mutex()
    fun isSupported(): Boolean = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }

    internal fun prompt(input: String, mode: PolishMode, languageGuidance: String = "",
                        format: String = "gemma4", context: TextContext? = null): String =
        PolishPromptBuilder.build(input, mode, format, languageGuidance, context)

    suspend fun polish(context: Context, input: String, mode: PolishMode, textContext: TextContext? = null, preferredModel: String? = null): String = withContext(Dispatchers.Default) {
        require(input.length <= 6000) { "Select less text for local polish (maximum 6,000 characters)" }
        val model = if (preferredModel == null) LocalGgufModel.selected(context) else GgufModelCatalog.resolve(context, preferredModel)
        check(LocalGgufModel.termsAccepted(context, model)) { "Review the selected model terms in AI Polish settings first" }
        check(isSupported()) { "Local GGUF requires a 64-bit Android device" }
        check(LocalGgufModel.isReady(context, model)) { "Download the local GGUF model in AI Polish settings first" }
        val settings = KeyboardSettings(context)
        if (model.languages == "English" && !settings.isAllAiLanguagesSelected) {
            check(settings.getSelectedAiLanguageCodes().all { it == "en" }) {
                "GRMR is trained for English. Choose a multilingual model for the selected languages."
            }
        }
        val languageGuidance = if (model.languages == "English") "Correct English without translating names or quoted text." else settings.getActiveAiLanguagePromptGuidance()
        mutex.withLock {
            val coroutine = currentCoroutineContext()
            coroutine.ensureActive()
            val result = try {
                GgufNative.ensureLoaded()
                GgufNative.generate(LocalGgufModel.file(context, model).absolutePath.toByteArray(Charsets.UTF_8),
                    prompt(input, mode, languageGuidance, model.format, textContext).toByteArray(Charsets.UTF_8), GgufCancellation(coroutine[Job]))
            } catch (e: LinkageError) {
                throw IllegalStateException("Local GGUF runtime is unavailable on this device", e)
            }
            coroutine.ensureActive()
            val output = AiOutputValidator.sanitize(result.toString(Charsets.UTF_8), input)
            check(output.isNotBlank() && AiOutputValidator.isValid(input, output, mode)) {
                "Local model could not produce a safe edit. Try a shorter selection."
            }
            output
        }
    }
}
