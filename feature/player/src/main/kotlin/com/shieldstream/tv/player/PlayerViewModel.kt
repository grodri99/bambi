package com.shieldstream.tv.player

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
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
import com.shieldstream.tv.model.SubtitlePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
import javax.inject.Inject

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
    private val texts: Texts,
) : ViewModel() {

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    private val _videoInfo = MutableStateFlow<PlaybackVideoInfo?>(null)
    val videoInfo: StateFlow<PlaybackVideoInfo?> = _videoInfo.asStateFlow()

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Resolving(texts.get(StringsR.string.player_preparing), 1, 1))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

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

    private val subtitleCoordinator = SubtitleCoordinator(
        scope = viewModelScope,
        subtitles = subtitles,
        subtitlePreference = subtitlePreference,
        texts = texts,
        media = { mediaSnapshot },
        playingFileName = { playingFileName },
        player = { _player.value },
        notify = { _notice.value = it },
    )
    val externalSubs: StateFlow<ExternalSubsState> = subtitleCoordinator.externalSubs
    val activeExternal: StateFlow<ActiveExternalSubtitle?> = subtitleCoordinator.activeExternal
    val subtitleTiming: StateFlow<SubtitleTiming> = subtitleCoordinator.subtitleTiming
    val syncStatus: StateFlow<SyncStatus> = subtitleCoordinator.syncStatus

    fun start() {
        if (started) return
        started = true

        val queue = queueSnapshot
        if (queue.isEmpty()) {
            _uiState.value = PlayerUiState.Error(texts.get(StringsR.string.player_no_source_selected))
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
                            val prefix = if (index > 0) texts.get(StringsR.string.player_queue_source, index + 1, queue.size) else ""
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
                        subtitleCoordinator.onNewSource()
                        Log.i("PlayerVM", "Fuente ${index + 1}/${queue.size}: ${(source.fileName ?: source.title).take(120)}")
                        playingFileName = source.fileName ?: source.title
                        val resumeAt = savedResumePosition()
                        trackSelectionToRestore = null // Es otra fuente: las pistas de la anterior no aplican.
                        launchPlayer(result.url, startPositionMs = resumeAt)
                        if (resumeAt > 0) _notice.value = texts.get(StringsR.string.player_resume_from, formatTime(resumeAt))
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
                _notice.value = texts.get(StringsR.string.player_no_other_working_spanish_source_back_to)
                playFrom(back)
                return@launch
            }
            _uiState.value = PlayerUiState.Error(
                if (allNotCached) {
                    texts.get(StringsR.string.player_none_of_the_sources_tried_is_cached) +
                        "${queue.firstNotNullOfOrNull { it.debrid } ?: "Real-Debrid"}.\n$lastQueueError"
                } else {
                    texts.get(StringsR.string.player_none_of_the_sources_tried_worked) + "\n$lastQueueError"
                },
            )
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    // -----------------------------------------------------------------------
    // -----------------------------------------------------------------------

    fun searchExternalSubtitles(language: String) = subtitleCoordinator.search(language)

    fun downloadExternalSubtitle(item: ExternalSubtitle) = subtitleCoordinator.download(item)

    fun resetExternalSubtitles() = subtitleCoordinator.reset()

    fun onManualSubtitleSelection() = subtitleCoordinator.onManualSubtitleSelection()

    fun disableExternalSubtitle() = subtitleCoordinator.disable()

    fun adjustSubtitleOffset(deltaMs: Long) = subtitleCoordinator.adjustOffset(deltaMs)

    fun autoSyncNow() = subtitleCoordinator.autoSyncNow()

    private fun buildMediaItem(url: String): MediaItem = MediaItem.Builder().setUri(url).build()

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
                SkipPromptUiState.Showing(texts.get(StringsR.string.player_skip_recap), recap.endMs)
            intro != null && position in intro && intro.endMs - position > 2_000 ->
                SkipPromptUiState.Showing(texts.get(StringsR.string.player_skip_intro), intro.endMs)
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
                    texts.get(StringsR.string.player_no_sources_with_verified_spanish_audio_available),
                )
            } else {
                _notice.value = texts.get(StringsR.string.player_this_source_has_no_spanish_audio)
            }
            return
        }
        spanishSkips++
        if (!spanishOnly && spanishFallback == null) spanishFallback = queueIndex
        _notice.value = texts.get(StringsR.string.player_no_spanish_audio_trying_another_source)
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
        lastQueueError = texts.get(StringsR.string.player_the_addon_couldn_t_find_this_source)
        allNotCached = false
        _notice.value = texts.get(StringsR.string.player_that_source_doesn_t_have_the_episode)
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
            subtitleCoordinator.pause(p.currentPosition)
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
                        subtitleCoordinator.onPlayerReady(player, player.currentTracks)
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
                        lastQueueError = texts.get(StringsR.string.player_source_open_error, error.errorCodeName)
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
                            texts.get(StringsR.string.player_playback_stopped, error.errorCodeName),
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
                    subtitleCoordinator.onTracksChanged(player, tracks)
                    checkSpanishAudio(player, tracks)
                }

                override fun onCues(cueGroup: CueGroup) {
                    subtitleCoordinator.onCues(cueGroup.presentationTimeUs / 1000, cueGroup.cues.isNotEmpty())
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                        subtitleCoordinator.onSeek(oldPosition.positionMs, newPosition.positionMs)
                    }
                }
            },
        )
        subtitleCoordinator.preparePlayer()
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
