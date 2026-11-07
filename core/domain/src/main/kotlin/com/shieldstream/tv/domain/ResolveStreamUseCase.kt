package com.shieldstream.tv.domain

import com.shieldstream.tv.model.RdTorrentFile
import com.shieldstream.tv.model.RdTorrentInfo
import com.shieldstream.tv.model.StreamSource
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

interface ResolveStreamTexts {
    fun requestingLink(service: String): String
    fun lookingInLibrary(service: String): String
    fun unrestrictingLink(): String
    fun addingToDebrid(service: String): String
    fun couldNotReadMagnet(service: String): String
    fun noVideoFiles(): String
    fun checkingCache(service: String): String
    fun sourceNotCached(service: String): String
    fun noLinks(service: String): String
    fun directMagnetUnsupported(service: String): String
    fun resolutionError(): String
    fun torrentStatus(service: String, status: String): String
}

class ResolveStreamUseCase(
    private val realDebrid: RealDebridRepository,
    private val allDebridTorrents: AllDebridRepository,
    private val torrentio: TorrentioLinkResolver,
    private val allDebrid: AllDebridAuthRepository,
    private val selectedDebrid: Flow<DebridService>,
    private val texts: ResolveStreamTexts,
) {
    operator fun invoke(source: StreamSource): Flow<ResolveState> = flow {
        source.directUrl?.let {
            emit(ResolveState.Ready(it))
            return@flow
        }

        source.resolveUrl?.let { url ->
            val service = source.debrid ?: selectedDebrid.first().title
            emit(ResolveState.Progress(texts.requestingLink(service)))
            val result = if (source.debrid == ITHIL_ALLDEBRID) {
                allDebrid.unlock(url)
            } else {
                torrentio.resolve(url, service)
            }
            when (val link = result) {
                is DebridLink.Ready -> emit(ResolveState.Ready(link.url))
                is DebridLink.Problem -> emit(ResolveState.Failed(link.message, notCached = link.notCached))
            }
            return@flow
        }

        val service = selectedDebrid.first()
        val debrid = when (service) {
            DebridService.REAL_DEBRID -> realDebrid
            DebridService.ALLDEBRID -> allDebridTorrents
            else -> error(texts.directMagnetUnsupported(service.title))
        }

        emit(ResolveState.Progress(texts.lookingInLibrary(service.title)))
        val existingId = debrid.findDownloaded(source.infoHash)
        if (existingId != null) {
            val info = debrid.torrentInfo(existingId)
            val link = pickFile(info.files, source)?.let { info.linkFor(it) }
            if (link != null) {
                emit(ResolveState.Progress(texts.unrestrictingLink()))
                emit(ResolveState.Ready(debrid.unrestrict(link)))
                return@flow
            }
        }

        emit(ResolveState.Progress(texts.addingToDebrid(service.title)))
        val torrentId = debrid.addMagnet(source.magnet)
        var keep = false
        try {
            if (debrid.requiresFileSelection) {
                val converted = poll(debrid, service.title, torrentId, MAGNET_TIMEOUT_MS) {
                    it.status == RdTorrentInfo.STATUS_WAITING_SELECTION ||
                        it.status == RdTorrentInfo.STATUS_DOWNLOADED
                } ?: error(texts.couldNotReadMagnet(service.title))

                if (converted.status == RdTorrentInfo.STATUS_WAITING_SELECTION) {
                    val target = pickFile(converted.files, source)
                        ?: error(texts.noVideoFiles())
                    debrid.selectFiles(torrentId, listOf(target.id))
                }
            }

            emit(ResolveState.Progress(texts.checkingCache(service.title)))
            val ready = poll(debrid, service.title, torrentId, CACHE_TIMEOUT_MS) { it.status == RdTorrentInfo.STATUS_DOWNLOADED }
            if (ready == null) {
                emit(ResolveState.Failed(texts.sourceNotCached(service.title), notCached = true))
                return@flow
            }

            val link = pickFile(ready.files, source)?.let { ready.linkFor(it) }
                ?: ready.links.firstOrNull()
                ?: error(texts.noLinks(service.title))

            emit(ResolveState.Progress(texts.unrestrictingLink()))
            val url = debrid.unrestrict(link)
            keep = true
            emit(ResolveState.Ready(url))
        } finally {
            if (!keep) withContext(NonCancellable) { debrid.deleteTorrent(torrentId) }
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
        debrid: MagnetDebridRepository,
        service: String,
        torrentId: String,
        timeoutMs: Long,
        done: (RdTorrentInfo) -> Boolean,
    ): RdTorrentInfo? {
        val attempts = (timeoutMs / POLL_INTERVAL_MS).toInt() + 1
        repeat(attempts) { attempt ->
            if (attempt > 0) delay(POLL_INTERVAL_MS)
            val info = debrid.torrentInfo(torrentId)
            if (info.status in RdTorrentInfo.STATUS_FAILED) {
                error(texts.torrentStatus(service, info.status))
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
