package com.shieldstream.tv.player

import com.shieldstream.tv.model.tr
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.shieldstream.tv.domain.ResolveState
import com.shieldstream.tv.domain.ResolveStreamUseCase
import com.shieldstream.tv.domain.SelectedContentHolder
import com.shieldstream.tv.domain.SkipSegments
import com.shieldstream.tv.domain.SkipSegmentsRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.SubtitleRepository
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.domain.ScrobbleAction
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.media.Media3Factory
import com.shieldstream.tv.media.PlaybackPolicyProvider
import com.shieldstream.tv.media.VideoFormatInspector
import androidx.media3.common.PlaybackException
import com.shieldstream.tv.model.ExternalSubtitle
import com.shieldstream.tv.model.PlaybackVideoInfo
import com.shieldstream.tv.model.SubtitleQuery
import com.shieldstream.tv.model.SubtitlePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.abs

sealed interface PlayerUiState {
    data class Resolving(val message: String, val attempt: Int, val total: Int) : PlayerUiState
    data object Playing : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

sealed interface SkipPromptUiState {
    data object Hidden : SkipPromptUiState
    data class Showing(val label: String, val targetMs: Long) : SkipPromptUiState
}

sealed interface NextUpUiState {
    data object Hidden : NextUpUiState
    data class Showing(val label: String, val secondsLeft: Int) : NextUpUiState
}

sealed interface ExternalSubsState {
    data object Idle : ExternalSubsState
    data class Searching(val language: String) : ExternalSubsState
    data class Results(
        val language: String,
        val items: List<ExternalSubtitle>,
        val evaluating: Boolean = false,
        val candidateCount: Int = 0,
        val referenceCues: Int = 0,
    ) : ExternalSubsState
    data class Downloading(val language: String, val item: ExternalSubtitle) : ExternalSubsState
    data class Attached(val label: String) : ExternalSubsState
    data class Error(val language: String, val message: String) : ExternalSubsState
}

@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val media3Factory: Media3Factory,
    private val playbackPolicies: PlaybackPolicyProvider,
    private val resolveStreamUseCase: ResolveStreamUseCase,
    private val settings: SettingsRepository,
    profiles: ProfileRepository,
    private val holder: SelectedContentHolder,
    private val watchProgress: WatchProgressRepository,
    private val subtitles: SubtitleRepository,
    private val trakt: TraktRepository,
    private val skipSegmentsRepository: SkipSegmentsRepository,
) : ViewModel() {

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    private val _videoInfo = MutableStateFlow<PlaybackVideoInfo?>(null)
    val videoInfo: StateFlow<PlaybackVideoInfo?> = _videoInfo.asStateFlow()

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Resolving(tr("Preparando…", "Preparing…"), 1, 1))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _externalSubs = MutableStateFlow<ExternalSubsState>(ExternalSubsState.Idle)
    val externalSubs: StateFlow<ExternalSubsState> = _externalSubs.asStateFlow()

    private val queueSnapshot = holder.queue.value
    private val mediaSnapshot = holder.media.value
    val title: StateFlow<String> = MutableStateFlow(holder.playLabel.value).asStateFlow()

    val seekThumbnails: StateFlow<Boolean> = combine(
        settings.deviceOptimization,
        settings.maxBufferSeconds,
        settings.seekThumbnails,
    ) { optimization, buffer, thumbnails ->
        playbackPolicies.resolve(optimization, buffer, thumbnails).seekThumbnails
    }.stateIn(viewModelScope, SharingStarted.Eagerly, playbackPolicies.automatic().seekThumbnails)

    private val progressKey: String? = holder.progressKey.value

    private var segments: SkipSegments = SkipSegments.NONE
    private var segmentsJob: Job? = null
    private var prefetched = false
    private var nextUpShownAt = -1L
    private val _skipPrompt = MutableStateFlow<SkipPromptUiState>(SkipPromptUiState.Hidden)
    val skipPrompt: StateFlow<SkipPromptUiState> = _skipPrompt.asStateFlow()
    private val nextSnapshot = holder.next.value
    private val _nextUp = MutableStateFlow<NextUpUiState>(NextUpUiState.Hidden)
    val nextUp: StateFlow<NextUpUiState> = _nextUp.asStateFlow()
    private val _goNext = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val goNext: SharedFlow<Unit> = _goNext.asSharedFlow()
    private var nextUpDismissed = false
    private var wentNext = false
    private val autoNext: StateFlow<Boolean> = settings.autoNextEpisode.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // --- Castellano de verdad ---------------------------------------------------------------------------
    private val spanishOnly = profiles.active.value.spanishOnlySources
    private val spanishFirst = profiles.active.value.spanishFirst || spanishOnly
    private val subtitlePreference = profiles.active.value.subtitlePreference
    private var subtitleDefaultHandled = false
    private var spanishChecked = false
    private var spanishSkips = 0
    private var spanishFallback: Int? = null

    private var started = false
    private var playbackVisible = true
    private var playerGeneration = 0L
    private var playerCreationJob: Job? = null
    private var trackSelectionToRestore: TrackSelectionParameters? = null
    private var resolvedUrl: String? = null
    private var playingFileName: String? = null
    private var resumePositionMs = 0L

    private var playbackRetries = 0

    private var queueIndex = 0
    private var everPlayed = false
    private var slateChecked = false
    private var lastQueueError = ""
    private var allNotCached = true
    private var saverJob: Job? = null
    private var subsJob: Job? = null

    private val _activeExternal = MutableStateFlow<ActiveExternalSubtitle?>(null)
    val activeExternal: StateFlow<ActiveExternalSubtitle?> = _activeExternal.asStateFlow()
    private var currentExternal: ExternalSubtitle? = null

    private val _subtitleTiming = MutableStateFlow(SubtitleTiming())
    val subtitleTiming: StateFlow<SubtitleTiming> = _subtitleTiming.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Off)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val reference = ReferenceCollector()
    private var referencePending = false
    private var referenceAwaitingStart = false
    private var referenceAvailable = false
    private var textSelectionBeforeReference: TrackSelectionParameters? = null
    private var syncJob: Job? = null

    private data class SubtitleCandidate(
        val item: ExternalSubtitle,
        val label: String,
        val segments: List<CuesWithTiming>,
        val intervals: List<SubInterval>,
        val run: MutableList<SyncResult> = mutableListOf(),
    )
    fun start() {
        if (started) return
        started = true

        val queue = queueSnapshot
        if (queue.isEmpty()) {
            _uiState.value = PlayerUiState.Error(tr("Sin fuente seleccionada", "No source selected"))
            return
        }
        loadSegments()
        playFrom(0)
    }

    private fun loadSegments() {
        val m = mediaSnapshot ?: return
        val imdb = m.imdbId ?: return
        val season = m.season ?: return
        val episode = m.episode ?: return
        viewModelScope.launch { segments = skipSegmentsRepository.episode(imdb, season, episode) }
    }

    private fun playFrom(from: Int) {
        val queue = queueSnapshot
        viewModelScope.launch {
            var attempts = 0
            for (index in from until queue.size) {
                if (attempts >= MAX_ATTEMPTS) break
                val source = queue[index]
                var outcome: ResolveState? = null
                resolveStreamUseCase(source).collect { state ->
                    when (state) {
                        is ResolveState.Progress -> {
                            val prefix = if (index > 0) tr("Fuente ${index + 1} de ${queue.size} · ", "Source ${index + 1} of ${queue.size} · ") else ""
                            _uiState.value = PlayerUiState.Resolving(prefix + state.message, index + 1, queue.size)
                        }
                        is ResolveState.Ready, is ResolveState.Failed -> outcome = state
                    }
                }
                when (val result = outcome) {
                    is ResolveState.Ready -> {
                        resolvedUrl = result.url
                        queueIndex = index
                        everPlayed = false
                        slateChecked = false
                        spanishChecked = false
                        subtitleDefaultHandled = false
                        subsJob?.cancel()
                        if ((_externalSubs.value as? ExternalSubsState.Results)?.evaluating == true) {
                            cancelCandidateEvaluation()
                        }
                        Log.i("PlayerVM", "Fuente ${index + 1}/${queue.size}: ${(source.fileName ?: source.title).take(120)}")
                        playingFileName = source.fileName ?: source.title
                        val resumeAt = savedResumePosition()
                        trackSelectionToRestore = null // Es otra fuente: las pistas de la anterior no aplican.
                        launchPlayer(result.url, startPositionMs = resumeAt)
                        if (resumeAt > 0) _notice.value = tr("Reanudando desde ${formatTime(resumeAt)}", "Resuming from ${formatTime(resumeAt)}")
                        _uiState.value = PlayerUiState.Playing
                        startProgressSaver()
                        startSegmentsWatcher()
                        return@launch
                    }
                    is ResolveState.Failed -> {
                        if (!result.blocked) attempts++
                        lastQueueError = result.message
                        if (!result.notCached) allNotCached = false
                    }
                    else -> Unit
                }
            }
            spanishFallback?.let { back ->
                spanishFallback = null
                spanishSkips = MAX_SPANISH_SKIPS
                _notice.value = tr("No hay otra fuente en castellano que funcione: vuelve la anterior", "No other working Spanish source: back to the previous one")
                playFrom(back)
                return@launch
            }
            _uiState.value = PlayerUiState.Error(
                if (allNotCached) {
                    tr("Ninguna de las fuentes probadas está en la caché de ", "None of the sources tried is cached on ") +
                        "${queue.firstNotNullOfOrNull { it.debrid } ?: "Real-Debrid"}.\n$lastQueueError"
                } else {
                    tr("No ha funcionado ninguna de las fuentes probadas.", "None of the sources tried worked.") + "\n$lastQueueError"
                },
            )
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    // -----------------------------------------------------------------------
    // -----------------------------------------------------------------------

    fun searchExternalSubtitles(language: String) = searchExternalSubtitles(language, automatic = false)

    private fun searchExternalSubtitles(language: String, automatic: Boolean) {
        if (!automatic) subtitleDefaultHandled = true
        val query = buildQuery(language) ?: run {
            _externalSubs.value = ExternalSubsState.Error(language, tr("No se sabe qué se está reproduciendo", "Unknown what's playing"))
            return
        }
        subsJob?.cancel()
        if ((_externalSubs.value as? ExternalSubsState.Results)?.evaluating == true) cancelCandidateEvaluation()
        _externalSubs.value = ExternalSubsState.Searching(language)
        subsJob = viewModelScope.launch {
            runCatching { subtitles.search(query) }
                .onSuccess { items ->
                    if (items.isEmpty()) {
                        _externalSubs.value = ExternalSubsState.Results(language, items)
                        if (automatic) {
                            _notice.value = tr("No se encontraron subtítulos externos en este idioma", "No external subtitles found in this language")
                        }
                    } else {
                        val count = candidateShortlist(items).size
                        _externalSubs.value = ExternalSubsState.Results(language, items, evaluating = true, candidateCount = count)
                        startCandidateEvaluation(language, items, query)
                    }
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    _externalSubs.value = ExternalSubsState.Error(language, it.message ?: tr("Error buscando subtítulos", "Error searching subtitles"))
                    if (automatic) _notice.value = tr("No se pudieron buscar subtítulos externos", "Couldn't search for external subtitles")
                }
        }
    }

    fun downloadExternalSubtitle(item: ExternalSubtitle) {
        subtitleDefaultHandled = true
        val query = buildQuery(item.language) ?: return
        val previous = _externalSubs.value
        cancelCandidateEvaluation()
        if (currentExternal != null) {
            Log.w(TAG, "AUTO_SYNC_FAILURE reason=replaced_by_user ${externalContext(currentExternal)}")
            disableExternalSubtitle()
        }
        subsJob?.cancel()
        Log.i(TAG, "SUBTITLE_DOWNLOAD_START ${externalContext(item)}")
        _externalSubs.value = ExternalSubsState.Downloading(item.language, item)
        subsJob = viewModelScope.launch {
            runCatching {
                val file = subtitles.download(item, query)
                val segments = withContext(Dispatchers.IO) { parseSubtitle(file.path, file.mimeType) }
                check(segments.isNotEmpty()) { tr("El subtítulo está vacío o no se puede leer", "The subtitle is empty or unreadable") }
                file to segments
            }
                .onSuccess { (file, segments) ->
                    currentExternal = item
                    attachExternal(file.label, segments)
                    Log.i(TAG, "SUBTITLE_DOWNLOAD_SUCCESS ${externalContext(item)}")
                    _externalSubs.value = ExternalSubsState.Attached(file.label)
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "SUBTITLE_DOWNLOAD_FAILURE ${externalContext(item)} error=${e.message}", e)
                    _externalSubs.value = ExternalSubsState.Error(item.language, e.message ?: tr("No se pudo descargar", "Couldn't download"))
                    if (previous is ExternalSubsState.Results) {
                        delay(2_500)
                        _externalSubs.value = previous.copy(evaluating = false)
                    }
                }
        }
    }

    fun resetExternalSubtitles() {
        subsJob?.cancel()
        if ((_externalSubs.value as? ExternalSubsState.Results)?.evaluating == true) cancelCandidateEvaluation()
        _externalSubs.value = ExternalSubsState.Idle
    }

    private fun startCandidateEvaluation(language: String, items: List<ExternalSubtitle>, query: SubtitleQuery) {
        syncJob?.cancel()
        if (currentExternal != null) {
            Log.w(TAG, "AUTO_SYNC_FAILURE reason=replaced_by_user ${externalContext(currentExternal)}")
            disableExternalSubtitle()
        }
        beginReferenceCapture()
        syncJob = viewModelScope.launch {
            val shortlist = candidateShortlist(items)
            Log.i(TAG, "AUTO_SELECTION_START candidates=${shortlist.size} ${externalContext(null)}")
            if (!referenceAvailable && !referencePending) {
                failCandidateEvaluation(language, items, "no_reference")
                return@launch
            }
            val downloaded = supervisorScope {
                shortlist.map { item ->
                    async {
                        try {
                            Log.i(TAG, "SUBTITLE_DOWNLOAD_START ${externalContext(item)}")
                            downloadCandidate(item, query)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "SUBTITLE_DOWNLOAD_FAILURE ${externalContext(item)} error=${e.message}")
                            null
                        }
                    }
                }.awaitAll().filterNotNull().distinctBy { it.intervals }
            }
            if (!isActive) return@launch
            if (downloaded.isEmpty()) {
                failCandidateEvaluation(language, items, "no_downloads")
                return@launch
            }
            Log.i(TAG, "AUTO_SELECTION_DOWNLOADED unique=${downloaded.size} of=${shortlist.size}")

            var nextAt = SubtitleSync.MIN_REFERENCE_CUES
            while (isActive) {
                delay(3_000)
                val count = reference.cueCount
                _externalSubs.value = ExternalSubsState.Results(
                    language, items, evaluating = true, candidateCount = downloaded.size, referenceCues = count,
                )
                if (!referenceAvailable && !referencePending) {
                    failCandidateEvaluation(language, items, "no_reference")
                    return@launch
                }
                if (count < nextAt) continue
                val position = _player.value?.currentPosition ?: continue
                val (refs, windows) = reference.snapshot(position)
                nextAt = count + 5

                val accepted = ArrayList<Pair<SubtitleCandidate, SyncResult>>()
                for (candidate in downloaded) {
                    val result = withContext(Dispatchers.Default) {
                        SubtitleSync.align(refs, windows, candidate.intervals)
                    } ?: continue
                    Log.i(
                        TAG,
                        "CANDIDATE_SCORE offsetMs=${result.timing.offsetMs} scale=${result.timing.scale} " +
                            "correlation=${result.correlation} ratio=${result.ratio} refs=${result.referenceCues} " +
                            externalContext(candidate.item),
                    )
                    if (result.strong || result.plausible || result.consistent) {
                        if (candidate.run.isNotEmpty() && !result.agreesWith(candidate.run.last())) candidate.run.clear()
                        candidate.run += result
                    } else {
                        candidate.run.clear()
                    }
                    val confirmed = result.strong || (result.plausible && candidate.run.size >= 2) || candidate.run.size >= 3
                    if (confirmed) accepted += candidate to result
                }

                val winner = accepted.maxWithOrNull(
                    compareByDescending<Pair<SubtitleCandidate, SyncResult>> { it.second.correlation }
                        .thenByDescending { it.second.ratio }
                        .thenByDescending { it.first.item.matchScore }
                        .thenBy { it.first.item.hearingImpaired }
                        .thenBy { it.first.item.provider }
                        .thenBy { it.first.item.id },
                )
                if (winner != null) {
                    selectCandidate(winner.first, winner.second)
                    return@launch
                }
                if (count >= MAX_AUTO_REFERENCE_CUES) {
                    failCandidateEvaluation(language, items, "no_reliable_match")
                    return@launch
                }
            }
        }
    }

    private fun candidateShortlist(items: List<ExternalSubtitle>): List<ExternalSubtitle> {
        val sorted = items.sortedWith(
            compareByDescending<ExternalSubtitle> { it.matchScore }.thenBy { it.hearingImpaired },
        )
        val providers = sorted.groupBy { it.provider }.values.toList()
        val diverse = ArrayList<ExternalSubtitle>()
        var index = 0
        while (diverse.size < AUTO_CANDIDATE_LIMIT && providers.any { index < it.size }) {
            for (provider in providers) {
                if (index < provider.size && diverse.size < AUTO_CANDIDATE_LIMIT) diverse += provider[index]
            }
            index++
        }
        return diverse
    }

    private suspend fun downloadCandidate(item: ExternalSubtitle, query: SubtitleQuery): SubtitleCandidate {
        val file = subtitles.download(item, query)
        val segments = withContext(Dispatchers.IO) { parseSubtitle(file.path, file.mimeType) }
        check(segments.isNotEmpty()) { tr("El subtítulo está vacío o no se puede leer", "The subtitle is empty or unreadable") }
        return SubtitleCandidate(item, file.label, segments, SubtitleSync.intervalsOf(segments))
    }

    private fun selectCandidate(candidate: SubtitleCandidate, result: SyncResult) {
        currentExternal = candidate.item
        _activeExternal.value = ActiveExternalSubtitle(candidate.label, candidate.segments, candidate.intervals)
        Log.i(
            TAG,
            "AUTO_SELECTION_SUCCESS correlation=${result.correlation} ratio=${result.ratio} " +
                externalContext(candidate.item),
        )
        applyAuto(result.timing, announce = true, force = true)
        _externalSubs.value = ExternalSubsState.Attached(candidate.label)
    }

    private fun failCandidateEvaluation(language: String, items: List<ExternalSubtitle>, reason: String) {
        Log.w(TAG, "AUTO_SELECTION_FAILURE reason=$reason")
        _externalSubs.value = ExternalSubsState.Results(language, items)
        _syncStatus.value = SyncStatus.Off
        restoreReferenceSelection()
        _notice.value = if (reason == "no_reference") {
            tr("Sin subtítulos de referencia: elige uno y ajústalo a mano", "No reference subtitles: choose one and adjust it manually")
        } else {
            tr("Ningún subtítulo encaja automáticamente: elige uno a mano", "No subtitle matched automatically: choose one manually")
        }
    }

    private fun cancelCandidateEvaluation() {
        syncJob?.cancel()
        syncJob = null
        if (_activeExternal.value == null) restoreReferenceSelection()
        val current = _externalSubs.value
        if (current is ExternalSubsState.Results && current.evaluating) {
            _externalSubs.value = current.copy(evaluating = false)
        }
    }

    private fun buildQuery(language: String): SubtitleQuery? {
        val media = mediaSnapshot ?: return null
        return SubtitleQuery(media = media, language = language, fileName = playingFileName)
    }

    private fun externalContext(item: ExternalSubtitle?): String = item?.let {
        "provider=${it.provider} id=${it.id} release=${it.release.take(100).ifBlank { "unknown" }}"
    } ?: "subtitle=none"

    private fun buildMediaItem(url: String): MediaItem = MediaItem.Builder().setUri(url).build()

    private fun parseSubtitle(path: String, mimeType: String): List<CuesWithTiming> {
        val format = Format.Builder().setSampleMimeType(mimeType).build()
        val factory = DefaultSubtitleParserFactory()
        if (!factory.supportsFormat(format)) return emptyList()
        val parser = factory.create(format)
        val out = ArrayList<CuesWithTiming>()
        parser.parse(File(path).readBytes(), SubtitleParser.OutputOptions.allCues()) { out += it }
        parser.reset()
        return out.filter { it.startTimeUs != C.TIME_UNSET }.sortedBy { it.startTimeUs }
    }

    private fun beginReferenceCapture() {
        if (textSelectionBeforeReference == null) {
            textSelectionBeforeReference = _player.value?.trackSelectionParameters
        }
        reference.reset()
        referencePending = true
        referenceAwaitingStart = false
        referenceAvailable = false
        _player.value?.let { selectReferenceTrack(it, it.currentTracks) }
    }

    private fun restoreReferenceSelection() {
        referencePending = false
        referenceAwaitingStart = false
        referenceAvailable = false
        textSelectionBeforeReference?.let { saved ->
            _player.value?.trackSelectionParameters = saved
        }
        textSelectionBeforeReference = null
        reference.reset()
    }

    private fun attachExternal(label: String, segments: List<CuesWithTiming>) {
        syncJob?.cancel()
        _activeExternal.value = ActiveExternalSubtitle(label, segments, SubtitleSync.intervalsOf(segments))
        _subtitleTiming.value = SubtitleTiming()
        _syncStatus.value = SyncStatus.Collecting(0)
        beginReferenceCapture()
        if (referenceAvailable) {
            _notice.value = tr("Subtítulos añadidos: $label · sincronizando…", "Subtitles added: $label · syncing…")
            startAutoSync()
        }
    }

    fun onManualSubtitleSelection() {
        subtitleDefaultHandled = true
    }

    fun disableExternalSubtitle() {
        syncJob?.cancel()
        restoreReferenceSelection()
        _activeExternal.value = null
        currentExternal = null
        _subtitleTiming.value = SubtitleTiming()
        _syncStatus.value = SyncStatus.Off
    }

    fun adjustSubtitleOffset(deltaMs: Long) {
        if (_activeExternal.value == null) return
        syncJob?.cancel()
        val timing = _subtitleTiming.value.let { it.copy(offsetMs = it.offsetMs + deltaMs) }
        Log.w(
            TAG,
            "AUTO_SYNC_FAILURE reason=manual_adjustment newOffsetMs=${timing.offsetMs} " +
                "scale=${timing.scale} ${externalContext(currentExternal)}",
        )
        _subtitleTiming.value = timing
        _syncStatus.value = SyncStatus.Manual(timing)
    }

    fun autoSyncNow() {
        val ext = _activeExternal.value ?: return
        if (!referenceAvailable) {
            Log.w(TAG, "AUTO_SYNC_FAILURE reason=no_reference ${externalContext(currentExternal)}")
            _syncStatus.value = SyncStatus.NoReference
            _notice.value = tr("Este archivo no trae subtítulos de referencia: solo se puede ajustar a mano", "This file has no reference subtitles: it can only be adjusted manually")
            return
        }
        syncJob?.cancel()
        viewModelScope.launch {
            val position = _player.value?.currentPosition ?: return@launch
            val (refs, windows) = reference.snapshot(position)
            val result = withContext(Dispatchers.Default) { SubtitleSync.align(refs, windows, ext.intervals) }
            logResult(result)
            if (result != null && (result.strong || result.plausible)) {
                applyAuto(result.timing, announce = true, force = true)
            } else {
                _syncStatus.value = SyncStatus.Collecting(reference.cueCount)
                _notice.value = tr("Aún no hay datos suficientes: deja correr la película unos segundos", "Not enough data yet: let the movie play for a few seconds")
                startAutoSync()
            }
        }
    }

    private fun selectReferenceTrack(player: Player, tracks: Tracks) {
        if (!referencePending || tracks.isEmpty) return
        referencePending = false
        val candidate = tracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g to it } }
            .filterNot { (g, i) -> isForced(g.getTrackFormat(i)) }
            .maxByOrNull { (g, i) -> referenceScore(g.getTrackFormat(i)) }
        if (candidate == null) {
            Log.w(TAG, "AUTO_SYNC_FAILURE reason=no_reference ${externalContext(currentExternal)}")
            referenceAvailable = false
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            if (_activeExternal.value != null) {
                if (_syncStatus.value !is SyncStatus.Manual) _syncStatus.value = SyncStatus.NoReference
                _notice.value = tr("Subtítulos añadidos. Si no van a tiempo: Audio y subtítulos › Sincronizar", "Subtitles added. If they're out of sync: Audio & subtitles › Sync")
            }
            return
        }
        referenceAvailable = true
        referenceAwaitingStart = true
        val (group, index) = candidate
        Log.i(TAG, "Referencia: ${group.getTrackFormat(index).language} ${group.getTrackFormat(index).label}")
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
            .build()
    }

    private fun isForced(f: Format): Boolean {
        val label = f.label.orEmpty().lowercase()
        return f.selectionFlags and C.SELECTION_FLAG_FORCED != 0 ||
            "forced" in label || "forzad" in label || "forcé" in label
    }

    private fun referenceScore(f: Format): Int {
        val label = f.label.orEmpty().lowercase()
        val sdh = "sdh" in label || "sordos" in label || "udenti" in label || "hearing" in label || "cc" == label
        var score = if (sdh) 0 else 50
        score += when (f.language?.take(2)) {
            "en" -> 3
            "es" -> 2
            else -> 0
        }
        if (f.sampleMimeType != androidx.media3.common.MimeTypes.APPLICATION_PGS) score += 1
        return score
    }

    private fun applySubtitlePreference(player: Player, tracks: Tracks) {
        if (subtitleDefaultHandled || tracks.isEmpty) return
        if (_activeExternal.value != null) {
            subtitleDefaultHandled = true
            return
        }
        if (subtitlePreference == SubtitlePreference.OFF) {
            subtitleDefaultHandled = true
            return
        }
        val candidate = tracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .flatMap { group ->
                (0 until group.length)
                    .filter { group.isTrackSupported(it) }
                    .map { group to it }
            }
            .filter { (group, index) -> subtitlePreference.matches(group.getTrackFormat(index).language) }
            .minByOrNull { (group, index) ->
                val format = group.getTrackFormat(index)
                (if (isForced(format)) 2 else 0) +
                    (if (format.sampleMimeType == androidx.media3.common.MimeTypes.APPLICATION_PGS) 1 else 0)
            }

        subtitleDefaultHandled = true
        if (candidate != null) {
            val (group, index) = candidate
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                .build()
            _notice.value = when (subtitlePreference) {
                SubtitlePreference.SPANISH -> tr("Subtítulos integrados en español", "Embedded Spanish subtitles")
                SubtitlePreference.ENGLISH -> tr("Subtítulos integrados en inglés", "Embedded English subtitles")
                SubtitlePreference.OFF -> return
            }
            return
        }

        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        _notice.value = when (subtitlePreference) {
            SubtitlePreference.SPANISH -> tr("Sin subtítulos integrados en español: buscando externos…", "No embedded Spanish subtitles: finding external subtitles…")
            SubtitlePreference.ENGLISH -> tr("Sin subtítulos integrados en inglés: buscando externos…", "No embedded English subtitles: finding external subtitles…")
            SubtitlePreference.OFF -> return
        }
        searchExternalSubtitles(subtitlePreference.code ?: return, automatic = true)
    }

    private fun onReferenceTracks(player: Player, tracks: Tracks) {
        applySubtitlePreference(player, tracks)
        selectReferenceTrack(player, tracks)
        if (referenceAwaitingStart && tracks.isTypeSelected(C.TRACK_TYPE_TEXT)) {
            referenceAwaitingStart = false
            reference.begin(player.currentPosition)
        }
    }

    private fun startAutoSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            var nextAt = SubtitleSync.MIN_REFERENCE_CUES
            val run = ArrayList<SyncResult>()
            var applied: SyncResult? = null
            while (isActive) {
                delay(3_000)
                val ext = _activeExternal.value ?: return@launch
                val count = reference.cueCount
                if (_syncStatus.value is SyncStatus.Collecting) _syncStatus.value = SyncStatus.Collecting(count)
                if (count < nextAt) continue
                val position = _player.value?.currentPosition ?: continue
                val (refs, windows) = reference.snapshot(position)
                val result = withContext(Dispatchers.Default) { SubtitleSync.align(refs, windows, ext.intervals) }
                logResult(result)
                if (result == null) continue
                nextAt = count + 5
                if (result.strong || result.plausible || result.consistent) {
                    if (run.isNotEmpty() && !result.agreesWith(run.last())) run.clear()
                    run += result
                } else {
                    run.clear()
                }
                val accepted = result.strong || (result.plausible && run.size >= 2) || run.size >= 3
                if (accepted) {
                    val first = applied
                    applyAuto(result.timing, announce = first == null, force = false)
                    if (first != null && result.agreesWith(first)) return@launch // confirmado: fin
                    applied = result
                } else if (count >= 80 && applied == null) {
                    Log.w(TAG, "AUTO_SYNC_FAILURE reason=no_reliable_offset refs=$count ${externalContext(currentExternal)}")
                    _syncStatus.value = SyncStatus.Unsure
                    _notice.value = tr("No he podido sincronizar solo los subtítulos: Audio y subtítulos › Sincronizar", "Couldn't sync the subtitles automatically: Audio & subtitles › Sync")
                    return@launch
                }
            }
        }
    }

    private fun applyAuto(timing: SubtitleTiming, announce: Boolean, force: Boolean) {
        if (!force && _syncStatus.value is SyncStatus.Manual) return
        if (announce) {
            Log.i(
                TAG,
                "AUTO_SYNC_SUCCESS offsetMs=${timing.offsetMs} scale=${timing.scale} ${externalContext(currentExternal)}",
            )
        }
        _subtitleTiming.value = timing
        _syncStatus.value = SyncStatus.Auto(timing)
        if (announce) {
            _notice.value = if (abs(timing.offsetMs) < 200 && timing.scale == 1.0) {
                tr("Subtítulos comprobados: ya iban sincronizados", "Subtitles checked: already in sync")
            } else {
                tr("Subtítulos sincronizados automáticamente: ${timing.describe()}", "Subtitles synced automatically: ${timing.describe()}")
            }
        }
    }

    private fun logResult(result: SyncResult?) {
        if (result == null) {
            Log.i(TAG, "Sync: datos insuficientes (${reference.cueCount} frases)")
        } else {
            Log.i(
                TAG,
                "Sync: offset=${result.timing.offsetMs} scale=${result.timing.scale} r=${result.correlation} " +
                    "ratio=${result.ratio} refs=${result.referenceCues} fuerte=${result.strong} plausible=${result.plausible}",
            )
        }
    }

    // -----------------------------------------------------------------------
    // Progreso y ciclo de vida
    // -----------------------------------------------------------------------

    private fun savedResumePosition(): Long {
        if (holder.startFromBeginning.value) return 0L
        val key = progressKey ?: return 0L
        return watchProgress.get(key)?.takeIf { it.isResumable }?.positionMs ?: 0L
    }

    private fun startSegmentsWatcher() {
        if (segmentsJob?.isActive == true) return
        segmentsJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                val p = _player.value ?: continue
                val duration = p.duration
                val position = p.currentPosition
                if (duration <= 0) continue
                updateSkipPrompt(position)
                updateNextUp(position, duration)
            }
        }
    }

    private fun updateSkipPrompt(position: Long) {
        val recap = segments.recap
        val intro = segments.intro
        val prompt = when {
            recap != null && position in recap && recap.endMs - position > 2_000 ->
                SkipPromptUiState.Showing(tr("Omitir resumen", "Skip recap"), recap.endMs)
            intro != null && position in intro && intro.endMs - position > 2_000 ->
                SkipPromptUiState.Showing(tr("Omitir intro", "Skip intro"), intro.endMs)
            else -> SkipPromptUiState.Hidden
        }
        if (prompt != _skipPrompt.value) _skipPrompt.value = prompt
    }

    private fun updateNextUp(position: Long, duration: Long) {
        val next = nextSnapshot
        if (next == null || !autoNext.value || duration <= MIN_EPISODE_MS || wentNext) {
            _nextUp.value = NextUpUiState.Hidden
            return
        }
        val s = segments
        val outro = s.outro?.takeIf { it.startMs < duration }
        val post = s.postCredits?.takeIf { it.startMs < duration }
        val outroEnd = outro?.endMs?.coerceAtMost(duration)
        val (cardFrom, autoAt) = when {
            post != null -> maxOf(post.endMs.coerceAtMost(duration), outro?.startMs ?: 0L) to duration
            outro != null && duration - outroEnd!! <= AFTER_CREDITS_SLACK_MS ->
                outro.startMs to minOf(outro.startMs + NEXT_UP_COUNTDOWN_MS, duration)
            outro != null -> outro.startMs to duration
            else -> duration - maxOf(duration / 100, NEXT_UP_FALLBACK_MIN_MS) to duration
        }
        if (position < cardFrom) {
            nextUpDismissed = false
            nextUpShownAt = -1L
            _nextUp.value = NextUpUiState.Hidden
            return
        }
        if (nextUpDismissed) {
            _nextUp.value = NextUpUiState.Hidden
            return
        }
        if (nextUpShownAt < 0) nextUpShownAt = position
        val goAt = if (autoAt < duration) minOf(maxOf(autoAt, nextUpShownAt + NEXT_UP_COUNTDOWN_MS), duration) else duration
        if (goAt < duration && position >= goAt) {
            playNextNow()
            return
        }
        val secondsLeft = ((goAt - position + 999) / 1000).toInt().coerceAtLeast(0)
        _nextUp.value = NextUpUiState.Showing(next.label, secondsLeft)
        if (!prefetched) {
            prefetched = true
            holder.prefetchNext(next)
        }
    }

    fun skipSegment() {
        val prompt = _skipPrompt.value as? SkipPromptUiState.Showing ?: return
        _player.value?.seekTo(prompt.targetMs)
        _skipPrompt.value = SkipPromptUiState.Hidden
    }

    private fun checkSpanishAudio(player: ExoPlayer, tracks: Tracks) {
        if (spanishChecked || !spanishFirst) return
        val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g.getTrackFormat(it).language?.lowercase() } }
        if (audio.isEmpty() && player.playbackState != Player.STATE_READY) return
        spanishChecked = true
        val source = queueSnapshot.getOrNull(queueIndex) ?: return
        if (!spanishOnly && source.spanishAudioScore == 0) return
        val known = audio.filterNotNull().filter { it != "und" && it != "mul" }
        if (known.any { it == "es" || it.startsWith("es-") || it == "spa" } || (!spanishOnly && known.isEmpty())) {
            if (spanishOnly) player.play()
            return
        }
        Log.i("PlayerVM", "Sin audio en castellano (${known.distinct()}): ${source.title.take(80)}")
        val next = (queueIndex + 1 until queueSnapshot.size).firstOrNull { queueSnapshot[it].spanishAudioScore > 0 }
        if (next == null || spanishSkips >= MAX_SPANISH_SKIPS) {
            if (spanishOnly) {
                player.stop()
                resolvedUrl = null
                _uiState.value = PlayerUiState.Error(
                    tr("No hay fuentes con audio en español verificado para este perfil", "No sources with verified Spanish audio available for this profile"),
                )
            } else {
                _notice.value = tr("Esta fuente no trae audio en castellano", "This source has no Spanish audio")
            }
            return
        }
        spanishSkips++
        if (!spanishOnly && spanishFallback == null) spanishFallback = queueIndex
        _notice.value = tr("Sin audio en castellano: probando otra fuente", "No Spanish audio: trying another source")
        player.pause()
        viewModelScope.launch {
            saveProgress()
            resumePositionMs = player.currentPosition
            player.release()
            if (_player.value === player) _player.value = null
            resolvedUrl = null
            playFrom(next)
        }
    }

    private fun checkErrorSlate(player: ExoPlayer): Boolean {
        if (slateChecked) return false
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) return false
        slateChecked = true
        val source = queueSnapshot.getOrNull(queueIndex) ?: return false
        if (source.resolveUrl == null || duration >= SLATE_MAX_MS || source.sizeBytes < SLATE_MIN_BYTES) return false
        if (queueIndex + 1 >= queueSnapshot.size) return false
        Log.i("PlayerVM", "Vídeo de error del addon (${duration / 1000} s, dice ${source.sizeBytes / 1_000_000} MB): ${source.title.take(80)}")
        lastQueueError = tr("El addon no encontró el archivo de esta fuente.", "The addon couldn't find this source's file.")
        allNotCached = false
        _notice.value = tr("Esa fuente no tiene el episodio: probando otra", "That source doesn't have the episode: trying another")
        viewModelScope.launch {
            player.release()
            if (_player.value === player) _player.value = null
            resolvedUrl = null
            playFrom(queueIndex + 1)
        }
        return true
    }

    fun playNextNow() {
        val next = nextSnapshot ?: return
        if (wentNext) return
        wentNext = true
        progressKey?.let { key ->
            val duration = _player.value?.duration ?: 0L
            if (duration > 0) watchProgress.save(key, duration, duration)
        }
        scrobble(ScrobbleAction.STOP, fraction = 1f)
        holder.requestNext(next)
        _nextUp.value = NextUpUiState.Hidden
        _goNext.tryEmit(Unit)
    }

    fun dismissNextUp() {
        nextUpDismissed = true
        _nextUp.value = NextUpUiState.Hidden
    }

    private fun startProgressSaver() {
        saverJob?.cancel()
        saverJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                saveProgress()
            }
        }
    }

    private var lastScrobble: ScrobbleAction? = null

    private fun scrobble(action: ScrobbleAction, fraction: Float? = null) {
        val key = progressKey ?: return
        if (action == lastScrobble) return
        val value = fraction ?: _player.value?.let { p ->
            if (p.duration > 0) p.currentPosition.toFloat() / p.duration else null
        } ?: watchProgress.get(key)?.fraction ?: return
        lastScrobble = action
        trakt.scrobble(key, value, action)
    }

    private fun saveProgress() {
        val key = progressKey ?: return
        val p = _player.value ?: return
        val duration = p.duration
        val position = p.currentPosition
        if (duration > 0 && position > 0) watchProgress.save(key, position, duration)
    }

    fun onStop() {
        playbackVisible = false
        playerGeneration++
        playerCreationJob?.cancel()
        playerCreationJob = null
        saveProgress()
        scrobble(ScrobbleAction.PAUSE)
        _player.value?.let { p ->
            resumePositionMs = p.currentPosition
            trackSelectionToRestore = p.trackSelectionParameters
            reference.pause(p.currentPosition)
            p.release()
        }
        _player.value = null
        _videoInfo.value = null
    }

    fun onStart() {
        playbackVisible = true
        if (_player.value != null || playerCreationJob?.isActive == true) return
        val url = resolvedUrl ?: return
        launchPlayer(url, resumePositionMs)
    }

    private fun launchPlayer(url: String, startPositionMs: Long) {
        if (!playbackVisible) return
        playerCreationJob?.cancel()
        val generation = ++playerGeneration
        playerCreationJob = viewModelScope.launch { createAndPlay(url, startPositionMs, generation) }
    }

    private suspend fun createAndPlay(url: String, startPositionMs: Long, generation: Long) {
        val policy = playbackPolicies.resolve(
            settings.deviceOptimization.first(),
            settings.maxBufferSeconds.first(),
            settings.seekThumbnails.first(),
        )
        if (!playbackVisible || generation != playerGeneration) return
        val player = media3Factory.createPlayer(policy)
        if (!playbackVisible || generation != playerGeneration) {
            player.release()
            return
        }
        _videoInfo.value = null
        var activeDecoderName: String? = null
        fun updateVideoInfo(format: Format? = player.videoFormat) {
            if (format != null) _videoInfo.value = VideoFormatInspector.inspect(format, activeDecoderName)
        }
        player.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onVideoDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long,
                ) {
                    activeDecoderName = decoderName
                    updateVideoInfo()
                }

                override fun onVideoInputFormatChanged(
                    eventTime: AnalyticsListener.EventTime,
                    format: Format,
                    decoderReuseEvaluation: DecoderReuseEvaluation?,
                ) {
                    _videoInfo.value = VideoFormatInspector.inspect(format, activeDecoderName)
                }
            },
        )
        if (spanishFirst) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguages("es", "spa")
                .build()
        }
        // Home/ON_STOP recrea el mismo recurso: conserva selección automática y manual de pistas.
        trackSelectionToRestore?.let { player.trackSelectionParameters = it }
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        updateVideoInfo()
                        applySubtitlePreference(player, player.currentTracks)
                        checkSpanishAudio(player, player.currentTracks)
                        if (checkErrorSlate(player)) return
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        val key = progressKey ?: return
                        val duration = player.duration
                        if (duration > 0) watchProgress.save(key, duration, duration)
                        scrobble(ScrobbleAction.STOP, fraction = 1f)
                        if (!nextUpDismissed && autoNext.value) playNextNow()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w("PlayerVM", "Error de reproducción (${error.errorCodeName})", error)
                    saveProgress()
                    val sourceError = error.errorCode in
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
                    if (!everPlayed && sourceError && queueIndex + 1 < queueSnapshot.size) {
                        lastQueueError = tr("La fuente no se pudo abrir (${error.errorCodeName}).", "The source couldn't be opened (${error.errorCodeName}).")
                        allNotCached = false
                        viewModelScope.launch {
                            player.release()
                            if (_player.value === player) _player.value = null
                            resolvedUrl = null
                            playFrom(queueIndex + 1)
                        }
                        return
                    }
                    if (playbackRetries < 1) {
                        playbackRetries++
                        player.prepare()
                        player.playWhenReady = !spanishOnly || spanishChecked
                    } else {
                        _uiState.value = PlayerUiState.Error(
                            tr("La reproducción se ha cortado (${error.errorCodeName}).", "Playback stopped (${error.errorCodeName})."),
                        )
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        playbackRetries = 0
                        everPlayed = true
                    }
                    if (!isPlaying) saveProgress()
                    if (isPlaying) scrobble(ScrobbleAction.START)
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!playWhenReady) scrobble(ScrobbleAction.PAUSE)
                }

                override fun onTracksChanged(tracks: Tracks) {
                    onReferenceTracks(player, tracks)
                    checkSpanishAudio(player, tracks)
                }

                override fun onCues(cueGroup: CueGroup) {
                    reference.onCues(cueGroup.presentationTimeUs / 1000, cueGroup.cues.isNotEmpty())
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                        reference.jump(oldPosition.positionMs, newPosition.positionMs)
                    }
                }
            },
        )
        val evaluating = (_externalSubs.value as? ExternalSubsState.Results)?.evaluating == true
        if ((_activeExternal.value != null || evaluating) && referenceAvailable) referencePending = true
        player.setMediaItem(buildMediaItem(url), startPositionMs)
        spanishChecked = false
        _player.value = player
        player.playWhenReady = !spanishOnly
        player.prepare()
    }

    override fun onCleared() {
        playbackVisible = false
        playerGeneration++
        playerCreationJob?.cancel()
        saveProgress()
        scrobble(ScrobbleAction.STOP)
        _player.value?.release()
        _player.value = null
        _videoInfo.value = null
        super.onCleared()
    }

    private companion object {
        const val TAG = "SubSync"
        const val AUTO_CANDIDATE_LIMIT = 6
        const val MAX_AUTO_REFERENCE_CUES = 80
        const val MAX_ATTEMPTS = 8
        const val NEXT_UP_COUNTDOWN_MS = 10_000L
        const val NEXT_UP_FALLBACK_MIN_MS = 20_000L
        const val AFTER_CREDITS_SLACK_MS = 30_000L
        const val MAX_SPANISH_SKIPS = 3
        const val SLATE_MAX_MS = 4 * 60_000L
        const val SLATE_MIN_BYTES = 150_000_000L
        const val MIN_EPISODE_MS = 5 * 60_000L
    }

    private fun formatTime(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
