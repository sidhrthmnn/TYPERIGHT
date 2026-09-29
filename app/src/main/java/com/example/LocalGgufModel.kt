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
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ModelDownloadState(val busy: Boolean = false, val progress: Float = 0f, val message: String = "")

/** Only this explicit download operation uses the network. Inference never downloads. */
object LocalGgufModel {
    const val LABEL = "Local GGUF · Qwen2.5 0.5B"
    private val mutex = Mutex()
    private val _state = MutableStateFlow(ModelDownloadState())
    val state = _state.asStateFlow()
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).build()

    private fun spec(context: Context) = JSONObject(context.assets.open("qwen-polish.json").bufferedReader().use { it.readText() })
    fun file(context: Context): File = File(context.noBackupFilesDir, "gguf/${spec(context).getString("filename")}")
    fun isReady(context: Context): Boolean = file(context).let {
        it.isFile && it.length() == spec(context).getLong("bytes")
    }

    suspend fun download(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isReady(context)) return@withLock
            val spec = spec(context)
            val target = file(context)
            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "Cannot create model directory" }
            check(target.parentFile!!.usableSpace > spec.getLong("bytes") + 64L * 1024 * 1024) {
                "Free at least 550 MB of storage before downloading"
            }
            val partial = File(target.path + ".part")
            _state.value = ModelDownloadState(busy = true, message = "Downloading model…")
            try {
                val request = Request.Builder().url(spec.getString("url")).build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Download failed (HTTP ${response.code}). Tap to retry." }
                    val body = checkNotNull(response.body) { "Empty model download" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                check(total <= spec.getLong("bytes")) { "Model download exceeds expected size" }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                                _state.value = ModelDownloadState(true, total.toFloat() / spec.getLong("bytes"), "Downloading model…")
                            }
                            output.fd.sync()
                        }
                    }
                    val sha = digest.digest().joinToString("") { "%02x".format(it) }
                    check(total == spec.getLong("bytes") && sha == spec.getString("sha256")) {
                        "Model checksum failed. Tap to retry."
                    }
                    currentCoroutineContext().ensureActive()
                    check(partial.renameTo(target)) { "Cannot install downloaded model" }
                }
                _state.value = ModelDownloadState(message = "Ready for offline polish")
            } finally {
                partial.delete()
                _state.value = _state.value.copy(busy = false)
            }
        }
    }
}
