package com.shieldstream.tv.data.repository

import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.TorBoxRepository
import com.shieldstream.tv.model.RdTorrentFile
import com.shieldstream.tv.model.RdTorrentInfo
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API de TorBox (https://api.torbox.app/v1/api), con la forma de [com.shieldstream.tv.domain.MagnetDebridRepository]
 * para que [com.shieldstream.tv.domain.ResolveStreamUseCase] lo trate igual que a Real-Debrid y AllDebrid:
 * añadir el magnet, esperar a que esté listo, elegir el archivo y pedir el enlace.
 *
 * Los "enlaces" de los archivos son internos ("torbox:ID_TORRENT/ID_ARCHIVO"); [unrestrict] los cambia por la URL de
 * descarga de verdad (`requestdl`).
 */
@Singleton
class TorBoxRepositoryImpl @Inject constructor(
    base: OkHttpClient,
    private val keys: ApiKeysRepository,
    private val texts: Texts,
) : TorBoxRepository {

    private val client = base.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    // TorBox da enlaces para todos los archivos en cuanto está listo; la elección del archivo se hace aquí.
    override val requiresFileSelection: Boolean = false

    override suspend fun cachedAmong(hashes: Collection<String>): Set<String> = withContext(Dispatchers.IO) {
        val wanted = hashes.map { it.lowercase() }.filter { it.length == 40 }.distinct()
        if (wanted.isEmpty()) return@withContext emptySet()
        val found = HashSet<String>()
        // Como mucho ~100 hashes por consulta.
        for (chunk in wanted.chunked(CACHE_CHUNK)) {
            val body = JSONObject().put("hashes", JSONArray(chunk)).toString().toRequestBody(JSON)
            val json = call(Request.Builder().url("$BASE/torrents/checkcached?format=list&list_files=false").post(body))
            val data = json.opt("data")
            when (data) {
                is JSONArray -> for (i in 0 until data.length()) {
                    data.optJSONObject(i)?.optString("hash")?.takeIf { it.isNotBlank() }?.let { found += it.lowercase() }
                }
                is JSONObject -> data.keys().forEach { found += it.lowercase() }
            }
        }
        found
    }

    override suspend fun findDownloaded(infoHash: String): String? = withContext(Dispatchers.IO) {
        val list = call(Request.Builder().url("$BASE/torrents/mylist?bypass_cache=true&limit=$LIST_LIMIT").get())
            .optJSONArray("data") ?: return@withContext null
        for (i in 0 until list.length()) {
            val t = list.optJSONObject(i) ?: continue
            if (t.optString("hash").equals(infoHash, ignoreCase = true) && t.isReady()) {
                return@withContext t.optLong("id").takeIf { it > 0 }?.toString()
            }
        }
        null
    }

    override suspend fun addMagnet(magnet: String): String = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("magnet", magnet).add("allow_zip", "false").build()
        val data = call(Request.Builder().url("$BASE/torrents/createtorrent").post(form)).optJSONObject("data")
        data?.optLong("torrent_id")?.takeIf { it > 0 }?.toString()
            // Sin hueco libre TorBox lo deja en cola ("queued_id"): no está en caché y no se puede ver ahora.
            ?: throw IOException(texts.get(StringsR.string.data_download_started, DebridService.TORBOX.title))
    }

    override suspend fun torrentInfo(torrentId: String): RdTorrentInfo = withContext(Dispatchers.IO) {
        val t = call(Request.Builder().url("$BASE/torrents/mylist?id=$torrentId&bypass_cache=true").get()).optJSONObject("data")
            ?: throw IOException(texts.get(StringsR.string.data_couldn_t_verify))
        val ready = t.isReady()
        val state = t.optString("download_state").lowercase()
        val array = t.optJSONArray("files")
        val files = (0 until (array?.length() ?: 0)).mapNotNull { array!!.optJSONObject(it) }.map { f ->
            val fileId = f.optInt("id")
            RdTorrentFile(
                // Como en Real-Debrid, ids desde 1 (ResolveStreamUseCase busca el archivo n.º fileIdx + 1).
                id = fileId + 1,
                name = f.optString("name").ifBlank { f.optString("short_name") },
                sizeBytes = f.optLong("size"),
                selected = true,
                link = "$LINK_PREFIX$torrentId/$fileId",
            )
        }
        RdTorrentInfo(
            id = torrentId,
            hash = t.optString("hash"),
            status = when {
                ready -> RdTorrentInfo.STATUS_DOWNLOADED
                "error" in state || "failed" in state -> "error"
                else -> state.ifBlank { "queued" }
            },
            files = if (ready) files else emptyList(),
            links = if (ready) files.mapNotNull { it.link } else emptyList(),
        )
    }

    override suspend fun selectFiles(torrentId: String, fileIds: List<Int>) {
        // TorBox no pide elegir archivos: da enlace para cualquiera de ellos.
    }

    override suspend fun deleteTorrent(torrentId: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().put("torrent_id", torrentId.toLong()).put("operation", "delete").toString().toRequestBody(JSON)
                call(Request.Builder().url("$BASE/torrents/controltorrent").post(body))
            }
        }
    }

    override suspend fun unrestrict(link: String): String = withContext(Dispatchers.IO) {
        val (torrentId, fileId) = link.removePrefix(LINK_PREFIX).split('/').let { it.getOrNull(0) to it.getOrNull(1) }
        if (torrentId.isNullOrBlank() || fileId.isNullOrBlank()) throw IOException(texts.get(StringsR.string.data_couldn_t_unrestrict_the_link))
        val url = "$BASE/torrents/requestdl?token=${key()}&torrent_id=$torrentId&file_id=$fileId&zip_link=false"
        (call(Request.Builder().url(url).get()).opt("data") as? String)?.takeIf { it.startsWith("http") }
            ?: throw IOException(texts.get(StringsR.string.data_couldn_t_unrestrict_the_link))
    }

    private fun JSONObject.isReady(): Boolean = optBoolean("download_finished") || optBoolean("download_present")

    /** Hace la llamada y devuelve el JSON si TorBox dice "success"; si no, un error con un mensaje entendible. */
    private fun call(builder: Request.Builder): JSONObject {
        val request = builder.header("Authorization", "Bearer ${key()}").header("User-Agent", "Bambi").build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: throw IOException("TorBox: HTTP ${response.code}")
            if (!json.optBoolean("success")) {
                val error = json.optString("error").takeUnless { it == "null" }.orEmpty()
                val detail = json.optString("detail").takeUnless { it == "null" }.orEmpty()
                val service = DebridService.TORBOX.title
                throw IOException(
                    when (error) {
                        "BAD_TOKEN", "AUTH_ERROR", "NO_AUTH" -> texts.get(StringsR.string.data_invalid_service_key, service)
                        "DOWNLOAD_TOO_LARGE" -> texts.get(StringsR.string.data_source_too_big, service)
                        "MONTHLY_LIMIT", "COOLDOWN_LIMIT", "ACTIVE_LIMIT" -> texts.get(StringsR.string.data_download_limit, service)
                        else -> detail.ifBlank { error.ifBlank { "TorBox: HTTP ${response.code}" } }
                    },
                )
            }
            return json
        }
    }

    private fun key(): String = keys.keys.value[ApiService.TORBOX]
        ?.takeIf { it.isNotBlank() }
        ?: error(texts.get(StringsR.string.data_debrid_key_missing, DebridService.TORBOX.title))

    private companion object {
        const val BASE = "https://api.torbox.app/v1/api"
        const val LINK_PREFIX = "torbox:"
        const val CACHE_CHUNK = 100
        const val LIST_LIMIT = 1000
        val JSON = "application/json".toMediaType()
    }
}
