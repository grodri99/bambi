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

/**
 * Contrato de catálogo (TMDB) + scraping de fuentes (Torrentio).
 */
interface CatalogRepository {
    /** Filas de la pantalla principal para el filtro dado. */
    suspend fun getHome(filter: HomeFilter): List<CatalogRow>

    /**
     * Portada grande del Inicio: hasta 5 destacados ("Recién llegada", "La más popular"…). Se eligen el
     * primer día que se abre la app en la semana y se mantienen toda la semana.
     */
    suspend fun getFeatured(filter: HomeFilter): List<FeaturedItem>

    /** Filas de la pantalla de una categoría (Dramas, Anime, Estados de ánimo…). */
    suspend fun getCategoryRows(category: Category, filter: HomeFilter): List<CatalogRow>

    /** Tendencias del día (películas y series), para la pantalla de búsqueda vacía. */
    suspend fun trending(): List<CatalogItem>

    /** Búsqueda de títulos (solo películas y series). */
    suspend fun search(query: String): List<CatalogItem>

    /** Ficha completa: logo, duración, géneros, temporadas e imdbId. */
    suspend fun getDetails(item: CatalogItem): CatalogDetails

    /** Título por id de TMDB (fila "Seguir viendo"). Cacheado en memoria; null si el id no es válido. */
    suspend fun getItem(type: MediaType, id: String): CatalogItem?

    /** Último episodio emitido y próximo anunciado de una serie. Cacheado durante unas horas. */
    suspend fun getShowAiring(id: String): ShowAiring?

    /** Episodios de una temporada. */
    suspend fun getEpisodes(item: CatalogItem, season: Int): List<Episode>

    /**
     * Fuentes de streaming. Para series [episode] es obligatorio (Torrentio pide tt…:T:E).
     * Lanza excepción si Torrentio falla, para poder mostrar el motivo en pantalla.
     */
    /** Va emitiendo según responde cada buscador (el más rápido sale primero); termina cuando han acabado todos. */
    fun getStreams(item: CatalogItem, episode: Episode? = null): kotlinx.coroutines.flow.Flow<StreamResults>
}

/** Fuentes de todos los buscadores + los que no han dado nada (nombre → "no responde" / "sin fuentes"). */
data class StreamResults(
    val sources: List<com.shieldstream.tv.model.StreamSource>,
    val unavailable: Map<String, String> = emptyMap(),
    /** Buscadores que aún no han respondido. */
    val pending: Set<String> = emptySet(),
)
