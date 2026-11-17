package com.shieldstream.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.model.Download
import com.shieldstream.tv.model.DownloadStatus
import com.shieldstream.tv.ui.theme.AppColors

/** Resumen de las descargas para el indicador de arriba a la derecha. */
data class DownloadIndicatorState(
    /** Descargando o en cola. */
    val active: Int,
    /** Progreso de la que se está bajando ahora (0–100), si hay alguna. */
    val percent: Int?,
    /** Preparando (buscando fuentes): "2/10". */
    val preparing: String? = null,
    /** Descargas terminadas (sin nada en marcha, el indicador queda como acceso a ellas). */
    val done: Int = 0,
)

/** null si no hay ninguna descarga (ni terminada, ni en marcha, ni preparándose). */
fun downloadIndicatorState(downloads: List<Download>, preparing: String? = null): DownloadIndicatorState? {
    if (downloads.isEmpty() && preparing == null) return null
    val active = downloads.filter { it.isActive }
    val running = active.firstOrNull { it.status == DownloadStatus.RUNNING }
    return DownloadIndicatorState(
        active = active.size,
        percent = running?.let { (it.progress * 100).toInt() },
        preparing = preparing,
        done = downloads.count { it.isDone },
    )
}

/** Hay algo moviéndose (no solo descargas ya terminadas). */
val DownloadIndicatorState.busy: Boolean get() = active > 0 || preparing != null

/**
 * Píldora con la flecha de descarga, el porcentaje y una barrita de progreso. Con [onClick] es un botón (abre
 * Descargas); sin él, solo informa.
 */
@Composable
fun DownloadIndicator(state: DownloadIndicatorState, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) Color.Black else Color.White
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) Color.White else Color.Black.copy(alpha = 0.55f))
            .border(1.dp, if (focused) Color.Transparent else Color.White.copy(alpha = 0.18f), shape)
            .then(if (onClick != null) Modifier.tvClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(LineIcons.Download, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            val text = buildString {
                when {
                    state.percent != null -> append("${state.percent}%")
                    state.preparing != null -> append(state.preparing)
                    state.active > 0 -> append("…")
                    // Nada en marcha: acceso a lo descargado, con cuántos hay.
                    else -> append(state.done)
                }
                if (state.active > 1) append("  ·  ${state.active}")
            }
            Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        state.percent?.let { p ->
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .width(64.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(fg.copy(alpha = 0.2f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(p / 100f)
                        .height(3.dp)
                        .background(if (focused) Color.Black else AppColors.Accent),
                )
            }
        }
    }
}
