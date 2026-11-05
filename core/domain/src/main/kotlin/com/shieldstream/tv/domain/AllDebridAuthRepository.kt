package com.shieldstream.tv.domain

data class AllDebridPin(
    val pin: String,
    val check: String,
    val verificationUrl: String,
    val expiresInSeconds: Int,
)

sealed interface AllDebridAuthPoll {
    data object Pending : AllDebridAuthPoll
    data class Connected(val apiKey: String) : AllDebridAuthPoll
    data object Expired : AllDebridAuthPoll
    data class Failed(val message: String) : AllDebridAuthPoll
}

interface AllDebridAuthRepository {
    suspend fun requestPin(): Result<AllDebridPin>

    suspend fun pollPin(pin: AllDebridPin): AllDebridAuthPoll
}
