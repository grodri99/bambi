package com.shieldstream.tv.details

import com.shieldstream.tv.strings.R as StringsR
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.ITHIL_ALLDEBRID
import com.shieldstream.tv.domain.PrioritizeSourcesUseCase
import com.shieldstream.tv.domain.RealDebridRepository
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.NextEpisode
import com.shieldstream.tv.domain.SelectedContentHolder
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.TraktRepository
import kotlinx.coroutines.flow.first
import com.shieldstream.tv.media.DeviceCapabilityDetector
import com.shieldstream.tv.model.DeviceOptimization
import com.shieldstream.tv.model.CatalogDetails
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.PlaybackMedia
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.WatchProgress
import com.shieldstream.tv.strings.Texts
import com.shieldstream.tv.strings.episodeCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject

sealed interface SourcesState {
    data object Idle : SourcesState
    data object Loading : SourcesState
    data class Ready(
        val sources: List<StreamSource>,
        val unavailable: Map<String, String> = emptyMap(),
        val pending: Set<String> = emptySet(),
    ) : SourcesState
    data class Error(val message: String) : SourcesState
}

sealed interface EpisodesState {
    data object Loading : EpisodesState
    data class Ready(val episodes: List<Episode>) : EpisodesState
    data class Error(val message: String) : EpisodesState
}

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Error(val message: String) : DetailsUiState
    data class Content(
        val details: CatalogDetails,
        val sources: SourcesState = SourcesState.Idle,
        val selectedSeason: Int? = null,
        val episodes: EpisodesState = EpisodesState.Loading,
        val selectedEpisode: Episode? = null,
        val pendingPlay: Boolean = false,
        val message: String? = null,
        val progress: Map<String, WatchProgress> = emptyMap(),
        val spanishFirst: Boolean = false,
        val spanishOnly: Boolean = false,
        val inMyList: Boolean = false,
        val following: Boolean = false,
    ) : DetailsUiState {
        val isSeries: Boolean get() = details.item.type == MediaType.TV

        fun progressOf(episode: Episode): WatchProgress? =
            progress[WatchProgress.episodeKey(details.item.id, episode.season, episode.number)]

        val mainProgress: WatchProgress?
            get() = if (isSeries) selectedEpisode?.let { progressOf(it) } else progress[WatchProgress.movieKey(details.item.id)]
    }
}

