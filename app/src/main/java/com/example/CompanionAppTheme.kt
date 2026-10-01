package com.example

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Companion activity only. The IME keeps its existing theme and rendering. */
@Composable
fun CompanionAppTheme(darkTheme: Boolean, midnight: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (darkTheme) darkColorScheme(
        primary = Color(0xFFB6C0FF), onPrimary = Color(0xFF202D79),
        primaryContainer = Color(0xFF263467), onPrimaryContainer = Color(0xFFDEE3FF),
        secondary = Color(0xFF9DDAC5), secondaryContainer = Color(0xFF1C4036),
        onSecondaryContainer = Color(0xFFCBF4E5),
        background = Color(0xFF10141D), onBackground = Color(0xFFF0F2FA),
        surface = Color(0xFF1A202C), onSurface = Color(0xFFF0F2FA),
        surfaceVariant = Color(0xFF252D3D), onSurfaceVariant = Color(0xFFADB6CB),
        outline = Color(0xFF747F97), outlineVariant = Color(0xFF303A4D)
    ) else lightColorScheme(
        primary = Color(0xFF4759CF), onPrimary = Color.White,
        primaryContainer = Color(0xFFEDF0FF), onPrimaryContainer = Color(0xFF3446A7),
        secondary = Color(0xFF267B61), secondaryContainer = Color(0xFFE8F5EE),
        onSecondaryContainer = Color(0xFF22684F),
        background = Color(0xFFF5F6FA), onBackground = Color(0xFF20283C),
        surface = Color.White, onSurface = Color(0xFF20283C),
        surfaceVariant = Color(0xFFEEF0F6), onSurfaceVariant = Color(0xFF69738A),
        outline = Color(0xFF8A93A6), outlineVariant = Color(0xFFE4E7EF)
    )
    MaterialTheme(
        colorScheme = if (darkTheme && midnight) colors.copy(background = Color(0xFF070A10), surface = Color(0xFF11151E), surfaceVariant = Color(0xFF1A202C)) else colors,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.8).sp),
            headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
            labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
        ),
        content = content
    )
}
