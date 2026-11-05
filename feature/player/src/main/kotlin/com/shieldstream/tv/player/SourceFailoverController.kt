package com.shieldstream.tv.player

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.shieldstream.tv.domain.ResolveState
import com.shieldstream.tv.domain.ResolveStreamUseCase
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal sealed interface SourceResolutionEvent {
    data class Resolving(val message: String, val attempt: Int, val total: Int) : SourceResolutionEvent
    data class Ready(val url: String, val index: Int, val source: StreamSource) : SourceResolutionEvent
    data class Fallback(val index: Int, val notice: String) : SourceResolutionEvent
    data class Error(val message: String) : SourceResolutionEvent
}

internal sealed interface SourceFailoverAction {
    data object Ignored : SourceFailoverAction
    data object AudioAccepted : SourceFailoverAction
    data class Notice(val message: String) : SourceFailoverAction
    data class FatalError(val message: String) : SourceFailoverAction
    data class SwitchSource(val index: Int, val notice: String? = null) : SourceFailoverAction
}

internal class SourceFailoverController(
    private val queue: List<StreamSource>,
    private val resolveStreamUseCase: ResolveStreamUseCase,
    private val texts: Texts,
    private val spanishOnly: Boolean,
    private val spanishFirst: Boolean,
) {
    var queueIndex = 0
        private set
    var everPlayed = false
    var slateChecked = false
        private set
    var spanishChecked = false
        private set
    private var spanishSkips = 0
    private var spanishFallback: Int? = null
    private var lastQueueError = ""
    private var allNotCached = true

    fun resolve(from: Int): Flow<SourceResolutionEvent> = flow {
        var attempts = 0
        for (index in from until queue.size) {
            if (attempts >= MAX_ATTEMPTS) break
            val source = queue[index]
            var outcome: ResolveState? = null
            resolveStreamUseCase(source).collect { state ->
                when (state) {
                    is ResolveState.Progress -> {
                        val prefix = if (index > 0) texts.get(StringsR.string.player_queue_source, index + 1, queue.size) else ""
                        emit(SourceResolutionEvent.Resolving(prefix + state.message, index + 1, queue.size))
                    }
                    is ResolveState.Ready, is ResolveState.Failed -> outcome = state
                }
            }
            when (val result = outcome) {
                is ResolveState.Ready -> {
                    queueIndex = index
                    everPlayed = false
                    slateChecked = false
                    spanishChecked = false
                    emit(SourceResolutionEvent.Ready(result.url, index, source))
                    return@flow
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
            emit(
                SourceResolutionEvent.Fallback(
                    back,
                    texts.get(StringsR.string.player_no_other_working_spanish_source_back_to),
                ),
            )
            return@flow
        }
        emit(
            SourceResolutionEvent.Error(
                if (allNotCached) {
                    texts.get(StringsR.string.player_none_of_the_sources_tried_is_cached) +
                        "${queue.firstNotNullOfOrNull { it.debrid } ?: "Real-Debrid"}.\n$lastQueueError"
                } else {
                    texts.get(StringsR.string.player_none_of_the_sources_tried_worked) + "\n$lastQueueError"
                },
            ),
        )
    }

    fun checkSpanishAudio(playbackState: Int, tracks: Tracks): SourceFailoverAction {
        if (spanishChecked || !spanishFirst) return SourceFailoverAction.Ignored
        val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g.getTrackFormat(it).language?.lowercase() } }
        if (audio.isEmpty() && playbackState != Player.STATE_READY) return SourceFailoverAction.Ignored
        spanishChecked = true
        val source = queue.getOrNull(queueIndex) ?: return SourceFailoverAction.Ignored
        if (!spanishOnly && source.spanishAudioScore == 0) return SourceFailoverAction.Ignored
        val known = audio.filterNotNull().filter { it != "und" && it != "mul" }
        if (known.any { it == "es" || it.startsWith("es-") || it == "spa" } || (!spanishOnly && known.isEmpty())) {
            return SourceFailoverAction.AudioAccepted
        }
        Log.i("PlayerVM", "Sin audio en castellano (${known.distinct()}): ${source.title.take(80)}")
        val next = (queueIndex + 1 until queue.size).firstOrNull { queue[it].spanishAudioScore > 0 }
        if (next == null || spanishSkips >= MAX_SPANISH_SKIPS) {
            return if (spanishOnly) {
                SourceFailoverAction.FatalError(texts.get(StringsR.string.player_no_sources_with_verified_spanish_audio_available))
            } else {
                SourceFailoverAction.Notice(texts.get(StringsR.string.player_this_source_has_no_spanish_audio))
            }
        }
        spanishSkips++
        if (!spanishOnly && spanishFallback == null) spanishFallback = queueIndex
        return SourceFailoverAction.SwitchSource(
            next,
            texts.get(StringsR.string.player_no_spanish_audio_trying_another_source),
        )
    }

    fun checkErrorSlate(duration: Long): SourceFailoverAction.SwitchSource? {
        if (slateChecked) return null
        if (duration == C.TIME_UNSET || duration <= 0) return null
        slateChecked = true
        val source = queue.getOrNull(queueIndex) ?: return null
        if (source.resolveUrl == null || duration >= SLATE_MAX_MS || source.sizeBytes < SLATE_MIN_BYTES) return null
        if (queueIndex + 1 >= queue.size) return null
        Log.i("PlayerVM", "Vídeo de error del addon (${duration / 1000} s, dice ${source.sizeBytes / 1_000_000} MB): ${source.title.take(80)}")
        lastQueueError = texts.get(StringsR.string.player_the_addon_couldn_t_find_this_source)
        allNotCached = false
        return SourceFailoverAction.SwitchSource(
            queueIndex + 1,
            texts.get(StringsR.string.player_that_source_doesn_t_have_the_episode),
        )
    }

    fun onSourceOpenError(errorCodeName: String): SourceFailoverAction.SwitchSource? {
        if (everPlayed || queueIndex + 1 >= queue.size) return null
        lastQueueError = texts.get(StringsR.string.player_source_open_error, errorCodeName)
        allNotCached = false
        return SourceFailoverAction.SwitchSource(queueIndex + 1)
    }

    fun onPlayerCreated() {
        spanishChecked = false
    }

    private companion object {
        const val MAX_ATTEMPTS = 8
        const val MAX_SPANISH_SKIPS = 3
        const val SLATE_MAX_MS = 4 * 60_000L
        const val SLATE_MIN_BYTES = 150_000_000L
    }
}
