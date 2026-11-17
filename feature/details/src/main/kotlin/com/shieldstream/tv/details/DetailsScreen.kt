package com.shieldstream.tv.details

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.domain.TraktRatingTarget
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.ui.component.requestFocusWithRetry
import com.shieldstream.tv.ui.component.downloadIndicatorState
import com.shieldstream.tv.ui.component.busy
import com.shieldstream.tv.ui.component.DownloadIndicator
import com.shieldstream.tv.model.DownloadQuality
import com.shieldstream.tv.model.DownloadStatus
import com.shieldstream.tv.model.Download
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.strings.localizedCode
import com.shieldstream.tv.strings.sectionResource
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.WatchProgress
import com.shieldstream.tv.ui.component.ActionMenuDialog
import com.shieldstream.tv.ui.component.ActionMenuItem
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.BottomActionDialog
import com.shieldstream.tv.ui.component.LineIcons
import com.shieldstream.tv.ui.component.SourceMediaBadges
import com.shieldstream.tv.ui.component.ProvideMinimalScroll
import com.shieldstream.tv.ui.component.ProvidePivotScroll
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.formatEpisodeDate
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.tvClickable
import java.util.Locale

private val Green = Color(0xFF46D369)
private val Grey = Color(0xFFB3B3B3)
private val WatchedCountColor = Color.White.copy(alpha = 0.75f)

