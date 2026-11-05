package com.shieldstream.tv.player

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import com.shieldstream.tv.model.Lang
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

internal data class SubInterval(val startMs: Long, val endMs: Long)

data class SubtitleTiming(val offsetMs: Long = 0L, val scale: Double = 1.0)

@OptIn(UnstableApi::class)
class ActiveExternalSubtitle internal constructor(
    val label: String,
    val segments: List<CuesWithTiming>,
    internal val intervals: List<SubInterval>,
)

sealed interface SyncStatus {
    data object Off : SyncStatus
    data class Collecting(val referenceCues: Int) : SyncStatus
    data class Auto(val timing: SubtitleTiming) : SyncStatus
    data class Manual(val timing: SubtitleTiming) : SyncStatus
    data object NoReference : SyncStatus
    data class ReferenceError(val failure: ReferenceReadFailure) : SyncStatus
    data object Unsure : SyncStatus
}

internal data class SyncResult(
    val timing: SubtitleTiming,
    val correlation: Float,
    val ratio: Float,
    val referenceCues: Int,
) {
    val strong: Boolean
        get() = when {
            timing.scale == 1.0 ->
                (correlation >= 0.55f && ratio >= 1.5f) ||
                (correlation >= 0.60f && ratio >= 1.4f) ||
                (correlation >= 0.65f && ratio >= 1.3f)
            correlation >= 0.75f -> ratio >= 1.1f
            else -> correlation >= 0.6f && ratio >= 1.8f
        }

    val plausible: Boolean
        get() = timing.scale == 1.0 && correlation >= 0.25f && ratio >= 2.0f

    val consistent: Boolean
        get() = timing.scale == 1.0 && correlation >= 0.35f && ratio >= 1.03f

    fun agreesWith(other: SyncResult): Boolean =
        other.timing.scale == timing.scale && abs(other.timing.offsetMs - timing.offsetMs) <= 300
}

internal fun confirmedByWindows(candidate: SyncResult, first: SyncResult?, second: SyncResult?): Boolean {
    if (candidate.strong) return true
    if (!candidate.plausible || first == null || second == null) return false
    return (first.strong || first.plausible) &&
        (second.strong || second.plausible) &&
        candidate.agreesWith(first) && candidate.agreesWith(second)
}

internal object SubtitleSync {
    const val MIN_REFERENCE_CUES = 10
    private const val BIN_MS = 100L
    private const val MAX_OFFSET_MS = 300_000L
    private const val MIN_WINDOW_MS = 20_000L
    private const val PEAK_EXCLUSION_BINS = 20 // 2 s
    private val SCALES = doubleArrayOf(1.0, 25.0 / 23.976, 23.976 / 25.0)

    fun align(
        reference: List<SubInterval>,
        windows: List<SubInterval>,
        external: List<SubInterval>,
        isActive: () -> Boolean = { true },
    ): SyncResult? {
        val usable = windows.filter { it.endMs - it.startMs >= MIN_WINDOW_MS }
        if (usable.isEmpty() || external.isEmpty()) return null
        val refs = reference.filter { r -> usable.any { w -> r.startMs >= w.startMs && r.endMs <= w.endMs } }
        if (refs.size < MIN_REFERENCE_CUES) return null
        val signals = usable.map { w -> Signal(w.startMs, referenceSignal(refs, w)) }

        var best: Candidate? = null
        for (scale in SCALES) {
            if (!isActive()) return null
            val c = bestOffset(signals, external, scale, isActive) ?: continue
            val adjusted = if (scale == 1.0) c.score.toDouble() else c.score * 0.97
            if (best == null || adjusted > best.adjusted) best = c.copy(adjusted = adjusted)
        }
        val b = best ?: return null
        val ratio = if (b.second <= 0f) 99f else b.score / b.second
        val fine = refine(refs, usable, external, b.scale, b.offsetMs, isActive)
        if (!isActive()) return null
        return SyncResult(SubtitleTiming(fine, b.scale), b.score, ratio, refs.size)
    }

