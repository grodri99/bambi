package com.shieldstream.tv.player

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.avi.AviExtractor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.ts.TsExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** Reads a handful of container timestamps without decoding media or starting another player. */
@OptIn(UnstableApi::class)
internal class EarlyFrameRateProbe(
    private val dataSourceFactory: DataSource.Factory,
) {
    suspend fun probe(sourceUrl: String): Float? = withContext(Dispatchers.IO) {
        val dataSource = BoundedDataSource(dataSourceFactory.createDataSource())
        val parserFactory = DefaultSubtitleParserFactory()
        val extractors = listOf(
            MatroskaExtractor(parserFactory, MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES),
            Mp4Extractor(parserFactory),
            AviExtractor(0, parserFactory),
            TsExtractor(parserFactory),
        )
        val output = ProbeOutput()
        val startedAt = SystemClock.elapsedRealtime()
        try {
            withTimeoutOrNull(TIMEOUT_MS) {
                runInterruptible { readSamples(extractors, dataSource, sourceUrl, output) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "FRAME_RATE_PROBE fallback reason=${e.javaClass.simpleName}")
        } finally {
            extractors.forEach { extractor -> runCatching { extractor.release() } }
            runCatching { dataSource.close() }
        }
        output.result().also { rate ->
            Log.i(
                TAG,
                "FRAME_RATE_PROBE rate=$rate elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                    "bytes=${dataSource.bytesRead} samples=${output.sampleCount}",
            )
        }
    }

    private fun readSamples(
        extractors: List<Extractor>,
        dataSource: BoundedDataSource,
        sourceUrl: String,
        output: ProbeOutput,
    ) {
        var input = openInput(dataSource, sourceUrl, 0L)
        val extractor = extractors.firstOrNull { candidate ->
            input.resetPeekPosition()
            val matches = runCatching { candidate.sniff(input) }.getOrDefault(false)
            input.resetPeekPosition()
            matches
        } ?: run {
            Log.d(TAG, "FRAME_RATE_PROBE no extractor matched")
            return
        }
        extractor.init(output)
        val positionHolder = PositionHolder()
        var seeks = 0
        while (!output.hasEnoughSamples && !output.hasDeclaredRate) {
            if (Thread.interrupted()) throw InterruptedException("Frame-rate probe interrupted")
            when (extractor.read(input, positionHolder)) {
                Extractor.RESULT_END_OF_INPUT -> break
                Extractor.RESULT_SEEK -> {
                    if (++seeks > MAX_SEEKS) break
                    dataSource.close()
                    input = openInput(dataSource, sourceUrl, positionHolder.position)
                }
                else -> Unit
            }
        }
    }

    private fun openInput(dataSource: DataSource, sourceUrl: String, position: Long): DefaultExtractorInput {
        val remaining = dataSource.open(DataSpec.Builder().setUri(Uri.parse(sourceUrl)).setPosition(position).build())
        val length = if (remaining == C.LENGTH_UNSET.toLong()) remaining else position + remaining
        return DefaultExtractorInput(dataSource, position, length)
    }

    private class ProbeOutput : ExtractorOutput {
        private val tracks = mutableListOf<ProbeTrackOutput>()
        private var selected: ProbeTrackOutput? = null

        val sampleCount: Int get() = selected?.timestamps?.size ?: 0
        val hasEnoughSamples: Boolean get() = sampleCount >= SAMPLE_COUNT
        val hasDeclaredRate: Boolean get() = selected?.declaredRate?.isUsable() == true

        override fun track(id: Int, type: Int): TrackOutput = ProbeTrackOutput().also { tracks += it }

        override fun endTracks() {
            selected = tracks.firstOrNull { it.isVideo }?.also { it.accepted = true }
        }

        override fun seekMap(seekMap: SeekMap) = Unit

        fun result(): Float? {
            val track = selected ?: return null
            track.declaredRate.takeIf { it.isUsable() }?.let { return it }
            return estimateFrameRateFromTimestamps(track.timestamps)
        }
    }

    private class ProbeTrackOutput : TrackOutput {
        var isVideo = false
        var accepted = false
        var declaredRate = Format.NO_VALUE.toFloat()
        val timestamps = mutableListOf<Long>()
        private val scratch = ByteArray(64 * 1024)

        override fun format(format: Format) {
            isVideo = MimeTypes.isVideo(format.sampleMimeType)
            declaredRate = format.frameRate
        }

        override fun sampleData(
            input: DataReader,
            length: Int,
            allowEndOfInput: Boolean,
            sampleDataPart: Int,
        ): Int {
            var total = 0
            while (total < length) {
                val read = input.read(scratch, 0, minOf(scratch.size, length - total))
                if (read == C.RESULT_END_OF_INPUT) {
                    return if (allowEndOfInput && total == 0) C.RESULT_END_OF_INPUT else total
                }
                total += read
            }
            return total
        }

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            data.skipBytes(length)
        }

        override fun sampleMetadata(
            timeUs: Long,
            flags: Int,
            size: Int,
            offset: Int,
            cryptoData: TrackOutput.CryptoData?,
        ) {
            if (accepted) timestamps += timeUs
        }
    }

    private class BoundedDataSource(
        private val delegate: DataSource,
    ) : DataSource by delegate {
        var bytesRead = 0L
            private set

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val remaining = MAX_BYTES - bytesRead
            if (remaining <= 0L) throw ProbeLimitExceeded()
            val read = delegate.read(buffer, offset, minOf(length, remaining.toInt()))
            if (read > 0) bytesRead += read
            return read
        }
    }

    private class ProbeLimitExceeded : IOException()

    private companion object {
        const val TAG = "FrameRate"
        const val SAMPLE_COUNT = 16
        const val TIMEOUT_MS = 2_500L
        const val MAX_BYTES = 2L * 1024 * 1024
        const val MAX_SEEKS = 1
    }
}

internal fun estimateFrameRateFromTimestamps(timestamps: List<Long>): Float? {
    val estimator = ContentFrameRateEstimator()
    timestamps.sorted().forEach { timestamp ->
        estimator.onNextFrame(timestamp)?.let { return it }
    }
    return null
}

private fun Float.isUsable(): Boolean = this > 0f && this != Format.NO_VALUE.toFloat()
