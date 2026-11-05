package com.shieldstream.tv.network.api

import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.network.dto.TmdbExternalIdsDto
import com.shieldstream.tv.network.dto.TmdbKeywordDto
import com.shieldstream.tv.network.dto.TmdbMovieDetailDto
import com.shieldstream.tv.network.dto.TmdbMovieDto
import com.shieldstream.tv.network.dto.TmdbPaginated
import com.shieldstream.tv.network.dto.TmdbSearchResponse
import com.shieldstream.tv.network.dto.TmdbSearchResultDto
import com.shieldstream.tv.network.dto.TmdbSeasonDto
import com.shieldstream.tv.network.dto.TmdbTvDetailDto
import com.shieldstream.tv.network.dto.TmdbTvDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface TmdbApi {

    @GET("3/trending/all/day")
    suspend fun trendingAll(
        @Query("api_key") key: String,
        @Query("page") page: Int = 1,
    ): TmdbPaginated<TmdbSearchResultDto>

    @GET("3/trending/all/week")
    suspend fun trendingAllWeek(
        @Query("api_key") key: String,
        @Query("page") page: Int = 1,
    ): TmdbPaginated<TmdbSearchResultDto>

    @GET("3/discover/movie")
    suspend fun discoverMovieQuery(
        @Query("api_key") key: String,
        @QueryMap params: Map<String, String>,
    ): TmdbPaginated<TmdbMovieDto>

    @GET("3/discover/tv")
    suspend fun discoverTvQuery(
        @Query("api_key") key: String,
        @QueryMap params: Map<String, String>,
    ): TmdbPaginated<TmdbTvDto>

    @GET("3/search/keyword")
    suspend fun searchKeyword(
        @Query("api_key") key: String,
        @Query("query") query: String,
    ): TmdbPaginated<TmdbKeywordDto>

    @GET("3/trending/movie/week")
    suspend fun trendingMovies(
        @Query("api_key") key: String,
        @Query("page") page: Int = 1,
    ): TmdbPaginated<TmdbMovieDto>

    @GET("3/trending/tv/week")
    suspend fun trendingTv(
        @Query("api_key") key: String,
        @Query("page") page: Int = 1,
    ): TmdbPaginated<TmdbTvDto>

    @GET("3/movie/popular")
    suspend fun popularMovies(
        @Query("api_key") key: String,
        @Query("page") page: Int = 1,
    ): TmdbPaginated<TmdbMovieDto>

    @GET("3/tv/popular")
    suspend fun popularTv(
        @Query("api_key") key: String,
        @Query("page") page: Int = 1,
    ): TmdbPaginated<TmdbTvDto>

    @GET("3/movie/top_rated")
    suspend fun topRatedMovies(
        @Query("api_key") key: String,
        @Query("page") page: Int = 1,
    ): TmdbPaginated<TmdbMovieDto>

    @GET("3/tv/top_rated")
    suspend fun topRatedTv(
        @Query("api_key") key: String,
        @Query("page") page: Int = 1,
    ): TmdbPaginated<TmdbTvDto>

    @GET("3/movie/now_playing")
    suspend fun nowPlayingMovies(
        @Query("api_key") key: String,
        @Query("region") region: String = Lang.current.region,
    ): TmdbPaginated<TmdbMovieDto>

    @GET("3/discover/movie")
    suspend fun discoverMovies(
        @Query("api_key") key: String,
        @Query("with_genres") genres: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") minVotes: Int = 300,
    ): TmdbPaginated<TmdbMovieDto>

    @GET("3/discover/tv")
    suspend fun discoverTv(
        @Query("api_key") key: String,
        @Query("with_genres") genres: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") minVotes: Int = 100,
    ): TmdbPaginated<TmdbTvDto>

    @GET("3/search/multi")
    suspend fun search(
        @Query("api_key") key: String,
        @Query("query") query: String,
    ): TmdbSearchResponse

    @GET("3/movie/{id}")
    suspend fun movieDetails(
        @Path("id") id: Int,
        @Query("api_key") key: String,
        @Query("append_to_response") append: String = "images,translations",
        @Query("include_image_language") imageLanguages: String = if (Lang.isEnglish) "en,null" else "es,en,null",
    ): TmdbMovieDetailDto

    @GET("3/tv/{id}")
    suspend fun tvDetails(
        @Path("id") id: Int,
        @Query("api_key") key: String,
        @Query("append_to_response") append: String = "images,external_ids,translations",
        @Query("include_image_language") imageLanguages: String = if (Lang.isEnglish) "en,null" else "es,en,null",
    ): TmdbTvDetailDto

    @GET("3/tv/{id}/season/{season}")
    suspend fun tvSeason(
        @Path("id") id: Int,
        @Path("season") season: Int,
        @Query("api_key") key: String,
    ): TmdbSeasonDto

    @GET("3/movie/{id}/external_ids")
    suspend fun movieExternalIds(
        @Path("id") id: Int,
        @Query("api_key") key: String,
    ): TmdbExternalIdsDto

    @GET("3/tv/{id}/external_ids")
    suspend fun tvExternalIds(
        @Path("id") id: Int,
        @Query("api_key") key: String,
    ): TmdbExternalIdsDto
}
