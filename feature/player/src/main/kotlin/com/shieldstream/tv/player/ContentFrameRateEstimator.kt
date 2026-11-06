package com.shieldstream.tv.player

import kotlin.math.abs

/** Estimates a fixed frame rate from presentation timestamps without trusting variable-rate video. */
internal class ContentFrameRateEstimator {
    private var previousTimestampUs = TIME_UNSET
    private var candidateDurationUs = TIME_UNSET
    private var matchingFrames = 0
    private var matchingDurationUs = 0L

    @Synchronized
    fun reset() {
        previousTimestampUs = TIME_UNSET
        resetCandidate()
    }

    @Synchronized
    fun onNextFrame(timestampUs: Long): Float? {
        if (previousTimestampUs == TIME_UNSET) {
            previousTimestampUs = timestampUs
            return null
        }
        val durationUs = timestampUs - previousTimestampUs
        previousTimestampUs = timestampUs
        if (durationUs <= 0L || durationUs > MAX_FRAME_DURATION_US) {
            resetCandidate()
            return null
        }
        if (candidateDurationUs == TIME_UNSET || abs(durationUs - candidateDurationUs) > MAX_MATCHING_DIFFERENCE_US) {
            candidateDurationUs = durationUs
            matchingFrames = 1
            matchingDurationUs = durationUs
        } else {
            matchingFrames++
            matchingDurationUs += durationUs
            candidateDurationUs = matchingDurationUs / matchingFrames
        }
        if (matchingFrames < MIN_MATCHING_FRAMES || matchingDurationUs < MIN_MATCHING_DURATION_US) return null
        return matchingFrames * 1_000_000f / matchingDurationUs
    }

    private fun resetCandidate() {
        candidateDurationUs = TIME_UNSET
        matchingFrames = 0
        matchingDurationUs = 0L
    }

    private companion object {
        const val TIME_UNSET = -1L
        const val MAX_FRAME_DURATION_US = 1_000_000L
        const val MAX_MATCHING_DIFFERENCE_US = 1_000L
        const val MIN_MATCHING_FRAMES = 10
        const val MIN_MATCHING_DURATION_US = 250_000L
    }
}
