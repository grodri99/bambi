package com.shieldstream.tv.model

object DeviceProfile {
    private const val NOKIA_8010 = "Nokia_Streaming_Box_8010"

    fun isLowPowerBox(model: String): Boolean =
        model.replace(' ', '_').equals(NOKIA_8010, ignoreCase = true)

    fun lightweightUiDefault(model: String): Boolean = isLowPowerBox(model)

    fun defaultBufferSeconds(model: String): Int = if (isLowPowerBox(model)) 40 else 80

    fun imageMemoryCacheBytes(model: String): Int =
        (if (isLowPowerBox(model)) 24 else 64) * 1024 * 1024

    fun imageDiskCacheBytes(model: String): Long =
        (if (isLowPowerBox(model)) 150L else 512L) * 1024 * 1024
}
