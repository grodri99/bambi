package com.shieldstream.tv.domain

import com.shieldstream.tv.model.RdTorrentInfo

interface RealDebridRepository {
    suspend fun validateToken(token: String): Boolean

    suspend fun cachedHashes(): Set<String>

    suspend fun findDownloaded(infoHash: String): String?

    suspend fun addMagnet(magnet: String): String

    suspend fun torrentInfo(torrentId: String): RdTorrentInfo

    suspend fun selectFiles(torrentId: String, fileIds: List<Int>)

    fun isBlocked(infoHash: String): Boolean = false

    suspend fun deleteTorrent(torrentId: String)

    suspend fun unrestrict(link: String): String
}
