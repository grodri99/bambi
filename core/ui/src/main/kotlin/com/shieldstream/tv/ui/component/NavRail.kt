package com.shieldstream.tv.ui.component

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes

/** Width the collapsed rail occupies at the left edge of every main tab. */
val RailCollapsedWidth: Dp = 72.dp

/** Start inset for browse content so it clears the collapsed rail. */
val RailContentStart: Dp = RailCollapsedWidth + 24.dp

private val RailExpandedWidth: Dp = 260.dp

enum class NavTab(val icon: ImageVector) {
    SEARCH(AppIcons.Search),
    HOME(AppIcons.Home),
    MOVIES(AppIcons.Movie),
    TV(AppIcons.Tv),
    EPISODES(AppIcons.Episodes),
    MY_LIST(AppIcons.Bookmark),
}

@get:StringRes
private val NavTab.labelResource: Int
    get() = when (this) {
        NavTab.SEARCH -> StringsR.string.common_search
        NavTab.HOME -> StringsR.string.common_home
        NavTab.MOVIES -> StringsR.string.common_movies
        NavTab.TV -> StringsR.string.common_tv_shows
        NavTab.EPISODES -> StringsR.string.common_episodes
        NavTab.MY_LIST -> StringsR.string.common_my_list
    }

/**
 * Left navigation rail. It is drawn as an overlay: expanding on focus widens only the rail and
 * never re-lays out the content behind it. Entering from the content with Left always lands on
 * the selected tab rather than the geometrically closest item. Right always moves to
 * [contentRequester]: content containers start at x = 0 behind the rail, so geometric focus
 * search would not treat them as being to the right.
 */
@Composable
fun NavRail(
    selected: NavTab,
    onSelect: (NavTab) -> Unit,
    onSettings: () -> Unit,
    selectedRequester: FocusRequester,
    modifier: Modifier = Modifier,
    contentRequester: FocusRequester = FocusRequester.Default,
    focusable: Boolean = true,
    profileInitial: String? = null,
    profileAvatar: Int = 0,
    onProfile: () -> Unit = {},
    newEpisodeCount: Int = 0,
    onFocusChange: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val scrim = if (expanded) ExpandedScrim else CollapsedScrim
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(if (expanded) RailExpandedWidth else RailCollapsedWidth)
            // Cache the rail in its own GPU layer: it is static while the rows scroll beneath it,
            // so each frame composites one texture instead of redrawing icons, text and gradient.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .background(scrim)
            .onFocusChanged {
                expanded = it.hasFocus
                onFocusChange(it.hasFocus)
            }
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection == FocusDirection.Left) selectedRequester.requestFocus()
                }
            }
            .focusGroup(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (profileInitial != null) {
                RailItem(
                    label = localizedStringResource(StringsR.string.common_switch_profile),
                    selected = false,
                    expanded = expanded,
                    onClick = onProfile,
                    contentRequester = contentRequester,
                    focusable = focusable,
                ) {
                    ProfileAvatar(initial = profileInitial, avatar = profileAvatar, size = 28.dp, shape = CircleShape)
                }
            }
            Spacer(Modifier.weight(1f))
            NavTab.entries.forEach { tab ->
                val label = localizedStringResource(tab.labelResource)
                RailItem(
                    label = label,
                    selected = tab == selected,
                    expanded = expanded,
                    badgeCount = if (tab == NavTab.EPISODES) newEpisodeCount else 0,
                    onClick = { onSelect(tab) },
                    contentRequester = contentRequester,
                    focusable = focusable,
                    modifier = if (tab == selected) Modifier.focusRequester(selectedRequester) else Modifier,
                ) { tint -> RailIcon(tab.icon, label.takeUnless { expanded }, tint) }
            }
            Spacer(Modifier.weight(1f))
            val settingsLabel = localizedStringResource(StringsR.string.common_settings)
            RailItem(
                label = settingsLabel,
                selected = false,
                expanded = expanded,
                onClick = onSettings,
                contentRequester = contentRequester,
                focusable = focusable,
            ) { tint -> RailIcon(AppIcons.Settings, settingsLabel.takeUnless { expanded }, tint) }
        }
    }
}

private val CollapsedScrim = Brush.horizontalGradient(
    0f to Color.Black.copy(alpha = 0.7f),
    1f to Color.Transparent,
)
private val ExpandedScrim = Brush.horizontalGradient(
    0f to Color.Black.copy(alpha = 0.96f),
    1f to Color.Black.copy(alpha = 0.82f),
)

@Composable
private fun RailIcon(icon: ImageVector, contentDescription: String?, tint: Color) {
    Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(24.dp))
}

@Composable
private fun RailItem(
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    contentRequester: FocusRequester,
    focusable: Boolean,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    icon: @Composable (tint: Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val tint = when {
        focused -> Color.Black
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.6f)
    }
    // Draw the shaped background directly instead of clipping; some TV render paths drop clips.
    val background = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(background, AppShapes.Pill)
            .focusProperties {
                canFocus = focusable
                right = contentRequester
            }
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            icon(tint)
            if (badgeCount > 0 && !expanded) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .size(8.dp)
                        .background(AppColors.Alert, CircleShape),
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                color = tint,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
                ),
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (badgeCount > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .background(AppColors.Alert, AppShapes.Pill)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}
