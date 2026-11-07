package com.shieldstream.tv.watchnext

import android.content.Intent
import com.shieldstream.tv.domain.EpisodeTarget
import com.shieldstream.tv.model.MediaType

data class WatchNextRequest(
    val profileId: String,
    val type: MediaType,
    val mediaId: String,
    val target: EpisodeTarget?,
    val requestedAtNs: Long = System.nanoTime(),
)

internal fun parseWatchNextRequest(intent: Intent): WatchNextRequest? {
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    if (uri.scheme != "shieldstream" || uri.host != "resume") return null

    val path = uri.pathSegments
    if (path.size != 2) return null
    val type = when (path[0].lowercase()) {
        "movie" -> MediaType.MOVIE
        "tv" -> MediaType.TV
        else -> return null
    }
    val mediaId = path[1].takeIf { it.isNotBlank() } ?: return null
    val profileId = uri.getQueryParameter("profile")?.takeIf { it.isNotBlank() } ?: return null

    val season = uri.getQueryParameter("season")?.toIntOrNull()
    val episode = uri.getQueryParameter("episode")?.toIntOrNull()
    val target = when (type) {
        MediaType.MOVIE -> null
        MediaType.TV -> EpisodeTarget(
            season = season?.takeIf { it >= 0 } ?: return null,
            number = episode?.takeIf { it > 0 } ?: return null,
        )
    }

    return WatchNextRequest(profileId, type, mediaId, target)
}
