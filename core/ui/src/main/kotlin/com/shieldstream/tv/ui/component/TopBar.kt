package com.shieldstream.tv.ui.component

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes
import androidx.annotation.StringRes
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.strings.R as StringsR
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import com.shieldstream.tv.ui.R
import com.shieldstream.tv.ui.theme.LocalLightweightUi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.sp

enum class TopTab {
    SEARCH,
    HOME,
    EPISODES,
    TV,
    MOVIES,
    MY_LIST,
}

@get:StringRes
private val TopTab.labelResource: Int
    get() = when (this) {
        TopTab.SEARCH -> StringsR.string.common_search
        TopTab.HOME -> StringsR.string.common_home
        TopTab.EPISODES -> StringsR.string.common_episodes
        TopTab.TV -> StringsR.string.common_tv_shows
        TopTab.MOVIES -> StringsR.string.common_movies
        TopTab.MY_LIST -> StringsR.string.common_my_list
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
                painter = painterResource(R.drawable.franklix_logo),
                contentDescription = "Franklix",
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Franklix",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
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
                    label = if (tab == TopTab.SEARCH) null else localizedStringResource(tab.labelResource),
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
                shape = CircleShape,
            )
            .padding(3.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick),
    ) {
        ProfileAvatar(initial = initial, avatar = avatar, size = 32.dp, shape = CircleShape)
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
    // Focus is a faint pill; the selected tab carries a small accent underline.
    val background = if (focused) Color.White.copy(alpha = 0.18f) else Color.Transparent
    val foreground = if (focused || selected) Color.White else Color.White.copy(alpha = 0.6f)
    val showIndicator = selected && label != null
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(background, AppShapes.Pill)
            .drawBehind {
                if (showIndicator) {
                    val width = 18.dp.toPx()
                    val height = 3.dp.toPx()
                    drawRoundRect(
                        color = AppColors.Accent,
                        topLeft = Offset((size.width - width) / 2f, size.height - height - 1.dp.toPx()),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(height / 2f),
                    )
                }
            }
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
                        fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                )
                if (badgeCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .background(AppColors.Alert, AppShapes.Pill)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}
