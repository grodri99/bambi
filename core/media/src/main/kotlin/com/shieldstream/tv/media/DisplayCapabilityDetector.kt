package com.shieldstream.tv.media

import android.content.Context
import android.os.Build
import android.view.Display
import android.view.WindowManager
import com.shieldstream.tv.model.HdrType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayCapabilityDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun supportedHdrTypes(): Set<HdrType> {
        val display = defaultDisplay() ?: return emptySet()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return emptySet()
        val types = display.hdrCapabilities?.supportedHdrTypes ?: return emptySet()
        return types.map { it.toHdrType() }.filterNotNull().toSet()
    }

    fun maxLuminance(): Float {
        val display = defaultDisplay() ?: return 0f
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return 0f
        return display.hdrCapabilities?.desiredMaxLuminance ?: 0f
    }

    fun supportsDolbyVision(): Boolean = supportedHdrTypes().contains(HdrType.DOLBY_VISION)

    private fun defaultDisplay(): Display? {
        @Suppress("DEPRECATION")
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return null
        @Suppress("DEPRECATION")
        return wm.defaultDisplay
    }

    private fun Int.toHdrType(): HdrType? = when (this) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> HdrType.DOLBY_VISION
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> HdrType.HDR10
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> HdrType.HDR10_PLUS
        Display.HdrCapabilities.HDR_TYPE_HLG -> HdrType.HLG
        else -> null
    }
}
