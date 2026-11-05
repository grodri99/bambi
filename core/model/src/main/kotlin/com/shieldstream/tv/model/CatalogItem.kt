package com.shieldstream.tv.model

data class CatalogItem(
    val id: String,
    val imdbId: String?,
    val type: MediaType,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val rating: Float?,
    val genres: List<String> = emptyList(),
) {
    val key: String get() = "${type.name}_$id"
}

data class CatalogRow(
    val id: String,
    val title: String,
    val items: List<CatalogItem>,
    val style: RowStyle = RowStyle.POSTER,
    val progress: Map<String, ContinueInfo> = emptyMap(),
    val newEpisodes: Map<String, Episode> = emptyMap(),
    val newEpisodeCounts: Map<String, Int> = emptyMap(),
    val upNext: Map<String, UpNextInfo> = emptyMap(),
)

data class UpNextInfo(
    val episode: Episode,
    val lastWatched: Long,
    val isNewEpisode: Boolean,
    val isNewSeason: Boolean,
)

data class ContinueInfo(
    val fraction: Float,
    val label: String,
    val season: Int? = null,
    val episode: Int? = null,
)
