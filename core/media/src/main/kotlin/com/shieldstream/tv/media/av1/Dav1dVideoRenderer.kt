package com.shieldstream.tv.media.av1

import android.os.Handler
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.CryptoConfig
import androidx.media3.decoder.Decoder
import androidx.media3.decoder.DecoderException
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.VideoDecoderOutputBuffer
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.video.DecoderVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.shieldstream.tv.media.isDolbyVisionAv1

@OptIn(UnstableApi::class)
class Dav1dVideoRenderer(
    allowedJoiningTimeMs: Long,
    eventHandler: Handler?,
    eventListener: VideoRendererEventListener?,
    maxDroppedFramesToNotify: Int,
) : DecoderVideoRenderer(allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify) {

    private var decoder: Dav1dDecoder? = null

    override fun getName(): String = "Dav1dVideoRenderer"

    override fun supportsFormat(format: Format): Int {
        val isAv1 = MimeTypes.VIDEO_AV1.equals(format.sampleMimeType, ignoreCase = true) || isDolbyVisionAv1(format)
        if (!isAv1 || !Dav1dLibrary.isAvailable) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        }
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_DRM)
        }
        return RendererCapabilities.create(
            C.FORMAT_HANDLED,
            RendererCapabilities.ADAPTIVE_SEAMLESS,
            RendererCapabilities.TUNNELING_NOT_SUPPORTED,
        )
    }

    override fun createDecoder(
        format: Format,
        cryptoConfig: CryptoConfig?,
    ): Decoder<DecoderInputBuffer, out VideoDecoderOutputBuffer, out DecoderException> {
        val inputSize = if (format.maxInputSize != Format.NO_VALUE) format.maxInputSize else DEFAULT_INPUT_BUFFER_SIZE
        return Dav1dDecoder(NUM_INPUT_BUFFERS, NUM_OUTPUT_BUFFERS, inputSize, threads = 0)
            .also { decoder = it }
    }

    override fun renderOutputBufferToSurface(outputBuffer: VideoDecoderOutputBuffer, surface: Surface) {
        val d = decoder ?: throw Dav1dDecoderException("Decoder no inicializado")
        d.renderToSurface(outputBuffer, surface)
        outputBuffer.release()
    }

    override fun setDecoderOutputMode(outputMode: Int) {
        decoder?.setOutputMode(outputMode)
    }

    override fun canReuseDecoder(decoderName: String, oldFormat: Format, newFormat: Format): DecoderReuseEvaluation =
        DecoderReuseEvaluation(
            decoderName,
            oldFormat,
            newFormat,
            DecoderReuseEvaluation.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION,
            /* discardReasons= */ 0,
        )

    private companion object {
        const val DEFAULT_INPUT_BUFFER_SIZE = 768 * 1024
        const val NUM_INPUT_BUFFERS = 8
        const val NUM_OUTPUT_BUFFERS = 8
    }
}
