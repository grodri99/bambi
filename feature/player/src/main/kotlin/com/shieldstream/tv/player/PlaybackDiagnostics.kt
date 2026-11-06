package com.shieldstream.tv.player

import android.os.SystemClock
import android.util.Log

/** Per-player timing logs used to distinguish startup drops from ongoing playback problems. */
internal class PlaybackDiagnostics(
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val startedAtMs = now()
    private var bufferingStartedAtMs = TIME_UNSET
    private var reachedReady = false
    private var rebufferCount = 0

    fun onBuffering(positionMs: Long) {
        if (bufferingStartedAtMs != TIME_UNSET) return
        bufferingStartedAtMs = now()
        Log.i(
            TAG,
            "${if (reachedReady) "REBUFFER" else "STARTUP"}_BUFFERING_START " +
                "positionMs=$positionMs playbackMs=${elapsedMs()}",
        )
    }

    fun onReady(positionMs: Long) {
        val bufferingDurationMs = bufferingStartedAtMs
            .takeIf { it != TIME_UNSET }
            ?.let { now() - it }
        if (reachedReady && bufferingDurationMs != null) {
            rebufferCount++
            Log.i(
                TAG,
                "REBUFFER_END count=$rebufferCount durationMs=$bufferingDurationMs " +
                    "positionMs=$positionMs playbackMs=${elapsedMs()}",
            )
        } else if (!reachedReady) {
            Log.i(TAG, "STARTUP_READY playbackMs=${elapsedMs()}")
        }
        bufferingStartedAtMs = TIME_UNSET
        reachedReady = true
    }

    fun onDroppedFrames(positionMs: Long, droppedFrames: Int, windowMs: Long) {
        Log.w(
            TAG,
            "VIDEO_DROPPED count=$droppedFrames windowMs=$windowMs " +
                "positionMs=$positionMs playbackMs=${elapsedMs()}",
        )
    }

    private fun elapsedMs() = now() - startedAtMs

    private companion object {
        const val TAG = "PlayerVM"
        const val TIME_UNSET = -1L
    }
}
