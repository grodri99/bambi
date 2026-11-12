package com.shieldstream.tv.home

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.model.BackdropUrls
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.FeaturedItem
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.ui.component.PosterWidth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Prepara el Inicio mientras se ve la intro y el "¿Quién está viendo?": pide las filas y los destacados (que quedan
 * en la caché del catálogo) y descarga las imágenes en el tamaño en que se pintan. Así, al entrar, todo aparece ya
 * dibujado en vez de ir llenándose póster a póster.
 *
 * Orden: primero lo que se ve nada más entrar (fondos de los destacados y las primeras filas), que es lo que marca
 * "listo"; después el resto de carátulas y las pestañas de Series y Películas, solo a disco.
 */
@Singleton
class HomePreloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalog: CatalogRepository,
    private val settings: SettingsRepository,
    private val profiles: ProfileRepository,
    private val imageLoader: ImageLoader,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val readyFor = MutableStateFlow<String?>(null)
    private var job: Job? = null
    private var jobKey: String? = null

    /** Empieza (o sigue) la precarga para el perfil activo. Se puede llamar las veces que haga falta. */
    fun start() {
        val key = keyFor(profiles.active.value)
        synchronized(this) {
            if (readyFor.value == key) return
            if (jobKey == key && job?.isActive == true) return
            job?.cancel()
            jobKey = key
            job = scope.launch { preload(key) }
        }
    }

    /** El Inicio del perfil activo ya está preparado. */
    fun isReady(): Boolean = readyFor.value == keyFor(profiles.active.value)

    /** Espera a que esté preparado, como mucho [timeoutMs]. Devuelve si llegó a tiempo. */
    suspend fun awaitReady(timeoutMs: Long): Boolean {
        start()
        val key = keyFor(profiles.active.value)
        return withTimeoutOrNull(timeoutMs) { readyFor.first { it == key } } != null
    }

    // Los perfiles infantiles tienen otro catálogo; el resto comparte el mismo.
    private fun keyFor(profile: Profile) = "${if (profile.kids) "kids" else "all"}_${Lang.current.name}"

    private suspend fun preload(key: String) {
        val startedAt = System.currentTimeMillis()
        val limit = settings.homeRowItemLimit.first()
        val sizes = Sizes(context, settings.lightweightUiEnabled, settings.highResBackdropsEnabled)
        try {
            val (featured, rows) = load(HomeFilter.ALL, limit)
            // Lo que se ve al entrar: los primeros destacados y las primeras filas.
            val essential = firstScreen(featured, rows, sizes)
            withTimeoutOrNull(ESSENTIAL_TIMEOUT_MS) { fetchAll(essential, memory = true) }
            readyFor.value = key
            Log.d(TAG, "Inicio listo en ${System.currentTimeMillis() - startedAt} ms (${essential.size} imágenes)")

            // Resto, sin prisa y solo a disco (para no echar de la memoria lo que se va a ver primero).
            fetchAll(rows.flatMap { row -> row.items.mapNotNull { it.posterUrl } }.map { sizes.poster(it) }, memory = false)
            for (other in listOf(HomeFilter.TV, HomeFilter.MOVIES)) {
                val (f, r) = load(other, limit)
                fetchAll(firstScreen(f, r, sizes), memory = false)
            }
            Log.d(TAG, "Precarga completa en ${System.currentTimeMillis() - startedAt} ms")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Si algo falla, el Inicio carga como siempre; no se bloquea la entrada.
            Log.w(TAG, "Precarga fallida: ${e.message}")
            readyFor.value = key
        }
    }

    private suspend fun load(filter: HomeFilter, limit: Int): Pair<List<FeaturedItem>, List<CatalogRow>> = coroutineScope {
        val featured = async { runCatching { catalog.getFeatured(filter) }.getOrDefault(emptyList()) }
        val rows = async { runCatching { catalog.getHome(filter, limit) }.getOrDefault(emptyList()) }
        featured.await() to rows.await()
    }

    private fun firstScreen(featured: List<FeaturedItem>, rows: List<CatalogRow>, sizes: Sizes): List<ImageRequest> = buildList {
        featured.take(FIRST_FEATURED).forEach { f ->
            sizes.backdrop(f.item.backdropUrl)?.let(::add)
            f.logoUrl?.let { add(sizes.logo(it)) }
        }
        rows.take(FIRST_ROWS).forEach { row ->
            row.items.take(FIRST_POSTERS).forEach { item -> item.posterUrl?.let { add(sizes.poster(it)) } }
        }
    }

    private suspend fun fetchAll(requests: List<ImageRequest>, memory: Boolean) = coroutineScope {
        val gate = Semaphore(PARALLEL_DOWNLOADS)
        requests.distinctBy { it.data }.map { base ->
            async {
                gate.withPermit {
                    val request = if (memory) base else base.newBuilder().memoryCachePolicy(CachePolicy.DISABLED).build()
                    runCatching { imageLoader.execute(request) }
                }
            }
        }.awaitAll()
    }

    /** Tamaños en píxeles en los que se pintan las imágenes (así la versión guardada sirve tal cual). */
    private class Sizes(private val context: Context, private val lightweight: Boolean, private val highRes: Boolean) {
        private val metrics = context.resources.displayMetrics
        private val posterW = (PosterWidth.value * metrics.density).roundToInt()
        private val posterH = (posterW * 1.5f).roundToInt()
        private val screenW = metrics.widthPixels
        private val screenH = metrics.heightPixels

        fun poster(url: String): ImageRequest = ImageRequest.Builder(context).data(url).size(posterW, posterH).build()

        fun backdrop(url: String?): ImageRequest? = BackdropUrls.fullScreen(url, lightweight, highRes)?.let {
            ImageRequest.Builder(context).data(it).size(screenW, screenH).build()
        }

        fun logo(url: String): ImageRequest =
            ImageRequest.Builder(context).data(url).size((screenW * 0.4f).roundToInt(), (screenH * 0.2f).roundToInt()).build()
    }

    private companion object {
        const val TAG = "HomePreloader"
        const val ESSENTIAL_TIMEOUT_MS = 6_000L
        const val FIRST_FEATURED = 3
        const val FIRST_ROWS = 4
        const val FIRST_POSTERS = 9
        const val PARALLEL_DOWNLOADS = 6
    }
}
