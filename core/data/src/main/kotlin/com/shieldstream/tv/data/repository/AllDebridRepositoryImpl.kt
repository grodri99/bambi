package com.shieldstream.tv.data.repository

import com.shieldstream.tv.domain.AllDebridRepository
import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.model.RdTorrentFile
import com.shieldstream.tv.model.RdTorrentInfo
import com.shieldstream.tv.network.api.AllDebridApi
import com.shieldstream.tv.network.api.AllDebridFileDto
import com.shieldstream.tv.network.api.AllDebridMagnetFilesDto
import com.shieldstream.tv.network.api.AllDebridResponse
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class AllDebridRepositoryImpl @Inject constructor(
    private val api: AllDebridApi,
    private val keys: ApiKeysRepository,
    private val texts: Texts,
) : AllDebridRepository {

    override val requiresFileSelection: Boolean = false

    override suspend fun findDownloaded(infoHash: String): String? {
        // AllDebrid's v4.1 status response does not include hashes. Uploading the magnet is
        // therefore the first reliable way to obtain its ID; Ready responses remain cache-only.
        return null
    }

    override suspend fun addMagnet(magnet: String): String {
        val response = api.uploadMagnet(authorization(), magnet)
        val data = response.requireData()
        val uploaded = data.magnets.firstOrNull()
            ?: throw IOException(texts.get(StringsR.string.data_couldn_t_verify))
        uploaded.error?.let { throw IOException(it.message ?: texts.get(StringsR.string.data_couldn_t_verify)) }
        return uploaded.id?.takeIf { it > 0 }?.toString()
            ?: throw IOException(texts.get(StringsR.string.data_couldn_t_verify))
    }

    override suspend fun torrentInfo(torrentId: String): RdTorrentInfo {
        val status = api.magnetStatus(authorization(), torrentId).requireData().magnets
            .firstOrNull { it.id.toString() == torrentId }
            ?: throw IOException(texts.get(StringsR.string.data_couldn_t_verify))
        val files = if (status.statusCode == ALLDEBRID_READY) magnetFiles(torrentId) else emptyList()
        return RdTorrentInfo(
            id = torrentId,
            hash = status.hash.orEmpty(),
            status = when {
                status.statusCode == ALLDEBRID_READY -> RdTorrentInfo.STATUS_DOWNLOADED
                status.statusCode >= ALLDEBRID_FIRST_ERROR -> "error"
                else -> status.status
            },
            files = files,
            links = files.mapNotNull { it.link },
        )
    }

    override suspend fun selectFiles(torrentId: String, fileIds: List<Int>) {
        // AllDebrid exposes links for every file when Ready; selection happens locally.
    }

    override suspend fun deleteTorrent(torrentId: String) {
        runCatching { api.deleteMagnet(authorization(), torrentId) }
    }

    override suspend fun unrestrict(link: String): String {
        return try {
            val response = api.unlock(agent = "Franklix", apiKey = key(), link = link)
            val unlocked = response.data?.link.orEmpty()
            if (response.status != "success" || unlocked.isBlank()) {
                throw IOException(
                    response.error?.message ?: texts.get(StringsR.string.data_couldn_t_unrestrict_the_link),
                )
            }
            unlocked
        } catch (e: CancellationException) {
            throw e
        }
    }

    private suspend fun magnetFiles(torrentId: String): List<RdTorrentFile> {
        val magnet = api.magnetFiles(authorization(), torrentId).requireData().magnets.firstOrNull()
            ?: throw IOException(texts.get(StringsR.string.data_empty_response))
        magnet.error?.let { error ->
            throw IOException(error.message ?: texts.get(StringsR.string.data_empty_response))
        }
        return flattenFiles(magnet)
    }

    private fun flattenFiles(magnet: AllDebridMagnetFilesDto): List<RdTorrentFile> {
        val result = mutableListOf<RdTorrentFile>()

        fun visit(nodes: List<AllDebridFileDto>, parent: String) {
            nodes.forEach { node ->
                val path = if (parent.isEmpty()) node.n else "$parent/${node.n}"
                val children = node.e
                if (children != null) {
                    visit(children, path)
                } else {
                    result += RdTorrentFile(
                        id = result.size + 1,
                        name = path,
                        sizeBytes = node.s,
                        selected = true,
                        link = node.l,
                    )
                }
            }
        }

        visit(magnet.files, parent = "")
        return result
    }

    private fun authorization(): String = "Bearer ${key()}"

    private fun key(): String = keys.keys.value[ApiService.ALLDEBRID]
        ?.takeIf { it.isNotBlank() }
        ?: error(texts.get(StringsR.string.data_debrid_key_missing, DebridService.ALLDEBRID.title))

    private fun <T> AllDebridResponse<T>.requireData(): T {
        if (status != "success") {
            throw IOException(error?.message ?: texts.get(StringsR.string.data_couldn_t_verify))
        }
        return data ?: throw IOException(texts.get(StringsR.string.data_couldn_t_verify))
    }

    private companion object {
        const val ALLDEBRID_READY = 4
        const val ALLDEBRID_FIRST_ERROR = 5
    }
}
