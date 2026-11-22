package com.shieldstream.tv.model

import java.util.Locale

data class CatalogDetails(
    val item: CatalogItem,
    val originalTitle: String? = null,
    val logoUrl: String? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val tagline: String? = null,
    val seasons: List<Season> = emptyList(),
    val latestEpisode: Episode? = null,
    val nextEpisode: Episode? = null,
    /** Reparto principal, en el orden de TMDB. */
    val cast: List<Person> = emptyList(),
    /** Dirección (películas) o creadores (series). */
    val directors: List<Person> = emptyList(),
    /** Vídeo de YouTube elegido como tráiler (ver [Trailers.pick]). */
    val trailerKey: String? = null,
    /** Todos los tráileres por orden de preferencia (el primero es [trailerKey]). */
    val trailerKeys: List<String> = emptyList(),
    /** Saga a la que pertenece la película, si TMDB la agrupa en una colección. */
    val collection: CollectionRef? = null,
)

data class Person(
    val id: Int,
    val name: String,
    val role: String? = null,
    val photoUrl: String? = null,
)

data class CollectionRef(val id: Int, val name: String)

data class CollectionDetails(
    val id: Int,
    val name: String,
    val overview: String,
    val backdropUrl: String?,
    /** Películas de la saga por orden de estreno. */
    val parts: List<CatalogItem>,
)

data class PersonDetails(
    val person: Person,
    val biography: String,
    val department: String?,
    val birthday: String?,
    val placeOfBirth: String?,
    /** Lo más conocido primero; sin duplicados. */
    val knownFor: List<CatalogItem>,
)

data class VideoInfo(
    val key: String,
    val site: String,
    val type: String,
    val official: Boolean,
    val language: String?,
)

object Trailers {
    private val TYPE_ORDER = listOf("trailer", "teaser")

    /** Comparación sin distinguir mayúsculas ni variantes regionales (`es-ES` → `es`). */
    private fun String?.normalized(): String? = this?.lowercase(Locale.ROOT)?.substringBefore('-')

    /**
     * Elige el tráiler a enseñar: solo YouTube; primero en el idioma de la interfaz, luego en inglés; dentro de eso,
     * tráileres antes que teasers y los oficiales antes que los demás.
     */
    fun pick(videos: List<VideoInfo>, language: String): String? = rank(videos, language).firstOrNull()

    /**
     * Todos los candidatos en ese orden: algunos tráileres no dejan verse incrustados (lo decide quien los sube) y
     * entonces se prueba el siguiente.
     */
    fun rank(videos: List<VideoInfo>, language: String): List<String> {
        val appLanguage = language.normalized()
        return videos
            .mapNotNull { video ->
                val type = video.type.normalized()?.let { TYPE_ORDER.indexOf(it) }?.takeIf { it >= 0 } ?: return@mapNotNull null
                if (!video.site.equals("YouTube", ignoreCase = true) || video.key.isBlank()) return@mapNotNull null
                val videoLanguage = video.language.normalized()
                val languageRank = when {
                    videoLanguage == appLanguage -> 0
                    videoLanguage == "en" -> 1
                    else -> 2
                }
                Ranked(video.key, languageRank, type, if (video.official) 0 else 1)
            }
            .sortedWith(compareBy<Ranked> { it.language }.thenBy { it.type }.thenBy { it.official })
            .map { it.key }
            .distinct()
    }

    private data class Ranked(val key: String, val language: Int, val type: Int, val official: Int)
}

data class Season(
    val number: Int,
    val name: String,
    val episodeCount: Int,
)

data class Episode(
    val season: Int,
    val number: Int,
    val title: String,
    val overview: String,
    val stillUrl: String?,
    val runtimeMinutes: Int?,
    val airDate: String? = null,
)

enum class HomeFilter { ALL, MOVIES, TV }

enum class RowStyle { POSTER, TOP10, CONTINUE, NEW_EPISODES, UP_NEXT, MY_LIST }

data class ShowAiring(
    val item: CatalogItem,
    val latestEpisode: Episode? = null,
    val nextEpisode: Episode? = null,
    val seasons: List<Season> = emptyList(),
)

data class ShowEpisodeStatus(
    val item: CatalogItem,
    val availableEpisodes: List<Episode> = emptyList(),
    val upcomingEpisode: Episode? = null,
)
