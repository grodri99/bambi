package com.shieldstream.tv.domain

import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.PlaybackMedia
import com.shieldstream.tv.model.StreamSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Episodio que va después del que se reproduce (lo calcula la ficha). [label]: "T1:E2 · Capítulo 2". */
data class NextEpisode(val showId: String, val season: Int, val episode: Int, val label: String)

/** Episodio concreto que debe seleccionar la ficha al abrirse desde Nuevos episodios. */
data class EpisodeTarget(val season: Int, val number: Int)

/**
 * Estado de navegación compartido en memoria (selección actual).
 * Evita serializar objetos complejos como argumentos de navegación.
 */
class SelectedContentHolder {
    private val _selectedItem = MutableStateFlow<CatalogItem?>(null)
    val selectedItem: StateFlow<CatalogItem?> = _selectedItem.asStateFlow()

    /**
     * Cola de reproducción: el reproductor prueba las fuentes en orden hasta que una
     * esté en la caché de Real-Debrid (sin instantAvailability no hay otra forma de saberlo).
     */
    private val _queue = MutableStateFlow<List<StreamSource>>(emptyList())
    val queue: StateFlow<List<StreamSource>> = _queue.asStateFlow()

    /** Texto que muestra el reproductor mientras resuelve ("Dune: Parte dos", "Severance · T1:E3"). */
    private val _playLabel = MutableStateFlow("")
    val playLabel: StateFlow<String> = _playLabel.asStateFlow()

    /** Clave de progreso de lo que se va a reproducir (ver WatchProgress.movieKey / episodeKey). */
    private val _progressKey = MutableStateFlow<String?>(null)
    val progressKey: StateFlow<String?> = _progressKey.asStateFlow()

    /** true = "Desde el principio": ignorar el punto guardado. */
    private val _startFromBeginning = MutableStateFlow(false)
    val startFromBeginning: StateFlow<Boolean> = _startFromBeginning.asStateFlow()

    /** Qué se reproduce (título, IMDb, temporada/episodio): lo usa la búsqueda de subtítulos externos. */
    private val _media = MutableStateFlow<PlaybackMedia?>(null)
    val media: StateFlow<PlaybackMedia?> = _media.asStateFlow()

    /** Siguiente episodio de lo que se va a reproducir (null: película o último episodio). */
    private val _next = MutableStateFlow<NextEpisode?>(null)
    val next: StateFlow<NextEpisode?> = _next.asStateFlow()

    /** El reproductor pide el siguiente episodio al salir; la ficha lo recoge al volver a verse. */
    @Volatile private var nextRequested: NextEpisode? = null

    fun requestNext(next: NextEpisode) {
        nextRequested = next
    }

    fun consumeNextRequest(): NextEpisode? = nextRequested.also { nextRequested = null }

    /** El reproductor está en los créditos: la ficha (debajo) ya puede ir buscando las fuentes del siguiente. */
    private val _prefetch = MutableStateFlow<NextEpisode?>(null)
    val prefetch: StateFlow<NextEpisode?> = _prefetch.asStateFlow()

    fun prefetchNext(next: NextEpisode) {
        _prefetch.value = next
    }

    @Volatile private var episodeTarget: EpisodeTarget? = null

    fun selectItem(item: CatalogItem, target: EpisodeTarget? = null) {
        episodeTarget = target
        _selectedItem.value = item
        _queue.value = emptyList()
        _prefetch.value = null
    }

    fun consumeEpisodeTarget(): EpisodeTarget? = episodeTarget.also { episodeTarget = null }

    fun playQueue(
        sources: List<StreamSource>,
        label: String,
        progressKey: String? = null,
        fromStart: Boolean = false,
        media: PlaybackMedia? = null,
        next: NextEpisode? = null,
    ) {
        _next.value = next
        _media.value = media
        _queue.value = sources
        _playLabel.value = label
        _progressKey.value = progressKey
        _startFromBeginning.value = fromStart
    }
}
