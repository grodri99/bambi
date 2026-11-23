package com.shieldstream.tv.data.repository.streams

import com.shieldstream.tv.data.repository.StreamTexts
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.network.api.TorrentioApi
import com.shieldstream.tv.network.dto.TorrentioStreamDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Un torrent encontrado en un buscador: nombre, hash, tamaño en bytes y seeds (0 si el buscador no los da). */
data class IndexerHit(val name: String, val hash: String, val size: Long, val seeders: Int)

/**
 * Las peticiones a los buscadores de torrents propios (The Pirate Bay, Knaben, Nyaa). Solo devuelven lo que tiene un
 * hash válido; qué resultados valen para el título lo decide quien busca.
 */
class TorrentIndexers(private val torrentio: TorrentioApi, private val texts: StreamTexts) {

    suspend fun pirateBay(query: String): List<IndexerHit> {
        val base = checkNotNull(SourceProvider.THEPIRATEBAY.baseUrl)
        val q = java.net.URLEncoder.encode(query.replace(Regex("[^A-Za-z0-9\\s.-]+"), ""), "UTF-8")
        val arr = org.json.JSONArray(torrentio.raw("$base/q.php?q=$q&cat=0").string())
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val hash = o.optString("info_hash")
            // id 0 = "No results returned" de apibay.
            if (o.optString("id") == "0" || hash.isBlank() || hash.all { it == '0' }) null
            else IndexerHit(o.optString("name"), hash, o.optLong("size"), o.optInt("seeders"))
        }
    }

    /** [categories] null: sin categoría (las webs españolas suben a menudo sin clasificar). */
    suspend fun knaben(query: String, categories: List<Int>?): List<IndexerHit> {
        val base = checkNotNull(SourceProvider.KNABEN.baseUrl)
        val body = org.json.JSONObject()
            .put("search_type", "100%").put("search_field", "title").put("query", query)
            .put("order_by", "seeders").put("order_direction", "desc").put("size", 150).put("hide_xxx", true)
            .apply { if (categories != null) put("categories", org.json.JSONArray(categories)) }
            .toString().toRequestBody("application/json".toMediaType())
        val hits = org.json.JSONObject(torrentio.postJson("$base/v1", body).string()).optJSONArray("hits")
        return (0 until (hits?.length() ?: 0)).mapNotNull { i ->
            val h = hits!!.getJSONObject(i)
            val hash = h.optString("hash").ifBlank { Regex("btih:([a-fA-F0-9]{40})").find(h.optString("magnetUrl"))?.groupValues?.get(1).orEmpty() }
            if (hash.length != 40) null else IndexerHit(h.optString("title"), hash, h.optLong("bytes"), h.optInt("seeders"))
        }
    }

    suspend fun nyaa(query: String): List<IndexerHit> {
        val base = checkNotNull(SourceProvider.NYAA.baseUrl)
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val html = torrentio.page("$base/?f=0&c=0_0&q=$enc", BROWSER_UA).string()
        return MAGNET.findAll(html).mapNotNull { m ->
            val params = m.groupValues[2].replace("&amp;", "&")
            val name = Regex("[?&]dn=([^&]+)").find(params)?.groupValues?.get(1)
                ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) } ?: return@mapNotNull null
            val size = Regex("[?&]xl=(\\d+)").find(params)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            IndexerHit(name, m.groupValues[1], size, 0)
        }.toList()
    }

    /** El resultado con el formato de Torrentio, para mapearlo igual que el resto ([toStreamSource]). */
    fun dto(p: SourceProvider, hit: IndexerHit) = TorrentioStreamDto(
        name = "Bambi\n${hit.name}",
        title = "${hit.name}\n👤 ${hit.seeders} 💾 ${"%.2f".format(java.util.Locale.US, hit.size / 1_073_741_824.0)} GB ⚙️ ${texts.providerTitle(p)}",
        infoHash = hit.hash.lowercase(),
    )

    private companion object {
        const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        val MAGNET = Regex("magnet:\\?xt=urn:btih:([a-fA-F0-9]{40})([^\"'<>\\s]*)")
    }
}
