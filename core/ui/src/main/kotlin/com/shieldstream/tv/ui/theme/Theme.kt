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

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 52.sp, lineHeight = 56.sp),
    displayMedium = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, lineHeight = 46.sp),
    displaySmall = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 27.sp, lineHeight = 31.sp),
    headlineSmall = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

/** Reduces image and animation work on low-power TV boxes. Provided by MainActivity. */
val LocalLightweightUi = staticCompositionLocalOf { false }

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
