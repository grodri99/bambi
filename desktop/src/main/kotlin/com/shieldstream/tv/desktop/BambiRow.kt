package com.shieldstream.tv.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Fila horizontal como en Netflix en el PC: se arrastra con el ratón (clic mantenido y mover, con inercia al soltar)
 * y, al pasar por encima, salen flechas ‹ › en los bordes que pasan una «página». La rueda sigue bajando por el Inicio.
 */
@Composable
fun BambiLazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    arrows: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val drag = rememberDraggableState { delta -> state.dispatchRawDelta(-delta) }
    BoxWithConstraints(modifier.hoverable(interaction)) {
        val pagePx = with(LocalDensity.current) { (maxWidth * 0.8f).toPx() }
        LazyRow(
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            modifier = Modifier.draggable(
                state = drag,
                orientation = Orientation.Horizontal,
                onDragStarted = { dragging = true },
                onDragStopped = { velocity ->
                    dragging = false
                    // Un poco de inercia, como al soltar en un móvil.
                    state.animateScrollBy(-velocity * 0.18f, tween(450, easing = LinearOutSlowInEasing))
                },
            ),
            content = content,
        )
        if (arrows) {
            RowArrow(
                visible = hovered && !dragging && state.canScrollBackward,
                left = true,
                width = 48.dp,
                modifier = Modifier.align(Alignment.CenterStart),
            ) { scope.launch { state.animateScrollBy(-pagePx, tween(500)) } }
            RowArrow(
                visible = hovered && !dragging && state.canScrollForward,
                left = false,
                width = 48.dp,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) { scope.launch { state.animateScrollBy(pagePx, tween(500)) } }
        }
    }
}

@Composable
private fun RowArrow(visible: Boolean, left: Boolean, width: Dp, modifier: Modifier, onClick: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier.fillMaxHeight()) {
        val interaction = remember { MutableInteractionSource() }
        val hot by interaction.collectIsHoveredAsState()
        val edge = BambiColors.Background.copy(alpha = if (hot) 0.92f else 0.75f)
        Box(
            Modifier.width(width).fillMaxHeight()
                .background(Brush.horizontalGradient(if (left) listOf(edge, Color.Transparent) else listOf(Color.Transparent, edge)))
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(if (hot) 40.dp else 34.dp).background(BambiColors.Glass, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.ExpandMore,
                    contentDescription = if (left) "Anteriores" else "Siguientes",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp).rotate(if (left) 90f else -90f),
                )
            }
        }
    }
}
