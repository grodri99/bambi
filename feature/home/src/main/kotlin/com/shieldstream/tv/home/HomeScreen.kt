package com.shieldstream.tv.home

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes
import com.shieldstream.tv.ui.theme.AppSpacing
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.model.Lang
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.ContinueInfo
import com.shieldstream.tv.model.FeaturedItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.strings.localizedCode
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.RowStyle
import com.shieldstream.tv.ui.component.ActionMenuDialog
import com.shieldstream.tv.ui.component.ActionMenuItem
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.ContinueCard
import com.shieldstream.tv.ui.component.LineIcons
import com.shieldstream.tv.ui.component.NewEpisodeCard
import com.shieldstream.tv.ui.component.CatalogPosterCard
import com.shieldstream.tv.ui.component.PosterWidth
import com.shieldstream.tv.ui.component.ProvidePivotScroll
import com.shieldstream.tv.ui.component.Top10Card
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.UpNextCard
import com.shieldstream.tv.ui.component.firstVirtualIndex
import com.shieldstream.tv.ui.component.formatEpisodeDate
import com.shieldstream.tv.ui.component.loopingItems
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.SoftBackdropImage
import com.shieldstream.tv.ui.component.rememberLoopingRowState
import com.shieldstream.tv.ui.component.requestFocusWithRetry
import com.shieldstream.tv.ui.component.shouldLoop
import com.shieldstream.tv.ui.theme.LocalLightweightUi
import com.shieldstream.tv.ui.theme.LocalSoftBackdrops
import com.shieldstream.tv.ui.theme.LocalHighResBackdrops
import com.shieldstream.tv.model.BackdropUrls
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import java.util.Locale

private val ContentStart = AppSpacing.ScreenEdge
private val RowBackground = AppColors.Background

internal enum class UpNextBadge { UP_NEXT, NEW_EPISODE, NEW_SEASON }

internal fun upNextBadge(info: com.shieldstream.tv.model.UpNextInfo?): UpNextBadge = when {
    info?.isNewEpisode == true && info.isNewSeason -> UpNextBadge.NEW_SEASON
    info?.isNewEpisode == true -> UpNextBadge.NEW_EPISODE
    else -> UpNextBadge.UP_NEXT
}

