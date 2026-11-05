package com.shieldstream.tv.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TmdbPaginated<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
)

@Serializable
data class TmdbMovieDto(
    val id: Int = 0,
    val title: String = "",
    val overview: String = "",
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String? = null,
    val vote_average: Double = 0.0,
)

@Serializable
data class TmdbTvDto(
    val id: Int = 0,
    val name: String = "",
    val overview: String = "",
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val first_air_date: String? = null,
    val vote_average: Double = 0.0,
)

@Serializable
data class TmdbSearchResponse(
    val results: List<TmdbSearchResultDto> = emptyList(),
)

@Serializable
data class TmdbSearchResultDto(
    val id: Int = 0,
    val media_type: String = "",
    val title: String? = null,
    val name: String? = null,
    val overview: String = "",
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val vote_average: Double = 0.0,
    val vote_count: Int = 0,
    /** Para el perfil infantil (búsqueda): animación 16, familia 10751, infantil (series) 10762. */
    val genre_ids: List<Int> = emptyList(),
)

@Serializable
data class TmdbKeywordDto(
    val id: Int = 0,
    val name: String = "",
)

/** Calificación por edades de películas (append_to_response=release_dates). */
@Serializable
data class TmdbReleaseDatesDto(val results: List<TmdbReleaseCountryDto> = emptyList())

@Serializable
data class TmdbReleaseCountryDto(
    val iso_3166_1: String = "",
    val release_dates: List<TmdbReleaseDateDto> = emptyList(),
)

@Serializable
data class TmdbReleaseDateDto(
    val certification: String = "",
    val type: Int = 0,
)

/** Calificación por edades de series (append_to_response=content_ratings). */
@Serializable
data class TmdbContentRatingsDto(val results: List<TmdbContentRatingDto> = emptyList())

@Serializable
data class TmdbContentRatingDto(
    val iso_3166_1: String = "",
    val rating: String = "",
)

@Serializable
data class TmdbExternalIdsDto(
    val imdb_id: String? = null,
)

@Serializable
data class TmdbGenreDto(
    val id: Int = 0,
    val name: String = "",
)

@Serializable
data class TmdbImageDto(
    val file_path: String = "",
    val iso_639_1: String? = null,
    val vote_average: Double = 0.0,
)

@Serializable
data class TmdbImagesDto(
    val logos: List<TmdbImageDto> = emptyList(),
)

@Serializable
data class TmdbMovieDetailDto(
    val id: Int = 0,
    val original_title: String? = null,
    val title: String = "",
    val overview: String = "",
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String? = null,
    val vote_average: Double = 0.0,
    val runtime: Int? = null,
    val genres: List<TmdbGenreDto> = emptyList(),
    val tagline: String? = null,
    val imdb_id: String? = null,
    val images: TmdbImagesDto? = null,
    val translations: TmdbTranslationsDto? = null,
    val release_dates: TmdbReleaseDatesDto? = null,
)

@Serializable
data class TmdbSeasonSummaryDto(
    val season_number: Int = 0,
    val name: String = "",
    val episode_count: Int = 0,
)

@Serializable
data class TmdbTvDetailDto(
    val id: Int = 0,
    val original_name: String? = null,
    val name: String = "",
    val overview: String = "",
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val first_air_date: String? = null,
    val vote_average: Double = 0.0,
    val episode_run_time: List<Int> = emptyList(),
    val genres: List<TmdbGenreDto> = emptyList(),
    val tagline: String? = null,
    val seasons: List<TmdbSeasonSummaryDto> = emptyList(),
    val external_ids: TmdbExternalIdsDto? = null,
    val images: TmdbImagesDto? = null,
    val translations: TmdbTranslationsDto? = null,
    val content_ratings: TmdbContentRatingsDto? = null,
    val number_of_seasons: Int = 0,
    /** "ja" + género Animación = anime: sus releases suelen ir con numeración absoluta ("One Piece - 1100"). */
    val original_language: String? = null,
    val last_episode_to_air: TmdbEpisodeDto? = null,
    val next_episode_to_air: TmdbEpisodeDto? = null,
)

@Serializable
data class TmdbEpisodeDto(
    val episode_number: Int = 0,
    val season_number: Int = 0,
    val name: String = "",
    val overview: String = "",
    val still_path: String? = null,
    val runtime: Int? = null,
    val air_date: String? = null,
)

@Serializable
data class TmdbSeasonDto(
    val season_number: Int = 0,
    val episodes: List<TmdbEpisodeDto> = emptyList(),
)

@Serializable
data class TmdbTranslationsDto(
    val translations: List<TmdbTranslationDto> = emptyList(),
)

@Serializable
data class TmdbTranslationDto(
    val iso_639_1: String = "",
    val data: TmdbTranslationDataDto? = null,
)

@Serializable
data class TmdbTranslationDataDto(
    val title: String? = null,
    val name: String? = null,
)
