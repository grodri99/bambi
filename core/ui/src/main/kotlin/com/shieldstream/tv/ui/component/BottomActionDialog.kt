package com.shieldstream.tv.ui.component

import androidx.compose.foundation.background
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
import com.shieldstream.tv.ui.theme.AppShapes
import java.util.Locale

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
            .background(Color.Black.copy(alpha = scrimAlpha))
            .then(if (consumeOpeningKeyRelease) Modifier.consumeInitialConfirmationKeyUp() else Modifier)
            .onBackKey(enabled = true, onBack = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.padding(bottom = bottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = remember(title) { title.uppercase(Locale.getDefault()) },
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

@Composable
fun ActionDockCard(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .width(108.dp)
            .height(84.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(AppShapes.Control)
            .background(if (focused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.07f))
            .border(1.5.dp, if (focused) Color.White else Color.Transparent, AppShapes.Control)
            .tvClickable(onClick = onClick)
            .padding(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