/** Cached bolt on a focused (white) row: a darker green keeps contrast. */
private val CachedFocused = Color(0xFF0A8F3C)

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
private fun DetailsActions(
    state: DetailsUiState.Content,
    onToggleSeasonWatched: () -> Unit,
    onToggleMovieWatched: () -> Unit,
    onToggleEpisodeWatched: (Episode) -> Unit,
    onToggleMyList: () -> Unit,
    onToggleFollowing: () -> Unit,
    onRate: (TraktRatingTarget) -> Unit,
    onRandomEpisode: () -> Unit,
    downloads: List<Download>,
    onDownload: (List<Episode>, DownloadQuality) -> Unit,
    onDownloadAction: (Download) -> Unit,
    onDismiss: () -> Unit,
) {
    var downloadMenu by remember { mutableStateOf(false) }
    MoreSidePanel(
        title = state.details.item.title,
        subtitle = state.selectedEpisode?.takeIf { state.isSeries }?.let { "${it.localizedCode()} · ${it.title}" },
        onBack = { if (downloadMenu) downloadMenu = false else onDismiss() },
    ) {
        if (downloadMenu) {
            DownloadOptions(state = state, downloads = downloads, onDownload = onDownload)
            return@MoreSidePanel
        }
        val requester = remember { FocusRequester() }
        LaunchedEffect(Unit) { requester.requestFocusWithRetry() }
        val item = state.details.item
        val ratings = state.ratings as? TraktRatingsState.Ready
        val episode = state.selectedEpisode?.takeIf { state.isSeries }
        val key = if (episode != null) WatchProgress.episodeKey(item.id, episode.season, episode.number) else WatchProgress.movieKey(item.id)
        val download = downloads.firstOrNull { it.id == key }
        var firstAssigned = false
        fun first(): Modifier = if (!firstAssigned) { firstAssigned = true; Modifier.focusRequester(requester) } else Modifier

        if (state.isSeries) {
            if (episode != null) {
                MoreSectionHeader(localizedStringResource(StringsR.string.details_more_episode, episode.localizedCode()))
                val watched = state.progressOf(episode)?.isWatched == true
                MoreRow(
                    text = episodeWatchedLabel(episode, watched),
                    icon = if (watched) AppIcons.Check else LineIcons.Eye,
                    highlighted = watched,
                    onClick = { onToggleEpisodeWatched(episode) },
                    modifier = first(),
                )
                // La descarga de ESTE episodio siempre va aquí: descargarlo, o pausar/borrar si ya está. Varios episodios
                // o la temporada van en la sección de la serie ("Descargar varios…").
                if (download == null || download.status == DownloadStatus.FAILED) {
                    MoreRow(
                        text = localizedStringResource(StringsR.string.downloads_download_episode),
                        icon = LineIcons.Download,
                        detail = download?.error,
                        onClick = { onDownload(listOf(episode), DownloadQuality.BEST) },
                    )
                } else {
                    DownloadMoreRow(download, onOpenOptions = { downloadMenu = true }, onDownloadAction = onDownloadAction)
                }
                val target = TraktRatingTarget.Episode(item.id, episode.season, episode.number)
                MoreRow(
                    text = ratingTargetLabel(state, target) + ratingSuffix(ratings?.episodeRating),
                    icon = LineIcons.Star,
                    onClick = { onRate(target) },
                )
            }
            MoreSectionHeader(localizedStringResource(StringsR.string.details_more_series))
            seasonWatchedLabel(state)?.let { label ->
                val all = seasonAllWatched(state)
                MoreRow(label, if (all) AppIcons.Check else LineIcons.Eye, onToggleSeasonWatched, highlighted = all, modifier = first())
            }
            MoreRow(
                text = if (state.inMyList) localizedStringResource(StringsR.string.details_in_my_list) else localizedStringResource(StringsR.string.details_my_list),
                icon = if (state.inMyList) AppIcons.Check else AppIcons.Add,
                highlighted = state.inMyList,
                onClick = onToggleMyList,
                modifier = first(),
            )
            MoreRow(
                text = if (state.following) localizedStringResource(StringsR.string.details_following) else localizedStringResource(StringsR.string.details_follow),
                icon = if (state.following) AppIcons.Check else LineIcons.Bookmark,
                highlighted = state.following,
                onClick = onToggleFollowing,
            )
            MoreRow(
                text = localizedStringResource(StringsR.string.downloads_download_several),
                icon = LineIcons.Download,
                detail = localizedStringResource(StringsR.string.downloads_options_hint),
                onClick = { downloadMenu = true },
            )
            val show = TraktRatingTarget.Show(item.id)
            MoreRow(ratingTargetLabel(state, show) + ratingSuffix(ratings?.overallRating), LineIcons.Star, { onRate(show) })
            if (state.details.seasons.isNotEmpty()) {
                MoreRow(localizedStringResource(StringsR.string.details_random_episode), AppIcons.Bolt, onRandomEpisode)
            }
        } else {
            val watched = state.mainProgress?.isWatched == true
            MoreRow(
                text = localizedStringResource(if (watched) StringsR.string.details_watched else StringsR.string.details_mark_watched),
                icon = if (watched) AppIcons.Check else LineIcons.Eye,
                highlighted = watched,
                onClick = onToggleMovieWatched,
                modifier = first(),
            )
            MoreRow(
                text = if (state.inMyList) localizedStringResource(StringsR.string.details_in_my_list) else localizedStringResource(StringsR.string.details_my_list),
                icon = if (state.inMyList) AppIcons.Check else AppIcons.Add,
                highlighted = state.inMyList,
                onClick = onToggleMyList,
            )
            DownloadMoreRow(download, onOpenOptions = { downloadMenu = true }, onDownloadAction = onDownloadAction)
            val movie = TraktRatingTarget.Movie(item.id)
            MoreRow(ratingTargetLabel(state, movie) + ratingSuffix(ratings?.overallRating), LineIcons.Star, { onRate(movie) })
        }
    }
}

