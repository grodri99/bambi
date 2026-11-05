package com.shieldstream.tv.player

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.avi.AviExtractor
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.CueDecoder
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.ts.TsExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException

enum class ReferenceFailureReason {
    TIMEOUT,
    NETWORK,
    UNSUPPORTED_CONTAINER,
    NO_TEXT_TRACK,
    INSUFFICIENT_CUES,
    READ_ERROR,
}

data class ReferenceReadFailure(
    val reason: ReferenceFailureReason,
    val retryable: Boolean,
    val elapsedMs: Long,
    val bytesRead: Long,
    val seeks: Int,
    val track: String?,
)

internal data class EmbeddedReference(
    val sourceUrl: String,
    val track: String,
    val sampledWindow: SubInterval,
    val intervals: List<SubInterval>,
    val elapsedMs: Long,
    val bytesRead: Long,
    val seeks: Int,
)

internal sealed interface ReferenceReadResult {
    data class Success(val reference: EmbeddedReference) : ReferenceReadResult
    data class Failure(val failure: ReferenceReadFailure) : ReferenceReadResult
}

internal interface EmbeddedReferenceReader {
    suspend fun read(sourceUrl: String, positionMs: Long): ReferenceReadResult
}

@OptIn(UnstableApi::class)
internal fun isReferenceSubtitle(format: Format): Boolean {
    val mime = format.sampleMimeType.orEmpty().lowercase()
    return mime == "application/x-media3-cues" || DefaultSubtitleParserFactory().supportsFormat(format)
}

@OptIn(UnstableApi::class)
internal fun referenceTrackScore(format: Format): Int? {
    if (!isReferenceSubtitle(format)) return null
    val language = format.language.orEmpty().lowercase()
    val label = format.label.orEmpty().lowercase()
    var score = if (language.startsWith("en")) 200 else if (language.startsWith("es")) 150 else 100
    if (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0 || "forced" in label) score -= 150
    return score
}

internal fun referenceTrackSelectionScore(baseScore: Int, cueCount: Int): Int =
    baseScore + minOf(cueCount, 20) * 20

private const val LAST_CUE_MS = 4_000L
private const val MAX_INFERRED_CUE_MS = 2_500L

internal fun referenceIntervalsFromStarts(starts: List<Long>): List<SubInterval> {
    val uniqueStarts = starts.distinct().sorted()
    return uniqueStarts.mapIndexedNotNull { index, start ->
        val next = uniqueStarts.getOrNull(index + 1)
        val end = if (next != null) minOf(next, start + MAX_INFERRED_CUE_MS) else start + LAST_CUE_MS
        if (end > start) SubInterval(start, end) else null
    }
}

@OptIn(UnstableApi::class)
private class CountingDataSource(private val delegate: DataSource) : DataSource by delegate {
    var bytesRead = 0L
        private set

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = delegate.read(buffer, offset, length)
        if (read > 0) bytesRead += read
        return read
    }
}

private class DemuxMetrics(val positionMs: Long) {
    var track: String? = null
    var seeks = 0
}

