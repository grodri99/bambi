package com.shieldstream.tv.home

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.model.Lang
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import com.shieldstream.tv.ui.theme.DisplayFont
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.ContinueInfo
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.strings.localizedCode
import com.shieldstream.tv.model.RowStyle
import com.shieldstream.tv.ui.component.ActionMenuDialog
import com.shieldstream.tv.ui.component.ActionMenuItem
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.ContinueCard
import com.shieldstream.tv.ui.component.LineIcons
import com.shieldstream.tv.ui.component.NewEpisodeCard
import com.shieldstream.tv.ui.component.CatalogPosterCard
import com.shieldstream.tv.ui.component.ProvidePivotScroll
import com.shieldstream.tv.ui.component.Top10Card
import com.shieldstream.tv.ui.component.LabeledPosterCard
import com.shieldstream.tv.ui.component.firstVirtualIndex
import com.shieldstream.tv.ui.component.formatEpisodeDate
import com.shieldstream.tv.ui.component.loopingItems
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.rememberLoopingRowState
import com.shieldstream.tv.ui.component.requestFocusWithRetry
import com.shieldstream.tv.ui.component.shouldLoop
import com.shieldstream.tv.ui.theme.LocalLightweightUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot

// Inicio → filas de pósteres, menús de cada tarjeta y el foco al moverse entre filas.

