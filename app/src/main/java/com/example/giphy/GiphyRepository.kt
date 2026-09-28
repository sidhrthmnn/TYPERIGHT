package com.example.giphy

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object GiphyRepository {
    private const val TAG = "GiphyRepository"
    private const val API_KEY = "6FzkGSsuAz2EHFfWc4gaESS7OqZfKqKX"
    private const val BASE_URL = "https://api.giphy.com/v1"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // Memory cache for fast tab and query switching
    private val memoryCache = ConcurrentHashMap<String, List<GiphyMediaItem>>()

    suspend fun getTrendingGifs(limit: Int = 25): List<GiphyMediaItem> = withContext(Dispatchers.IO) {
        val cacheKey = "trending_gifs_$limit"
        memoryCache[cacheKey]?.let { return@withContext it }

        val url = "$BASE_URL/gifs/trending?api_key=$API_KEY&limit=$limit&rating=g"
        val items = fetchGiphyMedia(url, isSticker = false)
        if (items.isNotEmpty()) {
            memoryCache[cacheKey] = items
        }
        items.ifEmpty { getFallbackGifs() }
    }

    suspend fun searchGifs(query: String, limit: Int = 25): List<GiphyMediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext getTrendingGifs(limit)

        val cacheKey = "search_gifs_${trimmed.lowercase()}_$limit"
        memoryCache[cacheKey]?.let { return@withContext it }

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val url = "$BASE_URL/gifs/search?api_key=$API_KEY&q=$encoded&limit=$limit&rating=g"
        val items = fetchGiphyMedia(url, isSticker = false)
        if (items.isNotEmpty()) {
            memoryCache[cacheKey] = items
        }
        items.ifEmpty { getFallbackGifs() }
    }

    suspend fun getTrendingStickers(limit: Int = 25): List<GiphyMediaItem> = withContext(Dispatchers.IO) {
        val cacheKey = "trending_stickers_$limit"
        memoryCache[cacheKey]?.let { return@withContext it }

        val url = "$BASE_URL/stickers/trending?api_key=$API_KEY&limit=$limit&rating=g"
        val items = fetchGiphyMedia(url, isSticker = true)
        if (items.isNotEmpty()) {
            memoryCache[cacheKey] = items
        }
        items.ifEmpty { getFallbackStickers() }
    }

    suspend fun searchStickers(query: String, limit: Int = 25): List<GiphyMediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext getTrendingStickers(limit)

        val cacheKey = "search_stickers_${trimmed.lowercase()}_$limit"
        memoryCache[cacheKey]?.let { return@withContext it }

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val url = "$BASE_URL/stickers/search?api_key=$API_KEY&q=$encoded&limit=$limit&rating=g"
        val items = fetchGiphyMedia(url, isSticker = true)
        if (items.isNotEmpty()) {
            memoryCache[cacheKey] = items
        }
        items.ifEmpty { getFallbackStickers() }
    }

    private fun fetchGiphyMedia(endpointUrl: String, isSticker: Boolean): List<GiphyMediaItem> {
        return try {
            val request = Request.Builder().url(endpointUrl).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = JSONObject(body)
            val dataArray = json.optJSONArray("data") ?: return emptyList()
            val result = mutableListOf<GiphyMediaItem>()

            for (i in 0 until dataArray.length()) {
                val itemObj = dataArray.optJSONObject(i) ?: continue
                val id = itemObj.optString("id")
                if (id.isBlank()) continue

                val title = itemObj.optString("title", if (isSticker) "Sticker" else "GIF")
                val webUrl = itemObj.optString("url", "https://giphy.com/gifs/$id")
                val images = itemObj.optJSONObject("images") ?: continue

                // Extract preview URL (fixed_height_small or fixed_width_small or downsized)
                val fixedHeightSmall = images.optJSONObject("fixed_height_small")
                val fixedHeight = images.optJSONObject("fixed_height")
                val downsized = images.optJSONObject("downsized")
                val original = images.optJSONObject("original")

                val previewUrl = fixedHeightSmall?.optString("url")
                    ?: fixedHeight?.optString("url")
                    ?: downsized?.optString("url")
                    ?: original?.optString("url")
                    ?: ""

                val fullUrl = downsized?.optString("url")
                    ?: original?.optString("url")
                    ?: previewUrl

                if (previewUrl.isBlank()) continue

                val width = fixedHeight?.optInt("width", 200) ?: 200
                val height = fixedHeight?.optInt("height", 200) ?: 200

                result.add(
                    GiphyMediaItem(
                        id = id,
                        title = title,
                        isSticker = isSticker,
                        previewUrl = previewUrl,
                        fullUrl = fullUrl,
                        webUrl = webUrl,
                        width = width,
                        height = height,
                        mimeType = "image/gif"
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching GIPHY media from $endpointUrl", e)
            emptyList()
        }
    }

    /**
     * Downloads and caches the GIF/Sticker file locally for rich media commit.
     */
    suspend fun downloadMediaFile(context: Context, item: GiphyMediaItem): File? = withContext(Dispatchers.IO) {
        try {
            val mediaDir = File(context.cacheDir, "keyboard_media")
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }

            val targetFile = File(mediaDir, "${item.id}.gif")
            if (targetFile.exists() && targetFile.length() > 0) {
                return@withContext targetFile
            }

            val downloadUrl = item.fullUrl.ifBlank { item.previewUrl }
            val request = Request.Builder().url(downloadUrl).build()
            val response = client.newCall(request).execute()
            val bytes = response.body?.bytes() ?: return@withContext null

            FileOutputStream(targetFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download media file for ${item.id}", e)
            null
        }
    }

    private fun getFallbackGifs(): List<GiphyMediaItem> = listOf(
        GiphyMediaItem(
            id = "1ojn1S7BTXUry8elNi",
            title = "Will Ferrell Meme",
            isSticker = false,
            previewUrl = "https://media4.giphy.com/media/1ojn1S7BTXUry8elNi/giphy-downsized.gif",
            fullUrl = "https://media4.giphy.com/media/1ojn1S7BTXUry8elNi/giphy.gif",
            webUrl = "https://giphy.com/gifs/1ojn1S7BTXUry8elNi"
        ),
        GiphyMediaItem(
            id = "l0MYt5jPR6QX5pnqM",
            title = "Applause Clap",
            isSticker = false,
            previewUrl = "https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy-downsized.gif",
            fullUrl = "https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy.gif",
            webUrl = "https://giphy.com/gifs/l0MYt5jPR6QX5pnqM"
        ),
        GiphyMediaItem(
            id = "3o7abKhOpu0NwenH3O",
            title = "Happy Celebration",
            isSticker = false,
            previewUrl = "https://media.giphy.com/media/3o7abKhOpu0NwenH3O/giphy-downsized.gif",
            fullUrl = "https://media.giphy.com/media/3o7abKhOpu0NwenH3O/giphy.gif",
            webUrl = "https://giphy.com/gifs/3o7abKhOpu0NwenH3O"
        ),
        GiphyMediaItem(
            id = "blSTtZehjAZ8I",
            title = "Cat Dance",
            isSticker = false,
            previewUrl = "https://media.giphy.com/media/blSTtZehjAZ8I/giphy-downsized.gif",
            fullUrl = "https://media.giphy.com/media/blSTtZehjAZ8I/giphy.gif",
            webUrl = "https://giphy.com/gifs/blSTtZehjAZ8I"
        )
    )

    private fun getFallbackStickers(): List<GiphyMediaItem> = listOf(
        GiphyMediaItem(
            id = "29NPoyouyhDYZfousy",
            title = "Pink Love Bows",
            isSticker = true,
            previewUrl = "https://media3.giphy.com/media/29NPoyouyhDYZfousy/100.gif",
            fullUrl = "https://media3.giphy.com/media/29NPoyouyhDYZfousy/giphy.gif",
            webUrl = "https://giphy.com/stickers/29NPoyouyhDYZfousy"
        ),
        GiphyMediaItem(
            id = "xT9IgG50Fb7Mi0prBC",
            title = "Cool Vibes",
            isSticker = true,
            previewUrl = "https://media.giphy.com/media/xT9IgG50Fb7Mi0prBC/100.gif",
            fullUrl = "https://media.giphy.com/media/xT9IgG50Fb7Mi0prBC/giphy.gif",
            webUrl = "https://giphy.com/stickers/xT9IgG50Fb7Mi0prBC"
        )
    )
}