@OptIn(UnstableApi::class)
internal class Media3EmbeddedReferenceReader(
    private val dataSourceFactory: DataSource.Factory,
) : EmbeddedReferenceReader {
    override suspend fun read(sourceUrl: String, positionMs: Long): ReferenceReadResult = withContext(Dispatchers.IO) {
        val dataSource = CountingDataSource(dataSourceFactory.createDataSource())
        val parserFactory = DefaultSubtitleParserFactory()
        val extractors = listOf(
            MatroskaExtractor(parserFactory),
            Mp4Extractor(parserFactory),
            AviExtractor(0, parserFactory),
            TsExtractor(parserFactory),
        )
        val metrics = DemuxMetrics(positionMs)
        val startedAt = SystemClock.elapsedRealtime()

        fun failure(reason: ReferenceFailureReason, retryable: Boolean) = ReferenceReadResult.Failure(
            ReferenceReadFailure(
                reason = reason,
                retryable = retryable,
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                bytesRead = dataSource.bytesRead,
                seeks = metrics.seeks,
                track = metrics.track,
            ),
        )

        try {
            Log.i(TAG, "DEMUX_START positionMs=$positionMs")
            val result = withTimeoutOrNull(DEMUX_TIMEOUT_MS) {
                runInterruptible<ReferenceReadResult?> {
                    demux(extractors, dataSource, sourceUrl, positionMs, metrics, startedAt)
                }
            } ?: failure(ReferenceFailureReason.TIMEOUT, retryable = true)
            logResult(result)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            failure(ReferenceFailureReason.NETWORK, retryable = true).also { logResult(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Embedded subtitle demux failed: ${e.javaClass.simpleName}: ${e.message}")
            failure(ReferenceFailureReason.READ_ERROR, retryable = true).also { logResult(it) }
        } finally {
            extractors.forEach { extractor -> runCatching { extractor.release() } }
            runCatching { dataSource.close() }
        }
    }

    private fun demux(
        extractors: List<Extractor>,
        dataSource: CountingDataSource,
        sourceUrl: String,
        positionMs: Long,
        metrics: DemuxMetrics,
        startedAt: Long,
    ): ReferenceReadResult {
        fun failure(reason: ReferenceFailureReason, retryable: Boolean) = ReferenceReadResult.Failure(
            ReferenceReadFailure(
                reason, retryable, SystemClock.elapsedRealtime() - startedAt,
                dataSource.bytesRead, metrics.seeks, metrics.track,
            ),
        )

        var input = openInput(dataSource, sourceUrl, 0L)
        val output = TimestampOutput(positionMs, metrics)
        val extractor = extractors.firstOrNull { candidate ->
            input.resetPeekPosition()
            val matches = runCatching { candidate.sniff(input) }.getOrDefault(false)
            input.resetPeekPosition()
            matches
        } ?: return failure(ReferenceFailureReason.UNSUPPORTED_CONTAINER, retryable = false)
        extractor.init(output)
        val positionHolder = PositionHolder()
        var lastSeekPosition = -1L
        var initialSeekHandled = positionMs == 0L
        while (!output.hasEnoughCues) {
            if (Thread.interrupted()) throw InterruptedException("Subtitle demux interrupted")
            val seekMap = output.seekMap
            if (!initialSeekHandled && seekMap != null) {
                initialSeekHandled = true
                val seekPoint = seekMap.getSeekPoints(positionMs * 1_000).first
                if (seekPoint.position > 0L && ++metrics.seeks <= MAX_SEEKS) {
                    extractor.seek(seekPoint.position, positionMs * 1_000)
                    dataSource.close()
                    input = openInput(dataSource, sourceUrl, seekPoint.position)
                    continue
                }
            }
            val readResult = extractor.read(input, positionHolder)
            if (output.tracksEnded && !output.hasReferenceTracks) {
                return failure(ReferenceFailureReason.NO_TEXT_TRACK, retryable = false)
            }
            when (readResult) {
                Extractor.RESULT_END_OF_INPUT -> break
                Extractor.RESULT_SEEK -> {
                    val target = positionHolder.position
                    if (target < 0L || target == lastSeekPosition || ++metrics.seeks > MAX_SEEKS) {
                        return failure(ReferenceFailureReason.READ_ERROR, retryable = true)
                    }
                    lastSeekPosition = target
                    dataSource.close()
                    input = openInput(dataSource, sourceUrl, target)
                }
                else -> Unit
            }
        }
        output.selectBestTrack()
        val decodedIntervals = SubtitleSync.intervalsOf(output.cues)
        val intervals = if (decodedIntervals.size >= SubtitleSync.MIN_REFERENCE_CUES) {
            decodedIntervals
        } else {
            referenceIntervalsFromStarts(output.starts)
        }
        Log.i(TAG, "DEMUX_CUES starts=${output.starts.size} decoded=${output.cues.size}")
        if (metrics.track == null) return failure(ReferenceFailureReason.NO_TEXT_TRACK, retryable = false)
        if (intervals.size < SubtitleSync.MIN_REFERENCE_CUES) {
            return failure(ReferenceFailureReason.INSUFFICIENT_CUES, retryable = true)
        }
        return ReferenceReadResult.Success(
            EmbeddedReference(
                sourceUrl = sourceUrl,
                track = metrics.track!!,
                sampledWindow = SubInterval(intervals.first().startMs, intervals.last().endMs),
                intervals = intervals,
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                bytesRead = dataSource.bytesRead,
                seeks = metrics.seeks,
            ),
        )
    }

    private fun logResult(result: ReferenceReadResult) {
        when (result) {
            is ReferenceReadResult.Success -> with(result.reference) {
                Log.i(
                    TAG,
                    "DEMUX_SUCCESS track=$track window=${sampledWindow.startMs}-${sampledWindow.endMs} " +
                        "cues=${intervals.size} elapsedMs=$elapsedMs bytes=$bytesRead seeks=$seeks",
                )
            }
            is ReferenceReadResult.Failure -> with(result.failure) {
                Log.w(
                    TAG,
                    "DEMUX_FAILURE reason=$reason retryable=$retryable track=$track elapsedMs=$elapsedMs " +
                        "bytes=$bytesRead seeks=$seeks",
                )
            }
        }
    }

    private fun openInput(dataSource: DataSource, sourceUrl: String, position: Long): DefaultExtractorInput {
        val remaining = dataSource.open(DataSpec.Builder().setUri(Uri.parse(sourceUrl)).setPosition(position).build())
        val length = if (remaining == C.LENGTH_UNSET.toLong()) remaining else position + remaining
        return DefaultExtractorInput(dataSource, position, length)
    }

    private inner class TimestampOutput(
        private val positionMs: Long,
        private val metrics: DemuxMetrics,
    ) : ExtractorOutput {
        private val tracks = mutableListOf<TimestampTrackOutput>()
        private var selected: TimestampTrackOutput? = null
        val starts: List<Long> get() = selected?.starts.orEmpty()
        val cues: List<CuesWithTiming> get() = selected?.cues.orEmpty()
        val hasEnoughCues: Boolean get() = tracks.any { it.starts.size >= REFERENCE_CUES }
        val hasReferenceTracks: Boolean get() = tracks.any { it.score != null }
        var seekMap: SeekMap? = null
        var tracksEnded = false

        override fun track(id: Int, type: Int): TrackOutput = TimestampTrackOutput(id).also { tracks += it }

        override fun endTracks() {
            tracks.forEach { it.accepted = it.score != null }
            tracksEnded = true
        }

        fun selectBestTrack() {
            val best = tracks.filter { it.score != null }.maxByOrNull {
                referenceTrackSelectionScore(it.score!!, it.starts.size)
            }
            selected = best
            best?.let { track ->
                val format = track.format!!
                metrics.track = "${track.id}:${format.language.orEmpty().lowercase()}:${format.sampleMimeType.orEmpty().lowercase()}"
                Log.i(
                    TAG,
                    "DEMUX_SELECTED track=${metrics.track} score=${track.score} cues=${track.starts.size}",
                )
            }
        }

        private inner class TimestampTrackOutput(val id: Int) : TrackOutput {
            var format: Format? = null
            var score: Int? = null
            var accepted = false
            val starts = mutableListOf<Long>()
            val cues = mutableListOf<CuesWithTiming>()
            private val sampleBuffer = ByteArrayOutputStream()
            private val cueDecoder = CueDecoder()

            override fun format(format: Format) {
                this.format = format
                score = referenceTrackScore(format)
                Log.i(
                    TAG,
                    "DEMUX_FORMAT track=$id mime=${format.sampleMimeType.orEmpty().lowercase()} " +
                        "language=${format.language.orEmpty().lowercase()} candidateScore=$score",
                )
            }

            override fun sampleData(
                input: DataReader,
                length: Int,
                allowEndOfInput: Boolean,
                sampleDataPart: Int,
            ): Int {
                val scratch = ByteArray(minOf(length, 64 * 1024))
                var total = 0
                while (total < length) {
                    val read = input.read(scratch, 0, minOf(scratch.size, length - total))
                    if (read == C.RESULT_END_OF_INPUT) {
                        return if (allowEndOfInput && total == 0) C.RESULT_END_OF_INPUT else total
                    }
                    if (accepted) sampleBuffer.write(scratch, 0, read)
                    total += read
                }
                return total
            }

            override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
                if (accepted) {
                    val bytes = ByteArray(length)
                    data.readBytes(bytes, 0, length)
                    sampleBuffer.write(bytes)
                } else {
                    data.skipBytes(length)
                }
            }

            override fun sampleMetadata(
                timeUs: Long,
                flags: Int,
                size: Int,
                offset: Int,
                cryptoData: TrackOutput.CryptoData?,
            ) {
                if (!accepted) return
                if (timeUs >= positionMs * 1_000) {
                    starts += timeUs / 1_000
                    val bytes = sampleBuffer.toByteArray()
                    val start = bytes.size - offset - size
                    if (size > 0 && start >= 0) {
                        runCatching { cueDecoder.decode(timeUs, bytes, start, size) }
                            .getOrNull()
                            ?.takeIf { it.cues.isNotEmpty() && it.startTimeUs != C.TIME_UNSET }
                            ?.let(cues::add)
                    }
                }
                sampleBuffer.reset()
            }
        }
        override fun seekMap(seekMap: SeekMap) {
            this.seekMap = seekMap
        }
    }

    private companion object {
        const val TAG = "SubSync"
        const val REFERENCE_CUES = 20
        const val DEMUX_TIMEOUT_MS = 30_000L
        const val MAX_SEEKS = 200
    }
}