@Composable
internal fun RowsBrowser(
    rows: List<CatalogRow>,
    onItemClick: (CatalogItem) -> Unit,
    onRemoveContinue: (CatalogItem) -> Unit,
    onRemoveMyList: (CatalogItem) -> Unit,
    onExitTop: (() -> Unit)?,
    onNewEpisodeClick: ((CatalogItem, Episode) -> Unit)? = null,
    onContinueClick: ((CatalogItem, ContinueInfo) -> Unit)? = null,
    onUpNextClick: ((CatalogItem, Episode) -> Unit)? = null,
    onMarkUpNextWatched: ((CatalogItem, Episode) -> Unit)? = null,
    onRemoveUpNext: ((CatalogItem, Episode) -> Unit)? = null,
    onBack: () -> Unit,
    header: String? = null,
) {
    val savedFocus = rememberSaveable { mutableStateOf(firstKey(rows.first())) }
    fun validKey(key: String) = if (findByKey(rows, key) != null) key else firstKey(rows.first())
    var restoreKey by remember { mutableStateOf(Snapshot.withoutReadObservation { validKey(savedFocus.value) }) }
    var lastVerticalRepeat by remember { mutableStateOf(0L) }
    var pendingFocus by remember { mutableStateOf<PendingFocus?>(null) }
    var continueMenu by remember { mutableStateOf<ItemMenu?>(null) }
    var myListMenu by remember { mutableStateOf<ItemMenu?>(null) }
    var upNextMenu by remember { mutableStateOf<UpNextMenu?>(null) }

    var hero by remember { mutableStateOf(Snapshot.withoutReadObservation { findByKey(rows, restoreKey) ?: rows.first().items.first() }) }
    LaunchedEffect(rows) {
        snapshotFlow { savedFocus.value }.collectLatest { key ->
            delay(300)
            hero = findByKey(rows, validKey(key)) ?: rows.first().items.first()
        }
    }

    val restoreRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        restoreRequester.requestFocusWithRetry()
    }
    LaunchedEffect(rows) {
        val pending = pendingFocus ?: return@LaunchedEffect
        restoreKey = pending.itemKey
            ?.let { itemKey -> focusKeyForItem(rows, pending.rowId, itemKey) }
            ?: pending.fallbackKey
        savedFocus.value = restoreKey
        restoreRequester.requestFocusWithRetry()
        pendingFocus = null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(RowBackground)
            .onBackKey(enabled = true) { onBack() }
            .onPreviewKeyEvent { e ->
                val vertical = e.key == Key.DirectionDown || e.key == Key.DirectionUp
                if (e.type != KeyEventType.KeyDown || !vertical || e.nativeKeyEvent.repeatCount == 0) return@onPreviewKeyEvent false
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastVerticalRepeat < com.shieldstream.tv.ui.component.VERTICAL_REPEAT_MS) true else { lastVerticalRepeat = now; false }
            },
    ) {
        if (!LocalLightweightUi.current) HeroBackdrop(hero)
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(236.dp)
                    .padding(start = ContentStart, top = 24.dp, end = 320.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                header?.let {
                    Text(
                        text = it.uppercase(Lang.locale),
                        fontFamily = DisplayFont,
                        fontSize = 30.sp,
                        color = AppColors.Accent,
                    )
                }
                HeroInfo(item = hero)
            }
            ProvidePivotScroll(leading = 51.dp) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 260.dp),
                ) {
                    itemsIndexed(rows, key = { _, row -> row.id }, contentType = { _, row -> row.style }) { rowIndex, row ->
                        CatalogRowView(
                            row = row,
                            focusKey = restoreKey,
                            restoreRequester = restoreRequester,
                            onFocused = { virtual -> savedFocus.value = keyOf(row, virtual) },
                            onItemClick = onItemClick,
                            onNewEpisodeClick = onNewEpisodeClick,
                            onContinueClick = onContinueClick,
                            onUpNextClick = onUpNextClick,
                            onContinueMenu = { virtual, item -> continueMenu = ItemMenu(row, virtual, item) },
                            onMyListMenu = { virtual, item -> myListMenu = ItemMenu(row, virtual, item) },
                            onUpNextMenu = { virtual, item, episode -> upNextMenu = UpNextMenu(row, virtual, item, episode) },
                            modifier = if (rowIndex == 0 && onExitTop != null) {
                                Modifier.onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                        onExitTop()
                                        true
                                    } else {
                                        false
                                    }
                                }
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
        continueMenu?.let { menu ->
            ContinueActions(
                item = menu.item,
                onDetails = {
                    continueMenu = null
                    onItemClick(menu.item)
                },
                onRemove = {
                    pendingFocus = PendingFocus(menu.row.id, itemKey = null, fallbackKey = neighbourKey(rows, menu.row, menu.virtual))
                    onRemoveContinue(menu.item)
                    continueMenu = null
                },
                onCancel = { continueMenu = null },
            )
        }
        myListMenu?.let { menu ->
            MyListActions(
                item = menu.item,
                onDetails = {
                    myListMenu = null
                    onItemClick(menu.item)
                },
                onRemove = {
                    pendingFocus = PendingFocus(menu.row.id, itemKey = null, fallbackKey = neighbourKey(rows, menu.row, menu.virtual))
                    onRemoveMyList(menu.item)
                    myListMenu = null
                },
                onCancel = { myListMenu = null },
            )
        }
        upNextMenu?.let { menu ->
            UpNextActions(
                item = menu.item,
                onDetails = {
                    upNextMenu = null
                    onItemClick(menu.item)
                },
                onMarkWatched = {
                    pendingFocus = PendingFocus(
                        menu.row.id,
                        itemKey = menu.item.key,
                        fallbackKey = neighbourKey(rows, menu.row, menu.virtual),
                    )
                    onMarkUpNextWatched?.invoke(menu.item, menu.episode)
                    upNextMenu = null
                },
                onRemove = {
                    pendingFocus = PendingFocus(menu.row.id, itemKey = null, fallbackKey = neighbourKey(rows, menu.row, menu.virtual))
                    onRemoveUpNext?.invoke(menu.item, menu.episode)
                    upNextMenu = null
                },
                onCancel = { upNextMenu = null },
            )
        }
    }
}