/** Fila de descarga del episodio/película: según el estado, descargar (con opciones), pausar, reanudar o borrar. */
@Composable
private fun DownloadMoreRow(download: Download?, onOpenOptions: () -> Unit, onDownloadAction: (Download) -> Unit) {
    when {
        download == null || download.status == DownloadStatus.FAILED -> MoreRow(
            text = localizedStringResource(StringsR.string.downloads_download_options),
            icon = LineIcons.Download,
            detail = download?.error,
            onClick = onOpenOptions,
        )
        download.isDone -> MoreRow(
            text = localizedStringResource(StringsR.string.downloads_delete),
            icon = LineIcons.Delete,
            detail = localizedStringResource(StringsR.string.downloads_downloaded),
            onClick = { onDownloadAction(download) },
        )
        download.isActive -> MoreRow(
            text = localizedStringResource(StringsR.string.downloads_pause),
            icon = AppIcons.Pause,
            detail = localizedStringResource(StringsR.string.downloads_downloading, (download.progress * 100).toInt()),
            progress = download.progress,
            onClick = { onDownloadAction(download) },
        )
        else -> MoreRow(
            text = localizedStringResource(StringsR.string.downloads_resume),
            icon = LineIcons.Download,
            detail = localizedStringResource(StringsR.string.downloads_paused),
            progress = download.progress,
            onClick = { onDownloadAction(download) },
        )
    }
}

/**
 * Opciones de descarga: este episodio, los siguientes, la temporada entera o solo lo que no has visto; y la calidad
 * (lo mejor, o 1080p/720p para que ocupe menos).
 */
@Composable
private fun DownloadOptions(
    state: DetailsUiState.Content,
    downloads: List<Download>,
    onDownload: (List<Episode>, DownloadQuality) -> Unit,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocusWithRetry() }
    var quality by remember { mutableStateOf(DownloadQuality.BEST) }
    val qualityLabel = localizedStringResource(
        when (quality) {
            DownloadQuality.BEST -> StringsR.string.downloads_quality_best
            DownloadQuality.FHD -> StringsR.string.downloads_quality_1080
            DownloadQuality.HD -> StringsR.string.downloads_quality_720
        },
    )
    MoreSectionHeader(localizedStringResource(StringsR.string.downloads_download_options))
    var assigned = false
    fun first(): Modifier = if (!assigned) { assigned = true; Modifier.focusRequester(requester) } else Modifier
    if (!state.isSeries) {
        MoreRow(
            text = localizedStringResource(StringsR.string.downloads_download_movie),
            icon = LineIcons.Download,
            onClick = { onDownload(emptyList(), quality) },
            modifier = first(),
        )
    } else {
        val today = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }
        val season = (state.episodes as? EpisodesState.Ready)?.episodes.orEmpty()
            .filter { it.airDate.isNullOrBlank() || it.airDate!! <= today }
        val downloadedKeys = downloads.filter { it.status != DownloadStatus.FAILED }.map { it.id }.toSet()
        fun key(e: Episode) = WatchProgress.episodeKey(state.details.item.id, e.season, e.number)
        val pending = season.filter { key(it) !in downloadedKeys }
        val current = state.selectedEpisode
        if (current != null && key(current) !in downloadedKeys) {
            MoreRow(
                text = localizedStringResource(StringsR.string.downloads_option_episode, current.localizedCode()),
                icon = LineIcons.Download,
                detail = current.title,
                onClick = { onDownload(listOf(current), quality) },
                modifier = first(),
            )
        }
        if (current != null) {
            val next = season.dropWhile { it != current }.drop(1).filter { key(it) !in downloadedKeys }.take(NEXT_EPISODES)
            if (next.size > 1) {
                MoreRow(
                    text = localizedStringResource(StringsR.string.downloads_option_next, next.size),
                    icon = LineIcons.Download,
                    detail = "${next.first().localizedCode()} – ${next.last().localizedCode()}",
                    onClick = { onDownload(next, quality) },
                    modifier = first(),
                )
            }
        }
        state.selectedSeason?.let { number ->
            if (pending.isNotEmpty()) {
                MoreRow(
                    text = localizedStringResource(StringsR.string.downloads_option_season, number),
                    icon = LineIcons.Download,
                    detail = localizedStringResource(StringsR.string.downloads_option_count, pending.size),
                    onClick = { onDownload(pending, quality) },
                    modifier = first(),
                )
            }
            if (pending.isEmpty()) {
                Text(
                    localizedStringResource(StringsR.string.downloads_season_all_done, number),
                    color = Grey,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            val unwatched = pending.filter { state.progressOf(it)?.isWatched != true }
            if (unwatched.isNotEmpty() && unwatched.size != pending.size) {
                MoreRow(
                    text = localizedStringResource(StringsR.string.downloads_option_unwatched, number),
                    icon = LineIcons.Eye,
                    detail = localizedStringResource(StringsR.string.downloads_option_count, unwatched.size),
                    onClick = { onDownload(unwatched, quality) },
                )
            }
        }
    }
    MoreSectionHeader(localizedStringResource(StringsR.string.downloads_quality))
    MoreRow(
        text = "‹  $qualityLabel  ›",
        icon = AppIcons.Hdr,
        detail = localizedStringResource(StringsR.string.downloads_quality_hint),
        onClick = { quality = DownloadQuality.entries[(quality.ordinal + 1) % DownloadQuality.entries.size] },
        onShift = { d -> quality = DownloadQuality.entries[((quality.ordinal + d) % 3 + 3) % 3] },
        modifier = first(),
    )
}

