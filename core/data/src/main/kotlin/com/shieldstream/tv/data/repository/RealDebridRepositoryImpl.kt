package com.shieldstream.tv.data.repository

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import android.os.SystemClock
import com.shieldstream.tv.domain.RealDebridRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.model.RdTorrentFile
import com.shieldstream.tv.model.RdTorrentInfo
import com.shieldstream.tv.network.api.RealDebridApi
import com.shieldstream.tv.network.dto.RdTorrentDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealDebridRepositoryImpl @Inject constructor(
    private val api: RealDebridApi,
    private val settings: SettingsRepository,
    private val texts: Texts,
) : RealDebridRepository {

    private val libraryMutex = Mutex()

    private val blockedHashes = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var libraryCache: List<RdTorrentDto> = emptyList()

    @Volatile
    private var libraryFetchedAt = 0L

    private suspend fun token(): String =
        settings.token.first() ?: error(texts.get(StringsR.string.data_real_debrid_token_not_set))

    override suspend fun validateToken(token: String): Boolean = runCatching {
        api.user("Bearer $token").premium >= 1L
    }.getOrDefault(false)

    override suspend fun cachedHashes(): Set<String> =
        library()
            .filter { it.status == RdTorrentInfo.STATUS_DOWNLOADED }
            .mapTo(HashSet()) { it.hash.lowercase() }

    override suspend fun findDownloaded(infoHash: String): String? =
        library().firstOrNull {
            it.hash.equals(infoHash, ignoreCase = true) && it.status == RdTorrentInfo.STATUS_DOWNLOADED
        }?.id

    override suspend fun addMagnet(magnet: String): String = addMagnet(magnet, attempt = 0)

    private val addPacing = Mutex()
    @Volatile private var lastAddAt = 0L

    override fun isBlocked(infoHash: String): Boolean = infoHash.lowercase() in blockedHashes

    private suspend fun addMagnet(magnet: String, attempt: Int): String {
        val hash = BTIH.find(magnet)?.groupValues?.get(1)?.lowercase()
        if (hash != null && hash in blockedHashes) {
            throw com.shieldstream.tv.domain.TorrentBlockedException(
                texts.get(StringsR.string.data_real_debrid_has_blocked_this_torrent_for),
            )
        }
        val t = token()
        addPacing.withLock {
            val wait = lastAddAt + ADD_SPACING_MS - SystemClock.elapsedRealtime()
            if (wait > 0) kotlinx.coroutines.delay(wait)
            lastAddAt = SystemClock.elapsedRealtime()
        }
        val resp = try {
            api.addMagnet("Bearer $t", magnet)
        } catch (e: retrofit2.HttpException) {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
            android.util.Log.w("RealDebrid", "addMagnet HTTP ${e.code()}: $body")
            val code = Regex("\"error_code\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
            if ((e.code() == 429 || code == 34) && attempt < LIMIT_RETRIES) {
                kotlinx.coroutines.delay(2_000L shl attempt)
                return addMagnet(magnet, attempt + 1)
            }
            if (code == 35) {
                hash?.let { blockedHashes += it }
                throw com.shieldstream.tv.domain.TorrentBlockedException(
                    texts.get(StringsR.string.data_real_debrid_has_blocked_this_torrent_for),
                )
            }
            error(
                when (code) {
                    35 -> texts.get(StringsR.string.data_real_debrid_has_blocked_this_torrent_for)
                    21 -> texts.get(StringsR.string.data_too_many_active_downloads_on_real_debrid)
                    34 -> texts.get(StringsR.string.data_real_debrid_asks_to_wait_a_moment)
                    8, 9 -> texts.get(StringsR.string.data_invalid_real_debrid_token_or_missing_permissions)
                    else -> texts.get(
                        StringsR.string.data_rd_torrent_rejected,
                        e.code(),
                        code?.let { texts.get(StringsR.string.data_error_code_suffix, it) }.orEmpty(),
                    )
                },
            )
        }
        require(resp.id.isNotBlank()) { texts.get(StringsR.string.data_real_debrid_returned_no_torrent_id) }
        invalidateLibrary()
        return resp.id
    }

    override suspend fun torrentInfo(torrentId: String): RdTorrentInfo {
        val t = token()
        val dto = api.torrentInfo("Bearer $t", torrentId)
        return RdTorrentInfo(
            id = dto.id,
            hash = dto.hash,
            status = dto.status,
            files = dto.files.map { f ->
                RdTorrentFile(
                    id = f.id,
                    name = f.path,
                    sizeBytes = f.bytes,
                    selected = f.selected == 1,
                    link = null,
                )
            },
            links = dto.links,
        )
    }

    override suspend fun selectFiles(torrentId: String, fileIds: List<Int>) {
        val t = token()
        val resp = api.selectFiles("Bearer $t", torrentId, fileIds.joinToString(","))
        check(resp.isSuccessful) { texts.get(StringsR.string.data_rd_file_selection_rejected, resp.code()) }
    }

    override suspend fun deleteTorrent(torrentId: String) {
        runCatching { api.deleteTorrent("Bearer ${token()}", torrentId) }
        invalidateLibrary()
    }

    override suspend fun unrestrict(link: String): String {
        val t = token()
        val resp = api.unrestrict("Bearer $t", link)
        require(resp.download.isNotBlank()) { texts.get(StringsR.string.data_couldn_t_unrestrict_the_link) }
        return resp.download
    }

    private suspend fun library(): List<RdTorrentDto> {
        return libraryMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            if (libraryFetchedAt != 0L && now - libraryFetchedAt < LIBRARY_TTL_MS) {
                libraryCache
            } else {
                val resp = api.torrents("Bearer ${token()}", limit = LIBRARY_LIMIT)
                if (resp.isSuccessful) {
                    libraryCache = resp.body().orEmpty()
                    libraryFetchedAt = now
                    libraryCache
                } else {
                    emptyList()
                }
            }
        }
    }

    private fun invalidateLibrary() {
        libraryFetchedAt = 0L
    }

    private companion object {
        const val LIBRARY_TTL_MS = 60_000L

        const val LIBRARY_LIMIT = 1000

        val BTIH = Regex("btih:([a-fA-F0-9]{40})")
        const val ADD_SPACING_MS = 1_000L
        const val LIMIT_RETRIES = 3
    }
}
