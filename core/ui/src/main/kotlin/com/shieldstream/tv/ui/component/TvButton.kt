package com.shieldstream.tv.ui.component

import com.shieldstream.tv.ui.theme.AppShapes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.ui.theme.LocalLightweightUi

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val duration = if (LocalLightweightUi.current) 0 else 150
    val background by animateColorAsState(
        if (focused) Color.White else Color.White.copy(alpha = 0.16f),
        tween(duration),
        label = "buttonBackground",
    )
    val content by animateColorAsState(if (focused) Color.Black else Color.White, tween(duration), label = "buttonContent")

    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .background(background)
            .tvClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        )
    }
}
