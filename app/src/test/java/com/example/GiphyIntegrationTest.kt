package com.example

import com.example.giphy.GiphyMediaItem
import com.example.giphy.GiphyRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GiphyIntegrationTest {

    @Test
    fun testGiphyMediaItemAspectRatio() {
        val item = GiphyMediaItem(
            id = "test_1",
            title = "Happy Dance",
            isSticker = false,
            previewUrl = "https://media.giphy.com/media/test/100.gif",
            fullUrl = "https://media.giphy.com/media/test/giphy.gif",
            webUrl = "https://giphy.com/gifs/test_1",
            width = 400,
            height = 200
        )

        assertEquals(2.0f, item.aspectRatio, 0.01f)
        assertFalse(item.isSticker)
        assertEquals("image/gif", item.mimeType)
    }

    @Test
    fun testGiphyMediaItemAspectRatioClamping() {
        val ultraWide = GiphyMediaItem(
            id = "wide",
            title = "Wide",
            previewUrl = "url",
            fullUrl = "url",
            webUrl = "url",
            width = 1000,
            height = 100
        )
        // Aspect ratio should be clamped to max 2.5f
        assertEquals(2.5f, ultraWide.aspectRatio, 0.01f)

        val ultraTall = GiphyMediaItem(
            id = "tall",
            title = "Tall",
            previewUrl = "url",
            fullUrl = "url",
            webUrl = "url",
            width = 100,
            height = 1000
        )
        // Aspect ratio should be clamped to min 0.5f
        assertEquals(0.5f, ultraTall.aspectRatio, 0.01f)
    }

    @Test
    fun testGiphyFallbackContent() = runBlocking {
        // If an empty query or offline fallback is triggered, fallback content must be valid
        val gifs = GiphyRepository.getTrendingGifs()
        assertNotNull(gifs)
        assertTrue(gifs.isNotEmpty())

        val firstGif = gifs.first()
        assertTrue(firstGif.id.isNotBlank())
        assertTrue(firstGif.previewUrl.startsWith("http"))
        assertTrue(firstGif.webUrl.startsWith("http"))

        val stickers = GiphyRepository.getTrendingStickers()
        assertNotNull(stickers)
        assertTrue(stickers.isNotEmpty())

        val firstSticker = stickers.first()
        assertTrue(firstSticker.id.isNotBlank())
        assertTrue(firstSticker.isSticker)
    }
}
