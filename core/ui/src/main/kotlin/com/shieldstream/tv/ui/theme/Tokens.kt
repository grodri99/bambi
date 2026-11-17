package com.shieldstream.tv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.model.ProfileTheme

/**
 * Colores compartidos con el estilo de Bambi: fondo casi negro, el foco invierte a blanco y el rojo de
 * Bambi marca progreso, selección, novedades y los indicadores activos.
 */
object AppColors {
    /** Full-screen canvas. */
    val Background = Color(0xFF0E0E10)

    /** Top stop of subtle full-screen gradients and image placeholders behind backdrops. */
    val BackgroundRaised = Color(0xFF151622)

    /** Panels, cards, dialogs, and unfocused controls. */
    val Surface = Color(0xFF1A1C27)

    /** Focused panel rows when the control does not invert to white. */
    val SurfaceFocused = Color(0xFF2A2D3D)

    /** Text fields and inset areas inside a [Surface]. */
    val Field = Color(0xFF101012)

    /** Image and skeleton placeholders. */
    val Placeholder = Color(0xFF232327)

    /** Acento del perfil activo: progreso, selección e indicadores activos. */
    var Accent by mutableStateOf(Color(0xFFE50914))
        private set

    /** Variante oscura del acento (degradados y estados secundarios). */
    var AccentDark by mutableStateOf(Color(0xFF8C0610))
        private set

    /** Content drawn on [Accent]. */
    var OnAccent by mutableStateOf(Color.White)
        private set

    /**
     * The single accent: "new" dots and counts, progress bars, the active rail marker and small
     * section labels. Keep it to small details; large accent areas compete with the artwork.
     */
    var Highlight by mutableStateOf(Color(0xFFE50914))
        private set

    /** Restricted age ratings only. */
    val Alert = Color(0xFFE50914)

    /** Translucent backing for pills drawn over artwork. */
    val PillScrim = Color.Black.copy(alpha = 0.62f)

    val Success = Color(0xFF46D369)
    val Warning = Color(0xFFF5B83D)
    val Error = Color(0xFFFF5A5F)

    /** Aplica el acento del perfil. Las pantallas leen estos valores como estado de Compose. */
    fun applyTheme(theme: ProfileTheme) {
        Accent = Color(theme.accent)
        AccentDark = Color(theme.accentDark)
        OnAccent = Color(theme.onAccent)
        Highlight = Color(theme.accent)
    }
}

/** Shared corner radii. Use these instead of ad-hoc `RoundedCornerShape` values. */
object AppShapes {
    /** Small labels: age ratings, media badges, "new" tags, checkboxes, skeleton text. */
    val Badge = RoundedCornerShape(4.dp)

    /** Buttons, cards, posters, list rows, keys, and text fields. */
    val Control = RoundedCornerShape(14.dp)

    /** Panels, sheets, and dialogs. */
    val Panel = RoundedCornerShape(16.dp)

    /** Chips, pills, and progress bars. */
    val Pill = RoundedCornerShape(50)
}

/** Shared spacing tokens. */
object AppSpacing {
    /** Horizontal inset from the screen edge for browse screens. */
    val ScreenEdge = 48.dp
}
