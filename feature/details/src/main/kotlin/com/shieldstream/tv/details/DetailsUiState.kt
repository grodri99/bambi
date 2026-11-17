package com.shieldstream.tv.details

import com.shieldstream.tv.model.CatalogDetails
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.SourceExclusions
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.WatchProgress

// Estado de la ficha que pinta DetailsScreen y lo que la marca como rota.

sealed interface SourcesState {
    data object Idle : SourcesState
    data object Loading : SourcesState
    data class Ready(
        val sources: List<StreamSource>,
        val unavailable: Map<String, String> = emptyMap(),
        val pending: Set<String> = emptySet(),
    ) : SourcesState
    data class Error(val message: String) : SourcesState
}

sealed interface EpisodesState {
    data object Loading : EpisodesState
    data class Ready(val episodes: List<Episode>) : EpisodesState
    data class Error(val message: String) : EpisodesState
}

sealed interface TraktRatingsState {
    data object Idle : TraktRatingsState
    data object Loading : TraktRatingsState
    data object NotConnected : TraktRatingsState
    data class Error(val message: String) : TraktRatingsState
    data class Ready(
        val overallRating: Int? = null,
        val episodeRating: Int? = null,
        val saving: Boolean = false,
        val error: String? = null,
    ) : TraktRatingsState
}

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Error(val message: String) : DetailsUiState
    data class Content(
        val details: CatalogDetails,
        val sources: SourcesState = SourcesState.Idle,
        val selectedSeason: Int? = null,
        val episodes: EpisodesState = EpisodesState.Loading,
        val selectedEpisode: Episode? = null,
        val pendingPlay: Boolean = false,
        val message: String? = null,
        val progress: Map<String, WatchProgress> = emptyMap(),
        val spanishFirst: Boolean = false,
        val spanishOnly: Boolean = false,
        val sourceExclusions: SourceExclusions = SourceExclusions(),
        /** Fuentes descartadas por el usuario, por id estable de fuente. */
        val brokenSources: Set<String> = emptySet(),
        /** Mostrar la fila de reparto y equipo (Ajustes). */
        val showCast: Boolean = true,
        val inMyList: Boolean = false,
        val following: Boolean = false,
        val ratings: TraktRatingsState = TraktRatingsState.Idle,
        /** Resto de películas de la saga (sin la actual), cuando la hay. */
        val collection: com.shieldstream.tv.model.CollectionDetails? = null,
    ) : DetailsUiState {
        val isSeries: Boolean get() = details.item.type == MediaType.TV

        fun progressOf(episode: Episode): WatchProgress? =
            progress[WatchProgress.episodeKey(details.item.id, episode.season, episode.number)]

        val mainProgress: WatchProgress?
            get() = if (isSeries) selectedEpisode?.let { progressOf(it) } else progress[WatchProgress.movieKey(details.item.id)]
    }
}

sealed interface DetailsEvent {
    data object OpenPlayer : DetailsEvent
}

/**
 * Clave con la que se marca una fuente rota: el hash del torrent, para que el mismo fichero se oculte venga del
 * buscador que venga. Las marcas antiguas usaban el id de la fuente y se siguen respetando.
 */
internal fun StreamSource.brokenKey(): String = infoHash.trim().lowercase().ifBlank { id }

internal fun StreamSource.isMarkedBroken(broken: Set<String>): Boolean = brokenKey() in broken || id in broken

internal fun DetailsUiState.Content.eligibleSources(
    sources: List<StreamSource>,
    includeExcluded: Boolean = false,
): List<StreamSource> = sources.filter {
    !it.isMarkedBroken(brokenSources) &&
        (includeExcluded || !sourceExclusions.excludes(it)) &&
        (!spanishOnly || it.advertisesSpanishAudio)
}
