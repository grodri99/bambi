package com.shieldstream.tv.model

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
    val airDate: String? = null,
) {
    val code: String get() = tr("T$season:E$number", "S$season:E$number")
}

enum class HomeFilter { ALL, MOVIES, TV }

enum class RowStyle { POSTER, TOP10, CONTINUE, NEW_EPISODES }

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
