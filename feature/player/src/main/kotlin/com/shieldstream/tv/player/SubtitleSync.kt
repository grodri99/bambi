package com.shieldstream.tv.player

import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.tr
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
    data object Unsure : SyncStatus
}

internal data class SyncResult(
    val timing: SubtitleTiming,
    val correlation: Float,
    val ratio: Float,
    val referenceCues: Int,
) {
    val strong: Boolean
        get() = if (timing.scale == 1.0) correlation >= 0.55f && ratio >= 1.5f else correlation >= 0.6f && ratio >= 1.8f

    val plausible: Boolean
        get() = timing.scale == 1.0 && correlation >= 0.33f && ratio >= 1.15f

    val consistent: Boolean
        get() = timing.scale == 1.0 && correlation >= 0.35f && ratio >= 1.03f

    fun agreesWith(other: SyncResult): Boolean =
        other.timing.scale == timing.scale && abs(other.timing.offsetMs - timing.offsetMs) <= 300
}

internal object SubtitleSync {
    const val MIN_REFERENCE_CUES = 10
    private const val BIN_MS = 100L
    private const val MAX_OFFSET_MS = 300_000L
    private const val MIN_WINDOW_MS = 20_000L
    private const val PEAK_EXCLUSION_BINS = 20 // 2 s
    private val SCALES = doubleArrayOf(1.0, 25.0 / 23.976, 23.976 / 25.0)

    fun align(reference: List<SubInterval>, windows: List<SubInterval>, external: List<SubInterval>): SyncResult? {
        val usable = windows.filter { it.endMs - it.startMs >= MIN_WINDOW_MS }
        if (usable.isEmpty() || external.isEmpty()) return null
        val refs = reference.filter { r -> usable.any { w -> r.startMs >= w.startMs && r.endMs <= w.endMs } }
        if (refs.size < MIN_REFERENCE_CUES) return null
        val signals = usable.map { w -> Signal(w.startMs, referenceSignal(refs, w)) }

        var best: Candidate? = null
        for (scale in SCALES) {
            val c = bestOffset(signals, external, scale) ?: continue
            val adjusted = if (scale == 1.0) c.score.toDouble() else c.score * 0.97
            if (best == null || adjusted > best.adjusted) best = c.copy(adjusted = adjusted)
        }
        val b = best ?: return null
        val ratio = if (b.second <= 0f) 99f else b.score / b.second
        val fine = refine(refs, usable, external, b.scale, b.offsetMs)
        return SyncResult(SubtitleTiming(fine, b.scale), b.score, ratio, refs.size)
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

    private fun bestOffset(signals: List<Signal>, external: List<SubInterval>, scale: Double): Candidate? {
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

    private fun refine(refs: List<SubInterval>, windows: List<SubInterval>, external: List<SubInterval>, scale: Double, coarse: Long): Long {
        var bestOffset = coarse
        var bestScore = Long.MIN_VALUE
        var o = coarse - BIN_MS
        while (o <= coarse + BIN_MS) {
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

internal class ReferenceCollector {
    private val intervals = ArrayList<SubInterval>()
    private val windows = ArrayList<SubInterval>()
    private var cueStart: Long? = null
    private var windowStart: Long? = null

    val cueCount: Int get() = intervals.size
    val isRunning: Boolean get() = windowStart != null

    fun reset() {
        intervals.clear()
        windows.clear()
        cueStart = null
        windowStart = null
    }

    fun begin(positionMs: Long) {
        windowStart = positionMs
        cueStart = null
    }

    fun pause(positionMs: Long) {
        val s = windowStart ?: return
        if (positionMs > s) windows += SubInterval(s, positionMs)
        windowStart = null
        cueStart = null
    }

    fun jump(fromMs: Long, toMs: Long) {
        if (windowStart == null) return
        pause(fromMs)
        begin(toMs)
    }

    fun onCues(timeMs: Long, hasText: Boolean) {
        if (windowStart == null) return
        val s = cueStart
        if (s != null && timeMs > s) intervals += SubInterval(s, timeMs)
        cueStart = if (hasText) timeMs else null
    }

    fun snapshot(positionMs: Long): Pair<List<SubInterval>, List<SubInterval>> {
        val w = ArrayList(windows)
        windowStart?.let { if (positionMs > it) w += SubInterval(it, positionMs) }
        return ArrayList(intervals) to w
    }
}

private val UiLocale: Locale get() = Lang.locale

fun SubtitleTiming.describe(): String {
    val seconds = String.format(UiLocale, "%.1f", abs(offsetMs) / 1000.0)
    val offsetText = when {
        offsetMs == 0L -> tr("0,0 s", "0.0 s")
        offsetMs > 0 -> tr("+$seconds s (más tarde)", "+$seconds s (later)")
        else -> tr("−$seconds s (antes)", "−$seconds s (earlier)")
    }
    val fps = when {
        scale > 1.01 -> tr(" · 25 → 23,976 fps", " · 25 → 23.976 fps")
        scale < 0.99 -> tr(" · 23,976 → 25 fps", " · 23.976 → 25 fps")
        else -> ""
    }
    return offsetText + fps
}

fun SyncStatus.describe(): String = when (this) {
    SyncStatus.Off -> ""
    is SyncStatus.Collecting -> if (referenceCues < SubtitleSync.MIN_REFERENCE_CUES) {
        tr("Sincronización automática: leyendo los subtítulos del archivo ($referenceCues/${SubtitleSync.MIN_REFERENCE_CUES} frases)…", "Auto-sync: reading the file's subtitles ($referenceCues/${SubtitleSync.MIN_REFERENCE_CUES} lines)…")
    } else {
        tr("Sincronización automática: buscando el ajuste ($referenceCues frases leídas)…", "Auto-sync: finding the offset ($referenceCues lines read)…")
    }
    is SyncStatus.Auto -> tr("Sincronizado automáticamente: ${timing.describe()}", "Synced automatically: ${timing.describe()}")
    is SyncStatus.Manual -> tr("Ajuste manual: ${timing.describe()}", "Manual adjustment: ${timing.describe()}")
    SyncStatus.NoReference -> tr("Este archivo no trae subtítulos que sirvan de referencia: ajuste manual", "This file has no subtitles to use as reference: manual adjustment")
    SyncStatus.Unsure -> tr("No se ha encontrado un ajuste fiable: ajústalo a mano", "No reliable offset found: adjust it manually")
}
