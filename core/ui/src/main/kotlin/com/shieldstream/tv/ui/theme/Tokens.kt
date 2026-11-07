package com.shieldstream.tv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared colour tokens. The palette is monochrome: white carries focus and selection, and
 * [Highlight] is the single accent for small details. Other hues are reserved for meaning
 * (status, age ratings, third-party brands).
 */
object AppColors {
    /** Full-screen canvas. */
    val Background = Color.Black

    /** Top stop of subtle full-screen gradients and image placeholders behind backdrops. */
    val BackgroundRaised = Color(0xFF141416)

    /** Panels, cards, dialogs, and unfocused controls. */
    val Surface = Color(0xFF1A1A1D)

    /** Focused panel rows when the control does not invert to white. */
    val SurfaceFocused = Color(0xFF2A2A2E)

    /** Text fields and inset areas inside a [Surface]. */
    val Field = Color(0xFF101012)

    /** Image and skeleton placeholders. */
    val Placeholder = Color(0xFF232327)

    /** Focus, selection, progress, and active indicators. */
    val Accent = Color.White

    /** Content drawn on [Accent]. */
    val OnAccent = Color.Black

    /**
     * The single accent: "new" dots and counts, progress bars, the active rail marker and small
     * section labels. Keep it to small details; large accent areas compete with the artwork.
     */
    val Highlight = Color(0xFFF2B65A)

    /** Restricted age ratings only. */
    val Alert = Color(0xFFE50914)

    /** Translucent backing for pills drawn over artwork. */
    val PillScrim = Color.Black.copy(alpha = 0.62f)

    val Success = Color(0xFF46D369)
    val Warning = Color(0xFFF5B83D)
    val Error = Color(0xFFFF5A5F)
}

/** Shared corner radii. Use these instead of ad-hoc `RoundedCornerShape` values. */
object AppShapes {
    /** Small labels: age ratings, media badges, "new" tags, checkboxes, skeleton text. */
    val Badge = RoundedCornerShape(4.dp)

    /** Buttons, cards, posters, list rows, keys, and text fields. */
    val Control = RoundedCornerShape(12.dp)

    /** Panels, sheets, and dialogs. */
    val Panel = RoundedCornerShape(20.dp)

    /** Chips, pills, and progress bars. */
    val Pill = RoundedCornerShape(50)
}

/** Shared spacing tokens. */
object AppSpacing {
    /** Horizontal inset from the screen edge for browse screens. */
    val ScreenEdge = 48.dp
}
