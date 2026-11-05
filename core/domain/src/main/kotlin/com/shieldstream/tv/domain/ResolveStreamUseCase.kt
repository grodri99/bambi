package com.shieldstream.tv.domain

import com.shieldstream.tv.model.RdTorrentFile
import com.shieldstream.tv.model.RdTorrentInfo
import com.shieldstream.tv.model.StreamSource
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

interface ResolveStreamTexts {
    fun debridService(): String
    fun requestingLink(service: String): String
    fun lookingInLibrary(): String
    fun unrestrictingLink(): String
    fun addingToRealDebrid(): String
    fun couldNotReadMagnet(): String
    fun noVideoFiles(): String
    fun checkingCache(): String
    fun sourceNotCached(): String
    fun noLinks(): String
    fun resolutionError(): String
    fun torrentStatus(status: String): String
}

class ResolveStreamUseCase(
    private val realDebrid: RealDebridRepository,
    private val torrentio: TorrentioLinkResolver,
    private val texts: ResolveStreamTexts,
) {
    operator fun invoke(source: StreamSource): Flow<ResolveState> = flow {
        source.directUrl?.let {
            emit(ResolveState.Ready(it))
            return@flow
        }

        source.resolveUrl?.let { url ->
            val service = source.debrid ?: texts.debridService()
            emit(ResolveState.Progress(texts.requestingLink(service)))
            when (val link = torrentio.resolve(url, service)) {
                is DebridLink.Ready -> emit(ResolveState.Ready(link.url))
                is DebridLink.Problem -> emit(ResolveState.Failed(link.message, notCached = link.notCached))
            }
            return@flow
        }

        emit(ResolveState.Progress(texts.lookingInLibrary()))
        val existingId = realDebrid.findDownloaded(source.infoHash)
        if (existingId != null) {
            val info = realDebrid.torrentInfo(existingId)
            val link = pickFile(info.files, source)?.let { info.linkFor(it) }
            if (link != null) {
                emit(ResolveState.Progress(texts.unrestrictingLink()))
                emit(ResolveState.Ready(realDebrid.unrestrict(link)))
                return@flow
            }
        }

        emit(ResolveState.Progress(texts.addingToRealDebrid()))
        val torrentId = realDebrid.addMagnet(source.magnet)
        var keep = false
        try {
            val converted = poll(torrentId, MAGNET_TIMEOUT_MS) {
                it.status == RdTorrentInfo.STATUS_WAITING_SELECTION ||
                    it.status == RdTorrentInfo.STATUS_DOWNLOADED
            } ?: error(texts.couldNotReadMagnet())

            if (converted.status == RdTorrentInfo.STATUS_WAITING_SELECTION) {
                val target = pickFile(converted.files, source)
                    ?: error(texts.noVideoFiles())
                realDebrid.selectFiles(torrentId, listOf(target.id))
            }

            emit(ResolveState.Progress(texts.checkingCache()))
            val ready = poll(torrentId, CACHE_TIMEOUT_MS) { it.status == RdTorrentInfo.STATUS_DOWNLOADED }
            if (ready == null) {
                emit(ResolveState.Failed(texts.sourceNotCached(), notCached = true))
                return@flow
            }

            val link = pickFile(ready.files, source)?.let { ready.linkFor(it) }
                ?: ready.links.firstOrNull()
                ?: error(texts.noLinks())

            emit(ResolveState.Progress(texts.unrestrictingLink()))
            val url = realDebrid.unrestrict(link)
            keep = true
            emit(ResolveState.Ready(url))
        } finally {
            if (!keep) withContext(NonCancellable) { realDebrid.deleteTorrent(torrentId) }
        }
    }.catch { e ->
        emit(
            ResolveState.Failed(
                e.message ?: texts.resolutionError(),
                blocked = e is TorrentBlockedException,
            ),
        )
    }

    private fun pickFile(files: List<RdTorrentFile>, source: StreamSource): RdTorrentFile? {
        val videos = files.filter { it.isVideo }.ifEmpty { files }
        source.fileName?.let { name ->
            videos.firstOrNull { it.name.substringAfterLast('/').equals(name, ignoreCase = true) }
                ?.let { return it }
        }
        if (source.season != null && source.episode != null) {
            val s = source.season
            val e = source.episode
            val exact = Regex("(?i)s0*$s\\W?e0*$e(?!\\d)|(?<!\\d)$s[x.]0*$e(?!\\d)")
            val loose = Regex("(?i)(?:\\be|ep|episode|cap|\\s-\\s)\\.?\\s*0*$e(?!\\d)")
            val names = videos.map { it to it.name.substringAfterLast('/') }
            (names.firstOrNull { exact.containsMatchIn(it.second) } ?: names.takeIf { it.size > 1 }?.firstOrNull { loose.containsMatchIn(it.second) })
                ?.let { return it.first }
        }
        source.fileIdx?.let { idx ->
            files.firstOrNull { it.id == idx + 1 }?.let { return it }
        }
        return videos.maxByOrNull { it.sizeBytes }
    }

    private suspend fun poll(
        torrentId: String,
        timeoutMs: Long,
        done: (RdTorrentInfo) -> Boolean,
    ): RdTorrentInfo? {
        val attempts = (timeoutMs / POLL_INTERVAL_MS).toInt() + 1
        repeat(attempts) { attempt ->
            if (attempt > 0) delay(POLL_INTERVAL_MS)
            val info = realDebrid.torrentInfo(torrentId)
            if (info.status in RdTorrentInfo.STATUS_FAILED) {
                error(texts.torrentStatus(info.status))
            }
            if (done(info)) return info
        }
        return null
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L

        const val MAGNET_TIMEOUT_MS = 15_000L

        const val CACHE_TIMEOUT_MS = 6_000L
    }
}
