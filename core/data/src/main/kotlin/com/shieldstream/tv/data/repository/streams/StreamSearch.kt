package com.shieldstream.tv.data.repository.streams

import android.util.Log
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.domain.StreamResults
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.api.TorrentioApi
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import com.shieldstream.tv.strings.sectionResource
import com.shieldstream.tv.strings.titleResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Búsqueda de fuentes de un título: lanza a la vez todos los buscadores activos y va emitiendo lo que llega, con
 * cada fuente en la sección de su buscador y, para los que aún no han respondido o no han dado nada, el motivo.
 * Cada buscador vive en su propio fichero; aquí solo se reparten y se juntan.
 */
internal class StreamSearch(
    private val tmdb: TmdbApi,
    torrentio: TorrentioApi,
    private val store: TokenStore,
    private val settings: SettingsRepository,
    private val texts: Texts,
    private val key: () -> String,
) {
    private val titles = ReleaseTitles(tmdb, key)
    private val indexers = TorrentIndexers(torrentio, texts)
    private val addons = AddonStreams(torrentio, store, settings, titles, texts)
    private val torrentioSearch = TorrentioSearch(torrentio, store, settings, titles, addons, texts)
    private val scrapers = ScraperSearch(indexers, titles, texts, ::canPickFile)
    private val castellano = CastellanoSearch(torrentio, indexers, titles, texts, ::canPickFile)

    fun streams(item: CatalogItem, episode: Episode?): Flow<StreamResults> = channelFlow {
        val aioConfigured = !store.getSecret(TokenStore.keyNameFor(ApiService.AIOSTREAMS)).isNullOrBlank()
        val enabled = settings.sourceProviders.first()
        // Los buscadores propios (castellano, The Pirate Bay, Knaben, Nyaa) dan torrents sueltos: se resuelven con el
        // servicio activo, sea cual sea (por su API o, si la app no la tiene, a través de Torrentio).
        val providers = SourceProvider.entries.filter {
            it in enabled && (it != SourceProvider.AIOSTREAMS || aioConfigured)
        }
        val imdbId = item.imdbId ?: resolveImdb(item) ?: error(texts.get(StringsR.string.data_imdb_id_missing, item.title))
        val (type, streamId) = if (item.type == MediaType.TV) {
            val ep = episode ?: error(texts.get(StringsR.string.data_pick_an_episode))
            "series" to "$imdbId:${ep.season}:${ep.number}"
        } else {
            "movie" to imdbId.orEmpty()
        }
        val done = LinkedHashMap<SourceProvider, Result<List<StreamSource>>>()
        val lock = Mutex()
        fun snapshot(): StreamResults {
            val sources = providers.flatMap { p ->
                done[p]?.getOrNull().orEmpty().map {
                    val section = if (p == SourceProvider.CASTELLANO && it.provider.isNotBlank()) it.provider else texts.get(p.sectionResource)
                    it.copy(id = "${p.name}:${it.id}", provider = section)
                }
            }.distinctBy { it.provider + ":" + if (it.resolveUrl == null) it.infoHash.lowercase() else it.id }
            val sections = providers.groupBy { texts.get(it.sectionResource) }
            val withSources = sources.map { it.provider }.toSet()
            val pending = sections.filter { (_, ps) -> ps.any { it !in done } }.keys
            val unavailable = sections.mapNotNull { (section, ps) ->
                when {
                    section in pending || section in withSources -> null
                    ps.all { done[it]?.isFailure == true } -> section to texts.get(StringsR.string.data_not_responding_right_now)
                    else -> section to texts.get(StringsR.string.data_no_sources_for_this)
                }
            }.toMap()
            return StreamResults(sources, unavailable, pending)
        }
        send(snapshot())
        coroutineScope {
            providers.map { p ->
                launch {
                    val r = try {
                        Result.success(withTimeout(PROVIDER_TIMEOUT_MS) { providerStreams(p, item, type, streamId) })
                    } catch (e: TimeoutCancellationException) {
                        Result.failure(e)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                    r.exceptionOrNull()?.let { Log.w("Catalog", "${texts.get(p.titleResource)} falló: ${it.message}") }
                    r.getOrNull()?.let { list ->
                        Log.i("Catalog", "${p.name}: ${list.size} fuentes (${list.count { it.isCached }} ⚡) con ${settings.debridService.first().title}")
                    }
                    lock.withLock {
                        done[p] = r
                        send(snapshot())
                    }
                }
            }.joinAll()
        }
        if (snapshot().sources.isEmpty()) {
            val failed = done.filter { it.value.isFailure }
            failed.values.firstNotNullOfOrNull { r -> r.exceptionOrNull()?.takeIf { it is IllegalStateException && it !is CancellationException } }?.let { throw it }
            if (failed.isNotEmpty()) {
                val names = failed.keys.joinToString { texts.get(it.titleResource) }
                error(texts.get(StringsR.string.data_provider_unavailable, names))
            }
        }
    }

    private suspend fun providerStreams(p: SourceProvider, item: CatalogItem, type: String, streamId: String): List<StreamSource> =
        when (p) {
            SourceProvider.CASTELLANO -> castellano.streams(item, type, streamId)
            SourceProvider.AIOSTREAMS -> addons.userAddons(item, type, streamId)
            SourceProvider.THEPIRATEBAY, SourceProvider.KNABEN, SourceProvider.NYAA -> scrapers.streams(p, item, streamId)
            SourceProvider.COMET -> addons.comet(item, type, streamId)
            SourceProvider.TORRENTIO, SourceProvider.PEERFLIX -> torrentioSearch.streams(p, item, type, streamId)
        }

    /**
     * Con resolución propia la app elige el capítulo dentro de un pack de temporada; a través de Torrentio, sin saber
     * el número de archivo, saldría el vídeo más grande del pack (otro capítulo): ahí solo valen los sueltos.
     */
    private suspend fun canPickFile(): Boolean = settings.debridService.first().supportsDirectMagnetResolution

    private suspend fun resolveImdb(item: CatalogItem): String? {
        val numericId = item.id.toIntOrNull() ?: return null
        return runCatching {
            if (item.type == MediaType.TV) {
                tmdb.tvExternalIds(numericId, key()).imdb_id
            } else {
                tmdb.movieExternalIds(numericId, key()).imdb_id
            }
        }.getOrNull()
    }

    private companion object {
        const val PROVIDER_TIMEOUT_MS = 25_000L
    }
}
