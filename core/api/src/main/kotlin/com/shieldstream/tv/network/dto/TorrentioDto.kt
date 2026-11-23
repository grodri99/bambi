package com.shieldstream.tv.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TorrentioStreamResponse(
    val streams: List<TorrentioStreamDto> = emptyList(),
)

@Serializable
data class TorrentioStreamDto(
    val name: String = "",
    val title: String = "",
    val description: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val behaviorHints: TorrentioBehaviorHintsDto? = null,
    val availability: Int? = null,
    val url: String? = null,
)

@Serializable
data class TorrentioBehaviorHintsDto(
    val filename: String? = null,
    val bingeGroup: String? = null,
)

@Serializable
data class TorrentioCatalogResponse(
    val metas: List<StremioMetaDto> = emptyList(),
)

@Serializable
data class StremioMetaDto(
    val id: String = "",
    val type: String = "",
    val name: String = "",
    val poster: String? = null,
    val description: String? = null,
    val background: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
)
