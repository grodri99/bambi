package com.shieldstream.tv.details

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.domain.SourceProvider
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.shieldstream.tv.domain.NewEpisodes
import com.shieldstream.tv.model.CatalogDetails
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.strings.localizedCode
import com.shieldstream.tv.strings.sectionResource
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.WatchProgress
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.SourceMediaBadges
import com.shieldstream.tv.ui.component.ProvideMinimalScroll
import com.shieldstream.tv.ui.component.ProvidePivotScroll
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.formatEpisodeDate
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.tvClickable
import java.util.Locale

private val Green = AppColors.Success
private val Grey = Color(0xFFB3B3B3)
private val WatchedCountColor = Color.White.copy(alpha = 0.75f)

internal enum class DetailsBackAction { DISMISS_ACTIONS, DISMISS_SOURCES, EXIT_DETAILS }

internal fun detailsBackAction(actionsOpen: Boolean, panelOpen: Boolean): DetailsBackAction = when {
    actionsOpen -> DetailsBackAction.DISMISS_ACTIONS
    panelOpen -> DetailsBackAction.DISMISS_SOURCES
    else -> DetailsBackAction.EXIT_DETAILS
}

internal fun detailsSourcesVisible(panelOpen: Boolean, actionsOpen: Boolean): Boolean = panelOpen && !actionsOpen

internal fun detailsMoreVisible(pendingPlay: Boolean): Boolean = !pendingPlay

/** Shows at most [max] language codes and summarises the rest, e.g. "EN/RU/IT +4". */
internal fun compactLanguages(languages: List<String>, max: Int = 3): String {
    if (languages.size <= max) return languages.joinToString("/")
    return languages.take(max).joinToString("/") + " +" + (languages.size - max)
}

internal fun upcomingEpisodeDate(airDate: String?, today: String): String? =
    airDate?.takeIf { it.isNotBlank() && it > today }

@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onPlay: () -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    DetailsEvent.OpenPlayer -> onPlay()
                }
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenHidden() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }

    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        when (val s = state) {
            DetailsUiState.Loading -> CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            is DetailsUiState.Error -> Text(
                text = s.message,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            is DetailsUiState.Content -> DetailsContent(s, viewModel, onBack)
        }
    }
}

@Composable
private fun DetailsContent(
    state: DetailsUiState.Content,
    viewModel: DetailsViewModel,
    onBack: () -> Unit,
) {
    var panelOpen by remember { mutableStateOf(false) }
    var actionsOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = actionsOpen || !panelOpen) {
        when (detailsBackAction(actionsOpen, panelOpen)) {
            DetailsBackAction.DISMISS_ACTIONS -> actionsOpen = false
            DetailsBackAction.DISMISS_SOURCES -> panelOpen = false
            DetailsBackAction.EXIT_DETAILS -> onBack()
        }
    }

    val playRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { playRequester.requestFocus() }
    }
    LaunchedEffect(panelOpen, actionsOpen) {
        if (!panelOpen && !actionsOpen) {
            withFrameNanos { }
            runCatching { playRequester.requestFocus() }
        }
    }

    val item = state.details.item

    Box(Modifier.fillMaxSize().onBackKey(enabled = panelOpen && !actionsOpen) { panelOpen = false }) {
        AsyncImage(
            model = item.backdropUrl ?: item.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black,
                    0.40f to Color.Black.copy(alpha = 0.85f),
                    0.75f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.40f to Color.Transparent, 1f to Color.Black),
            ),
        )

        ProvideMinimalScroll(margin = 72.dp) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = if (state.isSeries) 24.dp else 44.dp, bottom = 72.dp),
        ) {
            InfoBlock(
                details = state.details,
                state = state,
                interactive = !panelOpen && !actionsOpen,
                playRequester = playRequester,
                onPlay = { viewModel.play() },
                onPlayFromStart = { viewModel.play(fromStart = true) },
                onOpenSources = {
                    viewModel.ensureSources()
                    panelOpen = true
                },
                onOpenActions = { actionsOpen = true },
                modifier = Modifier.padding(start = 56.dp, end = 56.dp),
            )

            if (state.isSeries) {
                Spacer(Modifier.height(12.dp))
                SeasonsAndEpisodes(
                    state = state,
                    interactive = !panelOpen && !actionsOpen,
                    onSeason = { viewModel.selectSeason(it) },
                    onEpisode = { viewModel.playEpisode(it) },
                    onEpisodeLongPress = { viewModel.toggleEpisodeWatched(it) },
                )
            }
        }
        }

        AnimatedVisibility(
            visible = detailsSourcesVisible(panelOpen, actionsOpen),
            enter = fadeIn() + slideInHorizontally { it / 3 },
            exit = fadeOut() + slideOutHorizontally { it / 3 },
            modifier = Modifier.zIndex(2f),
        ) {
            SourcesPanel(
                state = state,
                onPick = { source ->
                    panelOpen = false
                    viewModel.playSource(source)
                },
                isBlocked = viewModel::isBlocked,
                onToggleSpanishOnly = viewModel::toggleSpanishOnly,
            )
        }
        if (actionsOpen) {
            DetailsActions(
                state = state,
                onToggleWatched = {
                    if (state.isSeries) viewModel.toggleSeasonWatched() else viewModel.toggleMovieWatched()
                    actionsOpen = false
                },
                onToggleMyList = {
                    viewModel.toggleMyList()
                    actionsOpen = false
                },
                onToggleFollowing = {
                    viewModel.toggleFollowing()
                    actionsOpen = false
                },
                onDismiss = { actionsOpen = false },
            )
        }
    }

    state.message?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3500)
            viewModel.consumeMessage()
        }
    }
}

