package com.shieldstream.tv.player

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.Display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max

internal data class DisplayModeSpec(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

internal fun selectMatchingDisplayMode(
    modes: List<DisplayModeSpec>,
    width: Int,
    height: Int,
    frameRate: Float,
): DisplayModeSpec? {
    if (frameRate <= 0f) return null
    val sameSize = modes.filter { it.width == width && it.height == height }
    val closest = sameSize.minByOrNull { abs(it.refreshRate - frameRate) } ?: return null
    val tolerance = max(MIN_REFRESH_TOLERANCE_HZ, frameRate * RELATIVE_REFRESH_TOLERANCE)
    return closest.takeIf { abs(it.refreshRate - frameRate) <= tolerance }
}

/** Selects the display mode before ExoPlayer starts decoding, then waits for it to stabilize. */
internal class EarlyDisplayModeRequest(
    private val activity: Activity,
    private val frameRate: Float,
) {
    private var originalModeId: Int? = null

    suspend fun applyAndWait() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val target = withContext(Dispatchers.Main) {
            val window = activity.window
            val display = window.decorView.display ?: return@withContext null
            val active = display.mode
            val selected = selectMatchingDisplayMode(
                modes = display.supportedModes.map { it.toSpec() },
                width = active.physicalWidth,
                height = active.physicalHeight,
                frameRate = frameRate,
            ) ?: return@withContext null
            if (originalModeId == null) originalModeId = active.modeId
            if (selected.modeId != active.modeId) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = selected.modeId }
            }
            Log.i(TAG, "WINDOW_MODE target=$frameRate selected=${selected.refreshRate} mode=${selected.modeId}")
            selected
        } ?: return

        var stablePolls = 0
        val settled = withTimeoutOrNull(SWITCH_TIMEOUT_MS) {
            while (stablePolls < REQUIRED_STABLE_POLLS) {
                val current = withContext(Dispatchers.Main) { activity.window.decorView.display?.mode }
                if (current != null && abs(current.refreshRate - target.refreshRate) <=
                    max(MIN_REFRESH_TOLERANCE_HZ, target.refreshRate * RELATIVE_REFRESH_TOLERANCE)
                ) {
                    stablePolls++
                } else {
                    stablePolls = 0
                }
                if (stablePolls < REQUIRED_STABLE_POLLS) delay(POLL_INTERVAL_MS)
            }
        } != null
        Log.i(TAG, "WINDOW_MODE ${if (settled) "settled" else "timeout"} target=${target.refreshRate}")
    }

    fun restore() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val original = originalModeId ?: return
        originalModeId = null
        val window = activity.window
        window.attributes = window.attributes.apply { preferredDisplayModeId = original }
        Log.i(TAG, "WINDOW_MODE restored mode=$original")
    }

    private fun Display.Mode.toSpec() = DisplayModeSpec(modeId, physicalWidth, physicalHeight, refreshRate)

    private companion object {
        const val TAG = "FrameRate"
        const val SWITCH_TIMEOUT_MS = 4_000L
        const val POLL_INTERVAL_MS = 60L
        const val REQUIRED_STABLE_POLLS = 2
    }
}

private const val MIN_REFRESH_TOLERANCE_HZ = 0.08f
private const val RELATIVE_REFRESH_TOLERANCE = 0.003f
