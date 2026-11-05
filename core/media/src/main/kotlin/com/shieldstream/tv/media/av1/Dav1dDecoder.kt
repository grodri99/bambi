package com.shieldstream.tv.media.av1

import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderException
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.VideoDecoderOutputBuffer
import java.nio.ByteBuffer

internal object Dav1dLibrary {
    val isAvailable: Boolean by lazy {
        runCatching { System.loadLibrary("dav1dJNI") }
            .onFailure { Log.w("Dav1d", "libdav1dJNI no disponible: ${it.message}") }
            .isSuccess
    }
}

class Dav1dDecoderException(message: String, cause: Throwable? = null) : DecoderException(message, cause)

@Suppress("UNCHECKED_CAST")
@OptIn(UnstableApi::class)
internal class Dav1dDecoder(
    numInputBuffers: Int,
    numOutputBuffers: Int,
    initialInputBufferSize: Int,
    threads: Int,
) : SimpleDecoder<DecoderInputBuffer, VideoDecoderOutputBuffer, Dav1dDecoderException>(
    arrayOfNulls<DecoderInputBuffer>(numInputBuffers) as Array<DecoderInputBuffer>,
    arrayOfNulls<VideoDecoderOutputBuffer>(numOutputBuffers) as Array<VideoDecoderOutputBuffer>,
) {
    private val nativeLock = Any()
    private val context: Long
    private var closed = false

    @Volatile
    private var outputMode: Int = C.VIDEO_OUTPUT_MODE_NONE

    private val frameInfo = LongArray(4)

    init {
        if (!Dav1dLibrary.isAvailable) throw Dav1dDecoderException("libdav1dJNI no cargada")
        context = nativeOpen(threads, MAX_FRAME_DELAY)
        if (context == 0L) throw Dav1dDecoderException("dav1d_open falló")
        setInitialInputBufferSize(initialInputBufferSize)
    }

    override fun getName(): String = "dav1d"

    override fun createInputBuffer(): DecoderInputBuffer =
        DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)

    override fun createOutputBuffer(): VideoDecoderOutputBuffer =
        VideoDecoderOutputBuffer { releaseOutputBuffer(it) }.apply { decoderPrivate = NO_SLOT }

    override fun decode(
        inputBuffer: DecoderInputBuffer,
        outputBuffer: VideoDecoderOutputBuffer,
        reset: Boolean,
    ): Dav1dDecoderException? {
        if (reset) nativeFlush(context)
        outputBuffer.decoderPrivate = NO_SLOT
        val data: ByteBuffer = inputBuffer.data ?: return Dav1dDecoderException("Paquete vacío")
        val sent = nativeDecode(context, data, data.limit(), inputBuffer.timeUs)
        if (sent < 0) return Dav1dDecoderException("dav1d_send_data: error $sent")

        val got = nativeDequeue(context, frameInfo)
        if (got < 0) return Dav1dDecoderException("dav1d_get_picture: error $got")
        if (got == 0) {
            outputBuffer.shouldBeSkipped = true
            return null
        }
        val slot = frameInfo[0].toInt()
        val timeUs = frameInfo[1]
        if (!isAtLeastOutputStartTimeUs(timeUs)) {
            nativeRelease(context, slot)
            outputBuffer.shouldBeSkipped = true
            return null
        }
        outputBuffer.shouldBeSkipped = false
        outputBuffer.init(timeUs, outputMode, /* supplementalData= */ null)
        outputBuffer.initForPrivateFrame(frameInfo[2].toInt(), frameInfo[3].toInt())
        outputBuffer.decoderPrivate = slot
        outputBuffer.format = inputBuffer.format
        return null
    }

    override fun createUnexpectedDecodeException(error: Throwable): Dav1dDecoderException =
        Dav1dDecoderException("Error inesperado en dav1d", error)

    override fun releaseOutputBuffer(outputBuffer: VideoDecoderOutputBuffer) {
        synchronized(nativeLock) {
            if (outputBuffer.decoderPrivate != NO_SLOT && !closed) {
                nativeRelease(context, outputBuffer.decoderPrivate)
            }
            outputBuffer.decoderPrivate = NO_SLOT
        }
        super.releaseOutputBuffer(outputBuffer)
    }

    override fun release() {
        super.release()
        synchronized(nativeLock) {
            if (!closed) nativeClose(context)
            closed = true
        }
    }

    fun setOutputMode(outputMode: Int) {
        this.outputMode = outputMode
    }

    fun renderToSurface(outputBuffer: VideoDecoderOutputBuffer, surface: Surface) {
        if (outputBuffer.mode != C.VIDEO_OUTPUT_MODE_SURFACE_YUV) {
            throw Dav1dDecoderException("Modo de salida no soportado: ${outputBuffer.mode}")
        }
        val result = synchronized(nativeLock) {
            if (closed) 0 else nativeRender(context, outputBuffer.decoderPrivate, surface)
        }
        if (result < 0) throw Dav1dDecoderException("Error al pintar el fotograma ($result)")
    }

    private external fun nativeOpen(threads: Int, maxFrameDelay: Int): Long
    private external fun nativeClose(context: Long)
    private external fun nativeFlush(context: Long)
    private external fun nativeDecode(context: Long, data: ByteBuffer, size: Int, timeUs: Long): Int
    private external fun nativeDequeue(context: Long, info: LongArray): Int
    private external fun nativeRelease(context: Long, slot: Int)
    private external fun nativeRender(context: Long, slot: Int, surface: Surface): Int

    private companion object {
        const val NO_SLOT = -1
        const val MAX_FRAME_DELAY = 0
    }
}
