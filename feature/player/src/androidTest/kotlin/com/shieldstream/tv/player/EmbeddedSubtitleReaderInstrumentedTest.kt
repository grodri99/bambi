package com.shieldstream.tv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class EmbeddedSubtitleReaderInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var server: MockWebServer

    @After
    fun tearDown() {
        if (::server.isInitialized) server.shutdown()
    }

    @Test
    fun mkvReadsEmbeddedTextReference() = runBlocking {
        serveAsset("reference.mkv")
        val result = read("reference.mkv")

        val success = result as? ReferenceReadResult.Success
            ?: error("Expected MKV reference, got $result")
        assertTrue(success.reference.intervals.size >= SubtitleSync.MIN_REFERENCE_CUES)
        assertTrue(success.reference.bytesRead > 0)
        assertTrue(success.reference.track.isNotBlank())
    }

    @Test
    fun mp4ReadsEmbeddedTx3gReference() = runBlocking {
        serveAsset("reference.mp4")
        val result = read("reference.mp4")

        val success = result as? ReferenceReadResult.Success
            ?: error("Expected MP4 reference, got $result")
        assertTrue(success.reference.intervals.size >= SubtitleSync.MIN_REFERENCE_CUES)
        assertTrue(success.reference.bytesRead > 0)
        assertTrue(success.reference.track.isNotBlank())
    }

    @Test
    fun mp4WithoutTextReturnsPermanentFailureBeforeTimeout() = runBlocking {
        serveAsset("no-subtitles.mp4")
        val result = read("no-subtitles.mp4")

        val failure = (result as? ReferenceReadResult.Failure)?.failure
            ?: error("Expected no-track failure, got $result")
        assertEquals(ReferenceFailureReason.NO_TEXT_TRACK, failure.reason)
        assertFalse(failure.retryable)
        assertTrue("Took ${failure.elapsedMs} ms", failure.elapsedMs < 5_000)
    }

    @Test
    fun unsupportedContainerIsPermanentTypedFailure() = runBlocking {
        serveBytes("invalid.bin", byteArrayOf(1, 2, 3, 4))
        val result = read("invalid.bin")

        val failure = (result as? ReferenceReadResult.Failure)?.failure
            ?: error("Expected typed failure, got $result")
        assertEquals(ReferenceFailureReason.UNSUPPORTED_CONTAINER, failure.reason)
        assertFalse(failure.retryable)
    }

    private fun read(name: String): ReferenceReadResult {
        val reader = Media3EmbeddedReferenceReader(DefaultHttpDataSource.Factory())
        return runBlocking { reader.read(server.url("/$name").toString(), positionMs = 0) }
    }

    private fun serveAsset(name: String) {
        val bytes = context.assets.open(name).use { it.readBytes() }
        serveBytes(name, bytes)
    }

    private fun serveBytes(name: String, bytes: ByteArray) {
        server = MockWebServer()
        server.dispatcher = RangeDispatcher(bytes)
        server.start()
        server.url("/$name")
    }
}

private class RangeDispatcher(private val bytes: ByteArray) : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val range = request.getHeader("Range")
        if (range == null) {
            return MockResponse()
                .setHeader("Accept-Ranges", "bytes")
                .setBody(Buffer().write(bytes))
        }
        val match = Regex("bytes=(\\d+)-(\\d*)").find(range)
            ?: return MockResponse().setResponseCode(400)
        val start = match.groupValues[1].toInt().coerceAtMost(bytes.size)
        val end = match.groupValues[2].ifEmpty { (bytes.size - 1).toString() }.toInt()
            .coerceAtMost(bytes.size - 1)
        if (start > end) return MockResponse().setResponseCode(416)
        return MockResponse()
            .setResponseCode(206)
            .setHeader("Accept-Ranges", "bytes")
            .setHeader("Content-Range", "bytes $start-$end/${bytes.size}")
            .setBody(Buffer().write(bytes, start, end - start + 1))
    }
}