    fun shouldAutoApply(
        reference: List<SubInterval>,
        sampledWindow: SubInterval,
        external: List<SubInterval>,
        result: SyncResult,
    ): Boolean {
        if (result.strong) return true
        if (!result.plausible || reference.size < MIN_REFERENCE_CUES * 2) return false
        val sorted = reference.sortedBy { it.startMs }
        val split = sorted.size / 2
        val midpoint = (sorted[split - 1].endMs + sorted[split].startMs) / 2
        val firstWindow = SubInterval(sampledWindow.startMs, midpoint)
        val secondWindow = SubInterval(midpoint, sampledWindow.endMs)
        if (firstWindow.endMs - firstWindow.startMs < MIN_WINDOW_MS ||
            secondWindow.endMs - secondWindow.startMs < MIN_WINDOW_MS
        ) {
            return false
        }
        val first = align(reference, listOf(firstWindow), external)
        val second = align(reference, listOf(secondWindow), external)
        return confirmedByWindows(result, first, second)
    }

    @OptIn(UnstableApi::class)
    fun intervalsOf(segments: List<CuesWithTiming>): List<SubInterval> {
        val out = ArrayList<SubInterval>()
        segments.forEachIndexed { index, s ->
            if (s.cues.isEmpty()) return@forEachIndexed
            val start = s.startTimeUs / 1000
            val endUs = if (s.durationUs != C.TIME_UNSET) s.startTimeUs + s.durationUs else segments.getOrNull(index + 1)?.startTimeUs ?: return@forEachIndexed
            val end = endUs / 1000
            if (end <= start) return@forEachIndexed
            val last = out.lastOrNull()
            if (last != null && start <= last.endMs + 1) out[out.size - 1] = last.copy(endMs = maxOf(last.endMs, end)) else out += SubInterval(start, end)
        }
        return out
    }

