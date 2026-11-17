package com.shieldstream.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
    // Se leen dentro de la composición para que un cambio de acento (perfil) repinte la app.
    val accent = AppColors.Accent
    val onAccent = AppColors.OnAccent
    val accentDark = AppColors.AccentDark
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            secondary = accentDark,
            background = AppColors.Background,
            onBackground = Color.White,
            surface = AppColors.Surface,
            onSurface = Color.White,
        ),
        content = content,
    )
}
