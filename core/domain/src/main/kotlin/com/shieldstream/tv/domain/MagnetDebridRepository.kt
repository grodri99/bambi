package com.shieldstream.tv.domain

import com.shieldstream.tv.model.RdTorrentInfo

/** Magnet operations shared by services with direct, cache-only torrent resolution. */
interface MagnetDebridRepository {
    val requiresFileSelection: Boolean get() = true

    suspend fun findDownloaded(infoHash: String): String?

    suspend fun addMagnet(magnet: String): String

    suspend fun torrentInfo(torrentId: String): RdTorrentInfo

    suspend fun selectFiles(torrentId: String, fileIds: List<Int>)

    suspend fun deleteTorrent(torrentId: String)

    suspend fun unrestrict(link: String): String
}
