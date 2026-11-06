package com.shieldstream.tv.player

import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.ui.PlayerView

/** Allows display-mode changes that Media3's seamless-only strategy would reject. */
@OptIn(UnstableApi::class)
class ContentFrameRateMatcher(
    private val playerView: PlayerView,
    private val player: ExoPlayer,
    private val enabled: Boolean,
    private val initialFrameRate: Float? = null,
) {
    private val surfaceView = playerView.videoSurfaceView as? SurfaceView
    @Volatile private var attached = false
    @Volatile private var contentFrameRate = NO_FRAME_RATE
    private val estimator = ContentFrameRateEstimator()
    private var appliedSurface: Surface? = null
    private var appliedFrameRate = NO_FRAME_RATE

    private val analyticsListener = object : AnalyticsListener {
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            contentFrameRate = if (format.frameRate.isUsable()) {
                format.frameRate
            } else {
                initialFrameRate ?: NO_FRAME_RATE
            }
            estimator.reset()
            Log.i(TAG, "FRAME_RATE_MATCH format declared=${format.frameRate} effective=$contentFrameRate")
            if (contentFrameRate.isUsable()) apply() else clearFrameRate()
        }
    }

    private val frameMetadataListener = object : VideoFrameMetadataListener {
        override fun onVideoFrameAboutToBeRendered(
            presentationTimeUs: Long,
            releaseTimeNs: Long,
            format: Format,
            mediaFormat: MediaFormat?,
        ) {
            if (contentFrameRate.isUsable()) return
            val estimatedRate = estimator.onNextFrame(presentationTimeUs) ?: return
            contentFrameRate = estimatedRate
            Log.i(TAG, "FRAME_RATE_MATCH estimated content=$estimatedRate")
            apply()
        }
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = apply()

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = apply()

        override fun surfaceDestroyed(holder: SurfaceHolder) = resetAppliedSurface()
    }

    fun attach() {
        Log.i(
            TAG,
            "FRAME_RATE_MATCH attach enabled=$enabled surface=${playerView.videoSurfaceView?.javaClass?.simpleName} " +
                "format=${player.videoFormat?.frameRate} initial=$initialFrameRate",
        )
        if (attached || !enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.R || surfaceView == null) return
        attached = true
        contentFrameRate = initialFrameRate?.takeIf { it.isUsable() }
            ?: player.videoFormat?.frameRate
            ?: NO_FRAME_RATE
        player.addAnalyticsListener(analyticsListener)
        player.setVideoFrameMetadataListener(frameMetadataListener)
        surfaceView.holder.addCallback(surfaceCallback)
        if (contentFrameRate.isUsable()) apply()
    }

    fun detach() {
        if (!attached) return
        attached = false
        player.removeAnalyticsListener(analyticsListener)
        player.clearVideoFrameMetadataListener(frameMetadataListener)
        surfaceView?.holder?.removeCallback(surfaceCallback)
        clearFrameRate()
    }

    @Synchronized
    private fun apply() {
        if (!attached || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val rate = contentFrameRate
        if (!rate.isUsable()) return
        val surface = surfaceView?.holder?.surface ?: return
        if (!surface.isValid || (appliedSurface === surface && appliedFrameRate == rate)) return
        runCatching {
            surface.setFrameRate(
                rate,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ALWAYS,
            )
        }.onSuccess {
            appliedSurface = surface
            appliedFrameRate = rate
            Log.i(TAG, "FRAME_RATE_MATCH content=$rate strategy=ALWAYS")
        }.onFailure {
            Log.w(TAG, "FRAME_RATE_MATCH failed content=$rate error=${it.message}")
        }
    }

    @Synchronized
    private fun clearFrameRate() {
        val surface = appliedSurface ?: return
        appliedSurface = null
        appliedFrameRate = NO_FRAME_RATE
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !surface.isValid) return
        runCatching {
            surface.setFrameRate(
                0f,
                Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                Surface.CHANGE_FRAME_RATE_ALWAYS,
            )
        }.onSuccess {
            Log.i(TAG, "FRAME_RATE_MATCH cleared")
        }.onFailure {
            Log.w(TAG, "FRAME_RATE_MATCH clear failed error=${it.message}")
        }
    }

    @Synchronized
    private fun resetAppliedSurface() {
        appliedSurface = null
        appliedFrameRate = NO_FRAME_RATE
    }

    private fun Float.isUsable(): Boolean = this != NO_FRAME_RATE && this > 0f

    private companion object {
        const val TAG = "FrameRate"
        const val NO_FRAME_RATE = -1f
    }
}