sealed interface DetailsEvent {
    data object OpenPlayer : DetailsEvent
}

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val realDebridRepository: RealDebridRepository,
    private val prioritizeSourcesUseCase: PrioritizeSourcesUseCase,
    private val deviceCapabilities: DeviceCapabilityDetector,
    private val holder: SelectedContentHolder,
    private val watchProgress: WatchProgressRepository,
    private val settings: SettingsRepository,
    private val trakt: TraktRepository,
    private val myList: MyListRepository,
    private val tracking: EpisodeTrackingRepository,
    private val texts: Texts,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DetailsEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var sourcesJob: Job? = null
    private var episodesJob: Job? = null
    private var pendingFromStart = false
    private var pendingNext: NextEpisode? = null
    private var requestedAtNs = 0L
    private var detailsLoaded = false

    init {
        load()
        viewModelScope.launch {
            watchProgress.progress.collect { map -> updateContent { c -> withProgress(c, map) } }
        }
        viewModelScope.launch {
            myList.entries.collect { list ->
                updateContent { c -> c.copy(inMyList = list.any { it.type == c.details.item.type && it.id == c.details.item.id }) }
            }
        }
        viewModelScope.launch {
            tracking.followedShowIds.collect { ids ->
                updateContent { c -> c.copy(following = c.details.item.id in ids) }
            }
        }
        viewModelScope.launch {
            holder.prefetch.collect { next ->
                val c = content() ?: return@collect
                if (next == null || !c.isSeries || next.showId != c.details.item.id) return@collect
                val ep = (c.episodes as? EpisodesState.Ready)?.episodes
                    ?.firstOrNull { it.season == next.season && it.number == next.episode } ?: return@collect
                if (c.selectedEpisode == ep && (c.sources is SourcesState.Ready || c.sources == SourcesState.Loading)) return@collect
                updateContent { it.copy(selectedEpisode = ep) }
                loadSources(ep)
            }
        }
    }

    private fun withProgress(c: DetailsUiState.Content, map: Map<String, WatchProgress>): DetailsUiState.Content {
        val updated = c.copy(progress = map)
        val current = updated.selectedEpisode?.takeIf { updated.isSeries } ?: return updated
        if (updated.progressOf(current)?.isWatched != true) return updated
        val episodes = (updated.episodes as? EpisodesState.Ready)?.episodes ?: return updated
        val next = episodes.getOrNull(episodes.indexOf(current) + 1) ?: return updated
        return updated.copy(selectedEpisode = next, sources = SourcesState.Idle)
    }

    fun isBlocked(source: StreamSource): Boolean = source.resolveUrl == null && realDebridRepository.isBlocked(source.infoHash)

    fun toggleSpanishOnly() {
        val enabled = !(content()?.spanishOnly ?: return)
        updateContent { it.copy(spanishOnly = enabled) }
        viewModelScope.launch { settings.setSpanishOnlySources(enabled) }
    }

    fun toggleMyList() {
        val item = content()?.details?.item ?: return
        if (myList.contains(item.type, item.id)) myList.remove(item.type, item.id) else myList.add(item.type, item.id)
    }

    fun toggleFollowing() {
        val item = content()?.details?.item ?: return
        if (item.type == MediaType.TV) tracking.setFollowing(item.id, !tracking.isFollowing(item.id))
    }

    // ------------------------------------------------------------------ visto / no visto a mano

    fun toggleMovieWatched() {
        val c = _uiState.value as? DetailsUiState.Content ?: return
        if (c.isSeries) return
        val key = WatchProgress.movieKey(c.details.item.id)
        setWatched(listOf(key to c.details.runtimeMinutes?.toLong()), watched = c.progress[key]?.isWatched != true)
    }

    fun toggleEpisodeWatched(episode: Episode) {
        val c = _uiState.value as? DetailsUiState.Content ?: return
        val key = WatchProgress.episodeKey(c.details.item.id, episode.season, episode.number)
        setWatched(listOf(key to episode.runtimeMinutes?.toLong()), watched = c.progressOf(episode)?.isWatched != true)
    }

    fun toggleSeasonWatched() {
        val c = _uiState.value as? DetailsUiState.Content ?: return
        val episodes = (c.episodes as? EpisodesState.Ready)?.episodes.orEmpty()
        if (episodes.isEmpty()) return
        val allWatched = episodes.all { c.progressOf(it)?.isWatched == true }
        setWatched(
            episodes.map { WatchProgress.episodeKey(c.details.item.id, it.season, it.number) to it.runtimeMinutes?.toLong() },
            watched = !allWatched,
        )
    }

    private fun setWatched(entries: List<Pair<String, Long?>>, watched: Boolean) {
        if (entries.isEmpty()) return
        trakt.setWatched(entries.map { it.first }, watched)
        if (watched) {
            entries.forEach { (key, minutes) ->
                val duration = watchProgress.get(key)?.durationMs?.takeIf { it > 1 } ?: minutes?.times(60_000L) ?: 1L
                watchProgress.save(key, duration, duration)
            }
        } else {
            watchProgress.removeRemote(entries.map { it.first })
        }
    }

    private fun load() {
        val request = holder.details.value
        if (request == null) {
            _uiState.value = DetailsUiState.Error(texts.get(StringsR.string.details_no_title_selected))
            return
        }
        val item = request.item
        requestedAtNs = request.requestedAtNs
        detailsLoaded = false
        trace("details_opened")
        val prefetchAutoplay = request.playImmediately &&
            (item.type == MediaType.MOVIE || request.target != null)
        if (request.playImmediately) {
            _uiState.value = DetailsUiState.Content(
                CatalogDetails(item),
                progress = watchProgress.progress.value,
            )
        }
        viewModelScope.launch {
            if (prefetchAutoplay) {
                val target = request.target
                if (item.type == MediaType.TV && target != null) {
                    selectSeason(
                        target.season,
                        preferredEpisode = target.number,
                        playImmediately = true,
                        prefetchSources = true,
                    )
                } else {
                    loadSources(episode = null)
                }
            }
            val details = runCatching { catalogRepository.getDetails(item) }.getOrElse { CatalogDetails(item) }
            trace("details_ready")
            val spanishFirst = settings.spanishFirst.first()
            val spanishOnly = settings.spanishOnlySources.first()
            if (request.playImmediately) {
                updateContent {
                    it.copy(
                        details = details,
                        progress = watchProgress.progress.value,
                        inMyList = myList.contains(details.item.type, details.item.id),
                        spanishFirst = spanishFirst,
                        spanishOnly = spanishOnly,
                    )
                }
            } else {
                _uiState.value = DetailsUiState.Content(
                    details,
                    progress = watchProgress.progress.value,
                    inMyList = myList.contains(details.item.type, details.item.id),
                    spanishFirst = spanishFirst,
                    spanishOnly = spanishOnly,
                )
            }
            detailsLoaded = true
            if (prefetchAutoplay) {
                if (item.type == MediaType.MOVIE) {
                    when (content()?.sources) {
                        is SourcesState.Error -> updateContent { it.copy(pendingPlay = false) }
                        SourcesState.Idle -> play()
                        else -> {
                            updateContent { it.copy(pendingPlay = true) }
                            tryStartPendingPlayback()
                        }
                    }
                } else {
                    tryStartPendingPlayback()
                }
                return@launch
            }
            val playImmediately = request.playImmediately
            if (details.item.type == MediaType.TV) {
                val requested = request.target?.let { it.season to it.number }
                val (season, episode) = requested
                    ?: continueTarget(details)
                    ?: (details.seasons.firstOrNull()?.number to null)
                if (season != null) {
                    selectSeason(season, preferredEpisode = episode, playImmediately = playImmediately)
                } else {
                    updateContent {
                        it.copy(
                            episodes = EpisodesState.Error(texts.get(StringsR.string.details_no_seasons_available)),
                        )
                    }
                }
            } else {
                if (playImmediately) play() else loadSources(episode = null)
            }
        }
    }

    private fun continueTarget(details: CatalogDetails): Pair<Int, Int>? {
        val prefix = WatchProgress.showPrefix(details.item.id)
        val last = watchProgress.progress.value
            .filterKeys { it.startsWith(prefix) }
            .maxByOrNull { it.value.updatedAt } ?: return null
        val parts = last.key.removePrefix(prefix).split(':')
        val season = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val episode = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (!last.value.isWatched) return season to episode

        val episodesInSeason = details.seasons.firstOrNull { it.number == season }?.episodeCount ?: Int.MAX_VALUE
        if (episode < episodesInSeason) return season to episode + 1
        val nextSeason = details.seasons.firstOrNull { it.number > season } ?: return season to episode
        return nextSeason.number to 1
    }

    fun selectSeason(
        season: Int,
        preferredEpisode: Int? = null,
        playImmediately: Boolean = false,
        prefetchSources: Boolean = false,
    ) {
        val content = content() ?: return
        if (preferredEpisode == null && content.selectedSeason == season && content.episodes is EpisodesState.Ready) return
        episodesJob?.cancel()
        sourcesJob?.cancel()
        updateContent {
            it.copy(
                selectedSeason = season,
                episodes = EpisodesState.Loading,
                selectedEpisode = null,
                sources = SourcesState.Idle,
                pendingPlay = false,
            )
        }
        if (prefetchSources && preferredEpisode != null) {
            trace("source_prefetch_started")
            loadSources(Episode(season, preferredEpisode, "", "", null, null))
        }
        episodesJob = viewModelScope.launch {
            runCatching { catalogRepository.getEpisodes(content.details.item, season) }
                .onSuccess { eps ->
                    trace("episodes_ready")
                    var episodeToPlay: Episode? = null
                    updateContent { c ->
                        val selected = preferredEpisode?.let { n -> eps.firstOrNull { it.number == n } }
                            ?: eps.firstOrNull()
                        if (playImmediately && c.sources !is SourcesState.Error) episodeToPlay = selected
                        c.copy(
                            episodes = EpisodesState.Ready(eps),
                            selectedEpisode = selected,
                        )
                    }
                    episodeToPlay?.let { episode ->
                        if (content()?.sources == SourcesState.Idle) {
                            playEpisode(episode)
                        } else {
                            updateContent { it.copy(selectedEpisode = episode, pendingPlay = true) }
                            tryStartPendingPlayback()
                        }
                    }
                    pendingNext?.takeIf { it.season == season }?.let { next ->
                        pendingNext = null
                        eps.firstOrNull { it.number == next.episode }?.let { playEpisode(it) }
                    }
                }
                .onFailure { e ->
                    updateContent {
                        it.copy(
                            episodes = EpisodesState.Error(e.message ?: "Error"),
                            pendingPlay = false,
                        )
                    }
                }
        }
    }

    fun play(fromStart: Boolean = false) {
        val c = content() ?: return
        pendingFromStart = fromStart
        if (c.isSeries) {
            val ep = c.selectedEpisode ?: return
            playEpisode(ep, fromStart)
            return
        }
        when (val s = c.sources) {
            is SourcesState.Ready -> if (s.sources.none { !c.spanishOnly || it.advertisesSpanishAudio } && s.pending.isNotEmpty()) {
                pendingFromStart = fromStart
                updateContent { it.copy(pendingPlay = true) }
            } else {
                startPlayback(s.sources, startIndex = 0, fromStart = fromStart)
            }
            SourcesState.Loading -> updateContent { it.copy(pendingPlay = true) }
            SourcesState.Idle, is SourcesState.Error -> {
                updateContent { it.copy(pendingPlay = true) }
                loadSources(episode = null)
            }
        }
    }

    fun playEpisode(episode: Episode, fromStart: Boolean = false) {
        val c = content() ?: return
        pendingFromStart = fromStart
        val sameEpisode = c.selectedEpisode == episode
        val ready = c.sources as? SourcesState.Ready
        if (sameEpisode && ready != null) {
            if (ready.sources.none { !c.spanishOnly || it.advertisesSpanishAudio } && ready.pending.isNotEmpty()) {
                updateContent { it.copy(pendingPlay = true) }
                return
            }
            startPlayback(ready.sources, startIndex = 0, fromStart = fromStart)
            return
        }
        updateContent { it.copy(selectedEpisode = episode, pendingPlay = true) }
        if (!(sameEpisode && c.sources == SourcesState.Loading)) loadSources(episode)
    }

    fun ensureSources() {
        val c = content() ?: return
        if (c.sources is SourcesState.Ready || c.sources == SourcesState.Loading) return
        if (c.isSeries && c.selectedEpisode == null) return
        loadSources(c.selectedEpisode)
    }

    fun playSource(source: StreamSource) {
        val ready = content()?.sources as? SourcesState.Ready ?: return
        startPlayback(
            ready.sources,
            startIndex = ready.sources.indexOf(source).coerceAtLeast(0),
            fromStart = false,
            useSavedSource = false,
        )
    }

    fun consumeMessage() = updateContent { it.copy(message = null) }

    fun onScreenResumed() {
        val next = holder.consumeNextRequest() ?: return
        val c = content() ?: return
        val loaded = (c.episodes as? EpisodesState.Ready)?.episodes
            ?.firstOrNull { it.season == next.season && it.number == next.episode }
        if (loaded != null) {
            playEpisode(loaded)
        } else {
            pendingNext = next
            selectSeason(next.season, preferredEpisode = next.episode)
        }
    }

    private fun nextEpisodeAfter(c: DetailsUiState.Content, episode: Episode): NextEpisode? {
        val eps = (c.episodes as? EpisodesState.Ready)?.episodes.orEmpty()
        val index = eps.indexOfFirst { it.season == episode.season && it.number == episode.number }
        val show = c.details.item.id
        eps.getOrNull(index + 1)?.takeIf { index >= 0 }?.let { return NextEpisode(show, it.season, it.number, "${texts.episodeCode(it)} · ${it.title}") }
        val nextSeason = c.details.seasons.map { it.number }.filter { it > episode.season }.minOrNull() ?: return null
        return NextEpisode(show, nextSeason, 1, texts.get(StringsR.string.details_next_episode_label, nextSeason))
    }

    fun onScreenHidden() {
        if (content()?.pendingPlay == true) updateContent { it.copy(pendingPlay = false) }
    }

    private fun loadSources(episode: Episode?) {
        val c = content() ?: return
        sourcesJob?.cancel()
        updateContent { it.copy(sources = SourcesState.Loading) }
        var sourcesLogged = false
        sourcesJob = viewModelScope.launch {
            val spanishFirst = settings.spanishFirst.first()
            val spanishOnly = settings.spanishOnlySources.first()
            val optimization = settings.deviceOptimization.first()
            val capabilities = deviceCapabilities.capabilities
            val hardwareCodecs = when (optimization) {
                DeviceOptimization.AUTOMATIC -> capabilities.hardwareVideoCodecs
                DeviceOptimization.CUSTOM -> emptySet()
            }
            updateContent { it.copy(spanishFirst = spanishFirst, spanishOnly = spanishOnly) }
            var rdCached: Set<String>? = null
            catalogRepository.getStreams(c.details.item, episode)
                .map { result ->
                    if (rdCached == null && result.sources.any { it.resolveUrl == null }) {
                        rdCached = runCatching { realDebridRepository.cachedHashes() }.getOrDefault(emptySet())
                    }
                    val knownCached = rdCached.orEmpty() + result.sources.filter { it.isCached }.map { it.infoHash.lowercase() }
                    val enriched = result.sources.map {
                        if (it.resolveUrl == null) it.copy(isCached = it.infoHash.lowercase() in knownCached) else it
                    }
                    result to prioritizeSourcesUseCase(
                        enriched,
                        capabilities.supportedHdr,
                        hardwareVideoCodecs = hardwareCodecs,
                        preferSpanish = spanishFirst,
                    )
                }
                .catch { e ->
                    updateContent {
                        it.copy(
                            sources = SourcesState.Error(texts.get(StringsR.string.details_sources_load_error, e.message ?: e.javaClass.simpleName)),
                            pendingPlay = false,
                        )
                    }
                }
                .collect { (result, sorted) ->
                    if (sorted.isNotEmpty() && !sourcesLogged) {
                        sourcesLogged = true
                        trace("sources_ready", "count=${sorted.size}")
                    }
                    updateContent { it.copy(sources = SourcesState.Ready(sorted, result.unavailable, result.pending)) }
                    tryStartPendingPlayback()
                }
        }
    }

    private fun tryStartPendingPlayback() {
        val c = content() ?: return
        if (!c.pendingPlay || !detailsLoaded) return
        val ready = c.sources as? SourcesState.Ready ?: return
        val enoughCached = ready.sources.count {
            it.isCached && (!c.spanishOnly || it.advertisesSpanishAudio)
        } >= MIN_CACHED_TO_START
        if (!enoughCached && ready.pending.isNotEmpty()) return
        updateContent { it.copy(pendingPlay = false) }
        startPlayback(ready.sources, startIndex = 0, fromStart = pendingFromStart)
    }

    private fun startPlayback(
        sorted: List<StreamSource>,
        startIndex: Int,
        fromStart: Boolean,
        useSavedSource: Boolean = true,
    ) {
        val c = content() ?: return
        val item = c.details.item
        val episode = c.selectedEpisode?.takeIf { c.isSeries }
        val key = if (episode != null) {
            WatchProgress.episodeKey(item.id, episode.season, episode.number)
        } else {
            WatchProgress.movieKey(item.id)
        }
        val savedSourceId = if (useSavedSource) watchProgress.get(key)?.sourceId else null
        val savedIndex = sorted.indexOfFirst { it.id == savedSourceId }
        val ordered = if (savedIndex > 0) {
            listOf(sorted[savedIndex]) + sorted.filterIndexed { index, _ -> index != savedIndex }
        } else {
            sorted
        }
        val queue = ordered.drop(startIndex)
            .filter { !c.spanishOnly || it.advertisesSpanishAudio }
            .distinctBy { it.infoHash.lowercase() }
            .filterIndexed { index, s ->
                index == 0 || s.resolveUrl == null || s.isCached || s.debrid == ITHIL_ALLDEBRID
            }
            .take(MAX_QUEUE)
        if (queue.isEmpty()) {
            updateContent {
                it.copy(
                    message = if (c.spanishOnly)
                        texts.get(StringsR.string.details_no_spanish_sources_available_for_this_profile_7dd7b1)
                    else texts.get(StringsR.string.details_no_sources_available_for_this_right_now),
                    pendingPlay = false,
                )
            }
            return
        }
        val label = episode?.let { "${item.title} · ${texts.episodeCode(it)} · ${it.title}" } ?: item.title
        val media = PlaybackMedia(
            type = item.type,
            imdbId = item.imdbId,
            title = item.title,
            originalTitle = c.details.originalTitle,
            year = item.year,
            season = episode?.season,
            episode = episode?.number,
        )
        updateContent { it.copy(pendingPlay = false) }
        trace("player_handoff", "queue=${queue.size}")
        holder.playQueue(
            queue,
            label,
            progressKey = key,
            fromStart = fromStart,
            media = media,
            next = episode?.let { nextEpisodeAfter(c, it) },
            requestedAtNs = requestedAtNs,
        )
        _events.tryEmit(DetailsEvent.OpenPlayer)
    }

    private fun content(): DetailsUiState.Content? = _uiState.value as? DetailsUiState.Content

    private fun trace(stage: String, details: String = "") {
        if (requestedAtNs == 0L) return
        val totalMs = (System.nanoTime() - requestedAtNs) / 1_000_000
        Log.i(STARTUP_TRACE_TAG, "stage=$stage totalMs=$totalMs" + if (details.isEmpty()) "" else " $details")
    }

    private fun updateContent(transform: (DetailsUiState.Content) -> DetailsUiState.Content) {
        _uiState.update { s -> if (s is DetailsUiState.Content) transform(s) else s }
    }

    private companion object {
        const val STARTUP_TRACE_TAG = "StartupTrace"
        const val MAX_QUEUE = 30
        const val MIN_CACHED_TO_START = 3
    }
}
