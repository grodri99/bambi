package com.shieldstream.tv.ui.component

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import com.shieldstream.tv.ui.theme.LocalLightweightUi
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BottomActionDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = 0.82f,
    bottomPadding: Dp = 44.dp,
    consumeOpeningKeyRelease: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            // Se oscurece más hacia abajo, donde va el menú; arriba se sigue intuyendo la pantalla de detrás.
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = scrimAlpha * 0.75f),
                    0.55f to Color.Black.copy(alpha = scrimAlpha),
                    1f to Color.Black.copy(alpha = (scrimAlpha + 0.14f).coerceAtMost(1f)),
                ),
            )
            .then(if (consumeOpeningKeyRelease) Modifier.consumeInitialConfirmationKeyUp() else Modifier)
            .onBackKey(enabled = true, onBack = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.padding(bottom = bottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 640.dp),
            )
            content()
        }
    }
}

data class ActionMenuItem(
    val text: String,
    val icon: ImageVector?,
    val onClick: () -> Unit,
)

/**
 * Standard bottom action menu. [consumeOpeningKeyRelease] is only for menus opened by long-press;
 * normal-click menus must deliver their first intentional OK press.
 */
@Composable
fun ActionMenuDialog(
    title: String,
    actions: List<ActionMenuItem>,
    onDismiss: () -> Unit,
    scrimAlpha: Float = 0.82f,
    consumeOpeningKeyRelease: Boolean = false,
) {
    val firstActionRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        firstActionRequester.requestFocusWithRetry()
    }
    BottomActionDialog(
        title = title,
        onDismiss = onDismiss,
        scrimAlpha = scrimAlpha,
        consumeOpeningKeyRelease = consumeOpeningKeyRelease,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 8.dp),
        ) {
            actions.forEachIndexed { index, action ->
                ActionDockCard(
                    text = action.text,
                    icon = action.icon,
                    onClick = action.onClick,
                    modifier = if (index == 0) Modifier.focusRequester(firstActionRequester) else Modifier,
                )
            }
        }
    }
}

/**
 * Tarjeta de acción estilo Bambi: ancha para que el texto quepa entero en dos líneas ("Marcar como visto",
 * "Reproducir episodio"), y al enfocarla pasa a blanco con el contenido en negro y crece un poco.
 */
@Composable
fun ActionDockCard(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (LocalLightweightUi.current) {
        if (focused) 1.06f else 1f
    } else {
        animateFloatAsState(if (focused) 1.06f else 1f, label = "actionCardScale").value
    }
    val content = if (focused) Color.Black else Color.White
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .width(156.dp)
            .height(104.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(ActionCardShape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.10f))
            .border(1.dp, if (focused) Color.Transparent else Color.White.copy(alpha = 0.14f), ActionCardShape)
            .tvClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = text,
            color = content,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val ActionCardShape = RoundedCornerShape(14.dp)
