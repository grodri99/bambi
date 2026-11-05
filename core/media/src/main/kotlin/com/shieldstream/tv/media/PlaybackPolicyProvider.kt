package com.shieldstream.tv.media

import com.shieldstream.tv.model.DeviceCapabilities
import com.shieldstream.tv.model.DeviceOptimization
import com.shieldstream.tv.model.PlaybackPolicy
import javax.inject.Inject
import javax.inject.Singleton

/** Combina las capacidades detectadas con los ajustes manuales, sin sobrescribir estos últimos. */
@Singleton
class PlaybackPolicyProvider @Inject constructor(
    private val deviceCapabilities: DeviceCapabilityDetector,
) {
    val capabilities: DeviceCapabilities get() = deviceCapabilities.capabilities

    fun automatic(): PlaybackPolicy = PlaybackPolicy.automatic(capabilities.heapLimitBytes)

    fun resolve(
        optimization: DeviceOptimization,
        customBufferSeconds: Int,
        customSeekThumbnails: Boolean,
    ): PlaybackPolicy = when (optimization) {
        DeviceOptimization.AUTOMATIC -> automatic()
        DeviceOptimization.CUSTOM -> PlaybackPolicy.custom(customBufferSeconds, customSeekThumbnails)
    }
}
