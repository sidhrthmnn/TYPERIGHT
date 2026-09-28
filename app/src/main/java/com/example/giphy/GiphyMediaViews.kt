package com.example.giphy

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Standard categories displayed in the top bar when the GIF tab is active.
 */
val GiphyGifCategories = listOf(
    "🔥 Trending", "😂 Reactions", "🎭 Memes", "❤️ Love", "🎉 Party",
    "💃 Dance", "👏 Applause", "🐱 Cats", "🤦 Oops", "🚀 Hype", "✨ Morning"
)

/**
 * Standard categories displayed in the top bar when the Stickers tab is active.
 */
val GiphyStickerCategories = listOf(
    "🔥 Trending", "💖 Love", "✨ Cute", "😎 Vibes", "👋 Hello",
    "🎉 Party", "🐱 Cute Cats", "💥 Anime", "😂 Funny"
)

/**
 * Creates and remembers an ImageLoader capable of decoding animated GIFs via Coil.
 */
@Composable
fun rememberGifImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .respectCacheHeaders(false)
            .build()
    }
}

/**
 * Live GIPHY GIF Grid view. Search bar and category pills have been elevated to the top bar,
 * maximizing vertical screen real estate for the GIF grid.
 */
@Composable
fun GiphyGifTabView(
    keyColor: Color,
    textColor: Color,
    accentColor: Color,
    searchQuery: String = "",
    selectedCategory: String = "🔥 Trending",
    onMediaSelected: (GiphyMediaItem) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val gifImageLoader = rememberGifImageLoader()

    var gifItems by remember { mutableStateOf<List<GiphyMediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var committingItemId by remember { mutableStateOf<String?>(null) }

    // Trigger API query whenever search query or category chip changes
    LaunchedEffect(searchQuery, selectedCategory) {
        isLoading = true
        errorMessage = null

        // Slight debounce for active typing
        if (searchQuery.isNotBlank()) {
            delay(300)
        }

        try {
            val results = when {
                searchQuery.isNotBlank() -> GiphyRepository.searchGifs(searchQuery)
                selectedCategory == "🔥 Trending" || selectedCategory == "Trending" -> GiphyRepository.getTrendingGifs()
                else -> {
                    val cleanQuery = selectedCategory.replace(Regex("[^a-zA-Z]"), "").trim()
                    GiphyRepository.searchGifs(cleanQuery)
                }
            }
            gifItems = results
            if (results.isEmpty()) {
                errorMessage = "No GIFs found"
            }
        } catch (e: Exception) {
            errorMessage = "Failed to load GIFs"
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp)
            .testTag("giphy_gif_tab_view"),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading && gifItems.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = accentColor,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Loading GIFs...",
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        } else if (gifItems.isEmpty() && errorMessage != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = errorMessage ?: "No GIFs found",
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 2.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(gifItems, key = { it.id }) { item ->
                    val isCommitting = committingItemId == item.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(105.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(textColor.copy(alpha = 0.08f))
                            .clickable(enabled = !isCommitting) {
                                committingItemId = item.id
                                coroutineScope.launch {
                                    try {
                                        onMediaSelected(item)
                                    } finally {
                                        committingItemId = null
                                    }
                                }
                            }
                            .testTag("gif_item_${item.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.previewUrl)
                                .crossfade(true)
                                .build(),
                            imageLoader = gifImageLoader,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Bottom gradient overlay for title legibility
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                                    )
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Text(
                                text = item.title.ifBlank { "GIF" },
                                color = Color.White,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Loading overlay when inserting
                        if (isCommitting) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Sending...",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Live GIPHY Stickers Grid view. Search bar and category chips are integrated into the top bar,
 * maximizing vertical space for sticker cards.
 */
@Composable
fun GiphyStickerTabView(
    keyColor: Color,
    textColor: Color,
    accentColor: Color,
    searchQuery: String = "",
    selectedCategory: String = "🔥 Trending",
    onMediaSelected: (GiphyMediaItem) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val gifImageLoader = rememberGifImageLoader()

    var stickerItems by remember { mutableStateOf<List<GiphyMediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var committingItemId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(searchQuery, selectedCategory) {
        isLoading = true
        errorMessage = null

        if (searchQuery.isNotBlank()) {
            delay(300)
        }

        try {
            val results = when {
                searchQuery.isNotBlank() -> GiphyRepository.searchStickers(searchQuery)
                selectedCategory == "🔥 Trending" || selectedCategory == "Trending" -> GiphyRepository.getTrendingStickers()
                else -> {
                    val cleanQuery = selectedCategory.replace(Regex("[^a-zA-Z]"), "").trim()
                    GiphyRepository.searchStickers(cleanQuery)
                }
            }
            stickerItems = results
            if (results.isEmpty()) {
                errorMessage = "No Stickers found"
            }
        } catch (e: Exception) {
            errorMessage = "Failed to load Stickers"
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp)
            .testTag("giphy_sticker_tab_view"),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading && stickerItems.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = accentColor,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Loading Stickers...",
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        } else if (stickerItems.isEmpty() && errorMessage != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = errorMessage ?: "No Stickers found",
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 2.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stickerItems, key = { it.id }) { item ->
                    val isCommitting = committingItemId == item.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(textColor.copy(alpha = 0.05f))
                            .clickable(enabled = !isCommitting) {
                                committingItemId = item.id
                                coroutineScope.launch {
                                    try {
                                        onMediaSelected(item)
                                    } finally {
                                        committingItemId = null
                                    }
                                }
                            }
                            .padding(4.dp)
                            .testTag("sticker_item_${item.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.previewUrl)
                                .crossfade(true)
                                .build(),
                            imageLoader = gifImageLoader,
                            contentDescription = item.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isCommitting) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
