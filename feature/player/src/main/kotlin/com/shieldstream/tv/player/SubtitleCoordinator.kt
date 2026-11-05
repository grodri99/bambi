package com.shieldstream.tv.player

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import com.shieldstream.tv.domain.SubtitleRepository
import com.shieldstream.tv.model.ExternalSubtitle
import com.shieldstream.tv.model.PlaybackMedia
import com.shieldstream.tv.model.SubtitlePreference
import com.shieldstream.tv.model.SubtitleQuery
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

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
internal class SubtitleCoordinator(
    private val scope: CoroutineScope,
    private val subtitles: SubtitleRepository,
    private val subtitlePreference: SubtitlePreference,
    private val texts: Texts,
    private val media: () -> PlaybackMedia?,
    private val playingFileName: () -> String?,
    private val sourceUrl: () -> String?,
    private val player: () -> Player?,
    private val notify: (String) -> Unit,
    private val referenceReader: EmbeddedReferenceReader,
) {
    private val _externalSubs = MutableStateFlow<ExternalSubsState>(ExternalSubsState.Idle)
    val externalSubs: StateFlow<ExternalSubsState> = _externalSubs.asStateFlow()

    private val _activeExternal = MutableStateFlow<ActiveExternalSubtitle?>(null)
    val activeExternal: StateFlow<ActiveExternalSubtitle?> = _activeExternal.asStateFlow()

    private val _subtitleTiming = MutableStateFlow(SubtitleTiming())
    val subtitleTiming: StateFlow<SubtitleTiming> = _subtitleTiming.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Off)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private var subtitleDefaultHandled = false
    private var subsJob: Job? = null
    private var syncJob: Job? = null
    private var currentExternal: ExternalSubtitle? = null
    private var cachedReference: Pair<String, ReferenceReadResult>? = null
    private var operationGeneration = 0L

    private data class PendingCandidate(
        val item: ExternalSubtitle,
        val active: ActiveExternalSubtitle,
    )

    private var pendingCandidate: PendingCandidate? = null

    fun onNewSource() {
        operationGeneration++
        subtitleDefaultHandled = false
        subsJob?.cancel()
        syncJob?.cancel()
        cachedReference = null
        pendingCandidate = null
        currentExternal = null
        _activeExternal.value = null
        _subtitleTiming.value = SubtitleTiming()
        _syncStatus.value = SyncStatus.Off
        _externalSubs.value = ExternalSubsState.Idle
    }

    fun onPlaybackStopped() {
        operationGeneration++
        subsJob?.cancel()
        syncJob?.cancel()
        pendingCandidate = null
        val active = _activeExternal.value
        _externalSubs.value = if (active != null) ExternalSubsState.Attached(active.label) else ExternalSubsState.Idle
        if (active == null) subtitleDefaultHandled = false
    }

    fun search(language: String, automatic: Boolean = false) {
        if (!automatic) subtitleDefaultHandled = true
        val query = buildQuery(language) ?: run {
            _externalSubs.value = ExternalSubsState.Error(language, texts.get(StringsR.string.player_unknown_what_s_playing))
            return
        }
        subsJob?.cancel()
        val generation = ++operationGeneration
        _externalSubs.value = ExternalSubsState.Searching(language)
        Log.i(TAG, "SUBTITLE_SEARCH_START automatic=$automatic language=$language ${queryContext(query)}")
        subsJob = scope.launch {
            runCatching { subtitles.search(query) }
                .onSuccess { items ->
                    if (generation != operationGeneration) return@onSuccess
                    Log.i(TAG, "SUBTITLE_SEARCH_RESULT total=${items.size} providers=${items.groupingBy { it.provider }.eachCount()}")
                    if (items.isEmpty()) {
                        _externalSubs.value = ExternalSubsState.Results(language, items)
                        if (automatic) notify(texts.get(StringsR.string.player_no_external_subtitles_found_in_this_language))
                    } else if (automatic) {
                        val best = items.sortedWith(
                            compareByDescending<ExternalSubtitle> { it.matchScore }.thenBy { it.hearingImpaired },
                        ).first()
                        downloadAndSync(best, query)
                    } else {
                        _externalSubs.value = ExternalSubsState.Results(language, items)
                    }
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (generation != operationGeneration) return@onFailure
                    Log.w(TAG, "SUBTITLE_SEARCH_FAILURE language=$language error=${it.message}")
                    _externalSubs.value = ExternalSubsState.Error(language, it.message ?: texts.get(StringsR.string.player_error_searching_subtitles))
                    if (automatic) notify(texts.get(StringsR.string.player_couldn_t_search_for_external_subtitles))
                }
        }
    }

    fun download(item: ExternalSubtitle) {
        val query = buildQuery(item.language) ?: return
        downloadAndSync(item, query)
    }

    private fun downloadAndSync(item: ExternalSubtitle, query: SubtitleQuery) {
        subtitleDefaultHandled = true
        val previous = _externalSubs.value
        subsJob?.cancel()
        syncJob?.cancel()
        val generation = ++operationGeneration
        _externalSubs.value = ExternalSubsState.Downloading(item.language, item)
        Log.i(TAG, "SUBTITLE_DOWNLOAD_START ${externalContext(item)}")
        subsJob = scope.launch {
            runCatching {
                val file = subtitles.download(item, query)
                val segments = withContext(Dispatchers.IO) { parseSubtitle(file.path, file.mimeType) }
                check(segments.isNotEmpty()) { texts.get(StringsR.string.player_the_subtitle_is_empty_or_unreadable) }
                file to segments
            }
                .onSuccess { (file, segments) ->
                    if (generation != operationGeneration) return@onSuccess
                    val candidate = PendingCandidate(
                        item = item,
                        active = ActiveExternalSubtitle(file.label, segments, SubtitleSync.intervalsOf(segments)),
                    )
                    pendingCandidate = candidate
                    Log.i(TAG, "SUBTITLE_DOWNLOAD_SUCCESS ${externalContext(item)}")
                    startSync(candidate)
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    if (generation != operationGeneration) return@onFailure
                    Log.w(TAG, "SUBTITLE_DOWNLOAD_FAILURE ${externalContext(item)} error=${e.message}")
                    _externalSubs.value = when {
                        _activeExternal.value != null -> ExternalSubsState.Attached(_activeExternal.value!!.label)
                        previous is ExternalSubsState.Results -> previous
                        else -> ExternalSubsState.Error(item.language, e.message ?: texts.get(StringsR.string.player_couldn_t_download))
                    }
                }
        }
    }

    fun reset() {
        subsJob?.cancel()
        _externalSubs.value = ExternalSubsState.Idle
    }

    fun onManualSubtitleSelection() {
        subtitleDefaultHandled = true
    }

    fun disable() {
        operationGeneration++
        subsJob?.cancel()
        syncJob?.cancel()
        pendingCandidate = null
        _activeExternal.value = null
        currentExternal = null
        _subtitleTiming.value = SubtitleTiming()
        _syncStatus.value = SyncStatus.Off
    }

    fun adjustOffset(deltaMs: Long) {
        if (_activeExternal.value == null) return
        operationGeneration++
        syncJob?.cancel()
        val timing = _subtitleTiming.value.let { it.copy(offsetMs = it.offsetMs + deltaMs) }
        _subtitleTiming.value = timing
        _syncStatus.value = SyncStatus.Manual(timing)
    }

    fun autoSyncNow() {
        val active = _activeExternal.value ?: return
        val item = currentExternal ?: return
        startSync(PendingCandidate(item, active), forceFresh = true)
    }

    fun onPlayerReady(player: Player, tracks: Tracks) = applySubtitlePreference(player, tracks)

    fun onTracksChanged(player: Player, tracks: Tracks) = applySubtitlePreference(player, tracks)

    private fun startSync(candidate: PendingCandidate, forceFresh: Boolean = false) {
        val ext = candidate.active
        syncJob?.cancel()
        val generation = ++operationGeneration
        pendingCandidate = candidate
        if (_activeExternal.value == null) _syncStatus.value = SyncStatus.Collecting(0)
        syncJob = scope.launch {
            when (val referenceResult = readReference(generation, forceFresh)) {
                null -> if (generation == operationGeneration) failCandidate(candidate, SyncStatus.Unsure)
                is ReferenceReadResult.Failure -> if (generation == operationGeneration) {
                    failCandidate(candidate, SyncStatus.ReferenceError(referenceResult.failure))
                }
                is ReferenceReadResult.Success -> {
                    if (generation != operationGeneration) return@launch
                    val reference = referenceResult.reference
                    val refs = reference.intervals
                    val windows = listOf(reference.sampledWindow)
                    Log.i(
                        TAG,
                        "SYNC_INPUT refs=${refs.size} window=${reference.sampledWindow.startMs}-${reference.sampledWindow.endMs} " +
                            "track=${reference.track} external=${ext.intervals.size}",
                    )
                    val result = withContext(Dispatchers.Default) {
                        SubtitleSync.align(refs, windows, ext.intervals, isActive = { isActive })
                    }
                    if (generation != operationGeneration) return@launch
                    Log.i(TAG, "SYNC_RESULT ${externalContext(candidate.item)} result=$result")
                    if (result != null && SubtitleSync.shouldAutoApply(refs, reference.sampledWindow, ext.intervals, result)) {
                        commitCandidate(candidate, result.timing)
                    } else {
                        failCandidate(candidate, SyncStatus.Unsure)
                    }
                }
            }
        }
    }

    private suspend fun readReference(generation: Long, forceFresh: Boolean): ReferenceReadResult? {
        val url = sourceUrl() ?: return null
        val position = player()?.currentPosition ?: 0L
        if (!forceFresh) {
            cachedReference?.takeIf { it.first == url }?.second?.let { cached ->
                val reusable = when (cached) {
                    is ReferenceReadResult.Success -> position <= cached.reference.sampledWindow.endMs
                    is ReferenceReadResult.Failure -> !cached.failure.retryable
                }
                if (reusable) return cached
            }
        }
        val result = referenceReader.read(url, position)
        if (generation == operationGeneration &&
            (result !is ReferenceReadResult.Failure || !result.failure.retryable)
        ) {
            cachedReference = url to result
        }
        return result
    }

    private fun commitCandidate(candidate: PendingCandidate, timing: SubtitleTiming) {
        pendingCandidate = null
        currentExternal = candidate.item
        _activeExternal.value = candidate.active
        _subtitleTiming.value = timing
        _syncStatus.value = SyncStatus.Auto(timing)
        _externalSubs.value = ExternalSubsState.Attached(candidate.active.label)
        notify(
            if (abs(timing.offsetMs) < 200 && timing.scale == 1.0) {
                texts.get(StringsR.string.player_subtitles_checked_already_in_sync)
            } else {
                texts.get(StringsR.string.player_subtitles_synced, timing.describe(texts))
            },
        )
    }

    private fun failCandidate(candidate: PendingCandidate, status: SyncStatus) {
        pendingCandidate = null
        val previous = _activeExternal.value
        if (previous != null) {
            _externalSubs.value = ExternalSubsState.Attached(previous.label)
            notifyFailure(status)
            return
        }
        currentExternal = candidate.item
        _activeExternal.value = candidate.active
        _subtitleTiming.value = SubtitleTiming()
        _syncStatus.value = status
        _externalSubs.value = ExternalSubsState.Attached(candidate.active.label)
        notifyFailure(status)
    }

    private fun notifyFailure(status: SyncStatus) {
        val missingReference = status is SyncStatus.ReferenceError && !status.failure.retryable
        notify(
            if (missingReference) {
                texts.get(StringsR.string.player_subtitles_added_if_they_re_out_of)
            } else {
                texts.get(StringsR.string.player_couldn_t_sync_the_subtitles_automatically_audio)
            },
        )
    }

    private fun buildQuery(language: String): SubtitleQuery? {
        val media = media() ?: return null
        return SubtitleQuery(media = media, language = language, fileName = playingFileName())
    }

    private fun externalContext(item: ExternalSubtitle?): String = item?.let {
        "provider=${it.provider} id=${it.id.hashCode().toUInt()} release=${it.release.take(100).ifBlank { "unknown" }}"
    } ?: "subtitle=none"

    private fun queryContext(query: SubtitleQuery): String {
        val media = query.media
        return "title=${media.title.take(80)} original=${media.originalTitle?.take(80)} year=${media.year} " +
            "season=${media.season} episode=${media.episode} file=${query.fileName?.take(100)}"
    }

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

    private fun applySubtitlePreference(player: Player, tracks: Tracks) {
        if (subtitleDefaultHandled) return
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
            notify(
                when (subtitlePreference) {
                    SubtitlePreference.SPANISH -> texts.get(StringsR.string.player_embedded_spanish_subtitles)
                    SubtitlePreference.ENGLISH -> texts.get(StringsR.string.player_embedded_english_subtitles)
                    SubtitlePreference.OFF -> return
                },
            )
            return
        }

        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        notify(
            when (subtitlePreference) {
                SubtitlePreference.SPANISH -> texts.get(StringsR.string.player_no_embedded_spanish_subtitles_finding_external_subtitles)
                SubtitlePreference.ENGLISH -> texts.get(StringsR.string.player_no_embedded_english_subtitles_finding_external_subtitles)
                SubtitlePreference.OFF -> return
            },
        )
        search(subtitlePreference.code ?: return, automatic = true)
    }

    private fun isForced(f: Format): Boolean {
        val label = f.label.orEmpty().lowercase()
        return f.selectionFlags and C.SELECTION_FLAG_FORCED != 0 ||
            "forced" in label || "forzad" in label || "forcé" in label
    }

    private companion object {
        const val TAG = "SubSync"
    }
}
