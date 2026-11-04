package com.shieldstream.tv.model

data class PlaybackBufferProfile(
    val maxBufferMs: Int,
    val minBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val backBufferMs: Int,
    val targetBufferBytes: Int,
) {
    companion object {
        fun forSeconds(requestedSeconds: Int): PlaybackBufferProfile {
            val lowMemory = requestedSeconds <= 40
            val maxBufferMs = requestedSeconds.coerceIn(20, 100) * 1000
            return PlaybackBufferProfile(
                maxBufferMs = maxBufferMs,
                minBufferMs = minOf(if (lowMemory) 20_000 else 50_000, maxBufferMs),
                bufferForPlaybackMs = if (lowMemory) 3_000 else 5_000,
                bufferForPlaybackAfterRebufferMs = if (lowMemory) 5_000 else 10_000,
                backBufferMs = if (lowMemory) 5_000 else 15_000,
                targetBufferBytes = (if (lowMemory) 64 else 112) * 1024 * 1024,
            )
        }
    }
}
