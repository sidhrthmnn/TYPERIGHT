package com.example

import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import android.view.inputmethod.EditorInfo
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ScreenshotPaste {
    fun supported(info: EditorInfo, mimeType: String) = EditorInfoCompat.getContentMimeTypes(info)
        .any { ClipDescription.compareMimeTypes(mimeType, it) }

    /** Copy only on an explicit paste, with a size limit, into the existing narrowly scoped provider. */
    suspend fun prepare(context: Context, item: SmartClipSuggestion): Uri = withContext(Dispatchers.IO) {
        require(item.image != null && item.mimeType in setOf("image/png", "image/jpeg", "image/webp"))
        val dir = File(context.cacheDir, "keyboard_media").apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()?.filter { it.name.startsWith("smart_") && now - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        val extension = when (item.mimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
        val file = File(dir, "smart_${SmartClipboardPolicy.identity(item.id, item.timestamp)}.$extension")
        val temp = File.createTempFile("smart_", ".tmp", dir)
        try {
            context.contentResolver.openInputStream(item.image)?.use { source ->
                temp.outputStream().use { target ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 20 * 1024 * 1024) { "Screenshot exceeds 20 MB" }
                        target.write(buffer, 0, count)
                    }
                    require(total > 0) { "Screenshot is empty" }
                }
            } ?: error("Screenshot is unavailable")
            if (file.exists()) file.delete()
            check(temp.renameTo(file)) { "Could not prepare screenshot" }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } finally { temp.delete() }
    }
}
