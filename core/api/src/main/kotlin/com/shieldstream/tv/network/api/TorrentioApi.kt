package com.shieldstream.tv.network.api

import com.shieldstream.tv.network.dto.TorrentioCatalogResponse
import com.shieldstream.tv.network.dto.TorrentioStreamResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface TorrentioApi {

    @GET("stream/{type}/{id}.json")
    suspend fun streams(
        @Path("type") type: String,
        @Path("id") id: String,
    ): TorrentioStreamResponse

    /** JSON tal cual (buscadores propios: apibay, YTS). */
    @GET
    suspend fun raw(@retrofit2.http.Url url: String): okhttp3.ResponseBody

    @GET
    suspend fun page(@retrofit2.http.Url url: String, @retrofit2.http.Header("User-Agent") userAgent: String): okhttp3.ResponseBody

    @retrofit2.http.POST
    suspend fun postJson(@retrofit2.http.Url url: String, @retrofit2.http.Body body: okhttp3.RequestBody): okhttp3.ResponseBody

    @GET
    suspend fun streamsAt(@retrofit2.http.Url url: String): TorrentioStreamResponse

    @GET("{config}/stream/{type}/{id}.json")
    suspend fun streamsWithConfig(
        @Path("config", encoded = true) config: String,
        @Path("type") type: String,
        @Path("id") id: String,
    ): TorrentioStreamResponse

    @GET("catalog/{type}/top.json")
    suspend fun catalogTop(
        @Path("type") type: String,
    ): TorrentioCatalogResponse
}
