package com.example

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.util.Log
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

sealed interface ModelInstallStatus {
    object NotDownloaded : ModelInstallStatus
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long, val progress: Float) : ModelInstallStatus
    data class Verifying(val progress: Float = 0f) : ModelInstallStatus
    data class Installed(val file: File, val sizeBytes: Long) : ModelInstallStatus
    data class Error(val message: String, val canRetry: Boolean = true) : ModelInstallStatus
}

/**
 * Model repository managing LiteRT-LM model files, downloads, integrity checks,
 * SAF import, and storage space validation.
 */
class ModelRepository(
    private val context: Context,
    val manifest: ModelManifest = ModelManifest.QWEN3_1_7B
) {
    companion object {
        private const val TAG = "ModelRepository"
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB

        @Volatile
        private var instance: ModelRepository? = null

        fun getInstance(context: Context): ModelRepository {
            return instance ?: synchronized(this) {
                instance ?: ModelRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    val isModelLocked = AtomicBoolean(false)

    private val _status = MutableStateFlow<ModelInstallStatus>(ModelInstallStatus.NotDownloaded)
    val status: StateFlow<ModelInstallStatus> = _status.asStateFlow()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    init {
        checkInstalledModel()
    }

    fun getModelDir(): File {
        val dir = File(context.filesDir, "litert_models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getModelFile(): File {
        return File(getModelDir(), manifest.fileName)
    }

    private fun getTempFile(): File {
        return File(getModelDir(), "${manifest.fileName}.tmp")
    }

    fun isModelInstalled(): Boolean {
        val file = getModelFile()
        return file.exists() && file.length() == manifest.expectedSizeBytes
    }

    fun checkInstalledModel(): ModelInstallStatus {
        val file = getModelFile()
        val current = _status.value
        if (current is ModelInstallStatus.Downloading || current is ModelInstallStatus.Verifying) {
            return current
        }

        val newStatus = if (file.exists() && file.length() == manifest.expectedSizeBytes) {
            ModelInstallStatus.Installed(file, file.length())
        } else {
            ModelInstallStatus.NotDownloaded
        }
        _status.value = newStatus
        return newStatus
    }

    fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.path)
            stat.availableBytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get available storage", e)
            0L
        }
    }

    fun startDownload() {
        if (downloadJob?.isActive == true) return
        val currentStatus = _status.value
        if (currentStatus is ModelInstallStatus.Downloading) return

        val freeSpace = getAvailableStorageBytes()
        if (freeSpace < manifest.minFreeStorageBytes) {
            val freeMb = freeSpace / (1024 * 1024)
            val requiredMb = manifest.minFreeStorageBytes / (1024 * 1024)
            _status.value = ModelInstallStatus.Error(
                "Insufficient storage: ${freeMb}MB available, but ${requiredMb}MB required.",
                canRetry = true
            )
            return
        }

        downloadJob = repositoryScope.launch {
            val tempFile = getTempFile()
            val targetFile = getModelFile()

            try {
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                _status.value = ModelInstallStatus.Downloading(0L, manifest.expectedSizeBytes, 0f)

                val request = Request.Builder()
                    .url(manifest.downloadUrl)
                    .header("User-Agent", "TypeRight-LiteRT-LM-Client")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _status.value = ModelInstallStatus.Error("Download failed with HTTP ${response.code}", canRetry = true)
                        return@launch
                    }

                    val body = response.body ?: run {
                        _status.value = ModelInstallStatus.Error("Empty server response body", canRetry = true)
                        return@launch
                    }

                    val contentLength = if (body.contentLength() > 0) body.contentLength() else manifest.expectedSizeBytes
                    val digest = MessageDigest.getInstance("SHA-256")

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytesRead: Int
                            var totalRead = 0L
                            var lastProgressUpdate = System.currentTimeMillis()

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                digest.update(buffer, 0, bytesRead)
                                totalRead += bytesRead

                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 100 || totalRead == contentLength) {
                                    lastProgressUpdate = now
                                    val progress = if (contentLength > 0) totalRead.toFloat() / contentLength else 0f
                                    _status.value = ModelInstallStatus.Downloading(totalRead, contentLength, progress)
                                }
                            }
                            output.flush()
                        }
                    }

                    // Verification phase
                    _status.value = ModelInstallStatus.Verifying(0.5f)
                    val calculatedSha256 = digest.digest().joinToString("") { "%02x".format(it) }

                    if (!calculatedSha256.equals(manifest.expectedSha256, ignoreCase = true)) {
                        tempFile.delete()
                        Log.e(TAG, "SHA-256 mismatch! Expected: ${manifest.expectedSha256}, calculated: $calculatedSha256")
                        _status.value = ModelInstallStatus.Error("Integrity check failed (SHA-256 checksum mismatch).", canRetry = true)
                        return@launch
                    }

                    if (tempFile.length() != manifest.expectedSizeBytes) {
                        tempFile.delete()
                        Log.e(TAG, "File size mismatch! Expected: ${manifest.expectedSizeBytes}, got: ${tempFile.length()}")
                        _status.value = ModelInstallStatus.Error("File size mismatch. Download incomplete.", canRetry = true)
                        return@launch
                    }

                    // Move to final target
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    val renamed = tempFile.renameTo(targetFile)
                    if (!renamed) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }

                    _status.value = ModelInstallStatus.Installed(targetFile, targetFile.length())
                    Log.i(TAG, "Model successfully downloaded and verified: ${targetFile.absolutePath}")
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    tempFile.delete()
                    _status.value = ModelInstallStatus.NotDownloaded
                    Log.i(TAG, "Download cancelled by user")
                } else {
                    tempFile.delete()
                    Log.e(TAG, "Download error", e)
                    _status.value = ModelInstallStatus.Error(e.message ?: "Download connection failed", canRetry = true)
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val tempFile = getTempFile()
        if (tempFile.exists()) {
            tempFile.delete()
        }
        checkInstalledModel()
    }

    suspend fun importFromFile(sourceUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val tempFile = getTempFile()
        val targetFile = getModelFile()

        try {
            _status.value = ModelInstallStatus.Verifying(0.1f)
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(Exception("Cannot open file from selected URI"))

            val digest = MessageDigest.getInstance("SHA-256")
            inputStream.use { input: InputStream ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        totalRead += bytesRead
                    }
                    output.flush()
                }
            }

            val calculatedSha256 = digest.digest().joinToString("") { "%02x".format(it) }

            // If user imports the standard model, verify SHA-256
            if (manifest.expectedSha256.isNotEmpty() &&
                !calculatedSha256.equals(manifest.expectedSha256, ignoreCase = true) &&
                tempFile.length() != manifest.expectedSizeBytes
            ) {
                // Check if valid litertlm format or warn
                Log.w(TAG, "Imported file SHA-256 ($calculatedSha256) differs from standard manifest.")
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            _status.value = ModelInstallStatus.Installed(targetFile, targetFile.length())
            Result.success(Unit)
        } catch (e: Exception) {
            tempFile.delete()
            _status.value = ModelInstallStatus.Error("Failed to import model: ${e.message}", canRetry = true)
            Result.failure(e)
        }
    }

    suspend fun deleteModel(): Boolean = withContext(Dispatchers.IO) {
        if (isModelLocked.get()) {
            Log.w(TAG, "Cannot delete model: model is currently in use by inference engine")
            return@withContext false
        }
        val file = getModelFile()
        val deleted = if (file.exists()) file.delete() else true
        val temp = getTempFile()
        if (temp.exists()) temp.delete()
        _status.value = ModelInstallStatus.NotDownloaded
        deleted
    }

    fun verifyExistingFileChecksum(): Boolean {
        val file = getModelFile()
        if (!file.exists() || file.length() != manifest.expectedSizeBytes) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            hash.equals(manifest.expectedSha256, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Checksum check failed", e)
            false
        }
    }
}
