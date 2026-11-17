package com.shieldstream.tv.settings

import com.shieldstream.tv.ui.theme.AppShapes
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.model.SubtitleColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.ui.component.tvClickable
import com.shieldstream.tv.ui.theme.DisplayFont

// Piezas comunes de Ajustes: colores, títulos de sección y tarjetas genéricas.

internal val SettingsBackground = Brush.verticalGradient(listOf(Color(0xFF151622), Color.Black))

internal val CardColor = Color(0xFF1A1C27)

internal val CardFocused = Color(0xFF2A2D3D)

internal val CardShape = RoundedCornerShape(14.dp)

internal val SettingsMuted = Color.White.copy(alpha = 0.6f)

internal val SettingsGreen = Color(0xFF46D369)

internal val SettingsAmber = Color(0xFFF5B83D)

internal val SettingsRed = Color(0xFFFF5A5F)

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = DisplayFont,
        fontSize = 26.sp,
        color = Color.White.copy(alpha = 0.85f),
        modifier = Modifier.padding(top = 18.dp, bottom = 2.dp, start = 4.dp),
    )
}

@Composable
internal fun FocusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .background(if (focused) CardFocused else CardColor, CardShape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                shape = CardShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick ?: {})
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        content(focused)
    }
}

internal fun SubtitleColor.shift(colors: List<SubtitleColor>, delta: Int): SubtitleColor {
    val index = colors.indexOf(this).coerceAtLeast(0)
    return colors[(index + delta + colors.size) % colors.size]
}

/** On/Off setting row: OK toggles, Left selects On, Right selects Off. */
@Composable
internal fun OnOffCard(title: String, summary: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    FocusCard(
        onClick = { onChange(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onChange(true); true }
                Key.DirectionRight -> { onChange(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), AppShapes.Pill)
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_on), selected = enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_off_9847b4), selected = !enabled)
            }
        }
    }
}

@Composable
internal fun SegmentOption(text: String, selected: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = if (selected) Color.Black else Color.White.copy(alpha = 0.7f),
        modifier = Modifier
            .background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
internal fun Arrow(symbol: String, enabled: Boolean) {
    Text(symbol, fontSize = 18.sp, color = if (enabled) Color.White else Color.White.copy(alpha = 0.2f))
}

@Composable
internal fun CyclingSettingCard(title: String, hint: String, value: String, onShift: (Int) -> Unit) {
    FocusCard(
        onClick = { onShift(1) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onShift(-1); true }
                Key.DirectionRight -> { onShift(1); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = SettingsMuted)
            }
            Spacer(Modifier.width(24.dp))
            Text(
                "‹  $value  ›",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}
