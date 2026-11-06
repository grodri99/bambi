package com.shieldstream.tv.network.api

import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AllDebridApi {
    @GET("v4.1/pin/get")
    suspend fun getPin(): AllDebridResponse<AllDebridPinDto>

    @FormUrlEncoded
    @POST("v4/pin/check")
    suspend fun checkPin(
        @Field("pin") pin: String,
        @Field("check") check: String,
    ): AllDebridResponse<AllDebridPinCheckDto>

    @GET("v4/link/unlock")
    suspend fun unlock(
        @Query("agent") agent: String,
        @Query("apikey") apiKey: String,
        @Query("link") link: String,
    ): AllDebridResponse<AllDebridUnlockDto>
}

@Serializable
data class AllDebridResponse<T>(
    val status: String,
    val data: T? = null,
    val error: AllDebridErrorDto? = null,
)

@Serializable
data class AllDebridErrorDto(
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class AllDebridPinDto(
    val pin: String,
    val check: String,
    val expires_in: Int,
    val user_url: String,
)

@Serializable
data class AllDebridPinCheckDto(
    val activated: Boolean,
    val expires_in: Int,
    val apikey: String? = null,
)

@Serializable
data class AllDebridUnlockDto(
    val link: String,
)
