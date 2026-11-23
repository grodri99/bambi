package com.shieldstream.tv.data.repository.streams

import com.shieldstream.tv.data.repository.StreamLog
import com.shieldstream.tv.data.repository.StreamTexts
import com.shieldstream.tv.data.repository.StreamSecrets
import com.shieldstream.tv.data.repository.StreamSettings
import com.shieldstream.tv.data.repository.isDebridError
import com.shieldstream.tv.data.repository.debridErrorMessage
import com.shieldstream.tv.data.repository.aioStreamBases
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.network.api.TorrentioApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Addons de Stremio: los que añade el usuario (Ajustes → Addons) y Comet, que se configura solo con el debrid activo.
 * Sus resultados pueden ser torrents o enlaces directos ya resueltos por el addon.
 */
class AddonStreams(
    private val torrentio: TorrentioApi,
    private val store: StreamSecrets,
    private val settings: StreamSettings,
    private val titles: ReleaseTitles,
    private val texts: StreamTexts,
) {
    /** Todos los addons del usuario a la vez; si uno falla, el resto sigue. */
    suspend fun userAddons(item: CatalogItem, type: String, streamId: String): List<StreamSource> = coroutineScope {
        val bases = aioStreamBases(store.secret(ApiService.AIOSTREAMS).orEmpty())
        bases.map { base ->
            val label = addonLabel(base, bases.size)
            async {
                runCatching { streams(base, item, type, streamId, label = label) }
                    .onFailure { StreamLog.w("Catalog", "Addon $label falló (${it.javaClass.simpleName})") }
                    .getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
    }

    suspend fun comet(item: CatalogItem, type: String, streamId: String): List<StreamSource> {
        // Comet resuelve él mismo con el servicio que se le diga: el activo, con su clave.
        val debrid = settings.debridService()
        val token = store.secret(debrid.apiService)?.takeIf { it.isNotBlank() } ?: return emptyList()
        // Formato actual de Comet: idiomas/resoluciones van como objetos (si se mandan como lista, como antes, Comet
        // rechaza toda la configuración: "OBSOLETE CONFIGURATION"). Se omiten y Comet usa sus valores por defecto.
        val params = org.json.JSONObject()
            .put("cachedOnly", false)
            .put("removeTrash", true)
            .put("resultFormat", org.json.JSONArray().put("all"))
            .put("maxResultsPerResolution", 0)
            .put("maxSize", 0)
            .put("debridServices", org.json.JSONArray().put(org.json.JSONObject().put("service", debrid.cometKey).put("apiKey", token)))
            .put("enableTorrent", false)
            .put("deduplicateStreams", true)
            .put("debridStreamProxyPassword", "")
            .toString()
        val b64 = cometConfigPath(params)
        return streams("${SourceProvider.COMET.baseUrl}/$b64", item, type, streamId, label = "Comet")
    }

    /** Resultados de un addon en [base]: los enlaces directos se reproducen tal cual; el resto, como torrents. */
    suspend fun streams(
        base: String,
        item: CatalogItem,
        type: String,
        streamId: String,
        label: String = texts.stremioAddon(),
    ): List<StreamSource> {
        val streams = titles.withoutOtherShows(torrentio.streamsAt("$base/stream/$type/$streamId.json").streams, item)
        streams.firstOrNull { it.isDebridError() }?.let { error(debridErrorMessage(it.title, label, texts)) }
        return streams.mapNotNull { s ->
            val link = s.url?.takeIf { it.startsWith("http") } ?: return@mapNotNull s.toStreamSource(item)
            val hash = s.infoHash ?: "aio%08x".format(link.hashCode())
            s.copy(infoHash = hash).toStreamSource(item)?.copy(
                resolveUrl = link,
                debrid = label,
                isCached = s.name.contains("⚡") || s.name.contains("+]"),
            )
        }
    }

    private fun addonLabel(base: String, count: Int): String =
        if (count <= 1) texts.stremioAddon()
        else base.removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore('.')

    /**
     * Base64 de la configuración sin "/" ni "+" (rompen la ruta de la URL): como JSON admite espacios al final, se
     * añaden hasta que el resultado sea limpio.
     */
    private fun cometConfigPath(json: String): String {
        var padded = json
        repeat(8) {
            val b64 = java.util.Base64.getEncoder().encodeToString(padded.toByteArray())
            if ('/' !in b64 && '+' !in b64) return b64
            padded += " "
        }
        return java.util.Base64.getUrlEncoder().encodeToString(json.toByteArray())
    }
}
