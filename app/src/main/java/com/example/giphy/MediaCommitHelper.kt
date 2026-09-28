package com.example.giphy

import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaCommitHelper {
    private const val TAG = "MediaCommitHelper"

    /**
     * Commits rich media (GIF or Sticker) to the target application.
     * Uses InputConnectionCompat.commitContent with FileProvider, and falls back to URL text insertion
     * if the target app doesn't support rich content.
     */
    suspend fun commitMedia(
        context: Context,
        inputConnection: InputConnection?,
        editorInfo: EditorInfo?,
        item: GiphyMediaItem,
        onComplete: (Boolean) -> Unit = {}
    ): Boolean {
        if (inputConnection == null) {
            onComplete(false)
            return false
        }

        // 1. Download / retrieve cached local media file
        val file = withContext(Dispatchers.IO) {
            GiphyRepository.downloadMediaFile(context, item)
        }

        // Check target app mime types
        val contentMimeTypes = editorInfo?.let { EditorInfoCompat.getContentMimeTypes(it) } ?: emptyArray()
        val targetSupportsImages = contentMimeTypes.any {
            it.startsWith("image/") || it == "*/*" || it.contains("gif") || it.contains("webp")
        }

        var committed = false

        if (file != null && file.exists() && (targetSupportsImages || contentMimeTypes.isEmpty())) {
            try {
                val authority = "${context.packageName}.fileprovider"
                val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)
                val mimeType = item.mimeType
                val description = ClipDescription(item.title.ifBlank { if (item.isSticker) "Sticker" else "GIF" }, arrayOf(mimeType))
                val inputContentInfo = InputContentInfoCompat(
                    contentUri,
                    description,
                    Uri.parse(item.webUrl)
                )

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                } else {
                    0
                }

                committed = InputConnectionCompat.commitContent(
                    inputConnection,
                    editorInfo ?: EditorInfo(),
                    inputContentInfo,
                    flags,
                    null
                )
            } catch (e: Exception) {
                Log.e(TAG, "InputConnectionCompat.commitContent threw exception", e)
                committed = false
            }
        }

        // 2. Fallback to URL text insertion if rich media commit failed or is unsupported
        if (!committed) {
            val urlToInsert = item.webUrl.ifBlank { item.fullUrl }
            inputConnection.commitText(urlToInsert + " ", 1)
            withContext(Dispatchers.Main) {
                val label = if (item.isSticker) "Sticker" else "GIF"
                Toast.makeText(context, "$label link inserted", Toast.LENGTH_SHORT).show()
            }
        } else {
            withContext(Dispatchers.Main) {
                val label = if (item.isSticker) "Sticker" else "GIF"
                Toast.makeText(context, "$label inserted", Toast.LENGTH_SHORT).show()
            }
        }

        onComplete(committed)
        return true
    }
}
