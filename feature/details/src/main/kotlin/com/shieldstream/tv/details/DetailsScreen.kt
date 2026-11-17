package com.shieldstream.tv.details

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.domain.TraktRatingTarget
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.key.type
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
import com.shieldstream.tv.model.CatalogDetails
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.ui.component.downloadIndicatorState
import com.shieldstream.tv.ui.component.busy
import com.shieldstream.tv.ui.component.DownloadIndicator
import com.shieldstream.tv.strings.localizedCode
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.WatchProgress
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.ProvideMinimalScroll
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.formatEpisodeDate
import com.shieldstream.tv.ui.component.onBackKey

internal val Green = Color(0xFF46D369)

internal val Grey = Color(0xFFB3B3B3)

internal val WatchedCountColor = Color.White.copy(alpha = 0.75f)

/** Cached bolt on a focused (white) row: a darker green keeps contrast. */
internal val CachedFocused = Color(0xFF0A8F3C)

internal enum class DetailsBackAction { DISMISS_ACTIONS, DISMISS_SOURCES, EXIT_DETAILS }

internal fun detailsBackAction(actionsOpen: Boolean, panelOpen: Boolean): DetailsBackAction = when {
    actionsOpen -> DetailsBackAction.DISMISS_ACTIONS
    panelOpen -> DetailsBackAction.DISMISS_SOURCES
    else -> DetailsBackAction.EXIT_DETAILS
}

internal fun detailsSourcesVisible(panelOpen: Boolean, actionsOpen: Boolean): Boolean = panelOpen && !actionsOpen

internal fun detailsMoreVisible(pendingPlay: Boolean): Boolean = !pendingPlay

internal fun upcomingEpisodeDate(airDate: String?, today: String): String? =
    airDate?.takeIf { it.isNotBlank() && it > today }

@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onOpenPerson: (Int) -> Unit = {},
    onOpenItem: (com.shieldstream.tv.model.CatalogItem) -> Unit = {},
    onOpenTrailer: (List<String>) -> Unit = {},
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
            is DetailsUiState.Content -> DetailsContent(s, viewModel, onBack, onOpenPerson, onOpenItem, onOpenTrailer)
        }
    }
}

