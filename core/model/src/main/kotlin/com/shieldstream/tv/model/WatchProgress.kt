package com.shieldstream.tv.model

data class WatchProgress(
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val isWatched: Boolean
        get() = durationMs > 0 && fraction >= WATCHED_FRACTION

    val isResumable: Boolean
        get() = !isWatched && positionMs >= MIN_RESUME_MS

    val remainingMs: Long
        get() = (durationMs - positionMs).coerceAtLeast(0L)

    companion object {
        const val WATCHED_FRACTION = 0.93f
        const val MIN_RESUME_MS = 30_000L

        fun movieKey(movieId: String) = "movie:$movieId"

        fun episodeKey(showId: String, season: Int, episode: Int) = "tv:$showId:$season:$episode"

        fun showPrefix(showId: String) = "tv:$showId:"
    }
}