@Composable
private fun InfoBlock(
    details: CatalogDetails,
    state: DetailsUiState.Content,
    interactive: Boolean,
    playRequester: FocusRequester,
    onPlay: () -> Unit,
    onPlayFromStart: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = details.item
    Column(modifier.widthIn(max = 600.dp)) {
        if (details.logoUrl != null) {
            AsyncImage(
                model = details.logoUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                modifier = Modifier.heightIn(max = if (state.isSeries) 80.dp else 110.dp).widthIn(max = 420.dp),
            )
        } else {
            Text(
                text = item.title,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))

        MetaRow(details, state)
        if (details.genres.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(details.genres.take(3).joinToString("  ·  "), color = Grey, style = MaterialTheme.typography.bodyMedium)
        }
        details.nextEpisode?.let { next ->
            val nextCode = next.localizedCode()
            val release = remember(next, nextCode) {
                listOf(nextCode, next.title, formatEpisodeDate(next.airDate, includeYear = true))
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = localizedStringResource(StringsR.string.details_coming_soon, release),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = item.overview.ifBlank { details.tagline.orEmpty() },
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (state.isSeries) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
        ) {
            val progress = state.mainProgress
            val resumable = progress?.isResumable == true
            val episodeCode = state.selectedEpisode?.takeIf { state.isSeries }?.let { " ${it.localizedCode()}" }.orEmpty()
            val playLabel = when {
                state.pendingPlay -> localizedStringResource(StringsR.string.details_preparing)
                resumable -> localizedStringResource(StringsR.string.details_resume_episode, episodeCode)
                else -> localizedStringResource(StringsR.string.details_play_episode, episodeCode)
            }
            TvButton(
                text = playLabel,
                icon = AppIcons.Play,
                onClick = onPlay,
                enabled = interactive,
                modifier = Modifier.focusRequester(playRequester),
            )
            if (resumable && !state.pendingPlay) {
                TvButton(
                    text = localizedStringResource(StringsR.string.details_from_the_beginning),
                    onClick = onPlayFromStart,
                    enabled = interactive,
                )
            }
            TvButton(
                text = localizedStringResource(StringsR.string.details_sources),
                icon = AppIcons.List,
                onClick = onOpenSources,
                enabled = interactive,
            )
            if (detailsMoreVisible(state.pendingPlay)) {
                TvButton(
                    text = localizedStringResource(StringsR.string.details_more),
                    onClick = onOpenActions,
                    enabled = interactive,
                )
            }
        }
        state.mainProgress?.takeIf { it.isResumable }?.let { p ->
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WatchBar(fraction = p.fraction, modifier = Modifier.width(240.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = buildString {
                        state.selectedEpisode?.takeIf { state.isSeries }?.let { append(it.localizedCode()).append(" · ") }
                        val left = formatRuntime(((p.remainingMs + 59_999) / 60_000).toInt())
                        append(localizedStringResource(StringsR.string.details_remaining, left))
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SourcesStatus(state)
    }
}

@Composable
private fun DetailsActions(
    state: DetailsUiState.Content,
    onToggleWatched: () -> Unit,
    onToggleMyList: () -> Unit,
    onToggleFollowing: () -> Unit,
    onDismiss: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .onBackKey(enabled = true, onBack = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(360.dp)
                .background(AppColors.Surface, AppShapes.Control)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = state.details.item.title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val watchedLabel = watchedLabel(state)
            if (watchedLabel != null) {
                TvButton(
                    text = watchedLabel,
                    icon = if (watchedDone(state)) AppIcons.Check else null,
                    onClick = onToggleWatched,
                    modifier = Modifier.focusRequester(requester),
                )
            }
            TvButton(
                text = if (state.inMyList) localizedStringResource(StringsR.string.details_in_my_list) else localizedStringResource(StringsR.string.details_my_list),
                icon = if (state.inMyList) AppIcons.Check else AppIcons.Add,
                onClick = onToggleMyList,
                modifier = if (watchedLabel == null) Modifier.focusRequester(requester) else Modifier,
            )
            if (state.isSeries) {
                TvButton(
                    text = if (state.following) localizedStringResource(StringsR.string.details_following) else localizedStringResource(StringsR.string.details_follow),
                    icon = if (state.following) AppIcons.Check else AppIcons.Add,
                    onClick = onToggleFollowing,
                )
            }
            TvButton(text = localizedStringResource(StringsR.string.details_cancel), onClick = onDismiss)
        }
    }
}

@Composable
private fun watchedLabel(state: DetailsUiState.Content): String? {
    if (!state.isSeries) {
        return localizedStringResource(
            if (state.mainProgress?.isWatched == true) StringsR.string.details_watched else StringsR.string.details_mark_watched,
        )
    }
    val season = state.selectedSeason
    val episodes = (state.episodes as? EpisodesState.Ready)?.episodes.orEmpty()
    if (season == null || episodes.isEmpty()) return null
    return if (episodes.all { state.progressOf(it)?.isWatched == true }) {
        localizedStringResource(StringsR.string.details_season_watched, season)
    } else {
        localizedStringResource(StringsR.string.details_mark_season_watched, season)
    }
}

private fun watchedDone(state: DetailsUiState.Content): Boolean {
    if (!state.isSeries) return state.mainProgress?.isWatched == true
    val episodes = (state.episodes as? EpisodesState.Ready)?.episodes.orEmpty()
    return episodes.isNotEmpty() && episodes.all { state.progressOf(it)?.isWatched == true }
}

@Composable
private fun MetaRow(details: CatalogDetails, state: DetailsUiState.Content) {
    val item = details.item
    Row(verticalAlignment = Alignment.CenterVertically) {
        item.rating?.takeIf { it > 0f }?.let {
            Text(
                text = localizedStringResource(StringsR.string.details_rating, (it * 10).toInt()),
                color = Green,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.width(14.dp))
        }
        item.year?.let {
            Text(it.toString(), color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(14.dp))
        }
        val duration = when {
            state.isSeries && details.seasons.isNotEmpty() ->
                if (details.seasons.size == 1) localizedStringResource(StringsR.string.details_1_season) else localizedStringResource(StringsR.string.details_season_count, details.seasons.size)
            else -> details.runtimeMinutes?.let { formatRuntime(it) }
        }
        duration?.let {
            Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(14.dp))
        }
        (state.sources as? SourcesState.Ready)?.sources?.firstOrNull()?.let { best ->
            Badge(best.quality.label)
            if (best.hdrType != HdrType.SDR) {
                Spacer(Modifier.width(8.dp))
                Badge(best.hdrType.label)
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = 0.6f), AppShapes.Badge)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun SourcesStatus(state: DetailsUiState.Content) {
    state.message?.let {
        Text(it, color = Grey, style = MaterialTheme.typography.bodyMedium)
        return
    }
    when (val s = state.sources) {
        SourcesState.Idle -> Unit
        SourcesState.Loading -> Text(localizedStringResource(StringsR.string.details_finding_sources), color = Grey, style = MaterialTheme.typography.bodyMedium)
        is SourcesState.Error -> Text(s.message, color = Grey, style = MaterialTheme.typography.bodyMedium)
        is SourcesState.Ready -> {
            val sources = s.sources
                .filter { !state.spanishOnly || it.advertisesSpanishAudio }
                .distinctBy { it.infoHash.lowercase() }
            val scope = state.selectedEpisode?.takeIf { state.isSeries }?.let { localizedStringResource(StringsR.string.details_for_episode, it.localizedCode()) }.orEmpty()
            when {
                sources.isEmpty() && s.pending.isNotEmpty() -> Text(localizedStringResource(StringsR.string.details_finding_sources), color = Grey, style = MaterialTheme.typography.bodyMedium)
                sources.isEmpty() && state.spanishOnly -> Text(localizedStringResource(StringsR.string.details_no_spanish_sources_available_for_this_profile), color = Grey, style = MaterialTheme.typography.bodyMedium)
                sources.isEmpty() -> Text(localizedStringResource(StringsR.string.details_no_sources_for, scope), color = Grey, style = MaterialTheme.typography.bodyMedium)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        AppIcons.List,
                        contentDescription = localizedStringResource(StringsR.string.details_sources),
                        tint = Grey,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(sources.size.toString(), color = Grey, style = MaterialTheme.typography.bodyMedium)
                    val cached = sources.count { it.isCached }
                    if (cached > 0) {
                        Spacer(Modifier.width(14.dp))
                        Icon(
                            AppIcons.Bolt,
                            contentDescription = localizedStringResource(StringsR.string.details_cached),
                            tint = Grey,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(cached.toString(), color = Grey, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonsAndEpisodes(
    state: DetailsUiState.Content,
    interactive: Boolean,
    onSeason: (Int) -> Unit,
    onEpisode: (Episode) -> Unit,
    onEpisodeLongPress: (Episode) -> Unit,
) {
    val seasons = state.details.seasons
    val loadedEpisodes = (state.episodes as? EpisodesState.Ready)?.episodes.orEmpty()
    val watched = loadedEpisodes.count { state.progressOf(it)?.isWatched == true }
    Column {
        if (seasons.size > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 56.dp),
            ) {
                ProvidePivotScroll(leading = 0.dp) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(seasons, key = { it.number }) { season ->
                            SeasonChip(
                                label = localizedStringResource(StringsR.string.details_season_number, season.number),
                                selected = season.number == state.selectedSeason,
                                enabled = interactive,
                                onClick = { onSeason(season.number) },
                            )
                        }
                    }
                }
                if (loadedEpisodes.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = localizedStringResource(StringsR.string.details_watched_count_short, watched, loadedEpisodes.size),
                        color = WatchedCountColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 56.dp, bottom = 10.dp),
            ) {
                Text(
                    text = localizedStringResource(StringsR.string.details_episodes),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                if (loadedEpisodes.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = localizedStringResource(StringsR.string.details_watched_count_short, watched, loadedEpisodes.size),
                        color = WatchedCountColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        when (val eps = state.episodes) {
            EpisodesState.Loading -> Text(localizedStringResource(StringsR.string.details_loading_episodes), color = Grey, modifier = Modifier.padding(start = 56.dp))
            is EpisodesState.Error -> Text(eps.message, color = Grey, modifier = Modifier.padding(start = 56.dp))
            is EpisodesState.Ready -> ProvidePivotScroll(leading = 56.dp) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(eps.episodes, key = { "${it.season}_${it.number}" }) { ep ->
                        EpisodeCard(
                            episode = ep,
                            progress = state.progressOf(ep),
                            selected = ep == state.selectedEpisode,
                            loading = ep == state.selectedEpisode && state.pendingPlay,
                            enabled = interactive,
                            onClick = { onEpisode(ep) },
                            onLongClick = { onEpisodeLongPress(ep) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    // Same filled style as the other chips: faint, brighter when selected, inverted on focus.
    val bg = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    Text(
        text = label,
        color = when {
            focused -> Color.Black
            selected -> Color.White
            else -> Color.White.copy(alpha = 0.75f)
        },
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(AppShapes.Pill)
            .background(bg)
            .tvClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

@Composable
private fun EpisodeCard(
    episode: Episode,
    progress: WatchProgress?,
    selected: Boolean,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "episodeScale")
    val shape = AppShapes.Control
    Column(
        Modifier
            .width(200.dp)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(AppColors.Placeholder)
                .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .then(if (enabled) Modifier.tvClickable(onLongClick = onLongClick, onClick = onClick) else Modifier.tvClickable(enabled = false) {}),
        ) {
            if (episode.stillUrl != null) {
                AsyncImage(
                    model = episode.stillUrl,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (progress?.isWatched == true) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(AppShapes.Pill)
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = Green, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
            } else if (progress != null && progress.fraction > 0.01f) {
                WatchBar(
                    fraction = progress.fraction,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            if (focused || loading) {
                Box(
                    Modifier.align(Alignment.Center).size(44.dp).clip(AppShapes.Pill)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(AppIcons.Play, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${episode.number}. ${episode.title}",
            color = if (selected || focused) Color.White else Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val today = remember { NewEpisodes.dateWindow(System.currentTimeMillis()).second }
        val releaseDate = remember(episode.airDate, today, Lang.current) {
            formatEpisodeDate(upcomingEpisodeDate(episode.airDate, today), includeYear = true)
        }
        val remainingMinutes = progress?.takeIf { it.isResumable }?.let { ((it.remainingMs + 59_999) / 60_000).toInt() }
        val remainingLabel = remainingMinutes?.let {
            localizedStringResource(StringsR.string.details_remaining, formatRuntime(it))
        }
        val metadata = remember(remainingLabel, episode.runtimeMinutes, releaseDate) {
            listOfNotNull(
                remainingLabel ?: episode.runtimeMinutes?.let { "$it min" },
                releaseDate.ifBlank { null },
            ).joinToString(" · ")
        }
        if (metadata.isNotEmpty()) {
            Text(metadata, color = Grey, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SourcesPanel(
    state: DetailsUiState.Content,
    onPick: (StreamSource) -> Unit,
    isBlocked: (StreamSource) -> Boolean,
    onToggleSpanishOnly: () -> Unit,
) {
    val firstRequester = remember { FocusRequester() }
    val sources = (state.sources as? SourcesState.Ready)?.sources.orEmpty()
    val order = SourceProvider.entries.map { localizedStringResource(it.sectionResource) }.distinct()
    val pending = (state.sources as? SourcesState.Ready)?.pending.orEmpty()
    val unavailable = (state.sources as? SourcesState.Ready)?.unavailable.orEmpty() +
        pending.associateWith { localizedStringResource(StringsR.string.details_searching) }
    val spanishFirst = state.spanishFirst
    val spanishOnly = state.spanishOnly
    val groups = remember(sources, unavailable, spanishFirst, spanishOnly) {
        val byProvider = sources.groupBy { it.provider }
        (byProvider.keys + unavailable.keys).distinct().sortedBy { order.indexOf(it) }.map { section ->
            section to byProvider[section].orEmpty()
                .filter { !spanishOnly || it.advertisesSpanishAudio }
                .sortedWith(
                compareByDescending<StreamSource> { if (spanishFirst) it.spanishAudioScore else 0 }
                    .thenByDescending { it.quality.rank }
                    .thenByDescending { it.sizeBytes },
            )
        }
    }
    var open by remember { mutableStateOf(emptySet<String>()) }
    var autoOpened by remember { mutableStateOf(false) }
    LaunchedEffect(groups) {
        if (!autoOpened) groups.firstOrNull { it.second.isNotEmpty() }?.let { open = setOf(it.first); autoOpened = true }
    }
    LaunchedEffect(sources.isNotEmpty()) {
        if (sources.isNotEmpty()) {
            withFrameNanos { }
            runCatching { firstRequester.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(520.dp)
                .background(AppColors.BackgroundRaised.copy(alpha = 0.95f))
                .padding(top = 32.dp, start = 24.dp, end = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = localizedStringResource(StringsR.string.details_choose_a_source),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                SpanishOnlyChip(enabled = spanishOnly, onClick = onToggleSpanishOnly)
            }
            state.selectedEpisode?.takeIf { state.isSeries }?.let {
                Text("${it.localizedCode()} · ${it.title}", color = Grey, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
            when (val s = state.sources) {
                SourcesState.Idle -> Text(localizedStringResource(StringsR.string.details_pick_an_episode_first), color = Grey)
                SourcesState.Loading -> Text(localizedStringResource(StringsR.string.details_finding_sources), color = Grey)
                is SourcesState.Error -> Text(s.message, color = Grey)
                is SourcesState.Ready -> if (groups.all { it.second.isEmpty() } && s.pending.isEmpty()) {
                    Text(
                        if (spanishOnly) localizedStringResource(StringsR.string.details_no_spanish_sources_available_for_this_profile)
                        else localizedStringResource(StringsR.string.details_no_sources),
                        color = Grey,
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        groups.forEachIndexed { index, (name, list) ->
                            item(key = "h:$name") {
                                ProviderHeader(
                                    name = name,
                                    count = list.size,
                                    cached = list.count { it.isCached },
                                    expanded = name in open,
                                    note = unavailable[name].takeIf { list.isEmpty() }
                                        ?: localizedStringResource(StringsR.string.details_none_in_spanish).takeIf { list.isEmpty() && spanishOnly },
                                    onClick = { if (list.isNotEmpty()) open = if (name in open) open - name else open + name },
                                    modifier = if (index == 0) Modifier.focusRequester(firstRequester) else Modifier,
                                )
                            }
                            if (name in open) {
                                items(list, key = { it.id }) { source -> SourceRow(source = source, blocked = isBlocked(source), onClick = { onPick(source) }) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpanishOnlyChip(enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = if (enabled) "✓ " + localizedStringResource(StringsR.string.details_spanish_only) else localizedStringResource(StringsR.string.details_spanish_only),
        color = if (focused) Color.Black else Color.White,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier
            .background(
                when {
                    focused -> Color.White
                    enabled -> Color.White.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                AppShapes.Pill,
            )
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun ProviderHeader(name: String, count: Int, cached: Int, expanded: Boolean, note: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val color = if (focused) Color.Black else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.10f), AppShapes.Control)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(name, color = color, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        Text(
            note ?: buildString {
                append(localizedStringResource(StringsR.string.details_source_count, count))
                if (cached > 0) append(" · $cached ⚡")
            },
            color = if (focused) Color.Black.copy(alpha = 0.7f) else Grey,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (note == null) Text(if (expanded) "   ▾" else "   ›", color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SourceRow(source: StreamSource, blocked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(AppShapes.Control)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
            .tvClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = when {
            focused -> Color.Black
            blocked -> Color.White.copy(alpha = 0.4f)
            else -> Color.White
        }
        val sub = if (focused) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = if (blocked) 0.3f else 0.55f)
        Box(Modifier.width(26.dp)) {
            if (source.isCached) Icon(AppIcons.Bolt, contentDescription = localizedStringResource(StringsR.string.details_in_your_real_debrid), tint = if (focused) Color(0xFF0A8F3C) else Green)
        }
        Column(Modifier.weight(1f)) {
            // Primary line: what the viewer chooses by (format, size, languages).
            // Secondary line: torrent health and the raw release name.
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceMediaBadges(source)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buildString {
                        if (source.sizeBytes > 0) append(formatSize(source.sizeBytes))
                        if (source.languages.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(compactLanguages(source.languages))
                        }
                        if (blocked) {
                            if (isNotEmpty()) append(" · ")
                            append(localizedStringResource(StringsR.string.details_blocked_by_real_debrid))
                        }
                    },
                    color = fg,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = source.seeders?.let { "$it seeds · ${source.title}" } ?: source.title,
                color = sub,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WatchBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(4.dp)
            .clip(AppShapes.Pill)
            .background(Color.White.copy(alpha = 0.3f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(AppColors.Accent),
        )
    }
}

private fun formatRuntime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

private fun formatSize(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1) String.format(Locale.US, "%.1f GB", gb) else String.format(Locale.US, "%.0f MB", gb * 1024)
}
