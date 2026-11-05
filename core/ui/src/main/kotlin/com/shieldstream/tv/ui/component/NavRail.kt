package com.shieldstream.tv.ui.component

import com.shieldstream.tv.model.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val RailCollapsedWidth: Dp = 72.dp
private val RailExpandedWidth: Dp = 250.dp

enum class RailDestination(val label: String, val icon: ImageVector) {
    SEARCH(tr("Buscar", "Search"), AppIcons.Search),
    HOME(tr("Inicio", "Home"), AppIcons.Home),
    TV(tr("Series", "TV Shows"), AppIcons.Tv),
    MOVIES(tr("Películas", "Movies"), AppIcons.Movie),
    SETTINGS(tr("Ajustes", "Settings"), AppIcons.Settings),
}

@Composable
fun NavRail(
    selected: RailDestination,
    onSelect: (RailDestination) -> Unit,
    selectedRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) RailExpandedWidth else RailCollapsedWidth, label = "railWidth")
    val scrim = if (expanded) {
        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.96f), Color.Black.copy(alpha = 0.75f)))
    } else {
        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent))
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .background(scrim)
            .onFocusChanged {
                expanded = it.hasFocus
                onFocusChange(it.hasFocus)
            }
            .focusGroup(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            RailDestination.entries.forEach { dest ->
                RailItem(
                    destination = dest,
                    selected = dest == selected,
                    expanded = expanded,
                    onClick = { onSelect(dest) },
                    modifier = if (dest == selected) Modifier.focusRequester(selectedRequester) else Modifier,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun RailItem(
    destination: RailDestination,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val tint = when {
        focused -> Color.Black
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.55f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .tvClickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(destination.icon, contentDescription = destination.label, tint = tint, modifier = Modifier.size(24.dp))
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(18.dp))
                Text(
                    text = destination.label,
                    color = tint,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
        }
        if (selected && !expanded) {
            Spacer(Modifier.width(4.dp))
        }
    }
}
