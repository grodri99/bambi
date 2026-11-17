package com.shieldstream.tv.data.repository.streams

import com.shieldstream.tv.data.repository.ShowNameFilter
import com.shieldstream.tv.data.repository.debridErrorMessage
import com.shieldstream.tv.data.repository.isDebridError
import com.shieldstream.tv.data.repository.torrentioConfig
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.network.api.TorrentioApi
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import kotlinx.coroutines.flow.first

/**
 * Torrentio y los addons con su mismo formato (Peerflix). Con Real-Debrid se piden los torrents y los resuelve la
 * app; con cualquier otro servicio se piden ya configurados con su clave y devuelven enlaces /resolve/.
 */
internal class TorrentioSearch(
    private val torrentio: TorrentioApi,
    private val store: TokenStore,
    private val settings: SettingsRepository,
    private val titles: ReleaseTitles,
    private val addons: AddonStreams,
    private val texts: Texts,
) {
    suspend fun streams(p: SourceProvider, item: CatalogItem, type: String, streamId: String): List<StreamSource> {
        if (p != SourceProvider.PEERFLIX) return torrentioStreams(p, item, type, streamId)
        // Peerflix busca por título en webs españolas y mezcla series parecidas: fuera las de otra serie.
        val sources = torrentioStreams(p, item, type, streamId)
        val names = titles.showTitlesFor(item)
        return sources.filter { ShowNameFilter.matches(it.title, names, movie = item.type != MediaType.TV) }
    }

    private suspend fun torrentioStreams(p: SourceProvider, item: CatalogItem, type: String, streamId: String): List<StreamSource> {
        val debrid = settings.debridService.first()
        val base = checkNotNull(p.baseUrl)
        val language = if (settings.spanishFirst.first()) "language=spanish" else null
        if (!debrid.viaTorrentio) {
            val streams = when {
                p != SourceProvider.TORRENTIO -> torrentio.streamsAt("$base/stream/$type/$streamId.json")
                language != null -> torrentio.streamsWithConfig(language, type, streamId)
                else -> torrentio.streams(type, streamId)
            }
            return titles.withoutOtherShows(streams.streams, item).mapNotNull { it.toStreamSource(item) }
        }
        if (p == SourceProvider.PEERFLIX) {
            val key = store.getSecret(TokenStore.keyNameFor(debrid.apiService))?.takeIf { it.isNotBlank() }
                ?: error(texts.get(StringsR.string.data_debrid_key_missing, debrid.title))
            return addons.streams("$base/${torrentioConfig(debrid, key)}", item, type, streamId, label = debrid.title)
        }
        if (!p.torrentioStyle) return emptyList()
        val key = store.getSecret(TokenStore.keyNameFor(debrid.apiService))?.takeIf { it.isNotBlank() }
            ?: error(texts.get(StringsR.string.data_debrid_key_missing, debrid.title))
        val config = listOfNotNull(language, torrentioConfig(debrid, key)).joinToString("|")
        val streams = if (p == SourceProvider.TORRENTIO) torrentio.streamsWithConfig(config, type, streamId).streams
        else torrentio.streamsAt("$base/$config/stream/$type/$streamId.json").streams
        val sources = titles.withoutOtherShows(streams, item).mapNotNull { it.toDebridSource(item, debrid) }
        if (sources.isEmpty()) {
            streams.firstOrNull { it.isDebridError() }?.let { error(debridErrorMessage(it.title, debrid.title, texts)) }
        }
        return sources
    }
}
