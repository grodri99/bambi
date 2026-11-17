package com.shieldstream.tv.details

import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.shieldstream.tv.strings.R as StringsR
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.PrioritizeSourcesUseCase
import com.shieldstream.tv.domain.RealDebridRepository
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.NextEpisode
import com.shieldstream.tv.domain.SelectedContentHolder
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.domain.TraktRatingTarget
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

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
    private val sourceProcessingDispatcher: SourceProcessingDispatcher,
    private val downloads: com.shieldstream.tv.domain.DownloadsRepository,
    private val planner: DownloadPlanner,
    private val debridCache: com.shieldstream.tv.domain.DebridCacheRepository,
) : ViewModel() {

    /** Descargas (para enseñar el estado en la ficha y reproducir lo descargado). */
    val downloadList: StateFlow<List<com.shieldstream.tv.model.Download>> = downloads.downloads
    val plannerProgress: StateFlow<PlannerProgress?> = planner.progress

    init {
        viewModelScope.launch { planner.messages.collect { msg -> updateContent { it.copy(message = msg) } } }
    }

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DetailsEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var sourcesJob: Job? = null
    private var episodesJob: Job? = null
    private var ratingsJob: Job? = null
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

    /** Descarta una fuente para este dispositivo y la quita de la lista al momento. */
    fun markSourceBroken(source: StreamSource) {
        viewModelScope.launch {
            settings.markSourceBroken(source.brokenKey())
            updateContent { c ->
                val ready = c.sources as? SourcesState.Ready
                c.copy(
                    brokenSources = c.brokenSources + source.brokenKey(),
                    sources = if (ready != null) {
                        ready.copy(sources = ready.sources.filterNot { it.brokenKey() == source.brokenKey() })
                    } else {
                        c.sources
                    },
                    message = texts.get(StringsR.string.details_source_marked_broken),
                )
            }
        }
    }

    fun toggleSpanishOnly() {
        val enabled = !(content()?.spanishOnly ?: return)
        updateContent { it.copy(spanishOnly = enabled) }
        viewModelScope.launch { settings.setSpanishOnlySources(enabled) }
    }

    private fun loadCollection(id: Int, currentId: String) {
        viewModelScope.launch {
            val collection = runCatching { catalogRepository.getCollection(id) }.getOrNull() ?: return@launch
            if (collection.parts.none { it.id != currentId }) return@launch
            updateContent { it.copy(collection = collection) }
        }
    }

    /** Un episodio al azar de toda la serie (de las temporadas ya emitidas) y a reproducir. */
    fun playRandomEpisode() {
        val c = content() ?: return
        if (!c.isSeries) return
        viewModelScope.launch {
            val seasons = c.details.seasons.filter { it.episodeCount > 0 }
            if (seasons.isEmpty()) return@launch
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            repeat(3) {
                val season = seasons.random()
                val episodes = runCatching { catalogRepository.getEpisodes(c.details.item, season.number) }
                    .getOrDefault(emptyList())
                    .filter { e -> e.airDate.isNullOrBlank() || e.airDate!! <= today }
                if (episodes.isNotEmpty()) {
                    val episode = episodes.random()
                    selectSeason(season.number, preferredEpisode = episode.number, playImmediately = true)
                    return@launch
                }
            }
        }
    }

    fun toggleMyList() {
        val item = content()?.details?.item ?: return
        if (myList.contains(item.type, item.id)) myList.remove(item.type, item.id) else myList.add(item.type, item.id)
    }

    fun toggleFollowing() {
        val item = content()?.details?.item ?: return
        if (item.type == MediaType.TV) tracking.setFollowing(item.id, !tracking.isFollowing(item.id))
    }

    fun loadRatings() {
        val c = content() ?: return
        val item = c.details.item
        val overallTarget = if (c.isSeries) {
            TraktRatingTarget.Show(item.id)
        } else {
            TraktRatingTarget.Movie(item.id)
        }
        val episodeTarget = c.selectedEpisode?.takeIf { c.isSeries }?.let {
            TraktRatingTarget.Episode(item.id, it.season, it.number)
        }
        ratingsJob?.cancel()
        updateContent { it.copy(ratings = TraktRatingsState.Loading) }
        ratingsJob = viewModelScope.launch {
            if (!trakt.isConnected) {
                updateContent { it.copy(ratings = TraktRatingsState.NotConnected) }
                return@launch
            }
            val result = runCatching {
                val overallRating = trakt.getRating(overallTarget)
                val episodeRating = episodeTarget?.let { trakt.getRating(it) }
                TraktRatingsState.Ready(
                    overallRating = overallRating,
                    episodeRating = episodeRating,
                )
            }
            updateContent {
                it.copy(
                    ratings = result.getOrElse {
                        TraktRatingsState.Error(texts.get(StringsR.string.details_ratings_load_error))
                    },
                )
            }
        }
    }

    fun setRating(target: TraktRatingTarget, rating: Int?) {
        val ready = (content()?.ratings as? TraktRatingsState.Ready) ?: return
        updateContent { it.copy(ratings = ready.copy(saving = true, error = null)) }
        ratingsJob = viewModelScope.launch {
            val result = runCatching { trakt.setRating(target, rating) }
            updateContent { current ->
                val state = current.ratings as? TraktRatingsState.Ready ?: return@updateContent current
                val updated = if (result.isSuccess) {
                    when (target) {
                        is TraktRatingTarget.Episode -> state.copy(episodeRating = rating)
                        else -> state.copy(overallRating = rating)
                    }
                } else state
                current.copy(
                    ratings = updated.copy(
                        saving = false,
                        error = if (result.isFailure) texts.get(StringsR.string.details_rating_save_error) else null,
                    ),
                )
            }
        }
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
            val sourceExclusions = settings.sourceExclusions.first()
            val brokenSources = settings.brokenSourceIds.first()
            val showCast = settings.showCast.first()
            if (request.playImmediately) {
                updateContent {
                    it.copy(
                        details = details,
                        progress = watchProgress.progress.value,
                        inMyList = myList.contains(details.item.type, details.item.id),
                        spanishFirst = spanishFirst,
                        spanishOnly = spanishOnly,
                        sourceExclusions = sourceExclusions,
                        brokenSources = brokenSources,
                        showCast = showCast,
                    )
                }
            } else {
                _uiState.value = DetailsUiState.Content(
                    details,
                    progress = watchProgress.progress.value,
                    inMyList = myList.contains(details.item.type, details.item.id),
                    spanishFirst = spanishFirst,
                    spanishOnly = spanishOnly,
                    sourceExclusions = sourceExclusions,
                    brokenSources = brokenSources,
                    showCast = showCast,
                )
            }
            detailsLoaded = true
            details.collection?.let { ref -> loadCollection(ref.id, details.item.id) }
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
        if (playDownloaded(c, episode = null, fromStart = fromStart)) return
        when (val s = c.sources) {
            is SourcesState.Ready -> if (c.eligibleSources(s.sources).isEmpty() && s.pending.isNotEmpty()) {
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
        if (playDownloaded(c.copy(selectedEpisode = episode), episode, fromStart)) {
            updateContent { it.copy(selectedEpisode = episode) }
            return
        }
        val sameEpisode = c.selectedEpisode == episode
        val ready = c.sources as? SourcesState.Ready
        if (sameEpisode && ready != null) {
            if (c.eligibleSources(ready.sources).isEmpty() && ready.pending.isNotEmpty()) {
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

    /**
     * Fuente elegida a mano: se reproduce esa (aunque esté excluida o no sea castellano: la has elegido tú) y, si
     * falla, se salta a la más parecida (mismo idioma, resolución, HDR, audio...) entre las que están en el debrid
     * y respetan tus filtros, no simplemente a la siguiente de la lista.
     */
    fun playSource(source: StreamSource) {
        val c = content() ?: return
        val ready = c.sources as? SourcesState.Ready ?: return
        val episode = c.selectedEpisode?.takeIf { c.isSeries }
        val key = downloadKey(c, episode) ?: return
        // Con el cambio automático desactivado (Ajustes), solo esa: si falla, se avisa y se vuelve aquí.
        val switchIfFails = switchFromPickedSource.value
        val queue = if (switchIfFails) {
            val rest = c.eligibleSources(ready.sources.filter { it.id != source.id })
                .filter { it.isCached || it.directUrl != null }
                .distinctBy { it.infoHash.lowercase().ifBlank { it.id } }
            (listOf(source) + com.shieldstream.tv.model.SourceSimilarity.orderBySimilarity(source, rest)).take(MAX_QUEUE)
        } else {
            listOf(source)
        }
        updateContent { it.copy(pendingPlay = false) }
        handoff(c, episode, key, fromStart = false, queue = queue, manualPick = !switchIfFails)
    }

    private val switchFromPickedSource: StateFlow<Boolean> = settings.switchFromPickedSource
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _reopenSources = MutableStateFlow(false)

    /** True cuando hay que volver a abrir la lista de fuentes (la elegida a mano falló); la pantalla lo consume. */
    val reopenSources: StateFlow<Boolean> = _reopenSources.asStateFlow()

    fun sourcesReopened() {
        _reopenSources.value = false
    }

    /** La fuente que se reproduce al pulsar Reproducir (la primera de la cola automática), para marcarla en la lista. */
    fun autoPlaySource(): StreamSource? {
        val c = content() ?: return null
        val ready = c.sources as? SourcesState.Ready ?: return null
        val key = downloadKey(c, c.selectedEpisode?.takeIf { c.isSeries }) ?: return null
        val savedId = watchProgress.get(key)?.sourceId
        val eligible = c.eligibleSources(ready.sources)
        return eligible.firstOrNull { it.id == savedId } ?: eligible.firstOrNull()
    }

    fun consumeMessage() = updateContent { it.copy(message = null) }

    fun onScreenResumed() {
        holder.consumePickedSourceFailure()?.let { reason ->
            updateContent { it.copy(message = texts.get(StringsR.string.details_picked_source_failed, reason)) }
            _reopenSources.value = true
        }
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
            val sourceExclusions = settings.sourceExclusions.first()
            val brokenSources = settings.brokenSourceIds.first()
            val preferredQuality = settings.preferredQuality.first()
            val optimization = settings.deviceOptimization.first()
            val capabilities = deviceCapabilities.capabilities
            val hardwareCodecs = when (optimization) {
                DeviceOptimization.AUTOMATIC -> capabilities.hardwareVideoCodecs
                DeviceOptimization.CUSTOM -> emptySet()
            }
            updateContent {
                it.copy(
                    spanishFirst = spanishFirst,
                    spanishOnly = spanishOnly,
                    sourceExclusions = sourceExclusions,
                    brokenSources = brokenSources,
                )
            }
            // Lo que el servicio activo ya tiene listo, para marcar ⚡ las fuentes de los buscadores propios. Los
            // buscadores van llegando poco a poco: se pregunta solo por los torrents nuevos de cada tanda.
            val checkedHashes = HashSet<String>()
            val debridCached = HashSet<String>()
            catalogRepository.getStreams(c.details.item, episode)
                .map { result ->
                    val unchecked = result.sources.filter { it.resolveUrl == null }.map { it.infoHash.lowercase() }
                        .filter { it !in checkedHashes }.distinct()
                    if (unchecked.isNotEmpty()) {
                        checkedHashes += unchecked
                        val ready = runCatching { debridCache.cachedAmong(unchecked) }
                            .onFailure { android.util.Log.w("Details", "Caché del debrid: ${it.message}") }
                            .getOrDefault(emptySet())
                        debridCached += ready
                        android.util.Log.i("Details", "Caché del debrid: ${ready.size} listos de ${unchecked.size} torrents nuevos")
                    }
                    val knownCached = debridCached + result.sources.filter { it.isCached }.map { it.infoHash.lowercase() }
                    val enriched = result.sources.map {
                        if (it.resolveUrl == null) it.copy(isCached = it.infoHash.lowercase() in knownCached) else it
                    }
                    result to prioritizeSourcesUseCase(
                        enriched,
                        capabilities.supportedHdr,
                        hardwareVideoCodecs = hardwareCodecs,
                        preferSpanish = spanishFirst || spanishOnly,
                        preferred = preferredQuality,
                    )
                }
                // Keep provider parsing, snapshot construction, enrichment and sorting off the UI thread.
                .flowOn(sourceProcessingDispatcher.dispatcher)
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
        val enoughCached = c.eligibleSources(ready.sources).count { it.isCached } >= MIN_CACHED_TO_START
        if (!enoughCached && ready.pending.isNotEmpty()) return
        updateContent { it.copy(pendingPlay = false) }
        startPlayback(ready.sources, startIndex = 0, fromStart = pendingFromStart)
    }

    private fun startPlayback(
        sorted: List<StreamSource>,
        startIndex: Int,
        fromStart: Boolean,
        useSavedSource: Boolean = true,
        includeExcluded: Boolean = false,
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
        val queue = c.eligibleSources(ordered.drop(startIndex), includeExcluded)
            .distinctBy { it.infoHash.lowercase() }
            .filterIndexed { index, s ->
                index == 0 || s.resolveUrl == null || s.isCached
            }
            .take(MAX_QUEUE)
        if (queue.isEmpty()) {
            updateContent {
                it.copy(
                    message = when {
                        c.spanishOnly -> texts.get(StringsR.string.details_no_spanish_sources_available_for_this_profile_7dd7b1)
                        !includeExcluded && sorted.isNotEmpty() && c.eligibleSources(sorted).isEmpty() ->
                            texts.get(StringsR.string.details_no_sources_match_exclusions)
                        else -> texts.get(StringsR.string.details_no_sources_available_for_this_right_now)
                    },
                    pendingPlay = false,
                )
            }
            return
        }
        handoff(c, episode, key, fromStart, queue)
    }

    /** Lo ya descargado se reproduce del disco, sin esperar a buscar fuentes (funciona sin conexión). */
    private fun playDownloaded(c: DetailsUiState.Content, episode: Episode?, fromStart: Boolean): Boolean {
        val key = downloadKey(c, episode) ?: return false
        val path = downloads.localFileFor(key) ?: return false
        val local = StreamSource(
            id = "LOCAL:$key",
            title = texts.get(StringsR.string.downloads_local_source),
            infoHash = "",
            fileIdx = null,
            magnet = "",
            quality = com.shieldstream.tv.model.Quality.UNKNOWN,
            hdrType = com.shieldstream.tv.model.HdrType.SDR,
            sizeBytes = java.io.File(path).length(),
            seeders = null,
            isCached = true,
            directUrl = android.net.Uri.fromFile(java.io.File(path)).toString(),
            provider = texts.get(StringsR.string.downloads_title),
        )
        handoff(c, episode, key, fromStart, listOf(local))
        return true
    }

    private fun downloadKey(c: DetailsUiState.Content, episode: Episode?): String? {
        val item = c.details.item
        return if (c.isSeries) {
            val ep = episode ?: c.selectedEpisode ?: return null
            WatchProgress.episodeKey(item.id, ep.season, ep.number)
        } else {
            WatchProgress.movieKey(item.id)
        }
    }

    /**
     * Descarga la película, o los episodios indicados (uno, los siguientes, una temporada...), con el tope de calidad
     * elegido. La preparación sigue aunque se salga de la ficha.
     */
    fun download(episodes: List<Episode> = emptyList(), quality: com.shieldstream.tv.model.DownloadQuality = com.shieldstream.tv.model.DownloadQuality.BEST) {
        val c = content() ?: return
        if (c.isSeries && episodes.isEmpty()) return
        message(StringsR.string.downloads_preparing)
        planner.plan(c.details.item, c.details.originalTitle, episodes, quality)
    }

    fun pauseDownload(id: String) = downloads.pause(id)
    fun resumeDownload(id: String) = downloads.resume(id)
    fun deleteDownload(id: String) {
        downloads.delete(id)
        message(StringsR.string.downloads_deleted)
    }

    private fun message(id: Int) = updateContent { it.copy(message = texts.get(id)) }

    private fun handoff(
        c: DetailsUiState.Content,
        episode: Episode?,
        key: String,
        fromStart: Boolean,
        queue: List<StreamSource>,
        manualPick: Boolean = false,
    ) {
        val item = c.details.item
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
            manualPick = manualPick,
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
