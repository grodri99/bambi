package com.shieldstream.tv.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.PlaybackVideoInfo
import com.shieldstream.tv.model.VideoCodec

@OptIn(UnstableApi::class)
object VideoFormatInspector {
    fun inspect(format: Format, decoderName: String? = null): PlaybackVideoInfo {
        val codecs = format.codecs.orEmpty().lowercase()
        val dolbyVision = format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION
        val dolbyVisionProfile7 = dolbyVision && (codecs.startsWith("dvhe.07") || codecs.startsWith("dvh1.07"))
        val dolbyVisionAv1 = dolbyVision && codecs.startsWith("dav1")
        val pq = format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084
        val softwareWithoutHdr = decoderName?.contains("dav1d", ignoreCase = true) == true

        return PlaybackVideoInfo(
            resolutionLabel = resolutionLabel(format.width, format.height),
            hdrType = when {
                softwareWithoutHdr -> HdrType.SDR
                dolbyVisionProfile7 -> HdrType.HDR10
                dolbyVisionAv1 -> if (pq) HdrType.HDR10 else HdrType.SDR
                dolbyVision -> HdrType.DOLBY_VISION
                pq -> HdrType.HDR10
                format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> HdrType.HLG
                else -> HdrType.SDR
            },
            codec = when {
                dolbyVisionAv1 -> VideoCodec.AV1
                dolbyVision -> VideoCodec.HEVC
                else -> format.sampleMimeType?.let(VideoCodec::fromMimeType)
            },
            hardwareDecode = decoderName?.let { !isSoftwareDecoder(it) },
            decoderName = decoderName,
        )
    }

    private fun resolutionLabel(width: Int, height: Int): String? = when {
        width >= 3800 || height >= 2000 -> "4K"
        height >= 1440 -> "1440p"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height > 0 -> "${height}p"
        else -> null
    }

    private fun isSoftwareDecoder(name: String): Boolean {
        val decoder = name.lowercase()
        return decoder.contains("dav1d") || decoder.contains("gav1") ||
            SOFTWARE_DECODER_PREFIXES.any(decoder::startsWith)
    }

    private val SOFTWARE_DECODER_PREFIXES = listOf("omx.google.", "c2.android.", "c2.lib")
}
