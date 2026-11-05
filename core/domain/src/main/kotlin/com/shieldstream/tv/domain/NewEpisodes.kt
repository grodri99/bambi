package com.shieldstream.tv.domain

import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.WatchProgress

/** Reglas puras para la fila de episodios recién emitidos de las series del perfil. */
object NewEpisodes {
    const val MAX_ITEMS = 15
    const val MAX_CANDIDATES = 15
    const val RECENT_DAYS = 30

    /** Series con historial o en Mi lista. El historial reciente tiene prioridad al limitar las consultas. */
    fun candidateIds(
        progress: Map<String, WatchProgress>,
        myList: List<MyListEntry>,
        filter: HomeFilter,
    ): List<String> {
        if (filter == HomeFilter.MOVIES) return emptyList()
        val watchedShows = progress.entries
            .filter { (key, _) -> key.startsWith("tv:") }
            .sortedByDescending { it.value.updatedAt }
            .mapNotNull { (key, _) -> key.split(':').getOrNull(1) }
        val listedShows = myList.filter { it.type == MediaType.TV }.map { it.id }
        return (watchedShows + listedShows).distinct().take(MAX_CANDIDATES)
    }

    /**
     * "Nuevo" acota la fila a lo emitido recientemente: sin ese límite, añadir una serie terminada hace años
     * a Mi lista la marcaría erróneamente como nueva. Las fechas ISO se comparan como texto (yyyy-MM-dd).
     */
    fun isNew(
        showId: String,
        episode: Episode,
        watchedKeys: Set<String>,
        cutoffDate: String,
        today: String,
    ): Boolean {
        val airDate = episode.airDate?.takeIf { ISO_DATE.matches(it) } ?: return false
        if (episode.season <= 0 || episode.number <= 0 || airDate !in cutoffDate..today) return false
        return WatchProgress.episodeKey(showId, episode.season, episode.number) !in watchedKeys
    }

    private val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
}
