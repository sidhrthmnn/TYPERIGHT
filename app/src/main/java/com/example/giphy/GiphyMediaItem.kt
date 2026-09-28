package com.example.giphy

/**
 * Represents a rich media item (GIF or Sticker) sourced from GIPHY.
 */
data class GiphyMediaItem(
    val id: String,
    val title: String,
    val isSticker: Boolean = false,
    val previewUrl: String,
    val fullUrl: String,
    val webUrl: String,
    val width: Int = 200,
    val height: Int = 200,
    val mimeType: String = "image/gif"
) {
    val aspectRatio: Float
        get() = if (height > 0) (width.toFloat() / height.toFloat()).coerceIn(0.5f, 2.5f) else 1f
}
