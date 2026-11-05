package com.shieldstream.tv.model

/** Ficha completa de un título para la pantalla de detalle. */
data class CatalogDetails(
    val item: CatalogItem,
    val originalTitle: String? = null,
    val logoUrl: String? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val tagline: String? = null,
    val seasons: List<Season> = emptyList(),
    /** Último episodio emitido y próximo anunciado (solo series). */
    val latestEpisode: Episode? = null,
    val nextEpisode: Episode? = null,
)

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
    /** Fecha de emisión ISO (yyyy-MM-dd), tal como la publica TMDB. */
    val airDate: String? = null,
) {
    val code: String get() = tr("T$season:E$number", "S$season:E$number")
}

/** Filtro de la Home: todo, solo películas o solo series. */
enum class HomeFilter { ALL, MOVIES, TV }

/** Estilo visual de una fila de la Home. */
enum class RowStyle { POSTER, TOP10, CONTINUE, NEW_EPISODES }

/** Estado de emisión ligero de una serie, sin logos ni traducciones. */
data class ShowAiring(
    val item: CatalogItem,
    val latestEpisode: Episode? = null,
    val nextEpisode: Episode? = null,
)
