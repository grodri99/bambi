package com.shieldstream.tv.model

enum class DeviceOptimization {
    AUTOMATIC,
    CUSTOM,
}

enum class VideoCodec(val mimeType: String, val label: String) {
    H264("video/avc", "H.264"),
    HEVC("video/hevc", "HEVC"),
    VP9("video/x-vnd.on2.vp9", "VP9"),
    AV1("video/av01", "AV1");

    companion object {
        fun fromMimeType(mimeType: String): VideoCodec? = entries.firstOrNull {
            it.mimeType.equals(mimeType, ignoreCase = true)
        }
    }
}

enum class MemoryProfile {
    FULL,
    CONSTRAINED,
}

data class DeviceCapabilities(
    val model: String,
    val heapLimitBytes: Long,
    val hardwareVideoCodecs: Set<VideoCodec>,
    val supportedHdr: Set<HdrType>,
)

data class PlaybackPolicy(
    val minBufferSeconds: Int,
    val maxBufferSeconds: Int,
    val targetBufferBytes: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val backBufferMs: Int,
    val seekThumbnails: Boolean,
    val memoryProfile: MemoryProfile,
) {
    companion object {
        private const val MIB = 1024 * 1024
        private const val CONSTRAINED_HEAP_LIMIT_BYTES = 256L * MIB

        fun automatic(heapLimitBytes: Long): PlaybackPolicy = if (heapLimitBytes <= CONSTRAINED_HEAP_LIMIT_BYTES) {
            PlaybackPolicy(
                minBufferSeconds = 30,
                maxBufferSeconds = 40,
                targetBufferBytes = 40 * MIB,
                bufferForPlaybackMs = 3_000,
                bufferForPlaybackAfterRebufferMs = 5_000,
                backBufferMs = 5_000,
                seekThumbnails = false,
                memoryProfile = MemoryProfile.CONSTRAINED,
            )
        } else {
            PlaybackPolicy(
                minBufferSeconds = 50,
                maxBufferSeconds = 80,
                targetBufferBytes = 112 * MIB,
                bufferForPlaybackMs = 5_000,
                bufferForPlaybackAfterRebufferMs = 10_000,
                backBufferMs = 15_000,
                seekThumbnails = true,
                memoryProfile = MemoryProfile.FULL,
            )
        }

        fun custom(maxBufferSeconds: Int, seekThumbnails: Boolean): PlaybackPolicy {
            val seconds = maxBufferSeconds.coerceIn(20, 100)
            val lowMemory = seconds <= 40
            return PlaybackPolicy(
                minBufferSeconds = minOf(if (lowMemory) 20 else 50, seconds),
                maxBufferSeconds = seconds,
                targetBufferBytes = (if (lowMemory) 64 else 112) * MIB,
                bufferForPlaybackMs = if (lowMemory) 3_000 else 5_000,
                bufferForPlaybackAfterRebufferMs = if (lowMemory) 5_000 else 10_000,
                backBufferMs = if (lowMemory) 5_000 else 15_000,
                seekThumbnails = seekThumbnails,
                memoryProfile = if (lowMemory) MemoryProfile.CONSTRAINED else MemoryProfile.FULL,
            )
        }
    }
}