private const val NEXT_EPISODES = 5

/** Panel lateral de la derecha (como el de fuentes), con el resto de la ficha atenuada detrás. */
@Composable
private fun MoreSidePanel(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .onBackKey(enabled = true, onBack = onBack),
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(480.dp)
                .background(AppColors.BackgroundRaised.copy(alpha = 0.97f))
                .verticalScroll(rememberScrollState())
                .padding(top = 36.dp, start = 24.dp, end = 24.dp, bottom = 36.dp),
        ) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
            subtitle?.let {
                Text(it, color = Grey, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MoreSectionHeader(text: String) {
    Text(
        text.uppercase(java.util.Locale.getDefault()),
        color = Color.White.copy(alpha = 0.45f),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun MoreRow(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    highlighted: Boolean = false,
    progress: Float? = null,
    onShift: ((Int) -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) Color.Black else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (onShift != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> { onShift(-1); true }
                            Key.DirectionRight -> { onShift(1); true }
                            else -> false
                        }
                    }
                } else {
                    Modifier
                },
            )
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .tvClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        focused -> Color.Black.copy(alpha = 0.08f)
                        highlighted -> AppColors.Accent.copy(alpha = 0.25f)
                        else -> Color.White.copy(alpha = 0.08f)
                    },
                ),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = if (highlighted && !focused) Color.White else fg, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text, color = fg, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = if (focused) Color.Black.copy(alpha = 0.6f) else Grey, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            progress?.let { p ->
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(fg.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(p).height(3.dp).background(if (focused) Color.Black else AppColors.Accent))
                }
            }
        }
    }
}


/**
 * Long-press on an episode card opens this modal instead of silently toggling the watched flag: the
 * gesture had no feedback, so an accidental hold could mark or unmark an episode with no way to tell.
 */
@Composable
private fun EpisodeActions(
    state: DetailsUiState.Content,
    episode: Episode,
    download: Download?,
    onDownload: () -> Unit,
    onPlay: () -> Unit,
    onToggleWatched: () -> Unit,
    onDismiss: () -> Unit,
) {
    val watched = state.progressOf(episode)?.isWatched == true
    ActionMenuDialog(
        title = listOf(episode.localizedCode(), episode.title).filter { it.isNotBlank() }.joinToString(" · "),
        onDismiss = onDismiss,
        scrimAlpha = 0.88f,
        consumeOpeningKeyRelease = true,
        actions = listOf(
            ActionMenuItem(localizedStringResource(StringsR.string.details_cancel), LineIcons.Close, onDismiss),
            ActionMenuItem(localizedStringResource(StringsR.string.details_episode_play), AppIcons.Play, onPlay),
            ActionMenuItem(
                localizedStringResource(
                    when {
                        download?.isDone == true -> StringsR.string.downloads_delete
                        download?.isActive == true -> StringsR.string.downloads_already_running
                        else -> StringsR.string.downloads_download_episode
                    },
                ),
                if (download?.isDone == true) LineIcons.Delete else LineIcons.Download,
                onDownload,
            ),
            ActionMenuItem(
                text = localizedStringResource(
                    if (episodeMenuTogglesToWatched(state, episode)) {
                        StringsR.string.details_episode_mark_watched
                    } else {
                        StringsR.string.details_episode_mark_unwatched
                    },
                ),
                icon = if (watched) AppIcons.Check else LineIcons.Eye,
                onClick = onToggleWatched,
            ),
        ),
    )
}

