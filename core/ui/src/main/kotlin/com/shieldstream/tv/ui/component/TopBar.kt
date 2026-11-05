package com.shieldstream.tv.ui.component

import com.shieldstream.tv.model.tr
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import com.shieldstream.tv.ui.R
import com.shieldstream.tv.ui.theme.LocalLightweightUi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class TopTab(val label: String) {
    SEARCH(tr("Buscar", "Search")),
    HOME(tr("Inicio", "Home")),
    EPISODES(tr("Episodios", "Episodes")),
    TV(tr("Series", "TV Shows")),
    MOVIES(tr("Películas", "Movies")),
    MY_LIST(tr("Mi lista", "My list")),
}

@Composable
fun TopBar(
    selected: TopTab,
    onSelect: (TopTab) -> Unit,
    onSettings: () -> Unit,
    selectedRequester: FocusRequester,
    modifier: Modifier = Modifier,
    profileInitial: String? = null,
    profileAvatar: Int = 0,
    onProfile: () -> Unit = {},
    newEpisodeCount: Int = 0,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.bambi_logo),
                contentDescription = "Bambi",
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(12.dp))
            Image(
                painter = painterResource(R.drawable.bambi_wordmark),
                contentDescription = null,
                modifier = Modifier.height(14.dp),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopTab.entries.forEach { tab ->
                TopBarItem(
                    icon = if (tab == TopTab.SEARCH) AppIcons.Search else null,
                    label = if (tab == TopTab.SEARCH) null else tab.label,
                    selected = tab == selected,
                    badgeCount = if (tab == TopTab.EPISODES) newEpisodeCount else 0,
                    onClick = { onSelect(tab) },
                    modifier = if (tab == selected) Modifier.focusRequester(selectedRequester) else Modifier,
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (profileInitial != null) {
                ProfileButton(initial = profileInitial, avatar = profileAvatar, onClick = onProfile)
            }
            TopBarItem(
                icon = AppIcons.Settings,
                label = null,
                selected = false,
                onClick = onSettings,
            )
        }
    }
}

@Composable
private fun ProfileButton(initial: String, avatar: Int, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (LocalLightweightUi.current) {
        if (focused) 1.12f else 1f
    } else {
        animateFloatAsState(if (focused) 1.12f else 1f, label = "profileScale").value
    }
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = 2.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(3.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick),
    ) {
        ProfileAvatar(initial = initial, avatar = avatar, size = 32.dp)
    }
}

@Composable
private fun TopBarItem(
    icon: ImageVector?,
    label: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (LocalLightweightUi.current) {
        if (focused) 1.03f else 1f
    } else {
        animateFloatAsState(if (focused) 1.03f else 1f, label = "topBarScale").value
    }
    val background = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val foreground = when {
        focused -> Color.Black
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.75f)
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(background, RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = if (label != null) 18.dp else 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = label, tint = foreground, modifier = Modifier.size(22.dp))
        } else if (label != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    color = foreground,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                    ),
                )
                if (badgeCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .background(Color(0xFFE50914), RoundedCornerShape(50))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}