@Composable
private fun ContinueActions(
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
private fun UpNextActions(
    item: CatalogItem,
    onDetails: () -> Unit,
    onMarkWatched: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
) = ActionMenuDialog(
    title = item.title,
    onDismiss = onCancel,
    consumeOpeningKeyRelease = true,
    actions = listOf(
        ActionMenuItem(localizedStringResource(StringsR.string.home_cancel), LineIcons.Close, onCancel),
        ActionMenuItem(localizedStringResource(StringsR.string.home_view_details), AppIcons.List, onDetails),
        ActionMenuItem(localizedStringResource(StringsR.string.home_mark_watched), LineIcons.Eye, onMarkWatched),
        ActionMenuItem(localizedStringResource(StringsR.string.home_remove), LineIcons.Delete, onRemove),
    ),
)

@Composable
private fun CatalogRowView(
    row: CatalogRow,
    focusKey: String?,
    restoreRequester: FocusRequester,
    onFocused: (Int) -> Unit,
    onItemClick: (CatalogItem) -> Unit,
    onNewEpisodeClick: ((CatalogItem, Episode) -> Unit)?,
    onContinueClick: ((CatalogItem, ContinueInfo) -> Unit)?,
    onUpNextClick: ((CatalogItem, Episode) -> Unit)?,
    onContinueMenu: (Int, CatalogItem) -> Unit,
    onMyListMenu: (Int, CatalogItem) -> Unit,
    onUpNextMenu: (Int, CatalogItem, Episode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(start = ContentStart, top = 4.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            if (row.style == RowStyle.CONTINUE || row.style == RowStyle.UP_NEXT || row.style == RowStyle.MY_LIST) {
                Text(
                    text = "   " + localizedStringResource(StringsR.string.home_hold_ok_for_options),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
        }
        key(shouldLoop(row.items.size)) {
            // + 6 dp: lo que crece por cada lado el póster enfocado; así su borde sigue alineado con el título.
            ProvidePivotScroll(leading = ContentStart + 6.dp) {
                LazyRow(
                    state = rememberLoopingRowState(row.items.size),
                    contentPadding = PaddingValues(horizontal = ContentStart, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (row.style == RowStyle.TOP10) 6.dp else 12.dp),
                ) {
                    loopingItems(row.items) { virtual, index, item ->
                        val itemModifier = if (keyOf(row, virtual) == focusKey) Modifier.focusRequester(restoreRequester) else Modifier
                        when (row.style) {
                            RowStyle.POSTER -> CatalogPosterCard(
                                item = item,
                                onClick = { onItemClick(item) },
                                onFocused = { onFocused(virtual) },
                                newEpisodeCount = row.newEpisodeCounts[item.key] ?: 0,
                                modifier = itemModifier,
                            )
                            RowStyle.MY_LIST -> CatalogPosterCard(
                                item = item,
                                onClick = { onItemClick(item) },
                                onFocused = { onFocused(virtual) },
                                onLongClick = { onMyListMenu(virtual, item) },
                                newEpisodeCount = row.newEpisodeCounts[item.key] ?: 0,
                                modifier = itemModifier,
                            )
                            RowStyle.CONTINUE -> {
                                val info = row.progress[item.key]
                                ContinueCard(
                                    item = item,
                                    fraction = info?.fraction ?: 0f,
                                    label = info?.label.orEmpty(),
                                    onClick = {
                                        if (info == null || onContinueClick == null) onItemClick(item)
                                        else onContinueClick(item, info)
                                    },
                                    onLongClick = { onContinueMenu(virtual, item) },
                                    onFocused = { onFocused(virtual) },
                                    newEpisodeCount = row.newEpisodeCounts[item.key] ?: 0,
                                    modifier = itemModifier,
                                )
                            }
                            RowStyle.NEW_EPISODES -> {
                                val episode = row.newEpisodes[item.key]
                                val episodeCode = episode?.localizedCode()
                                val episodeLabel = remember(row.newEpisodes, item.key, episodeCode) {
                                    episode?.let {
                                        listOf(episodeCode.orEmpty(), formatEpisodeDate(it.airDate)).filter { part -> part.isNotEmpty() }.joinToString(" · ")
                                    }.orEmpty()
                                }
                                NewEpisodeCard(
                                    item = item,
                                    label = episodeLabel,
                                    count = row.newEpisodeCounts[item.key] ?: 1,
                                    onClick = {
                                        if (episode == null || onNewEpisodeClick == null) onItemClick(item)
                                        else onNewEpisodeClick(item, episode)
                                    },
                                    onFocused = { onFocused(virtual) },
                                    modifier = itemModifier,
                                )
                            }
                            RowStyle.UP_NEXT -> {
                                val info = row.upNext[item.key]
                                val episode = info?.episode
                                val episodeCode = episode?.localizedCode()
                                val episodeLabel = remember(row.upNext, item.key, episodeCode) {
                                    episode?.let {
                                        listOf(episodeCode.orEmpty(), it.title).filter { part -> part.isNotEmpty() }.joinToString(" · ")
                                    }.orEmpty()
                                }
                                val releaseLabel = remember(row.upNext, item.key) {
                                    formatEpisodeDate(episode?.airDate)
                                }
                                val badgeText = when (upNextBadge(info)) {
                                    UpNextBadge.NEW_SEASON -> localizedStringResource(StringsR.string.home_new_season)
                                    UpNextBadge.NEW_EPISODE -> localizedStringResource(StringsR.string.home_new_episode)
                                    UpNextBadge.UP_NEXT -> localizedStringResource(StringsR.string.home_up_next)
                                }
                                if (episode == null) {
                                    CatalogPosterCard(
                                        item = item,
                                        onClick = { onItemClick(item) },
                                        onFocused = { onFocused(virtual) },
                                        modifier = itemModifier,
                                    )
                                } else {
                                    // Bambi: póster con la etiqueta (A continuación / Nuevo episodio) y el capítulo abajo.
                                    LabeledPosterCard(
                                        item = item,
                                        badge = badgeText,
                                        badgeDot = upNextBadge(info) != UpNextBadge.UP_NEXT,
                                        label = listOf(episodeLabel, releaseLabel).filter { it.isNotEmpty() }.joinToString("\n"),
                                        onClick = {
                                            if (onUpNextClick == null) onItemClick(item)
                                            else onUpNextClick(item, episode)
                                        },
                                        onLongClick = { onUpNextMenu(virtual, item, episode) },
                                        onFocused = { onFocused(virtual) },
                                        modifier = itemModifier,
                                    )
                                }
                            }
                            RowStyle.TOP10 -> Top10Card(
                                rank = index + 1,
                                item = item,
                                onClick = { onItemClick(item) },
                                onFocused = { onFocused(virtual) },
                                newEpisodeCount = row.newEpisodeCounts[item.key] ?: 0,
                                modifier = itemModifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun keyOf(row: CatalogRow, virtual: Int) = "${row.id}/$virtual"

private fun firstKey(row: CatalogRow) = keyOf(row, firstVirtualIndex(row.items.size))

private fun findByKey(rows: List<CatalogRow>, key: String?): CatalogItem? {
    if (key == null) return null
    val row = rows.firstOrNull { it.id == key.substringBeforeLast('/') } ?: return null
    if (row.items.isEmpty()) return null
    val virtual = key.substringAfterLast('/').toIntOrNull() ?: return null
    if (!shouldLoop(row.items.size) && virtual >= row.items.size) return null
    return row.items[virtual % row.items.size]
}

internal fun focusKeyForItem(rows: List<CatalogRow>, rowId: String, itemKey: String): String? {
    val row = rows.firstOrNull { it.id == rowId } ?: return null
    val index = row.items.indexOfFirst { it.key == itemKey }
    if (index < 0) return null
    return keyOf(row, firstVirtualIndex(row.items.size) + index)
}

private fun neighbourKey(rows: List<CatalogRow>, row: CatalogRow, virtual: Int): String {
    val newSize = row.items.size - 1
    if (newSize <= 0) {
        val below = rows.getOrNull(rows.indexOf(row) + 1) ?: rows.first()
        return firstKey(below)
    }
    val index = minOf(virtual % row.items.size, newSize - 1)
    return keyOf(row, firstVirtualIndex(newSize) + index)
}