@Composable
private fun RatingPickerDialog(
    state: DetailsUiState.Content,
    target: TraktRatingTarget,
    ratings: TraktRatingsState,
    onSave: (Int?) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ready = ratings as? TraktRatingsState.Ready
    val currentRating = when (target) {
        is TraktRatingTarget.Episode -> ready?.episodeRating
        else -> ready?.overallRating
    }
    var selected by remember(target) { mutableIntStateOf(currentRating ?: 8) }
    LaunchedEffect(target, currentRating) { selected = currentRating ?: 8 }
    val requester = remember { FocusRequester() }
    LaunchedEffect(ratings) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    RatingsDialog(title = ratingTargetLabel(state, target), onDismiss = onDismiss) {
        when (ratings) {
            TraktRatingsState.Idle, TraktRatingsState.Loading -> {
                CircularProgressIndicator(
                    color = AppColors.Accent,
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(36.dp),
                )
            }
            TraktRatingsState.NotConnected -> {
                Text(
                    text = localizedStringResource(StringsR.string.details_trakt_connect_to_rate),
                    color = Grey,
                    style = MaterialTheme.typography.bodyLarge,
                )
                TvButton(
                    text = localizedStringResource(StringsR.string.details_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(requester).align(Alignment.CenterHorizontally),
                )
            }
            is TraktRatingsState.Error -> {
                Text(ratings.message, color = AppColors.Error, style = MaterialTheme.typography.bodyLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    TvButton(
                        text = localizedStringResource(StringsR.string.details_retry),
                        onClick = onRetry,
                        modifier = Modifier.focusRequester(requester),
                    )
                    TvButton(text = localizedStringResource(StringsR.string.details_cancel), onClick = onDismiss)
                }
            }
            is TraktRatingsState.Ready -> {
                Text(
                    text = localizedStringResource(StringsR.string.details_rating_picker_help),
                    color = Grey,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                RatingStarsControl(
                    value = selected,
                    enabled = !ratings.saving,
                    onChange = { selected = it },
                    onSave = { onSave(selected) },
                    modifier = Modifier.align(Alignment.CenterHorizontally).focusRequester(requester),
                )
                ratings.error?.let { Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodyMedium) }
                if (ratings.saving) {
                    Text(
                        text = localizedStringResource(StringsR.string.details_rating_saving),
                        color = Grey,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    if (currentRating != null) {
                        TvButton(
                            text = localizedStringResource(StringsR.string.details_remove_rating),
                            onClick = { onSave(null) },
                            enabled = !ratings.saving,
                        )
                    }
                    TvButton(
                        text = localizedStringResource(StringsR.string.details_cancel),
                        onClick = onDismiss,
                        enabled = !ratings.saving,
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingsDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) = BottomActionDialog(
    title = title,
    onDismiss = onDismiss,
    scrimAlpha = 0.94f,
    content = content,
)

@Composable
private fun RatingStarsControl(
    value: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onChange((value - 1).coerceAtLeast(1)); true }
                    Key.DirectionRight -> { onChange((value + 1).coerceAtMost(10)); true }
                    else -> false
                }
            }
            .clip(AppShapes.Control)
            .background(if (focused) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .border(1.dp, if (focused) Color.White.copy(alpha = 0.65f) else Color.Transparent, AppShapes.Control)
            .tvClickable(enabled = enabled, onClick = onSave)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (star in 1..10) {
                Icon(
                    imageVector = AppIcons.Star,
                    contentDescription = null,
                    tint = if (star <= value) AppColors.Accent else Color.White.copy(alpha = 0.20f),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = localizedStringResource(StringsR.string.details_rating_value, value),
            color = if (focused) Color.White else Grey,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun ratingTargetLabel(state: DetailsUiState.Content, target: TraktRatingTarget): String = when (target) {
    is TraktRatingTarget.Movie -> localizedStringResource(StringsR.string.details_rate_movie)
    is TraktRatingTarget.Show -> localizedStringResource(StringsR.string.details_rate_show)
    is TraktRatingTarget.Episode -> {
        val code = state.selectedEpisode
            ?.takeIf { it.season == target.season && it.number == target.episode }
            ?.localizedCode() ?: "S${target.season}E${target.episode}"
        localizedStringResource(StringsR.string.details_rate_episode, code)
    }
}

private fun ratingSuffix(rating: Int?): String = rating?.let { " · $it/10" }.orEmpty()

internal data class EpisodeWatchedAction(val episode: Episode, val watched: Boolean)

/**
 * The episode the dock can mark watched: the one currently selected. [EpisodeWatchedAction.watched] mirrors
 * [WatchProgress.isWatched] so the action reads "watched" or "mark watched" for that single episode.
 */
internal fun episodeWatchedAction(state: DetailsUiState.Content): EpisodeWatchedAction? {
    if (!state.isSeries) return null
    val episode = state.selectedEpisode ?: return null
    return EpisodeWatchedAction(episode, state.progressOf(episode)?.isWatched == true)
}

internal fun seasonAllWatched(state: DetailsUiState.Content): Boolean {
    val episodes = (state.episodes as? EpisodesState.Ready)?.episodes.orEmpty()
    return episodes.isNotEmpty() && episodes.all { state.progressOf(it)?.isWatched == true }
}

/** What the long-press modal's toggle will do: unwatched episodes get marked, watched ones unmarked. */
internal fun episodeMenuTogglesToWatched(state: DetailsUiState.Content, episode: Episode): Boolean =
    state.progressOf(episode)?.isWatched != true

@Composable
private fun episodeWatchedLabel(episode: Episode, watched: Boolean): String = localizedStringResource(
    if (watched) StringsR.string.details_episode_watched else StringsR.string.details_mark_episode_watched,
    episode.localizedCode(),
)

@Composable
private fun seasonWatchedLabel(state: DetailsUiState.Content): String? {
    val season = state.selectedSeason ?: return null
    val episodes = (state.episodes as? EpisodesState.Ready)?.episodes.orEmpty()
    if (episodes.isEmpty()) return null
    return if (seasonAllWatched(state)) {
        localizedStringResource(StringsR.string.details_season_watched, season)
    } else {
        localizedStringResource(StringsR.string.details_mark_season_watched, season)
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
            val sources = state.sourceExclusions.filter(s.sources)
                .filter { !state.spanishOnly || it.advertisesSpanishAudio }
                .distinctBy { it.infoHash.lowercase() }
            val scope = state.selectedEpisode?.takeIf { state.isSeries }?.let { localizedStringResource(StringsR.string.details_for_episode, it.localizedCode()) }.orEmpty()
            when {
                sources.isEmpty() && s.pending.isNotEmpty() -> Text(localizedStringResource(StringsR.string.details_finding_sources), color = Grey, style = MaterialTheme.typography.bodyMedium)
                sources.isEmpty() && state.spanishOnly -> Text(localizedStringResource(StringsR.string.details_no_spanish_sources_available_for_this_profile), color = Grey, style = MaterialTheme.typography.bodyMedium)
                sources.isEmpty() && s.sources.isNotEmpty() && !state.sourceExclusions.isEmpty ->
                    Text(localizedStringResource(StringsR.string.details_no_sources_match_exclusions), color = Grey, style = MaterialTheme.typography.bodyMedium)
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
    onEpisodeFocused: (Episode) -> Unit = {},
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
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
        if (loadedEpisodes.isNotEmpty()) {
            // Con varias temporadas el recuento va aquí: al lado de las pestañas lo tapaban al desplazarlas.
            if (seasons.size > 1) {
                Text(
                    text = localizedStringResource(StringsR.string.details_watched_count_short, watched, loadedEpisodes.size),
                    color = WatchedCountColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 56.dp, bottom = 2.dp),
                )
            }
            Text(
                text = localizedStringResource(StringsR.string.details_hold_ok_for_episode_options),
                color = Color.White.copy(alpha = 0.38f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 56.dp),
            )
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
                            onFocused = { onEpisodeFocused(ep) },
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
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(if (!focused && !selected) Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp)) else Modifier)
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
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) { if (focused) onFocused() }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "episodeScale")
    val shape = RoundedCornerShape(6.dp)
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
                .background(Color(0xFF232327))
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
                        .clip(RoundedCornerShape(13.dp))
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
                    Modifier.align(Alignment.Center).size(44.dp).clip(RoundedCornerShape(22.dp))
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
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
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
    onMarkBroken: (StreamSource) -> Unit,
    autoPick: StreamSource? = null,
) {
    val firstRequester = remember { FocusRequester() }
    val allSources = (state.sources as? SourcesState.Ready)?.sources.orEmpty().filter { !it.isMarkedBroken(state.brokenSources) }
    var brokenCandidate by remember { mutableStateOf<StreamSource?>(null) }
    var showAll by remember { mutableStateOf(false) }
    val sources = if (showAll) allSources else state.sourceExclusions.filter(allSources)
    val order = SourceProvider.entries.map { localizedStringResource(it.sectionResource) }.distinct()
    val pending = (state.sources as? SourcesState.Ready)?.pending.orEmpty()
    // El buscador de castellano reparte sus fuentes en las secciones de cada sitio: no tiene cabecera propia.
    val castellanoSection = localizedStringResource(SourceProvider.CASTELLANO.sectionResource)
    val unavailable = ((state.sources as? SourcesState.Ready)?.unavailable.orEmpty() +
        pending.associateWith { localizedStringResource(StringsR.string.details_searching) }) - castellanoSection
    val spanishFirst = state.spanishFirst
    val spanishOnly = state.spanishOnly
    val groups = remember(sources, unavailable, spanishFirst, spanishOnly) {
        val byProvider = sources.groupBy { it.provider }
        (byProvider.keys + unavailable.keys).distinct().sortedBy { order.indexOf(it) }.map { section ->
            // Mismo orden que "Reproducir" (el que ya trae la lista): nada de reordenar aquí.
            section to byProvider[section].orEmpty()
                .filter { !spanishOnly || it.advertisesSpanishAudio }
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
            if (state.sources is SourcesState.Ready) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = localizedStringResource(StringsR.string.details_hold_ok_to_mark_source_broken),
                    color = Grey.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = localizedStringResource(
                            StringsR.string.details_sources_filtered_count,
                            sources.count { !spanishOnly || it.advertisesSpanishAudio },
                            allSources.size,
                        ),
                        color = Grey,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (!state.sourceExclusions.isEmpty) {
                        ShowAllChip(showAll = showAll, onClick = { showAll = !showAll })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            when (val s = state.sources) {
                SourcesState.Idle -> Text(localizedStringResource(StringsR.string.details_pick_an_episode_first), color = Grey)
                SourcesState.Loading -> Text(localizedStringResource(StringsR.string.details_finding_sources), color = Grey)
                is SourcesState.Error -> Text(s.message, color = Grey)
                is SourcesState.Ready -> if (groups.all { it.second.isEmpty() } && s.pending.isEmpty()) {
                    Text(
                        when {
                            spanishOnly -> localizedStringResource(StringsR.string.details_no_spanish_sources_available_for_this_profile)
                            !showAll && allSources.isNotEmpty() && sources.isEmpty() ->
                                localizedStringResource(StringsR.string.details_no_sources_match_exclusions)
                            else -> localizedStringResource(StringsR.string.details_no_sources)
                        },
                        color = Grey,
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        val pick = autoPick?.takeIf { p -> sources.any { it.id == p.id } }
                        if (pick != null) {
                            item(key = "auto") {
                                Column {
                                    Text(
                                        localizedStringResource(StringsR.string.details_plays_on_play),
                                        color = AppColors.Accent,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                                    )
                                    SourceRow(
                                        source = pick,
                                        blocked = isBlocked(pick),
                                        onClick = { onPick(pick) },
                                        onLongClick = { brokenCandidate = pick },
                                        modifier = Modifier.focusRequester(firstRequester),
                                    )
                                    Spacer(Modifier.height(10.dp))
                                }
                            }
                        }
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
                                    modifier = if (index == 0 && pick == null) Modifier.focusRequester(firstRequester) else Modifier,
                                )
                            }
                            if (name in open) {
                                items(list, key = { it.id }) { source ->
                                    SourceRow(
                                        source = source,
                                        blocked = isBlocked(source),
                                        onClick = { onPick(source) },
                                        onLongClick = { brokenCandidate = source },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    brokenCandidate?.let { source ->
        ActionMenuDialog(
            title = source.title,
            onDismiss = { brokenCandidate = null },
            consumeOpeningKeyRelease = true,
            actions = listOf(
                ActionMenuItem(
                    localizedStringResource(StringsR.string.details_cancel),
                    LineIcons.Close,
                ) { brokenCandidate = null },
                ActionMenuItem(
                    localizedStringResource(StringsR.string.details_mark_source_broken),
                    LineIcons.Delete,
                ) {
                    onMarkBroken(source)
                    brokenCandidate = null
                },
            ),
        )
    }
}

@Composable
private fun SpanishOnlyChip(enabled: Boolean, onClick: () -> Unit) {
    SourcePanelChip(
        text = if (enabled) "✓ " + localizedStringResource(StringsR.string.details_spanish_only) else localizedStringResource(StringsR.string.details_spanish_only),
        highlighted = enabled,
        onClick = onClick,
    )
}

@Composable
private fun ShowAllChip(showAll: Boolean, onClick: () -> Unit) {
    SourcePanelChip(
        text = localizedStringResource(
            if (showAll) StringsR.string.details_hide_excluded else StringsR.string.details_show_all,
        ),
        highlighted = showAll,
        onClick = onClick,
    )
}

@Composable
private fun SourcePanelChip(text: String, highlighted: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = text,
        color = if (focused) Color.Black else Color.White,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .background(
                when {
                    focused -> Color.White
                    highlighted -> Color.White.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                RoundedCornerShape(50),
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
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(name, color = color, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        val secondary = if (focused) Color.Black.copy(alpha = 0.7f) else Grey
        Text(
            note ?: localizedStringResource(StringsR.string.details_source_count, count),
            color = secondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (note == null && cached > 0) {
            // Same bolt icon and colour as the cached source rows, instead of a yellow emoji.
            Text("  ·  ", color = secondary, style = MaterialTheme.typography.bodyMedium)
            Icon(
                AppIcons.Bolt,
                contentDescription = localizedStringResource(StringsR.string.details_cached),
                tint = if (focused) CachedFocused else Green,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(cached.toString(), color = secondary, style = MaterialTheme.typography.bodyMedium)
        }
        if (note == null) Text(if (expanded) "   ▾" else "   ›", color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SourceRow(
    source: StreamSource,
    blocked: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
            .tvClickable(onLongClick = onLongClick, onClick = onClick)
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
            if (source.isCached) Icon(AppIcons.Bolt, contentDescription = localizedStringResource(StringsR.string.details_in_your_real_debrid), tint = if (focused) CachedFocused else Green)
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
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(2.dp))
            // Enfocada, el nombre completo de la fuente se desplaza (marquesina) en vez de cortarse con "…".
            Text(
                text = source.seeders?.let { "$it seeds · ${source.title}" } ?: source.title,
                color = sub,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (focused) {
                    Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 700, repeatDelayMillis = 1500)
                } else {
                    Modifier
                },
            )
        }
    }
}

@Composable
private fun WatchBar(fraction: Float, modifier: Modifier = Modifier) {
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
