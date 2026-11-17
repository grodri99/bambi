package com.shieldstream.tv.player

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.strings.rememberTexts
import com.shieldstream.tv.model.SubtitleAppearance
import com.shieldstream.tv.strings.labelResource
import com.shieldstream.tv.model.cycle
import com.shieldstream.tv.model.SubtitleEdge
import com.shieldstream.tv.model.SubtitleFont
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.shieldstream.tv.ui.component.tvClickable

// Panel de pistas → editor de la apariencia de los subtítulos en vivo.

@Composable
private fun AppearanceControlRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onShift: (Int) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val foreground = if (focused) Color.Black else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onShift(-1); true }
                    Key.DirectionRight -> { onShift(1); true }
                    else -> false
                }
            }
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .tvClickable(onClick = { onShift(1) })
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = title,
            color = foreground,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(text = "‹", color = foreground.copy(alpha = 0.65f), style = MaterialTheme.typography.titleMedium)
        Text(
            text = value,
            color = foreground,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(text = "›", color = foreground.copy(alpha = 0.65f), style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Editor de subtítulos en vivo: pausa el vídeo, deja la imagen a la vista y pinta un subtítulo de ejemplo con el
 * estilo actual mientras se cambian tamaño, posición, fuente, colores, fondo y borde. Al salir, si estaba
 * reproduciendo, sigue.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun LiveSubtitleEditor(
    player: Player,
    appearance: SubtitleAppearance,
    onChange: (SubtitleAppearance) -> Unit,
) {
    val texts = rememberTexts()
    DisposableEffect(player) {
        val wasPlaying = player.isPlaying || player.playWhenReady
        player.pause()
        onDispose { if (wasPlaying) player.play() }
    }
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { initialFocus.requestFocus() }
    }
    val sample = texts.get(StringsR.string.subtitle_sample)
    Box(Modifier.fillMaxSize()) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx -> androidx.media3.ui.SubtitleView(ctx) },
            update = { view ->
                view.applySubtitleAppearance(appearance.copy(applyEmbeddedStyles = false))
                view.setCues(listOf(androidx.media3.common.text.Cue.Builder().setText(sample).build()))
            },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier
                .fillMaxHeight()
                .width(400.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.92f),
                        0.85f to Color.Black.copy(alpha = 0.75f),
                        1f to Color.Transparent,
                    ),
                )
                .padding(start = 40.dp, end = 32.dp, top = 40.dp),
        ) {
            Text(
                text = localizedStringResource(StringsR.string.subtitle_live_title),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 14.dp),
            )
            Text(
                text = localizedStringResource(StringsR.string.subtitle_live_hint),
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 12.dp),
            )
            val a = appearance
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_size),
                value = localizedStringResource(StringsR.string.subtitle_size_value, a.sizePercent),
                modifier = Modifier.focusRequester(initialFocus),
                onShift = { d ->
                    onChange(a.copy(sizePercent = (a.sizePercent + d * SubtitleAppearance.SIZE_STEP).coerceIn(SubtitleAppearance.MIN_SIZE, SubtitleAppearance.MAX_SIZE)))
                },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_position),
                value = localizedStringResource(StringsR.string.subtitle_position_value, a.verticalPositionPercent),
                onShift = { d ->
                    onChange(a.copy(verticalPositionPercent = (a.verticalPositionPercent + d * SubtitleAppearance.POSITION_STEP).coerceIn(SubtitleAppearance.MIN_POSITION, SubtitleAppearance.MAX_POSITION)))
                },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_font),
                value = localizedStringResource(a.font.labelResource),
                onShift = { d -> onChange(a.copy(font = a.font.cycle(SubtitleFont.entries, d))) },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_text_color),
                value = localizedStringResource(a.textColor.labelResource),
                onShift = { d -> onChange(a.copy(textColor = a.textColor.cycle(SubtitleAppearance.textColors, d))) },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_background_color),
                value = localizedStringResource(a.backgroundColor.labelResource),
                onShift = { d -> onChange(a.copy(backgroundColor = a.backgroundColor.cycle(SubtitleAppearance.backgroundColors, d))) },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_outline_color),
                value = localizedStringResource(a.outlineColor.labelResource),
                onShift = { d -> onChange(a.copy(outlineColor = a.outlineColor.cycle(SubtitleAppearance.outlineColors, d))) },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_edge),
                value = localizedStringResource(a.edge.labelResource),
                onShift = { d -> onChange(a.copy(edge = a.edge.cycle(SubtitleEdge.entries, d))) },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_embedded_style),
                value = localizedStringResource(
                    if (a.applyEmbeddedStyles) StringsR.string.subtitle_style_original else StringsR.string.subtitle_style_custom,
                ),
                onShift = { onChange(a.copy(applyEmbeddedStyles = !a.applyEmbeddedStyles)) },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_reset),
                value = "",
                onShift = { onChange(SubtitleAppearance()) },
            )
        }
    }
}
