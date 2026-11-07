package com.shieldstream.tv.network.api

import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
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

    @GET("v4/user")
    suspend fun user(
        @Header("Authorization") authorization: String,
    ): AllDebridResponse<AllDebridUserDataDto>

    @FormUrlEncoded
    @POST("v4/magnet/upload")
    suspend fun uploadMagnet(
        @Header("Authorization") authorization: String,
        @Field("magnets[]") magnet: String,
    ): AllDebridResponse<AllDebridUploadDataDto>

    @FormUrlEncoded
    @POST("v4.1/magnet/status")
    suspend fun magnetStatus(
        @Header("Authorization") authorization: String,
        @Field("id") id: String,
    ): AllDebridResponse<AllDebridStatusDataDto>

    @FormUrlEncoded
    @POST("v4/magnet/files")
    suspend fun magnetFiles(
        @Header("Authorization") authorization: String,
        @Field("id[]") id: String,
    ): AllDebridResponse<AllDebridFilesDataDto>

    @FormUrlEncoded
    @POST("v4/magnet/delete")
    suspend fun deleteMagnet(
        @Header("Authorization") authorization: String,
        @Field("id") id: String,
    ): AllDebridResponse<AllDebridMessageDto>
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

@Serializable
data class AllDebridUserDataDto(
    val user: AllDebridUserDto,
)

@Serializable
data class AllDebridUserDto(
    val username: String = "",
    val isPremium: Boolean = false,
    val premiumUntil: Long = 0,
)

@Serializable
data class AllDebridUploadDataDto(
    val magnets: List<AllDebridUploadMagnetDto> = emptyList(),
)

@Serializable
data class AllDebridUploadMagnetDto(
    val id: Long? = null,
    val hash: String? = null,
    val ready: Boolean = false,
    val error: AllDebridErrorDto? = null,
)

@Serializable
data class AllDebridStatusDataDto(
    val magnets: List<AllDebridStatusDto> = emptyList(),
)

@Serializable
data class AllDebridStatusDto(
    val id: Long,
    val hash: String? = null,
    val status: String = "",
    val statusCode: Int = 0,
)

@Serializable
data class AllDebridFilesDataDto(
    val magnets: List<AllDebridMagnetFilesDto> = emptyList(),
)

@Serializable
data class AllDebridMagnetFilesDto(
    val files: List<AllDebridFileDto> = emptyList(),
    val error: AllDebridErrorDto? = null,
)

@Serializable
data class AllDebridFileDto(
    val n: String,
    val s: Long = 0,
    val l: String? = null,
    val e: List<AllDebridFileDto>? = null,
)

@Serializable
data class AllDebridMessageDto(
    val message: String = "",
)
