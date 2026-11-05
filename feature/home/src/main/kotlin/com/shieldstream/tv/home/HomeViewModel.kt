package com.shieldstream.tv.home

import com.shieldstream.tv.model.tr
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.NewEpisodes
import com.shieldstream.tv.domain.ContinueEntry
import com.shieldstream.tv.domain.ContinueWatching
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.ContinueInfo
import com.shieldstream.tv.model.FeaturedItem
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.RowStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Content(val rows: List<CatalogRow>, val featured: List<FeaturedItem> = emptyList()) : HomeUiState
}

/** Solo estos cambios obligan a consultar emisiones; mover la barra de progreso no las vuelve a pedir. */
private data class NewEpisodeQuery(val showIds: List<String>, val watchedKeys: Set<String>)

/** Una instancia por pestaña (Inicio / Series / Películas): ver `hiltViewModel(key = …)` en HomeScreen. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val watchProgress: WatchProgressRepository,
    settings: SettingsRepository,
    myList: MyListRepository,
    profiles: com.shieldstream.tv.domain.ProfileRepository,
) : ViewModel() {

    /** Perfil infantil: la portada solo enseña las categorías para niños (las filas ya vienen filtradas). */
    val kidsProfile: Boolean = profiles.active.value.kids

    /** Categorías ocultas en el perfil (nombres). Aparte del UiState: cambiarlas no recarga las filas. */
    val hiddenCategories: StateFlow<Set<String>> = settings.hiddenCategories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Filas del catálogo (TMDB): se cargan una vez por pestaña. */
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)

    /** Fila "Seguir viendo" (0 o 1 filas). null = aún calculándose: la Home espera para no mover las filas. */
    private val continueRows = MutableStateFlow<List<CatalogRow>?>(null)
    private val filterFlow = MutableStateFlow<HomeFilter?>(null)

    /** Últimos episodios emitidos de las series del perfil. */
    private val newEpisodeRows = MutableStateFlow<List<CatalogRow>?>(null)

    /** Fila "Mi lista" (0 o 1). null = aún calculándose (como "Seguir viendo", para no mover las filas). */
    private val myListRows = MutableStateFlow<List<CatalogRow>?>(null)

    val uiState: StateFlow<HomeUiState> = combine(_uiState, continueRows, newEpisodeRows, myListRows) { catalog, continueRow, newRow, listRow ->
        when {
            catalog !is HomeUiState.Content -> catalog
            continueRow == null || newRow == null || listRow == null -> HomeUiState.Loading
            else -> catalog.copy(rows = continueRow + newRow + listRow + catalog.rows)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState.Loading)

    private var loadedFilter: HomeFilter? = null

    init {
        // Se recalcula cada vez que cambia el progreso (p. ej. al volver del reproductor).
        viewModelScope.launch {
            combine(filterFlow.filterNotNull(), watchProgress.progress) { filter, all ->
                ContinueWatching.entries(all, filter)
            }
                .distinctUntilChanged()
                .mapLatest { entries -> listOfNotNull(buildContinueRow(entries)) }
                .collect { continueRows.value = it }
        }
        // Nuevos episodios: series con historial o en Mi lista. La caché evita consultas al volver del reproductor.
        viewModelScope.launch {
            combine(filterFlow.filterNotNull(), watchProgress.progress, myList.entries) { filter, progress, list ->
                NewEpisodeQuery(
                    showIds = NewEpisodes.candidateIds(progress, list, filter),
                    watchedKeys = progress.filterValues { it.isWatched }.keys,
                )
            }
                .distinctUntilChanged()
                .mapLatest { query -> listOfNotNull(buildNewEpisodesRow(query)) }
                .collect { newEpisodeRows.value = it }
        }
        // "Mi lista": en su pestaña (películas o series); se rehace al añadir o quitar desde la ficha.
        viewModelScope.launch {
            combine(filterFlow.filterNotNull(), myList.entries) { filter, entries ->
                entries.filter {
                    when (filter) {
                        HomeFilter.ALL -> true
                        HomeFilter.MOVIES -> it.type == MediaType.MOVIE
                        HomeFilter.TV -> it.type == MediaType.TV
                    }
                }.take(MY_LIST_MAX)
            }
                .distinctUntilChanged()
                .mapLatest { entries -> listOfNotNull(buildMyListRow(entries)) }
                .collect { myListRows.value = it }
        }
    }

    /** Mantener OK sobre una tarjeta de "Seguir viendo". */
    fun removeFromContinue(item: CatalogItem) {
        ContinueWatching.keysToRemove(watchProgress.progress.value, item.type, item.id)
            .forEach(watchProgress::clear)
    }

    private suspend fun buildContinueRow(entries: List<ContinueEntry>): CatalogRow? {
        if (entries.isEmpty()) return null
        val loaded = coroutineScope {
            entries.map { entry ->
                async {
                    // Sin red o TMDB lento: ese título no sale, pero la Home no se queda esperando.
                    val item = withTimeoutOrNull(5_000) {
                        runCatching { catalogRepository.getItem(entry.type, entry.id) }.getOrNull()
                    }
                    item?.let { entry to it }
                }
            }.awaitAll().filterNotNull()
        }
        if (loaded.isEmpty()) return null
        return CatalogRow(
            id = CONTINUE_ROW_ID,
            title = tr("Seguir viendo", "Continue watching"),
            items = loaded.map { it.second },
            style = RowStyle.CONTINUE,
            progress = loaded.associate { (entry, item) ->
                item.key to ContinueInfo(entry.progress.fraction, labelFor(entry))
            },
        )
    }

    private suspend fun buildNewEpisodesRow(query: NewEpisodeQuery): CatalogRow? {
        if (query.showIds.isEmpty()) return null
        val now = System.currentTimeMillis()
        val today = isoDate(Date(now))
        val cutoff = isoDate(Date(now - NewEpisodes.RECENT_DAYS * 24 * 60 * 60 * 1000L))
        val loaded = coroutineScope {
            query.showIds.map { showId ->
                async {
                    withTimeoutOrNull(5_000) {
                        runCatching { catalogRepository.getShowAiring(showId) }.getOrNull()
                    }?.let { airing ->
                        val episode = airing.latestEpisode
                            ?.takeIf { NewEpisodes.isNew(showId, it, query.watchedKeys, cutoff, today) }
                            ?: return@let null
                        if (airing.item.posterUrl == null) null else airing.item to episode
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (loaded.isEmpty()) return null
        val entries = loaded.sortedByDescending { it.second.airDate }.take(NewEpisodes.MAX_ITEMS)
        return CatalogRow(
            id = NEW_EPISODES_ROW_ID,
            title = tr("Nuevos episodios", "New episodes"),
            items = entries.map { it.first },
            style = RowStyle.NEW_EPISODES,
            newEpisodes = entries.associate { (item, episode) -> item.key to episode },
        )
    }

    private fun isoDate(date: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

    private suspend fun buildMyListRow(entries: List<com.shieldstream.tv.domain.MyListEntry>): CatalogRow? {
        if (entries.isEmpty()) return null
        val items = coroutineScope {
            entries.map { e ->
                async { withTimeoutOrNull(5_000) { runCatching { catalogRepository.getItem(e.type, e.id) }.getOrNull() } }
            }.awaitAll().filterNotNull()
        }
        if (items.isEmpty()) return null
        return CatalogRow(id = MY_LIST_ROW_ID, title = tr("Mi lista", "My list"), items = items)
    }

    private fun labelFor(entry: ContinueEntry): String {
        val remaining = formatRemaining(entry.progress.remainingMs)
        return if (entry.type == MediaType.TV) tr("T${entry.season}:E${entry.episode} · $remaining", "S${entry.season}:E${entry.episode} · $remaining") else tr("Quedan $remaining", "$remaining left")
    }

    private fun formatRemaining(ms: Long): String {
        val totalMin = ((ms + 59_999) / 60_000).toInt()
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }

    private companion object {
        const val CONTINUE_ROW_ID = "continue_watching"
        const val NEW_EPISODES_ROW_ID = "new_episodes"
        const val MY_LIST_ROW_ID = "my_list"
        const val MY_LIST_MAX = 40
    }

    /**
     * Con el Inicio ya en pantalla, carga en segundo plano Series y Películas (quedan en la caché del
     * repositorio): al cambiar de pestaña aparecen al instante en vez de mostrar la pantalla de carga.
     */
    private fun prefetchOtherTabs() {
        viewModelScope.launch {
            delay(1_500) // primero que el Inicio termine de descargar sus imágenes
            for (other in listOf(HomeFilter.TV, HomeFilter.MOVIES)) {
                coroutineScope {
                    launch { runCatching { catalogRepository.getFeatured(other) } }
                    runCatching { catalogRepository.getHome(other) }
                }
            }
        }
    }

    /** Idempotente: al volver del detalle no se recarga el catálogo. */
    fun load(filter: HomeFilter, force: Boolean = false) {
        filterFlow.value = filter
        if (!force && loadedFilter == filter && _uiState.value is HomeUiState.Content) return
        loadedFilter = filter
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            // Portada semanal en paralelo; si falla, el Inicio sale igual (sin portada).
            val featured = async { runCatching { catalogRepository.getFeatured(filter) }.getOrDefault(emptyList()) }
            runCatching { catalogRepository.getHome(filter) }
                .onSuccess { rows ->
                    _uiState.value = if (rows.isEmpty()) {
                        HomeUiState.Error(tr("No se pudo cargar el catálogo. Revisa la conexión.", "Couldn't load the catalog. Check your connection."))
                    } else {
                        HomeUiState.Content(rows, featured.await())
                    }
                    if (filter == HomeFilter.ALL) prefetchOtherTabs()
                }
                .onFailure { e ->
                    _uiState.value = HomeUiState.Error(e.message ?: tr("Error cargando el catálogo", "Error loading the catalog"))
                }
        }
    }
}
