package com.shieldstream.tv.domain

import com.shieldstream.tv.model.CatalogDetails
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.FeaturedItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.ShowAiring

interface CatalogRepository {
    /** Filas del Inicio con hasta [maxItems] títulos cada una (se piden las páginas de TMDB necesarias). */
    suspend fun getHome(filter: HomeFilter, maxItems: Int = SettingsRepository.DEFAULT_HOME_ROW_ITEM_LIMIT): List<CatalogRow>

    suspend fun getFeatured(filter: HomeFilter): List<FeaturedItem>

    suspend fun getCategoryRows(category: Category, filter: HomeFilter): List<CatalogRow>

    suspend fun trending(): List<CatalogItem>

    suspend fun search(query: String): List<CatalogItem>

    suspend fun getDetails(item: CatalogItem): CatalogDetails

    suspend fun getItem(type: MediaType, id: String): CatalogItem?

    suspend fun getShowAiring(id: String): ShowAiring?

    suspend fun getEpisodes(item: CatalogItem, season: Int): List<Episode>

    fun getStreams(item: CatalogItem, episode: Episode? = null): kotlinx.coroutines.flow.Flow<StreamResults>
}

data class StreamResults(
    val sources: List<com.shieldstream.tv.model.StreamSource>,
    val unavailable: Map<String, String> = emptyMap(),
    val pending: Set<String> = emptySet(),
)
