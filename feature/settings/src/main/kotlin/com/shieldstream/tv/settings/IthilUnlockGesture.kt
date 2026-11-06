package com.shieldstream.tv.settings

internal class IthilUnlockGesture {
    private var taps = 0
    private var lastTapAt: Long? = null

    fun registerTap(now: Long): Boolean {
        val previous = lastTapAt
        if (previous == null || now < previous || now - previous > WINDOW_MS) taps = 0
        lastTapAt = now
        taps++
        if (taps < REQUIRED_TAPS) return false
        taps = 0
        lastTapAt = null
        return true
    }

    private companion object {
        const val REQUIRED_TAPS = 3
        const val WINDOW_MS = 3_000L
    }
}
