package com.shieldstream.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE50914),
    onPrimary = Color.White,
    secondary = Color(0xFFB0B3B8),
    background = Color(0xFF0E0E10),
    onBackground = Color.White,
    surface = Color(0xFF1A1A1D),
    onSurface = Color.White,
)

/** Reduces image and animation work on low-power TV boxes. Provided by MainActivity. */
val LocalLightweightUi = staticCompositionLocalOf { false }

@Composable
fun ShieldStreamTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