    @OptIn(UnstableApi::class)
    fun segmentAt(segments: List<CuesWithTiming>, timeUs: Long): Int {
        var lo = 0
        var hi = segments.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (segments[mid].startTimeUs <= timeUs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (found < 0) return -1
        val s = segments[found]
        val endUs = if (s.durationUs != C.TIME_UNSET) s.startTimeUs + s.durationUs else segments.getOrNull(found + 1)?.startTimeUs ?: Long.MAX_VALUE
        return if (timeUs < endUs && s.cues.isNotEmpty()) found else -1
    }

    // --- Interno ---

    private class Signal(val startMs: Long, val values: IntArray)

    private data class Candidate(
        val offsetMs: Long,
        val scale: Double,
        val score: Float,
        val second: Float,
        val adjusted: Double = 0.0,
    )

    private fun referenceSignal(refs: List<SubInterval>, window: SubInterval): IntArray {
        val n = ((window.endMs - window.startMs) / BIN_MS).toInt()
        val sig = IntArray(n)
        for (r in refs) {
            val a = ((maxOf(r.startMs, window.startMs) - window.startMs) / BIN_MS).toInt().coerceIn(0, n)
            val b = ((minOf(r.endMs, window.endMs) - window.startMs) / BIN_MS).toInt().coerceIn(0, n)
            for (i in a until b) sig[i] = 1
        }
        return sig
    }

    private fun bestOffset(
        signals: List<Signal>,
        external: List<SubInterval>,
        scale: Double,
        isActive: () -> Boolean,
    ): Candidate? {
        val lo = floor(external.minOf { it.startMs } * scale / BIN_MS).toLong() - 1
        val hi = ceil(external.maxOf { it.endMs } * scale / BIN_MS).toLong() + 1
        val size = (hi - lo + 1).toInt()
        if (size <= 0) return null
        val ext = BooleanArray(size)
        for (e in external) {
            val a = (floor(e.startMs * scale / BIN_MS).toLong() - lo).toInt().coerceIn(0, size)
            val b = (floor(e.endMs * scale / BIN_MS).toLong() - lo).toInt().coerceIn(0, size)
            for (j in a until b) ext[j] = true
        }
        val n = signals.sumOf { it.values.size }.toDouble()
        val sumR = signals.sumOf { s -> s.values.sum() }.toDouble()
        val varR = sumR - sumR * sumR / n
        if (varR <= 0.0) return null
        val maxBins = (MAX_OFFSET_MS / BIN_MS).toInt()
        val scores = FloatArray(2 * maxBins + 1)
        for (k in scores.indices) {
            if (k % 256 == 0 && !isActive()) return null
            val ob = k - maxBins
            var sumX = 0
            var sumRX = 0
            for (s in signals) {
                val base = (s.startMs / BIN_MS - ob - lo).toInt()
                val from = maxOf(0, -base)
                val to = minOf(s.values.size, size - base)
                for (i in from until to) if (ext[base + i]) {
                    sumX++
                    sumRX += s.values[i]
                }
            }
            val varX = sumX - sumX.toDouble() * sumX / n
            scores[k] = if (varX <= 0.0) -1f else ((sumRX - sumR * sumX / n) / Math.sqrt(varR * varX)).toFloat()
        }
        var bestK = 0
        for (k in scores.indices) if (scores[k] > scores[bestK]) bestK = k
        var second = -1f
        for (k in scores.indices) if (abs(k - bestK) > PEAK_EXCLUSION_BINS && scores[k] > second) second = scores[k]
        return Candidate(
            offsetMs = (bestK - maxBins) * BIN_MS,
            scale = scale,
            score = scores[bestK],
            second = second,
        )
    }

    private fun refine(
        refs: List<SubInterval>,
        windows: List<SubInterval>,
        external: List<SubInterval>,
        scale: Double,
        coarse: Long,
        isActive: () -> Boolean,
    ): Long {
        var bestOffset = coarse
        var bestScore = Long.MIN_VALUE
        var o = coarse - BIN_MS
        while (o <= coarse + BIN_MS) {
            if (!isActive()) return coarse
            var score = 0L
            for (e in external) {
                val s = (e.startMs * scale).toLong() + o
                val en = (e.endMs * scale).toLong() + o
                for (w in windows) {
                    val a = maxOf(s, w.startMs)
                    val b = minOf(en, w.endMs)
                    if (b <= a) continue
                    var overlap = 0L
                    for (r in refs) {
                        val x = maxOf(a, r.startMs)
                        val y = minOf(b, r.endMs)
                        if (y > x) overlap += y - x
                    }
                    score += 2 * overlap - (b - a)
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestOffset = o
            }
            o += 10
        }
        return bestOffset
    }
}

private val UiLocale: Locale get() = Lang.locale

fun SubtitleTiming.describe(texts: Texts): String {
    val seconds = String.format(UiLocale, "%.1f", abs(offsetMs) / 1000.0)
    val offsetText = when {
        offsetMs == 0L -> texts.get(StringsR.string.player_0_0_s)
        offsetMs > 0 -> texts.get(StringsR.string.player_subtitle_offset_later, seconds)
        else -> texts.get(StringsR.string.player_subtitle_offset_earlier, seconds)
    }
    val fps = when {
        scale > 1.01 -> texts.get(StringsR.string.player_25_23_976_fps)
        scale < 0.99 -> texts.get(StringsR.string.player_23_976_25_fps)
        else -> ""
    }
    return offsetText + fps
}

fun SyncStatus.describe(texts: Texts): String = when (this) {
    SyncStatus.Off -> ""
    is SyncStatus.Collecting -> if (referenceCues < SubtitleSync.MIN_REFERENCE_CUES) {
        texts.get(StringsR.string.player_sync_reading, referenceCues, SubtitleSync.MIN_REFERENCE_CUES)
    } else {
        texts.get(StringsR.string.player_sync_finding, referenceCues)
    }
    is SyncStatus.Auto -> texts.get(StringsR.string.player_sync_auto, timing.describe(texts))
    is SyncStatus.Manual -> texts.get(StringsR.string.player_sync_manual, timing.describe(texts))
    SyncStatus.NoReference -> texts.get(StringsR.string.player_this_file_has_no_subtitles_to_use)
    is SyncStatus.ReferenceError -> if (failure.retryable) {
        texts.get(StringsR.string.player_no_reliable_offset_found_adjust_it_manually)
    } else {
        texts.get(StringsR.string.player_this_file_has_no_subtitles_to_use)
    }
    SyncStatus.Unsure -> texts.get(StringsR.string.player_no_reliable_offset_found_adjust_it_manually)
}
