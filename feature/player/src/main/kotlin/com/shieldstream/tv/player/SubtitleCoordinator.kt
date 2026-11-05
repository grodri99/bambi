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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
    private val player: () -> Player?,
    private val notify: (String) -> Unit,
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

    private val reference = ReferenceCollector()
    private var referencePending = false
    private var referenceAwaitingStart = false
    private var referenceAvailable = false
    private var textSelectionBeforeReference: androidx.media3.common.TrackSelectionParameters? = null

    private data class SubtitleCandidate(
        val item: ExternalSubtitle,
        val label: String,
        val segments: List<CuesWithTiming>,
        val intervals: List<SubInterval>,
        val run: MutableList<SyncResult> = mutableListOf(),
    )

    fun onNewSource() {
        subtitleDefaultHandled = false
        subsJob?.cancel()
        if ((_externalSubs.value as? ExternalSubsState.Results)?.evaluating == true) {
            cancelCandidateEvaluation()
        }
    }

    fun search(language: String, automatic: Boolean = false) {
        if (!automatic) subtitleDefaultHandled = true
        val query = buildQuery(language) ?: run {
            _externalSubs.value = ExternalSubsState.Error(language, texts.get(StringsR.string.player_unknown_what_s_playing))
            return
        }
        subsJob?.cancel()
        if ((_externalSubs.value as? ExternalSubsState.Results)?.evaluating == true) cancelCandidateEvaluation()
        _externalSubs.value = ExternalSubsState.Searching(language)
        subsJob = scope.launch {
            runCatching { subtitles.search(query) }
                .onSuccess { items ->
                    if (items.isEmpty()) {
                        _externalSubs.value = ExternalSubsState.Results(language, items)
                        if (automatic) {
                            notify(texts.get(StringsR.string.player_no_external_subtitles_found_in_this_language))
                        }
                    } else {
                        val count = candidateShortlist(items).size
                        _externalSubs.value = ExternalSubsState.Results(language, items, evaluating = true, candidateCount = count)
                        startCandidateEvaluation(language, items, query)
                    }
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    _externalSubs.value = ExternalSubsState.Error(language, it.message ?: texts.get(StringsR.string.player_error_searching_subtitles))
                    if (automatic) notify(texts.get(StringsR.string.player_couldn_t_search_for_external_subtitles))
                }
        }
    }

    fun download(item: ExternalSubtitle) {
        subtitleDefaultHandled = true
        val query = buildQuery(item.language) ?: return
        val previous = _externalSubs.value
        cancelCandidateEvaluation()
        if (currentExternal != null) {
            Log.w(TAG, "AUTO_SYNC_FAILURE reason=replaced_by_user ${externalContext(currentExternal)}")
            disable()
        }
        subsJob?.cancel()
        Log.i(TAG, "SUBTITLE_DOWNLOAD_START ${externalContext(item)}")
        _externalSubs.value = ExternalSubsState.Downloading(item.language, item)
        subsJob = scope.launch {
            runCatching {
                val file = subtitles.download(item, query)
                val segments = withContext(Dispatchers.IO) { parseSubtitle(file.path, file.mimeType) }
                check(segments.isNotEmpty()) { texts.get(StringsR.string.player_the_subtitle_is_empty_or_unreadable) }
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
                    _externalSubs.value = ExternalSubsState.Error(item.language, e.message ?: texts.get(StringsR.string.player_couldn_t_download))
                    if (previous is ExternalSubsState.Results) {
                        delay(2_500)
                        _externalSubs.value = previous.copy(evaluating = false)
                    }
                }
        }
    }

    fun reset() {
        subsJob?.cancel()
        if ((_externalSubs.value as? ExternalSubsState.Results)?.evaluating == true) cancelCandidateEvaluation()
        _externalSubs.value = ExternalSubsState.Idle
    }

    fun onManualSubtitleSelection() {
        subtitleDefaultHandled = true
    }

    fun disable() {
        syncJob?.cancel()
        restoreReferenceSelection()
        _activeExternal.value = null
        currentExternal = null
        _subtitleTiming.value = SubtitleTiming()
        _syncStatus.value = SyncStatus.Off
    }

    fun adjustOffset(deltaMs: Long) {
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
            notify(texts.get(StringsR.string.player_this_file_has_no_reference_subtitles_it))
            return
        }
        syncJob?.cancel()
        scope.launch {
            val position = player()?.currentPosition ?: return@launch
            val (refs, windows) = reference.snapshot(position)
            val result = withContext(Dispatchers.Default) { SubtitleSync.align(refs, windows, ext.intervals) }
            logResult(result)
            if (result != null && (result.strong || result.plausible)) {
                applyAuto(result.timing, announce = true, force = true)
            } else {
                _syncStatus.value = SyncStatus.Collecting(reference.cueCount)
                notify(texts.get(StringsR.string.player_not_enough_data_yet_let_the_movie))
                startAutoSync()
            }
        }
    }

    fun preparePlayer() {
        val evaluating = (_externalSubs.value as? ExternalSubsState.Results)?.evaluating == true
        if ((_activeExternal.value != null || evaluating) && referenceAvailable) referencePending = true
    }

    fun onPlayerReady(player: Player, tracks: Tracks) = applySubtitlePreference(player, tracks)

    fun onTracksChanged(player: Player, tracks: Tracks) {
        applySubtitlePreference(player, tracks)
        selectReferenceTrack(player, tracks)
        if (referenceAwaitingStart && tracks.isTypeSelected(C.TRACK_TYPE_TEXT)) {
            referenceAwaitingStart = false
            reference.begin(player.currentPosition)
        }
    }

    fun onCues(positionMs: Long, hasText: Boolean) = reference.onCues(positionMs, hasText)

    fun onSeek(fromMs: Long, toMs: Long) = reference.jump(fromMs, toMs)

    fun pause(positionMs: Long) = reference.pause(positionMs)

    private fun startCandidateEvaluation(language: String, items: List<ExternalSubtitle>, query: SubtitleQuery) {
        syncJob?.cancel()
        if (currentExternal != null) {
            Log.w(TAG, "AUTO_SYNC_FAILURE reason=replaced_by_user ${externalContext(currentExternal)}")
            disable()
        }
        beginReferenceCapture()
        syncJob = scope.launch {
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
                val position = player()?.currentPosition ?: continue
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
        check(segments.isNotEmpty()) { texts.get(StringsR.string.player_the_subtitle_is_empty_or_unreadable) }
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
        notify(
            if (reason == "no_reference") {
                texts.get(StringsR.string.player_no_reference_subtitles_choose_one_and_adjust)
            } else {
                texts.get(StringsR.string.player_no_subtitle_matched_automatically_choose_one_manually)
            },
        )
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
        val media = media() ?: return null
        return SubtitleQuery(media = media, language = language, fileName = playingFileName())
    }

    private fun externalContext(item: ExternalSubtitle?): String = item?.let {
        "provider=${it.provider} id=${it.id} release=${it.release.take(100).ifBlank { "unknown" }}"
    } ?: "subtitle=none"

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
            textSelectionBeforeReference = player()?.trackSelectionParameters
        }
        reference.reset()
        referencePending = true
        referenceAwaitingStart = false
        referenceAvailable = false
        player()?.let { selectReferenceTrack(it, it.currentTracks) }
    }

    private fun restoreReferenceSelection() {
        referencePending = false
        referenceAwaitingStart = false
        referenceAvailable = false
        textSelectionBeforeReference?.let { saved ->
            player()?.trackSelectionParameters = saved
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
            notify(texts.get(StringsR.string.player_subtitles_added, label))
            startAutoSync()
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
                notify(texts.get(StringsR.string.player_subtitles_added_if_they_re_out_of))
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

    private fun startAutoSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            var nextAt = SubtitleSync.MIN_REFERENCE_CUES
            val run = ArrayList<SyncResult>()
            var applied: SyncResult? = null
            while (isActive) {
                delay(3_000)
                val ext = _activeExternal.value ?: return@launch
                val count = reference.cueCount
                if (_syncStatus.value is SyncStatus.Collecting) _syncStatus.value = SyncStatus.Collecting(count)
                if (count < nextAt) continue
                val position = player()?.currentPosition ?: continue
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
                    if (first != null && result.agreesWith(first)) return@launch
                    applied = result
                } else if (count >= 80 && applied == null) {
                    Log.w(TAG, "AUTO_SYNC_FAILURE reason=no_reliable_offset refs=$count ${externalContext(currentExternal)}")
                    _syncStatus.value = SyncStatus.Unsure
                    notify(texts.get(StringsR.string.player_couldn_t_sync_the_subtitles_automatically_audio))
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
            notify(
                if (abs(timing.offsetMs) < 200 && timing.scale == 1.0) {
                    texts.get(StringsR.string.player_subtitles_checked_already_in_sync)
                } else {
                    texts.get(StringsR.string.player_subtitles_synced, timing.describe(texts))
                },
            )
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

    private companion object {
        const val TAG = "SubSync"
        const val AUTO_CANDIDATE_LIMIT = 6
        const val MAX_AUTO_REFERENCE_CUES = 80
    }
}
