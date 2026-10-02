package com.example

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ModelDownloadState(val busy: Boolean = false, val progress: Float = 0f,
                              val message: String = "", val modelId: String = "")

/** Explicit downloads use the network. Installed models coexist and inference stays offline. */
object LocalGgufModel {
    const val LABEL = "Local GGUF"
    private val mutex = Mutex()
    private val _state = MutableStateFlow(ModelDownloadState())
    val state = _state.asStateFlow()
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).build()
    fun selected(context: Context) = GgufModelCatalog.resolve(context, KeyboardSettings(context).aiModel)
    fun label(context: Context) = "Local GGUF · ${selected(context).name}"
    fun select(context: Context, model: GgufModelSpec) { KeyboardSettings(context).aiModel = model.id }
    fun termsAccepted(context: Context, model: GgufModelSpec = selected(context)): Boolean {
        if (!model.requiresConsent) return true
        val prefs = KeyboardSettings(context).sharedPreferences
        val key = "gguf_consent_${model.id}"
        return if (prefs.contains(key)) prefs.getBoolean(key, false)
        else prefs.getBoolean("gemma_terms_accepted", false)
    }
    fun acceptTerms(context: Context, accepted: Boolean, model: GgufModelSpec = selected(context)) {
        KeyboardSettings(context).sharedPreferences.edit().putBoolean("gguf_consent_${model.id}", accepted).commit()
    }
    fun file(context: Context, model: GgufModelSpec = selected(context)): File = File(context.noBackupFilesDir, "gguf/${model.filename}")
    fun isReady(context: Context, model: GgufModelSpec = selected(context)): Boolean = file(context, model).let {
        model.bytes > 0 && it.isFile && it.length() == model.bytes
    }
    suspend fun remove(context: Context, model: GgufModelSpec) = withContext(Dispatchers.IO) {
        mutex.withLock { check(!file(context, model).exists() || file(context, model).delete()) { "Cannot remove model" } }
    }
    suspend fun download(context: Context, model: GgufModelSpec = selected(context)) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(termsAccepted(context, model)) { "Review and accept the selected model's terms first" }
            if (isReady(context, model)) return@withLock
            val target = file(context, model)
            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "Cannot create model directory" }
            val partial = File(target.path + ".part")
            _state.value = ModelDownloadState(true, message = "Downloading ${model.name}…", modelId = model.id)
            try {
                client.newCall(Request.Builder().url(model.url).build()).execute().use { response ->
                    check(response.isSuccessful) { "Download failed (HTTP ${response.code}). Tap to retry." }
                    check(response.request.url.isHttps) { "Model download must stay on HTTPS" }
                    val body = checkNotNull(response.body) { "Empty model download" }
                    val expected = if (model.bytes > 0) model.bytes else body.contentLength().coerceAtLeast(0)
                    check(expected <= 8_000_000_000L) { "Model exceeds the 8 GB mobile download limit" }
                    check(target.parentFile!!.usableSpace > expected + 64L * 1024 * 1024) { "Free storage for ${model.sizeLabel} and 64 MB before downloading" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val magic = ByteArray(4)
                            var filled = 0
                            while (filled < 4) { val count = input.read(magic, filled, 4 - filled); check(count > 0) { "Empty GGUF download" }; filled += count }
                            check(magic.contentEquals(byteArrayOf(71, 71, 85, 70))) { "The download is not a GGUF model" }
                            output.write(magic); digest.update(magic); total = 4
                            val buffer = ByteArray(64 * 1024)
                            var lastPublished = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                check(total <= 8_000_000_000L && (expected == 0L || total <= expected)) { "Model download exceeds expected size" }
                                check(target.parentFile!!.usableSpace > count + 16L * 1024 * 1024) { "Not enough storage to finish this download" }
                                digest.update(buffer, 0, count); output.write(buffer, 0, count)
                                if (total - lastPublished >= 1024 * 1024) {
                                    _state.value = ModelDownloadState(true, if (expected > 0) total.toFloat() / expected else 0f, "Downloading ${model.name}…", model.id)
                                    lastPublished = total
                                }
                            }
                            output.fd.sync()
                        }
                    }
                    val sha = digest.digest().joinToString("") { "%02x".format(it) }
                    check((expected == 0L || total == expected) && sha == model.sha256) { "Model checksum failed. Tap to retry." }
                    currentCoroutineContext().ensureActive()
                    check(partial.renameTo(target)) { "Cannot install downloaded model" }
                    if (model.custom) GgufModelCatalog.saveCustom(context, model.copy(bytes = total))
                }
                _state.value = ModelDownloadState(message = "${model.name} is ready for offline polish", modelId = model.id)
            } finally {
                partial.delete()
                _state.value = _state.value.copy(busy = false)
            }
        }
    }
}