@Composable
fun HomeScreen(
    filter: HomeFilter,
    onItemClick: (CatalogItem) -> Unit,
    onContinueClick: (CatalogItem, ContinueInfo) -> Unit,
    onNewEpisodeClick: (CatalogItem, Episode) -> Unit,
    onUpNextClick: (CatalogItem, Episode) -> Unit,
    onOpenCategory: (Category) -> Unit,
    onContentReady: () -> Unit = {},
    topBar: @Composable (Modifier) -> Unit,
    topBarRequester: FocusRequester,
    focusTopBar: () -> Unit,
    preferTopBarFocus: Boolean,
    onTopBarFocusConsumed: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(key = "home_${filter.name}"),
) {
    LaunchedEffect(filter) { viewModel.load(filter) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenCategories.collectAsStateWithLifecycle()

    when (val s = state) {
        HomeUiState.Loading -> Column(Modifier.fillMaxSize().background(BillboardBackground)) {
            topBar(Modifier)
            LaunchedEffect(Unit) {
                if (preferTopBarFocus) {
                    withFrameNanos { }
                    focusTopBar()
                }
            }
            BillboardSkeleton()
        }
        is HomeUiState.Error -> Column(Modifier.fillMaxSize().background(BillboardBackground)) {
            topBar(Modifier)
            HomeError(
                s.message,
                onRetry = { viewModel.load(filter, force = true) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        is HomeUiState.Content -> {
            LaunchedEffect(Unit) { onContentReady() }
            HomeContent(
            rows = s.rows,
            featured = s.featured,
            categories = remember(filter, hidden) {
                when {
                    viewModel.kidsProfile -> Category.forFilter(filter).filter { it in Category.KIDS_SAFE }
                    else -> Category.forFilter(filter).filter { it.name !in hidden }.ifEmpty { Category.forFilter(filter) }
                }
            },
            onItemClick = { item ->
                viewModel.cancelBackgroundPrefetch()
                onItemClick(item)
            },
            onContinueClick = { item, info ->
                viewModel.cancelBackgroundPrefetch()
                onContinueClick(item, info)
            },
            onNewEpisodeClick = { item, episode ->
                viewModel.cancelBackgroundPrefetch()
                onNewEpisodeClick(item, episode)
            },
            onUpNextClick = { item, episode ->
                viewModel.cancelBackgroundPrefetch()
                onUpNextClick(item, episode)
            },
            onOpenCategory = onOpenCategory,
            onRemoveContinue = viewModel::removeFromContinue,
            onRemoveMyList = viewModel::removeFromMyList,
            onMarkUpNextWatched = viewModel::markUpNextWatched,
            onRemoveUpNext = viewModel::removeUpNext,
            topBar = topBar,
            topBarRequester = topBarRequester,
            focusTopBar = focusTopBar,
            preferTopBarFocus = preferTopBarFocus,
            onTopBarFocusConsumed = onTopBarFocusConsumed,
            )
        }
    }
}

private enum class TopFocus { HERO, CHIPS }
private data class ItemMenu(val row: CatalogRow, val virtual: Int, val item: CatalogItem)
private data class UpNextMenu(val row: CatalogRow, val virtual: Int, val item: CatalogItem, val episode: Episode)
private data class PendingFocus(val rowId: String, val itemKey: String?, val fallbackKey: String)

@Composable
private fun HomeContent(
    rows: List<CatalogRow>,
    featured: List<FeaturedItem>,
    categories: List<Category>,
    onItemClick: (CatalogItem) -> Unit,
    onContinueClick: (CatalogItem, ContinueInfo) -> Unit,
    onNewEpisodeClick: (CatalogItem, Episode) -> Unit,
    onUpNextClick: (CatalogItem, Episode) -> Unit,
    onOpenCategory: (Category) -> Unit,
    onRemoveContinue: (CatalogItem) -> Unit,
    onRemoveMyList: (CatalogItem) -> Unit,
    onMarkUpNextWatched: (CatalogItem, Episode) -> Unit,
    onRemoveUpNext: (CatalogItem, Episode) -> Unit,
    topBar: @Composable (Modifier) -> Unit,
    topBarRequester: FocusRequester,
    focusTopBar: () -> Unit,
    preferTopBarFocus: Boolean,
    onTopBarFocusConsumed: () -> Unit,
) {
    var inRows by rememberSaveable { mutableStateOf(rows.any { it.style == RowStyle.CONTINUE }) }
    var topFocus by rememberSaveable { mutableStateOf(TopFocus.HERO) }
    val saveable = rememberSaveableStateHolder()
    // Swapping the rows view and the billboard disposes the focused item, and the new view only
    // requests focus a frame later. Park focus on this holder meanwhile; otherwise it falls to the
    // top bar and the screen jumps.
    var parkingFocus by remember { mutableStateOf(false) }
    val parkingRequester = remember { FocusRequester() }
    fun switchView(change: () -> Unit) {
        parkingFocus = true
        change()
    }
    LaunchedEffect(parkingFocus) {
        if (parkingFocus) runCatching { parkingRequester.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(parkingRequester)
            .onFocusChanged { if (it.hasFocus && !it.isFocused) parkingFocus = false }
            .focusProperties { canFocus = parkingFocus }
            .focusTarget(),
    ) {
        if (inRows && rows.isNotEmpty()) saveable.SaveableStateProvider("rows") {
            RowsBrowser(
                rows = rows,
                onItemClick = onItemClick,
                onContinueClick = onContinueClick,
                onNewEpisodeClick = onNewEpisodeClick,
                onUpNextClick = onUpNextClick,
                onRemoveContinue = onRemoveContinue,
                onRemoveMyList = onRemoveMyList,
                onMarkUpNextWatched = onMarkUpNextWatched,
                onRemoveUpNext = onRemoveUpNext,
                onExitTop = {
                    topFocus = TopFocus.CHIPS
                    switchView { inRows = false }
                },
                onBack = {
                    topFocus = if (featured.isNotEmpty()) TopFocus.HERO else TopFocus.CHIPS
                    switchView { inRows = false }
                },
            )
        } else saveable.SaveableStateProvider("billboard") {
            Billboard(
                featured = featured,
                categories = categories,
                initialFocus = topFocus,
                onFocusChange = { topFocus = it },
                onItemClick = onItemClick,
                onOpenCategory = onOpenCategory,
                onEnterRows = { if (rows.isNotEmpty()) switchView { inRows = true } },
                topBar = topBar,
                topBarRequester = topBarRequester,
                focusTopBar = focusTopBar,
                preferTopBarFocus = preferTopBarFocus,
                onTopBarFocusConsumed = onTopBarFocusConsumed,
            )
        }
    }
}

@Composable
private fun Billboard(
    featured: List<FeaturedItem>,
    categories: List<Category>,
    initialFocus: TopFocus,
    onFocusChange: (TopFocus) -> Unit,
    onItemClick: (CatalogItem) -> Unit,
    onOpenCategory: (Category) -> Unit,
    onEnterRows: () -> Unit,
    topBar: @Composable (Modifier) -> Unit,
    topBarRequester: FocusRequester,
    focusTopBar: () -> Unit,
    preferTopBarFocus: Boolean,
    onTopBarFocusConsumed: () -> Unit,
) {
    val heroRequester = remember { FocusRequester() }
    val chipsRequester = remember { FocusRequester() }
    var topBarFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        when {
            preferTopBarFocus -> {
                focusTopBar()
                onTopBarFocusConsumed()
            }
            initialFocus == TopFocus.HERO && featured.isNotEmpty() -> heroRequester.requestFocusWithRetry()
            else -> chipsRequester.requestFocusWithRetry()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .onBackKey(enabled = !topBarFocused) { focusTopBar() },
    ) {
        if (featured.isNotEmpty()) {
            FeaturedCarousel(
                items = featured,
                onOpen = onItemClick,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(heroRequester)
                    .focusProperties {
                        up = topBarRequester
                        down = chipsRequester
                    }
                    .onFocusChanged { if (it.isFocused) onFocusChange(TopFocus.HERO) },
            )
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(112.dp)
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.75f), 1f to Color.Transparent)),
        )
        topBar(
            Modifier
                .align(Alignment.TopCenter)
                .onFocusChanged { topBarFocused = it.hasFocus },
        )
        CategoryChips(
            categories = categories,
            onOpen = onOpenCategory,
            requester = chipsRequester,
            upRequester = if (featured.isNotEmpty()) heroRequester else topBarRequester,
            onFocused = { onFocusChange(TopFocus.CHIPS) },
            onDown = onEnterRows,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.9f)))
                .padding(bottom = 6.dp),
        )
    }
}

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
                if (now - lastVerticalRepeat < 130) true else { lastVerticalRepeat = now; false }
            },
    ) {
        if (!LocalLightweightUi.current) HeroBackdrop(hero)
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(start = ContentStart, top = 24.dp, end = 320.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                header?.let {
                    Text(
                        text = it.uppercase(Lang.locale),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.12.em,
                        ),
                        color = AppColors.Highlight,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                HeroInfo(item = hero)
            }
            ProvidePivotScroll(leading = 46.dp) {
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
            modifier = Modifier.padding(start = ContentStart, top = 22.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.92f),
            )
            if (row.style == RowStyle.CONTINUE || row.style == RowStyle.UP_NEXT || row.style == RowStyle.MY_LIST) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = localizedStringResource(StringsR.string.home_hold_ok_for_options),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.38f),
                )
            }
        }
        key(shouldLoop(row.items.size)) {
            ProvidePivotScroll(leading = ContentStart + 3.dp) {
                LazyRow(
                    state = rememberLoopingRowState(row.items.size),
                    contentPadding = PaddingValues(horizontal = ContentStart, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (row.style == RowStyle.TOP10) 6.dp else 16.dp),
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
                                    UpNextCard(
                                        item = item,
                                        episode = episode,
                                        label = episodeLabel,
                                        releaseLabel = releaseLabel,
                                        badgeText = badgeText,
                                        onClick = {
                                            if (onUpNextClick == null) onItemClick(item)
                                            else onUpNextClick(item, episode)
                                        },
                                        onLongClick = { onUpNextMenu(virtual, item, episode) },
                                        onFocused = { onFocused(virtual) },
                                        highlightBadge = upNextBadge(info) != UpNextBadge.UP_NEXT,
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

@Composable
private fun HeroBackdrop(item: CatalogItem) {
    Box(Modifier.fillMaxSize()) {
        // Softened by default so the rows, not the artwork, carry the screen. The featured carousel
        // stays sharp either way.
        item.backdropUrl?.let { url ->
            if (LocalSoftBackdrops.current) {
                SoftBackdropImage(url = url, modifier = Modifier.fillMaxSize())
            } else {
                AsyncImage(
                    // The soft path decodes at 320 px, so resolution only matters for sharp backdrops.
                    model = BackdropUrls.fullScreen(url, lightweight = false, highRes = LocalHighResBackdrops.current),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to AppColors.Background,
                    0.28f to AppColors.Background,
                    0.52f to AppColors.Background.copy(alpha = 0.72f),
                    0.72f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.40f to Color.Transparent,
                    0.72f to AppColors.Background.copy(alpha = 0.25f),
                    1f to AppColors.Background,
                ),
            ),
        )
    }
}

@Composable
private fun HeroInfo(item: CatalogItem) {
    Column {
        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = metaLine(item),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun metaLine(item: CatalogItem): String = buildList {
    add(if (item.type == MediaType.TV) localizedStringResource(StringsR.string.home_series) else localizedStringResource(StringsR.string.home_movie))
    item.year?.let { add(it.toString()) }
    item.rating?.takeIf { it > 0f }?.let { add("★ " + String.format(Lang.locale, "%.1f", it)) }
}.joinToString("  ·  ")

private val BillboardBackground = Brush.verticalGradient(listOf(AppColors.BackgroundRaised, Color.Black))
private val SkeletonColor = AppColors.Placeholder

@Composable
private fun delayedSkeletonAlpha(): Float {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(250)
        visible = true
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(300), label = "skeleton")
    return alpha
}

@Composable
private fun skeletonPulse(): Float {
    if (LocalLightweightUi.current) return 0.8f
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Reverse),
        label = "skeletonPulseAlpha",
    )
    return pulse
}

@Composable
private fun ColumnScope.BillboardSkeleton() {
    val alpha = delayedSkeletonAlpha() * skeletonPulse()
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .background(Brush.verticalGradient(listOf(AppColors.BackgroundRaised, AppColors.Background))),
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 40.dp)) {
            Box(Modifier.width(320.dp).height(42.dp).background(SkeletonColor, AppShapes.Badge))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(340.dp).height(16.dp).background(SkeletonColor, AppShapes.Badge))
            Spacer(Modifier.height(18.dp))
            Box(Modifier.width(126.dp).height(36.dp).background(SkeletonColor, AppShapes.Pill))
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, top = 10.dp, bottom = 16.dp)
            .graphicsLayer { this.alpha = alpha },
    ) {
        repeat(8) { index ->
            Box(
                Modifier
                    .width((104 + (index % 3) * 18).dp)
                    .height(40.dp)
                    .background(SkeletonColor, AppShapes.Pill),
            )
        }
    }
}

@Composable
internal fun HomeSkeleton() {
    val alpha = delayedSkeletonAlpha() * skeletonPulse()
    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .graphicsLayer { this.alpha = alpha }
            .padding(start = ContentStart, top = 140.dp),
    ) {
        repeat(2) {
            Box(
                Modifier
                    .width(180.dp)
                    .height(18.dp)
                    .background(SkeletonColor, AppShapes.Badge),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(7) {
                    Box(
                        Modifier
                            .width(PosterWidth)
                            .aspectRatio(2f / 3f)
                            .background(SkeletonColor, AppShapes.Control),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun HomeError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier.fillMaxSize()) {
    val retryRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { retryRequester.requestFocus() }
    }
    Column(
        modifier.background(AppColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        TvButton(text = localizedStringResource(StringsR.string.home_retry), onClick = onRetry, modifier = Modifier.focusRequester(retryRequester))
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

