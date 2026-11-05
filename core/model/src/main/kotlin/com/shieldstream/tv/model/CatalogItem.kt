package com.shieldstream.tv.model

/**
 * Elemento de catálogo (película o serie) con metadatos.
 * Las URLs de imagen vienen ya resueltas (TMDB image CDN).
 */
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
    /** Clave única entre películas y series (TMDB reutiliza ids numéricos entre ambos). */
    val key: String get() = "${type.name}_$id"
}

/**
 * Fila de carrusel mostrada en la home.
 */
data class CatalogRow(
    val id: String,
    val title: String,
    val items: List<CatalogItem>,
    val style: RowStyle = RowStyle.POSTER,
    /** Solo fila "Seguir viendo": progreso por [CatalogItem.key]. */
    val progress: Map<String, ContinueInfo> = emptyMap(),
    /** Solo fila "Nuevos episodios": último episodio emitido por [CatalogItem.key]. */
    val newEpisodes: Map<String, Episode> = emptyMap(),
)

/** Progreso que pinta una tarjeta de "Seguir viendo" (barra roja + "T1:E6 · 14 min"). */
data class ContinueInfo(
    val fraction: Float,
    val label: String,
)
