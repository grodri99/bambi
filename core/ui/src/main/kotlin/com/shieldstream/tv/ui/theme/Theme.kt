package com.shieldstream.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.OnAccent,
    secondary = Color(0xFFB0B3B8),
    background = AppColors.Background,
    onBackground = Color.White,
    surface = AppColors.Surface,
    onSurface = Color.White,
)

/** Reduces image and animation work on low-power TV boxes. Provided by MainActivity. */
val LocalLightweightUi = staticCompositionLocalOf { false }

/** Blur the backdrop behind Home rows. Provided by MainActivity from the app-wide setting. */
val LocalSoftBackdrops = staticCompositionLocalOf { false }

/** Load sharp full-screen backdrops at TMDB's original size. Provided by MainActivity. */
val LocalHighResBackdrops = staticCompositionLocalOf { false }

@Composable
fun ShieldStreamTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,

        content = content,
    )
}
