package com.shieldstream.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.OnAccent,
    secondary = Color(0xFFB0B3B8),
    background = AppColors.Background,
    onBackground = Color.White,
    surface = AppColors.Surface,
    onSurface = Color.White,
)

// Six sizes and three weights. Inter ships only Regular, Medium and SemiBold; asking for Bold or
// Black silently renders SemiBold, so use SemiBold explicitly. Material slots that differ only by
// size map onto the same role.
private val Display = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 40.sp)
private val Headline = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp)
private val Title = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 26.sp)
private val Body = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp)
private val Small = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
private val Caption = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)

private val AppTypography = Typography(
    displayLarge = Display,
    displayMedium = Display,
    displaySmall = Display,
    headlineLarge = Headline,
    headlineMedium = Headline,
    headlineSmall = Title.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Title,
    titleMedium = Body.copy(fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    titleSmall = Small.copy(fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    bodyLarge = Body,
    bodyMedium = Small,
    bodySmall = Caption,
    labelLarge = Small.copy(fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelMedium = Caption.copy(fontWeight = FontWeight.Medium),
    labelSmall = Caption.copy(fontWeight = FontWeight.Medium),
)

/** Reduces image and animation work on low-power TV boxes. Provided by MainActivity. */
val LocalLightweightUi = staticCompositionLocalOf { false }

/** Blur the backdrop behind Home rows. Provided by MainActivity from the app-wide setting. */
val LocalSoftBackdrops = staticCompositionLocalOf { true }

/** Load sharp full-screen backdrops at TMDB's original size. Provided by MainActivity. */
val LocalHighResBackdrops = staticCompositionLocalOf { false }

@Composable
fun FranklixTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content,
    )
}
