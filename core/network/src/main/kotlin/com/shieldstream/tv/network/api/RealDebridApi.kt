package com.shieldstream.tv.network.api

import com.shieldstream.tv.network.dto.RdAddMagnetDto
import com.shieldstream.tv.network.dto.RdTorrentDto
import com.shieldstream.tv.network.dto.RdTorrentInfoDto
import com.shieldstream.tv.network.dto.RdUnrestrictDto
import com.shieldstream.tv.network.dto.RdUserDto
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface RealDebridApi {

    @GET("rest/1.0/user")
    suspend fun user(@Header("Authorization") token: String): RdUserDto

    @GET("rest/1.0/torrents")
    suspend fun torrents(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int,
    ): Response<List<RdTorrentDto>>

    @FormUrlEncoded
    @POST("rest/1.0/torrents/addMagnet")
    suspend fun addMagnet(
        @Header("Authorization") token: String,
        @Field("magnet") magnet: String,
    ): RdAddMagnetDto

    @GET("rest/1.0/torrents/info/{id}")
    suspend fun torrentInfo(
        @Header("Authorization") token: String,
        @Path("id") id: String,
    ): RdTorrentInfoDto

    @FormUrlEncoded
    @POST("rest/1.0/torrents/selectFiles/{id}")
    suspend fun selectFiles(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Field("files") files: String,
    ): Response<Unit>

    @DELETE("rest/1.0/torrents/delete/{id}")
    suspend fun deleteTorrent(
        @Header("Authorization") token: String,
        @Path("id") id: String,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("rest/1.0/unrestrict/link")
    suspend fun unrestrict(
        @Header("Authorization") token: String,
        @Field("link") link: String,
    ): RdUnrestrictDto
}
