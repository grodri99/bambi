package com.shieldstream.tv.domain

data class TimeRange(val startMs: Long, val endMs: Long) {
    operator fun contains(positionMs: Long): Boolean = positionMs in startMs until endMs
}

data class SkipSegments(
    val intro: TimeRange? = null,
    val recap: TimeRange? = null,
    val outro: TimeRange? = null,
    val postCredits: TimeRange? = null,
) {
    companion object {
        val NONE = SkipSegments()
    }
}

interface SkipSegmentsRepository {
    suspend fun episode(imdbId: String, season: Int, episode: Int): SkipSegments
}
