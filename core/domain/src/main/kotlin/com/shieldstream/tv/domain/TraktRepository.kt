package com.shieldstream.tv.domain

import com.shieldstream.tv.model.MediaType

data class TraktCredentials(
    val clientId: String,
    val clientSecret: String = "",
    val redirectUri: String = "urn:ietf:wg:oauth:2.0:oob",
) {
    val isConfigured: Boolean get() = clientId.isNotBlank()
}

data class TraktDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
)

sealed interface TraktAuthPoll {
    data object Pending : TraktAuthPoll
    data object SlowDown : TraktAuthPoll
    data class Connected(val username: String) : TraktAuthPoll
    data object Expired : TraktAuthPoll
    data object Denied : TraktAuthPoll
    data class Failed(val message: String) : TraktAuthPoll
}

enum class ScrobbleAction { START, PAUSE, STOP }

sealed interface TraktRatingTarget {
    data class Movie(val tmdbId: String) : TraktRatingTarget
    data class Show(val tmdbId: String) : TraktRatingTarget
    data class Episode(val showTmdbId: String, val season: Int, val episode: Int) : TraktRatingTarget
}

interface TraktRepository {
    val isConfigured: Boolean
    val isConnected: Boolean

    suspend fun requestDeviceCode(): Result<TraktDeviceCode>

    suspend fun pollDeviceToken(profileId: String, code: TraktDeviceCode): TraktAuthPoll

    suspend fun disconnect(profileId: String)

    suspend fun recommendations(profileId: String, type: MediaType): List<String>

    suspend fun getRating(target: TraktRatingTarget): Int?

    suspend fun setRating(target: TraktRatingTarget, rating: Int?)

    fun syncActive(force: Boolean = false)

    fun scrobble(progressKey: String, fraction: Float, action: ScrobbleAction)

    fun setWatched(progressKeys: Collection<String>, watched: Boolean)
}
