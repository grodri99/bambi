package com.shieldstream.tv.media

import android.media.MediaCodecList
import android.os.Build
import com.shieldstream.tv.model.DeviceCapabilities
import com.shieldstream.tv.model.VideoCodec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceCapabilityDetector @Inject constructor(
    private val displayCapabilities: DisplayCapabilityDetector,
) {
    private val staticCapabilities by lazy {
        DeviceCapabilities(
            model = Build.MODEL.ifBlank { "Android TV" },
            heapLimitBytes = Runtime.getRuntime().maxMemory(),
            hardwareVideoCodecs = hardwareVideoCodecs(),
            supportedHdr = emptySet(),
        )
    }

    val capabilities: DeviceCapabilities
        get() = staticCapabilities.copy(supportedHdr = displayCapabilities.supportedHdrTypes())

    private fun hardwareVideoCodecs(): Set<VideoCodec> = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filterNot { it.isEncoder }
            .filterNot(::isSoftwareDecoder)
            .flatMap { codec -> runCatching { codec.supportedTypes.asList() }.getOrDefault(emptyList()) }
            .mapNotNull(VideoCodec::fromMimeType)
            .toSet()
    }.getOrDefault(emptySet())

    private fun isSoftwareDecoder(codec: android.media.MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return !codec.isHardwareAccelerated
        val name = codec.name.lowercase()
        return SOFTWARE_DECODER_PREFIXES.any(name::startsWith)
    }

    private companion object {
        val SOFTWARE_DECODER_PREFIXES = listOf("omx.google.", "c2.android.", "c2.dav1d.", "c2.lib")
    }
}
