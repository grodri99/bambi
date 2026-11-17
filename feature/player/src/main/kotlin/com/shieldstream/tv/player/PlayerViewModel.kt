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
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
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
import com.shieldstream.tv.domain.TrackMemoryRepository
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.media.AudioOutputSwitch
import com.shieldstream.tv.media.EncodedAudio
import com.shieldstream.tv.media.Media3Factory
import com.shieldstream.tv.media.PlaybackPolicyProvider
import com.shieldstream.tv.media.VideoFormatInspector
import androidx.media3.common.PlaybackException
import com.shieldstream.tv.model.ExternalSubtitle
import com.shieldstream.tv.model.PlaybackVideoInfo
import com.shieldstream.tv.model.SubtitleAppearance
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

/** Opciones del temporizador de apagado del reproductor. */
sealed interface SleepTimer {
    data object Off : SleepTimer
    data class Minutes(val minutes: Int) : SleepTimer
    data object EndOfEpisode : SleepTimer
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
    private val trackMemory: TrackMemoryRepository,
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

    private val playbackSnapshot = holder.playback.value
    private val queueSnapshot = playbackSnapshot?.sources.orEmpty()
    private val mediaSnapshot = playbackSnapshot?.media
    val title: StateFlow<String> = MutableStateFlow(playbackSnapshot?.label.orEmpty()).asStateFlow()

    val seekThumbnails: StateFlow<Boolean> = combine(
        settings.deviceOptimization,
        settings.maxBufferSeconds,
        settings.seekThumbnails,
    ) { optimization, buffer, thumbnails ->
        playbackPolicies.resolve(optimization, buffer, thumbnails).seekThumbnails
    }.stateIn(viewModelScope, SharingStarted.Eagerly, playbackPolicies.automatic().seekThumbnails)

    private val progressKey: String? = playbackSnapshot?.progressKey

    private var segments: SkipSegments = SkipSegments.NONE
    private var segmentsJob: Job? = null
    private val autoSkipped = mutableSetOf<String>()
    private var prefetched = false
    private var nextUpShownAt = -1L
    private val _skipPrompt = MutableStateFlow<SkipPromptUiState>(SkipPromptUiState.Hidden)
    val skipPrompt: StateFlow<SkipPromptUiState> = _skipPrompt.asStateFlow()
    private val nextSnapshot = playbackSnapshot?.next
    private val _nextUp = MutableStateFlow<NextUpUiState>(NextUpUiState.Hidden)
    val nextUp: StateFlow<NextUpUiState> = _nextUp.asStateFlow()
    private val _goNext = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val goNext: SharedFlow<Unit> = _goNext.asSharedFlow()

    private val _backToSources = MutableSharedFlow<Unit>(replay = 1)

    /** Se cierra el reproductor para elegir otra fuente (la elegida a mano falló y no se cambia sola). */
    val backToSources: SharedFlow<Unit> = _backToSources.asSharedFlow()

