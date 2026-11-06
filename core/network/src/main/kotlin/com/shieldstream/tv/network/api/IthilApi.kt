package com.shieldstream.tv.network.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Url

interface IthilApi {
    @GET
    suspend fun contents(@Url url: String): List<IthilContentDto>
}

@Serializable
data class IthilContentDto(
    val name: String,
    val sha: String,
    val size: Long,
    val download_url: String,
)