@Composable
private fun DetailsContent(
    state: DetailsUiState.Content,
    viewModel: DetailsViewModel,
    onBack: () -> Unit,
    onOpenPerson: (Int) -> Unit,
    onOpenItem: (com.shieldstream.tv.model.CatalogItem) -> Unit,
    onOpenTrailer: (List<String>) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloadList by viewModel.downloadList.collectAsStateWithLifecycle()
    val plannerProgress by viewModel.plannerProgress.collectAsStateWithLifecycle()
    // El episodio sobre el que se estaba (enfocado), no solo el último abierto: "Más" actúa sobre ese.
    var focusedEpisode by remember { mutableStateOf<Episode?>(null) }
    val menuState = remember(state, focusedEpisode) {
        val focused = focusedEpisode?.takeIf { f -> state.isSeries && f.season == state.selectedSeason }
        if (focused != null) state.copy(selectedEpisode = focused) else state
    }
    var panelOpen by remember { mutableStateOf(false) }
    // La fuente elegida a mano falló (sin cambio automático): se vuelve con la lista abierta para elegir otra.
    val reopenSources by viewModel.reopenSources.collectAsStateWithLifecycle()
    LaunchedEffect(reopenSources) {
        if (reopenSources) {
            panelOpen = true
            viewModel.sourcesReopened()
        }
    }
    var actionsOpen by remember { mutableStateOf(false) }
    var ratingTarget by remember { mutableStateOf<TraktRatingTarget?>(null) }
    var episodeMenu by remember { mutableStateOf<Episode?>(null) }
    BackHandler(enabled = actionsOpen || episodeMenu != null || !panelOpen) {
        when {
            ratingTarget != null -> ratingTarget = null
            episodeMenu != null -> episodeMenu = null
            else -> when (detailsBackAction(actionsOpen, panelOpen)) {
                DetailsBackAction.DISMISS_ACTIONS -> actionsOpen = false
                DetailsBackAction.DISMISS_SOURCES -> panelOpen = false
                DetailsBackAction.EXIT_DETAILS -> onBack()
            }
        }
    }

    val playRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { playRequester.requestFocus() }
    }
    LaunchedEffect(panelOpen, actionsOpen, episodeMenu) {
        if (!panelOpen && !actionsOpen && episodeMenu == null) {
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

        val pageScroll = rememberScrollState()
        val pageScope = rememberCoroutineScope()
        ProvideMinimalScroll(margin = 72.dp) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(pageScroll)
                .padding(top = 44.dp, bottom = 72.dp),
        ) {
            InfoBlock(
                details = state.details,
                state = state,
                interactive = !panelOpen && !actionsOpen && episodeMenu == null,
                playRequester = playRequester,
                onPlay = { viewModel.play() },
                onPlayFromStart = { viewModel.play(fromStart = true) },
                onOpenSources = {
                    viewModel.ensureSources()
                    panelOpen = true
                },
                onOpenActions = { actionsOpen = true },
                onTrailer = state.details.trailerKey?.let { first -> { onOpenTrailer(state.details.trailerKeys.ifEmpty { listOf(first) }) } },
                // Al volver a los botones desde abajo, la página sube del todo: si no, la sinopsis quedaba cortada.
                modifier = Modifier
                    .padding(start = 56.dp, end = 56.dp)
                    .onFocusChanged { if (it.hasFocus && pageScroll.value > 0) pageScope.launch { pageScroll.scrollToTopFirmly() } },
            )

            if (state.isSeries) {
                Spacer(Modifier.height(28.dp))
                SeasonsAndEpisodes(
                    state = state,
                    interactive = !panelOpen && !actionsOpen && episodeMenu == null,
                    onSeason = { viewModel.selectSeason(it) },
                    onEpisode = { viewModel.playEpisode(it) },
                    onEpisodeLongPress = { episodeMenu = it },
                    onEpisodeFocused = { focusedEpisode = it },
                )
            }

            val interactive = !panelOpen && !actionsOpen && episodeMenu == null
            state.collection?.let { saga ->
                Spacer(Modifier.height(28.dp))
                SectionTitle(localizedStringResource(StringsR.string.details_collection, saga.name))
                PosterStrip(
                    items = saga.parts,
                    highlightId = item.id,
                    onItem = { if (interactive && it.id != item.id) onOpenItem(it) },
                )
            }
            val people = if (state.showCast) state.details.directors + state.details.cast else emptyList()
            if (people.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                val shown = people.distinctBy { it.id }
                Row(verticalAlignment = Alignment.Bottom) {
                    SectionTitle(localizedStringResource(StringsR.string.details_cast))
                    Text(
                        text = shown.size.toString(),
                        color = Grey,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 10.dp, bottom = 5.dp),
                    )
                }
                PeopleRow(
                    people = shown,
                    enabled = interactive,
                    onPerson = { onOpenPerson(it.id) },
                    crewIds = state.details.directors.map { it.id }.toSet(),
                    crewLabel = localizedStringResource(
                        if (state.isSeries) StringsR.string.details_crew_creator else StringsR.string.details_crew_director,
                    ),
                )
            }
        }
        }

        val indicator = downloadIndicatorState(
            downloadList,
            preparing = plannerProgress?.let { "${it.done + 1}/${it.total}" },
        )
        if (indicator != null && indicator.busy && !panelOpen && !actionsOpen) {
            DownloadIndicator(
                state = indicator,
                onClick = null,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 32.dp).zIndex(1f),
            )
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
                onMarkBroken = viewModel::markSourceBroken,
                autoPick = remember(state.sources, state.selectedEpisode, state.sourceExclusions, state.brokenSources, state.spanishOnly) {
                    viewModel.autoPlaySource()
                },
            )
        }
        if (actionsOpen) {
            val target = ratingTarget
            when {
                target != null -> RatingPickerDialog(
                    state = state,
                    target = target,
                    ratings = state.ratings,
                    onSave = { viewModel.setRating(target, it) },
                    onRetry = viewModel::loadRatings,
                    onDismiss = { ratingTarget = null },
                )
                else -> DetailsActions(
                    state = menuState,
                    onToggleSeasonWatched = {
                        viewModel.toggleSeasonWatched()
                        actionsOpen = false
                    },
                    onToggleMovieWatched = {
                        viewModel.toggleMovieWatched()
                        actionsOpen = false
                    },
                    onToggleEpisodeWatched = { episode ->
                        viewModel.toggleEpisodeWatched(episode)
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
                    onRate = {
                        ratingTarget = it
                        viewModel.loadRatings()
                    },
                    onRandomEpisode = {
                        actionsOpen = false
                        viewModel.playRandomEpisode()
                    },
                    downloads = downloadList,
                    onDownload = { episodes, quality ->
                        actionsOpen = false
                        viewModel.download(episodes, quality)
                    },
                    onDownloadAction = { d ->
                        actionsOpen = false
                        when {
                            d.isDone -> viewModel.deleteDownload(d.id)
                            d.isActive -> viewModel.pauseDownload(d.id)
                            else -> viewModel.resumeDownload(d.id)
                        }
                    },
                    onDismiss = { actionsOpen = false },
                )
            }
        }

        episodeMenu?.let { episode ->
            val episodeDownload = downloadList.firstOrNull {
                it.id == WatchProgress.episodeKey(state.details.item.id, episode.season, episode.number)
            }
            EpisodeActions(
                state = state,
                episode = episode,
                download = episodeDownload,
                onDownload = {
                    episodeMenu = null
                    if (episodeDownload?.isDone == true) viewModel.deleteDownload(episodeDownload.id) else viewModel.download(listOf(episode))
                },
                onPlay = {
                    episodeMenu = null
                    viewModel.playEpisode(episode)
                },
                onToggleWatched = {
                    viewModel.toggleEpisodeWatched(episode)
                    episodeMenu = null
                },
                onDismiss = { episodeMenu = null },
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
    onTrailer: (() -> Unit)?,
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
                modifier = Modifier.heightIn(max = 110.dp).widthIn(max = 420.dp),
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
            Text(details.genres.take(4).joinToString("  ·  "), color = Grey, style = MaterialTheme.typography.bodyMedium)
        }
        if (details.directors.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = localizedStringResource(
                    if (item.type == com.shieldstream.tv.model.MediaType.TV) StringsR.string.details_created_by else StringsR.string.details_directed_by,
                    details.directors.take(2).joinToString(", ") { it.name },
                ),
                color = Grey,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = item.overview.ifBlank { details.tagline.orEmpty() },
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 4,
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
            if (onTrailer != null) {
                TvButton(
                    text = localizedStringResource(StringsR.string.details_trailer),
                    icon = AppIcons.Movie,
                    onClick = onTrailer,
                    enabled = interactive,
                )
            }
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
        (state.sources as? SourcesState.Ready)?.sources?.firstOrNull {
            !state.sourceExclusions.excludes(it) && (!state.spanishOnly || it.advertisesSpanishAudio)
        }?.let { best ->
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
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
internal fun WatchBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
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

internal fun formatRuntime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

/**
 * Sube la página del todo. Con prioridad alta: el desplazamiento que hace el foco al llegar al botón ("traer a la
 * vista") tiene prioridad normal y, la segunda vez, cancelaba un animateScrollTo(0) normal a medio camino.
 */
private suspend fun androidx.compose.foundation.ScrollState.scrollToTopFirmly() {
    scroll(androidx.compose.foundation.MutatePriority.PreventUserInput) {
        var last = value.toFloat()
        androidx.compose.animation.core.animate(
            initialValue = last,
            targetValue = 0f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 260),
        ) { current, _ ->
            scrollBy(current - last)
            last = current
        }
    }
}