    /**
     * La fuente no se puede reproducir. Si se eligió a mano con el cambio automático desactivado, no se prueba otra:
     * se cierra y la ficha vuelve a abrir la lista con el motivo. Si no, la pantalla de error de siempre.
     */
    private fun sourceFailed(message: String) {
        if (playbackSnapshot?.manualPick == true) {
            Log.i("PlayerVM", "Fuente elegida a mano falló, vuelta a la lista: $message")
            holder.reportPickedSourceFailed(message.lineSequence().lastOrNull { it.isNotBlank() } ?: message)
            _backToSources.tryEmit(Unit)
        } else {
            _uiState.value = PlayerUiState.Error(message)
        }
    }
    private var nextUpDismissed = false
    private var wentNext = false
    private val autoNext: StateFlow<Boolean> = settings.autoNextEpisode.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Formato de imagen: con bandas, ampliado (recorta) o estirado. Se guarda para lo siguiente que se vea. */
    val videoFit: StateFlow<com.shieldstream.tv.model.VideoFit> = settings.videoFit
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.shieldstream.tv.model.VideoFit.FIT)

    fun cycleVideoFit() {
        val next = videoFit.value.next()
        viewModelScope.launch { settings.setVideoFit(next) }
    }
    private val autoSkipIntro: StateFlow<Boolean> = settings.autoSkipIntro.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // --- Castellano de verdad ---------------------------------------------------------------------------
    private val spanishOnly = profiles.active.value.spanishOnlySources
    private val spanishFirst = profiles.active.value.spanishFirst || spanishOnly
    private val subtitlePreference = profiles.active.value.subtitlePreference
    /** Clave estable del título para recordar audio/subtítulos (la misma en todos los episodios). */
    // Misma clave para todos los episodios: el IMDb de la serie o, si falta, su título (la etiqueta incluye el episodio).
    private val trackMemoryKey = mediaSnapshot?.let { it.imdbId ?: "${it.type.name}:${it.title}" } ?: playbackSnapshot?.label
    private val _subtitleAppearance = MutableStateFlow(profiles.active.value.subtitleAppearance)
    val subtitleAppearance: StateFlow<SubtitleAppearance> = _subtitleAppearance.asStateFlow()
    val tracksPanelCard: StateFlow<Boolean> = settings.tracksPanelCard.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val experimentalSubtitleSync = profiles.active.value.experimentalSubtitleSync
    val matchContentFrameRate = profiles.active.value.matchContentFrameRate
    private val frameRateProbe = EarlyFrameRateProbe(media3Factory.dataSourceFactory)
    var earlyFrameRate: Float? = null
        private set

    private var started = false
    private var playbackVisible = true
    private var playerGeneration = 0L
    private var playerCreationJob: Job? = null
    private var earlyPreparationPending: ExoPlayer? = null
    private var trackSelectionToRestore: TrackSelectionParameters? = null
    private var resolvedUrl: String? = null
    private var playingFileName: String? = null
    private var currentSourceId: String? = null
    private var resumePositionMs = 0L

    private var playbackRetries = 0
    private var rememberedTracksApplied = false
    /** Con velocidad != 1x se fuerza PCM (sin passthrough) para que ExoPlayer pueda aplicarla. */
    private var forcePcmAudio = false
    private var audioOutput = AudioOutputSwitch()
    private var audioSwitchJob: Job? = null

    private var saverJob: Job? = null

    private val sourceFailover = SourceFailoverController(
        queue = queueSnapshot,
        resolveStreamUseCase = resolveStreamUseCase,
        texts = texts,
        spanishOnly = spanishOnly,
        spanishFirst = spanishFirst,
    )

    private val subtitleCoordinator = SubtitleCoordinator(
        scope = viewModelScope,
        subtitles = subtitles,
        subtitlePreference = subtitlePreference,
        texts = texts,
        media = { mediaSnapshot },
        playingFileName = { playingFileName },
        sourceUrl = { resolvedUrl },
        player = { _player.value },
        notify = { _notice.value = it },
        referenceReader = Media3EmbeddedReferenceReader(media3Factory.dataSourceFactory),
        experimentalSyncEnabled = experimentalSubtitleSync,
        preferredSubtitle = {
            trackMemoryKey?.let(trackMemory::subtitle)?.takeUnless { it == TrackMemoryRepository.SUBTITLES_OFF }
        },
        subtitlesOff = {
            trackMemoryKey?.let(trackMemory::subtitle) == TrackMemoryRepository.SUBTITLES_OFF
        },
    )
    val externalSubs: StateFlow<ExternalSubsState> = subtitleCoordinator.externalSubs
    val activeExternal: StateFlow<ActiveExternalSubtitle?> = subtitleCoordinator.activeExternal
    val subtitleTiming: StateFlow<SubtitleTiming> = subtitleCoordinator.subtitleTiming
    val syncStatus: StateFlow<SyncStatus> = subtitleCoordinator.syncStatus

    fun start() {
        if (started) return
        started = true
        trace("player_opened")

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
        viewModelScope.launch {
            segments = skipSegmentsRepository.episode(imdb, season, episode)
            autoSkipped.clear()
        }
    }

    private fun playFrom(from: Int) {
        viewModelScope.launch {
            sourceFailover.resolve(from).collect { event ->
                when (event) {
                    is SourceResolutionEvent.Resolving -> {
                        _uiState.value = PlayerUiState.Resolving(event.message, event.attempt, event.total)
                    }
                    is SourceResolutionEvent.Ready -> onSourceReady(event)
                    is SourceResolutionEvent.Fallback -> {
                        _notice.value = event.notice
                        playFrom(event.index)
                    }
                    is SourceResolutionEvent.Error -> sourceFailed(event.message)
                }
            }
        }
    }

    private fun onSourceReady(event: SourceResolutionEvent.Ready) {
        trace("source_resolved", "attempt=${event.index + 1}")
        resolvedUrl = event.url
        subtitleCoordinator.onNewSource()
        val source = event.source
        Log.i("PlayerVM", "Fuente ${event.index + 1}/${queueSnapshot.size}: ${(source.fileName ?: source.title).take(120)}")
        playingFileName = source.fileName ?: source.title
        currentSourceId = source.id
        val resumeAt = savedResumePosition()
        trackSelectionToRestore = null // Es otra fuente: las pistas de la anterior no aplican.
        launchPlayer(event.url, startPositionMs = resumeAt)
        if (resumeAt > 0) _notice.value = texts.get(StringsR.string.player_resume_from, formatTime(resumeAt))
        // Si no es la primera de la cola es que alguna falló: se dice a cuál se ha pasado (calidad, HDR, idioma).
        if (event.index > 0) _notice.value = texts.get(StringsR.string.player_switched_source, describeSource(source))
        _uiState.value = PlayerUiState.Playing
        startProgressSaver()
        startSegmentsWatcher()
    }

    fun clearNotice() {
        _notice.value = null
    }

    private fun describeSource(source: com.shieldstream.tv.model.StreamSource): String = listOfNotNull(
        source.quality.label.takeIf { source.quality != com.shieldstream.tv.model.Quality.UNKNOWN },
        source.hdrType.label.takeIf { source.isHdr },
        texts.get(StringsR.string.player_castellano).takeIf { source.advertisesSpanishAudio },
    ).joinToString(" · ").ifBlank { source.title.take(40) }

    fun rememberAudioLanguage(language: String?) {
        val key = trackMemoryKey ?: return
        if (!language.isNullOrBlank()) trackMemory.saveAudio(key, language)
    }

    fun rememberSubtitleLanguage(language: String?) {
        val key = trackMemoryKey ?: return
        if (!language.isNullOrBlank()) trackMemory.saveSubtitle(key, language)
    }

    fun rememberSubtitlesOff() {
        trackMemoryKey?.let(trackMemory::saveSubtitlesOff)
    }

    // --- Velocidad y temporizador de apagado -------------------------------------------------------------
    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    /** Pasa a la siguiente velocidad (1x → 1,25x → 1,5x → 2x → 0,75x → 1x). */
    fun cyclePlaybackSpeed() {
        val next = PLAYBACK_SPEEDS[(PLAYBACK_SPEEDS.indexOf(_playbackSpeed.value) + 1) % PLAYBACK_SPEEDS.size]
        _playbackSpeed.value = next
        // Sin reproductor todavía: se crea ya con esta velocidad (y en PCM si no es 1x).
        val player = _player.value ?: return
        player.setPlaybackSpeed(next)
        maybeSwitchAudioModeForSpeed(player)
    }

    /**
     * Con audio directo (Dolby/DTS al amplificador) el reloj lo marca el audio y la velocidad no se aplica: hay que
     * decodificar a PCM. Solo se recrea el reproductor si el audio actual es de ese tipo; con AAC/Opus/PCM basta con
     * setPlaybackSpeed. Al volver a 1x se recupera el audio directo (surround).
     */
    private fun maybeSwitchAudioModeForSpeed(player: ExoPlayer) {
        val wantsPcm = _playbackSpeed.value != 1f
        if (wantsPcm == forcePcmAudio) return
        val audioMime = player.audioFormat?.sampleMimeType ?: player.currentTracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            ?.let { group -> (0 until group.length).firstOrNull(group::isTrackSelected)?.let { group.getTrackFormat(it).sampleMimeType } }
        if (!EncodedAudio.isPassthroughCapable(audioMime)) return
        if (wantsPcm) _notice.value = texts.get(StringsR.string.player_speed_stereo_notice)
        switchAudioOutput(player, wantsPcm)
    }

    /**
     * Cambia entre audio directo y PCM sin recrear el reproductor: se cambia la salida y se desactiva y reactiva
     * solo la pista de audio, para que el decodificador de audio se vuelva a montar. El vídeo sigue; como mucho hay
     * un instante sin sonido.
     */
    private fun switchAudioOutput(player: ExoPlayer, pcm: Boolean) {
        forcePcmAudio = pcm
        audioOutput.pcmOnly = pcm
        audioSwitchJob?.cancel()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        audioSwitchJob = viewModelScope.launch {
            // Tiene que dar tiempo a que el hilo de reproducción aplique la desactivación antes de reactivar.
            delay(AUDIO_SWITCH_GAP_MS)
            if (_player.value !== player) return@launch
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .build()
        }
    }

    private val _sleepTimerFinished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sleepTimerFinished: SharedFlow<Unit> = _sleepTimerFinished.asSharedFlow()

    /** Temporizador de apagado: al vencer pausa y avisa a la pantalla para volver a Inicio. */
    private val sleepTimerControl = SleepTimerController(viewModelScope) {
        _player.value?.pause()
        _notice.value = texts.get(StringsR.string.player_sleep_timer_paused)
        _sleepTimerFinished.tryEmit(Unit)
    }
    val sleepTimer: StateFlow<SleepTimer> = sleepTimerControl.timer

    /** Minutos que quedan, redondeados hacia arriba (null si no hay cuenta atrás). */
    val sleepTimerRemainingMinutes: StateFlow<Int?> = sleepTimerControl.remainingMinutes

    /** Pasa a la siguiente opción (desactivado → 15 → 30 → 45 → 60 → 90 → fin del episodio → desactivado). */
    fun cycleSleepTimer() = sleepTimerControl.cycle()

    // -----------------------------------------------------------------------
    // -----------------------------------------------------------------------

    fun searchExternalSubtitles(language: String) = subtitleCoordinator.search(language)

    fun downloadExternalSubtitle(item: ExternalSubtitle) = subtitleCoordinator.download(item)

    fun resetExternalSubtitles() = subtitleCoordinator.reset()

    fun onManualSubtitleSelection() = subtitleCoordinator.onManualSubtitleSelection()

    fun disableExternalSubtitle() = subtitleCoordinator.disable()

    fun adjustSubtitleOffset(deltaMs: Long) = subtitleCoordinator.adjustOffset(deltaMs)

    fun setSubtitleAppearance(appearance: SubtitleAppearance) {
        val normalized = appearance.normalized()
        _subtitleAppearance.value = normalized
        viewModelScope.launch { settings.setSubtitleAppearance(normalized) }
    }

    fun autoSyncNow() = subtitleCoordinator.autoSyncNow()

    private fun buildMediaItem(url: String): MediaItem = MediaItem.Builder().setUri(url).build()

    // -----------------------------------------------------------------------
    // Progreso y ciclo de vida
    // -----------------------------------------------------------------------

    private fun savedResumePosition(): Long {
        if (playbackSnapshot?.fromStart == true) return 0L
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
        if (autoSkipIntro.value) {
            // Cada tramo se salta solo una vez: si alguien vuelve atrás a propósito, se le deja verlo.
            val auto = listOfNotNull(recap?.let { "recap" to it }, intro?.let { "intro" to it })
                .firstOrNull { (name, seg) -> position in seg && seg.endMs - position > 2_000 && name !in autoSkipped }
            if (auto != null) {
                val (name, seg) = auto
                autoSkipped += name
                _player.value?.seekTo(seg.endMs)
                _notice.value = texts.get(if (name == "recap") StringsR.string.player_recap_skipped else StringsR.string.player_intro_skipped)
                if (_skipPrompt.value != SkipPromptUiState.Hidden) _skipPrompt.value = SkipPromptUiState.Hidden
                return
            }
        }
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
        when (val action = sourceFailover.checkSpanishAudio(player.playbackState, tracks)) {
            SourceFailoverAction.Ignored -> Unit
            SourceFailoverAction.AudioAccepted -> if (spanishOnly) player.play()
            is SourceFailoverAction.Notice -> _notice.value = action.message
            is SourceFailoverAction.FatalError -> {
                player.stop()
                resolvedUrl = null
                sourceFailed(action.message)
            }
            is SourceFailoverAction.SwitchSource -> switchSource(player, action, savePosition = true)
        }
    }

    private fun checkErrorSlate(player: ExoPlayer): Boolean {
        val action = sourceFailover.checkErrorSlate(player.duration) ?: return false
        switchSource(player, action)
        return true
    }

    private fun switchSource(
        player: ExoPlayer,
        action: SourceFailoverAction.SwitchSource,
        savePosition: Boolean = false,
    ) {
        action.notice?.let { _notice.value = it }
        if (savePosition) player.pause()
        viewModelScope.launch {
            if (savePosition) {
                saveProgress()
                resumePositionMs = player.currentPosition
            }
            player.release()
            if (_player.value === player) _player.value = null
            resolvedUrl = null
            playFrom(action.index)
        }
    }

    fun playNextNow() {
        if (sleepTimerControl.endsWithEpisode) {
            sleepTimerControl.finish()
            return
        }
        val next = nextSnapshot ?: return
        if (wentNext) return
        wentNext = true
        markCurrentFinished()
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
        if (duration > 0 && position > 0) watchProgress.save(key, position, duration, currentSourceId)
    }

    /**
     * Playback reached the end (or moved on to the next episode), so the item is watched even when the
     * container reported no usable duration. Falling back to the previous duration (or 1 ms) keeps the saved
     * `fraction` at 1f, which is what [com.shieldstream.tv.model.WatchProgress.isWatched] reads.
     */
    private fun markCurrentFinished() {
        val key = progressKey ?: return
        val known = _player.value?.duration?.takeIf { it > 0 }
            ?: watchProgress.get(key)?.durationMs?.takeIf { it > 1 }
        val duration = known ?: 1L
        watchProgress.save(key, duration, duration, currentSourceId)
    }

    fun onStop() {
        playbackVisible = false
        subtitleCoordinator.onPlaybackStopped()
        sleepTimerControl.reset()
        playerGeneration++
        playerCreationJob?.cancel()
        playerCreationJob = null
        earlyPreparationPending = null
        saveProgress()
        scrobble(ScrobbleAction.PAUSE)
        _player.value?.let { p ->
            resumePositionMs = p.currentPosition
            trackSelectionToRestore = p.trackSelectionParameters
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

    fun onEarlyDisplayModeReady(player: ExoPlayer) {
        if (_player.value !== player) return
        if (earlyPreparationPending === player) {
            earlyPreparationPending = null
            player.prepare()
            trace("player_prepared")
        }
        player.playWhenReady = !spanishOnly || sourceFailover.spanishChecked
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
        earlyPreparationPending = null
        earlyFrameRate = if (matchContentFrameRate) frameRateProbe.probe(url) else null
        if (matchContentFrameRate) trace("frame_rate_probe_finished", "rate=$earlyFrameRate")
        if (!playbackVisible || generation != playerGeneration) return
        forcePcmAudio = _playbackSpeed.value != 1f
        audioSwitchJob?.cancel()
        audioOutput = AudioOutputSwitch(forcePcmAudio)
        val player = media3Factory.createPlayer(policy, audioOutput)
        trace("player_created")
        if (matchContentFrameRate) {
            // PlayerScreen coordinates non-seamless Activity and surface display-mode changes.
            player.setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
        }
        if (!playbackVisible || generation != playerGeneration) {
            player.release()
            return
        }
        _videoInfo.value = null
        var activeDecoderName: String? = null
        val diagnostics = PlaybackDiagnostics()
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

                override fun onDroppedVideoFrames(
                    eventTime: AnalyticsListener.EventTime,
                    droppedFrames: Int,
                    elapsedMs: Long,
                ) {
                    diagnostics.onDroppedFrames(player.currentPosition, droppedFrames, elapsedMs)
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
                override fun onRenderedFirstFrame() {
                    trace("first_frame")
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_BUFFERING) {
                        diagnostics.onBuffering(player.currentPosition)
                    }
                    if (playbackState == Player.STATE_READY) {
                        diagnostics.onReady(player.currentPosition)
                        updateVideoInfo()
                        subtitleCoordinator.onPlayerReady(player, player.currentTracks)
                        applyRememberedAudio(player, player.currentTracks)
                        checkSpanishAudio(player, player.currentTracks)
                        if (checkErrorSlate(player)) return
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        markCurrentFinished()
                        scrobble(ScrobbleAction.STOP, fraction = 1f)
                        if (sleepTimerControl.endsWithEpisode) {
                            sleepTimerControl.finish()
                        } else if (!nextUpDismissed && autoNext.value) {
                            playNextNow()
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w("PlayerVM", "Error de reproducción (${error.errorCodeName})", error)
                    saveProgress()
                    val sourceError = error.errorCode in
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
                    if (sourceError) {
                        val action = sourceFailover.onSourceOpenError(error.errorCodeName)
                        if (action != null) {
                            switchSource(player, action)
                            return
                        }
                    }
                    if (playbackRetries < 1) {
                        playbackRetries++
                        player.prepare()
                        player.playWhenReady = !spanishOnly || sourceFailover.spanishChecked
                    } else {
                        sourceFailed(texts.get(StringsR.string.player_playback_stopped, error.errorCodeName))
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        playbackRetries = 0
                        sourceFailover.everPlayed = true
                    }
                    if (!isPlaying) saveProgress()
                    if (isPlaying) scrobble(ScrobbleAction.START)
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!playWhenReady) scrobble(ScrobbleAction.PAUSE)
                }

                override fun onTracksChanged(tracks: Tracks) {
                    subtitleCoordinator.onTracksChanged(player, tracks)
                    applyRememberedAudio(player, tracks)
                    checkSpanishAudio(player, tracks)
                    // Se eligió la velocidad antes de saber qué audio traía la fuente.
                    // (Fuera del callback: recrear libera este mismo reproductor.)
                    if (_playbackSpeed.value != 1f) {
                        viewModelScope.launch { if (_player.value === player) maybeSwitchAudioModeForSpeed(player) }
                    }
                }

            },
        )
        rememberedTracksApplied = false
        player.setMediaItem(buildMediaItem(url), startPositionMs)
        sourceFailover.onPlayerCreated()
        if (_playbackSpeed.value != 1f) player.setPlaybackSpeed(_playbackSpeed.value) // al cambiar de fuente se mantiene
        _player.value = player
        if (earlyFrameRate != null) {
            earlyPreparationPending = player
            player.playWhenReady = false
        } else {
            player.playWhenReady = !spanishOnly
            player.prepare()
            trace("player_prepared")
        }
    }

    /** Aplica una sola vez la pista de audio recordada para esta serie/película. */
    private fun applyRememberedAudio(player: ExoPlayer, tracks: Tracks) {
        if (rememberedTracksApplied) return
        val key = trackMemoryKey ?: return
        val language = trackMemory.audio(key) ?: return
        val match = tracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .firstNotNullOfOrNull { group ->
                (0 until group.length)
                    .firstOrNull { index ->
                        group.isTrackSupported(index) &&
                            group.getTrackFormat(index).language?.let { it == language || it.startsWith("$language-") } == true
                    }
                    ?.let { group to it }
            } ?: return
        rememberedTracksApplied = true
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(match.first.mediaTrackGroup, match.second))
            .build()
    }

    private fun trace(stage: String, details: String = "") {
        val startedAtNs = playbackSnapshot?.requestedAtNs ?: return
        if (startedAtNs == 0L) return
        val totalMs = (System.nanoTime() - startedAtNs) / 1_000_000
        Log.i(STARTUP_TRACE_TAG, "stage=$stage totalMs=$totalMs" + if (details.isEmpty()) "" else " $details")
    }

    override fun onCleared() {
        playbackVisible = false
        sleepTimerControl.cancel()
        playerGeneration++
        playerCreationJob?.cancel()
        earlyPreparationPending = null
        saveProgress()
        scrobble(ScrobbleAction.STOP)
        _player.value?.release()
        _player.value = null
        _videoInfo.value = null
        super.onCleared()
    }

    private companion object {
        val PLAYBACK_SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
        const val AUDIO_SWITCH_GAP_MS = 120L
        const val STARTUP_TRACE_TAG = "StartupTrace"
        const val NEXT_UP_COUNTDOWN_MS = 10_000L
        const val NEXT_UP_FALLBACK_MIN_MS = 20_000L
        const val AFTER_CREDITS_SLACK_MS = 30_000L
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
