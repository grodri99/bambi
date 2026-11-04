package com.shieldstream.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shieldstream.tv.model.CatalogItem

val PosterWidth: Dp = 124.dp
private val CardShape = RoundedCornerShape(6.dp)

/**
 * Póster 2:3 con foco estilo Netflix TV: escala + borde blanco. Sin ripple.
 * [onFocused] se usa para actualizar el fondo/hero de la pantalla.
 */
@Composable
fun PosterCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    width: Dp = PosterWidth,
    onLongClick: (() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) 1.1f else 1f

    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .width(width)
            .aspectRatio(2f / 3f)
            // Bambi 1.0.6: el foco (clickable) va ANTES que la escala. Si la escala va delante, el rectángulo que
            // la tarjeta pide "traer a la vista" crece con la animación (1.0 → 1.1) y la lista de filas lo sigue
            // en vertical: el "saltito" de las carátulas en todas las filas menos la primera.
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onLongClick = onLongClick, onClick = onClick)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CardShape)
            .background(Color(0xFF232327))
            .then(if (focused) Modifier.border(2.dp, Color.White, CardShape) else Modifier),
    ) {
        if (item.posterUrl != null) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = item.title,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
            )
        }
        overlay()
    }
}

/**
 * Tarjeta de "Seguir viendo": póster con la etiqueta (T1:E6 · 14 min) y la barra roja de progreso.
 * Mantener OK → [onLongClick] (quitar de la fila).
 */
@Composable
fun ContinueCard(
    item: CatalogItem,
    fraction: Float,
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    PosterCard(
        item = item,
        onClick = onClick,
        modifier = modifier,
        onFocused = onFocused,
        onLongClick = onLongClick,
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(84.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.75f),
                        1f to Color.Black.copy(alpha = 0.95f),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .background(Color(0xFFE50914)),
                )
            }
        }
    }
}

/**
 * Tarjeta "Top 10": número gigante con contorno gris + póster, como en Netflix.
 */
@Composable
fun Top10Card(
    rank: Int,
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    val numberWidth = if (rank >= 10) 104.dp else 70.dp
    val posterHeight = PosterWidth * 1.5f
    Box(modifier = Modifier.width(numberWidth + PosterWidth).height(posterHeight)) {
        val numberStyle = TextStyle(
            fontSize = 150.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-12).sp,
        )
        // Relleno oscuro + contorno gris superpuestos.
        Text(
            text = rank.toString(),
            style = numberStyle.copy(color = Color(0xFF0E0E10)),
            modifier = Modifier.align(Alignment.BottomStart).offset(x = (-4).dp, y = 36.dp),
        )
        Text(
            text = rank.toString(),
            style = numberStyle.copy(color = Color(0xFF8C8C90), drawStyle = Stroke(width = 5f)),
            modifier = Modifier.align(Alignment.BottomStart).offset(x = (-4).dp, y = 36.dp),
        )
        PosterCard(
            item = item,
            onClick = onClick,
            onFocused = onFocused,
            modifier = modifier.align(Alignment.BottomEnd),
        )
    }
}
