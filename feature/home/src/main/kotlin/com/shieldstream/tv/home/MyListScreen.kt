package com.shieldstream.tv.home

import com.shieldstream.tv.ui.theme.AppShapes
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.ui.component.ActionMenuDialog
import com.shieldstream.tv.ui.component.ActionMenuItem
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.LineIcons
import com.shieldstream.tv.ui.component.PosterCard
import com.shieldstream.tv.ui.component.tvClickable
import kotlinx.coroutines.delay

@Composable
fun MyListScreen(
    onItemClick: (CatalogItem) -> Unit,
    viewModel: MyListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cardRequester = remember { FocusRequester() }
    var lastCardKey by rememberSaveable { mutableStateOf<String?>(null) }
    var cardToRestore by remember { mutableStateOf<String?>(null) }
    var menuItem by remember { mutableStateOf<CatalogItem?>(null) }
    LaunchedEffect(state is MyListUiState.Content) {
        if (state !is MyListUiState.Content || lastCardKey == null) return@LaunchedEffect
        withFrameNanos { }
        repeat(4) {
            if (runCatching { cardRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(60)
        }
    }
    LaunchedEffect(cardToRestore) {
        if (cardToRestore == null) return@LaunchedEffect
        withFrameNanos { }
        repeat(4) {
            if (runCatching { cardRequester.requestFocus() }.isSuccess) {
                cardToRestore = null
                return@LaunchedEffect
            }
            delay(60)
        }
        cardToRestore = null
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 36.dp, top = 24.dp)) {
        val currentSort = when (val s = state) {
            is MyListUiState.Content -> s.sort
            is MyListUiState.Empty -> s.sort
            MyListUiState.Loading -> MyListSort.RECENT
        }
        val hidingCompleted = when (val s = state) {
            is MyListUiState.Content -> s.hideCompleted
            is MyListUiState.Empty -> s.hideCompleted
            MyListUiState.Loading -> false
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                localizedStringResource(StringsR.string.home_my_list),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.width(24.dp))
            val current = when (val s = state) {
                is MyListUiState.Content -> s.filter
                is MyListUiState.Empty -> s.filter
                MyListUiState.Loading -> MyListFilter.ALL
            }
            listOf(
                MyListFilter.ALL to localizedStringResource(StringsR.string.home_all),
                MyListFilter.TV to localizedStringResource(StringsR.string.home_tv_shows),
                MyListFilter.MOVIES to localizedStringResource(StringsR.string.home_movies),
            ).forEach { (f, label) ->
                FilterChip(label = label, selected = f == current, onClick = { viewModel.setFilter(f) })
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            if (state is MyListUiState.Content) {
                Text(localizedStringResource(StringsR.string.home_hold_ok_to_remove), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(
                MyListSort.NEW_FIRST to localizedStringResource(StringsR.string.home_new_first),
                MyListSort.RECENT to localizedStringResource(StringsR.string.home_recent),
                MyListSort.TITLE to localizedStringResource(StringsR.string.home_a_z),
            ).forEach { (value, label) ->
                FilterChip(label = label, selected = value == currentSort, onClick = { viewModel.setSort(value) })
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            FilterChip(
                label = localizedStringResource(StringsR.string.home_hide_completed),
                selected = hidingCompleted,
                onClick = viewModel::toggleHideCompleted,
            )
        }
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            MyListUiState.Loading -> Text(localizedStringResource(StringsR.string.home_loading), color = Color.White.copy(alpha = 0.6f))
            is MyListUiState.Empty -> Text(
                if (s.filter == MyListFilter.ALL) {
                    localizedStringResource(StringsR.string.home_your_list_is_empty_add_movies_and)
                } else if (s.hideCompleted) {
                    localizedStringResource(StringsR.string.home_no_unfinished_shows_match_these_filters)
                } else {
                    localizedStringResource(StringsR.string.home_nothing_of_this_kind_in_your_list)
                },
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.titleMedium,
            )
            is MyListUiState.Content -> LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(s.items, key = { it.key }) { item ->
                    PosterCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onFocused = { lastCardKey = item.key },
                        onLongClick = { menuItem = item },
                        newEpisodeCount = s.newEpisodeCounts[item.key] ?: 0,
                        width = 140.dp,
                        modifier = if (item.key == lastCardKey) Modifier.focusRequester(cardRequester) else Modifier,
                    )
                }
            }
        }
        }
        menuItem?.let { item ->
            MyListActions(
                item = item,
                onDetails = {
                    menuItem = null
                    onItemClick(item)
                },
                onRemove = {
                    val neighbour = (state as? MyListUiState.Content)?.items?.let { items ->
                        val index = items.indexOfFirst { it.key == item.key }
                        if (index < 0) null else items.getOrNull(index + 1) ?: items.getOrNull(index - 1)
                    }
                    lastCardKey = neighbour?.key
                    cardToRestore = neighbour?.key
                    menuItem = null
                    viewModel.remove(item)
                },
                onCancel = { menuItem = null },
            )
        }
    }
}

@Composable
internal fun MyListActions(
    item: CatalogItem,
    onDetails: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
) = ActionMenuDialog(
    title = item.title,
    onDismiss = onCancel,
    consumeOpeningKeyRelease = true,
    actions = listOf(
        ActionMenuItem(localizedStringResource(StringsR.string.home_cancel), LineIcons.Close, onCancel),
        ActionMenuItem(localizedStringResource(StringsR.string.home_view_details), AppIcons.List, onDetails),
        ActionMenuItem(localizedStringResource(StringsR.string.home_remove), LineIcons.Delete, onRemove),
    ),
)

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .background(
                when {
                    focused -> Color.White
                    selected -> Color.White.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                AppShapes.Pill,
            )
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (focused) Color.Black else Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}
