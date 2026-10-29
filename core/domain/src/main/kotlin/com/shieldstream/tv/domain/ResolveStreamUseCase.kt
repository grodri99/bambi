package com.shieldstream.tv.domain

import com.shieldstream.tv.model.tr
import com.shieldstream.tv.model.RdTorrentFile
import com.shieldstream.tv.model.RdTorrentInfo
import com.shieldstream.tv.model.StreamSource
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Resuelve una fuente a una URL directa para ExoPlayer, emitiendo el progreso.
 *
 * Flujo (sin instantAvailability, retirado por RD en nov-2024):
 *  1. Si el torrent ya está `downloaded` en tu librería con el archivo seleccionado → unrestrict directo.
 *  2. addMagnet → polling hasta `waiting_files_selection`.
 *  3. selectFiles con el archivo correcto (nombre → fileIdx + 1 → vídeo más grande).
 *  4. Polling corto hasta `downloaded`. Si estaba en caché, RD lo marca en 1–3 s.
 *     Si no llega en [CACHE_TIMEOUT_MS] no está cacheado → se borra y se emite Failed(notCached = true).
 *  5. unrestrict del enlace.
 *
 * Todo torrent añadido que no termine en Ready se borra, también si el usuario sale de la
 * pantalla a mitad (el borrado corre en NonCancellable). Así no se llena la librería ni se
 * gastan huecos de descarga activa.
 */
class ResolveStreamUseCase(
    private val realDebrid: RealDebridRepository,
    private val torrentio: TorrentioLinkResolver,
    private val allDebrid: AllDebridRepository,
    private val settings: SettingsRepository,
) {
    operator fun invoke(source: StreamSource): Flow<ResolveState> = flow {
        source.directUrl?.let {
            emit(ResolveState.Ready(it))
            return@flow
        }

        // Otros debrids (TorBox, Premiumize…): Torrentio convierte su enlace /resolve/ en el enlace directo.
        source.resolveUrl?.let { url ->
            val service = source.debrid ?: tr("el servicio debrid", "the debrid service")
            emit(ResolveState.Progress(tr("Pidiendo el enlace a $service…", "Requesting the link from $service…")))
            when (val link = torrentio.resolve(url, service)) {
                is DebridLink.Ready -> emit(ResolveState.Ready(link.url))
                is DebridLink.Problem -> emit(ResolveState.Failed(link.message, notCached = link.notCached))
            }
            return@flow
        }

        // AllDebrid: su API propia (Torrentio no resuelve los buscadores propios con este servicio).
        if (settings.debridService.first() == DebridService.ALLDEBRID) {
            emit(ResolveState.Progress(tr("Añadiendo a AllDebrid…", "Adding to AllDebrid…")))
            emit(ResolveState.Ready(allDebrid.resolveMagnet(source)))
            return@flow
        }

        // 1. Fast-path: ya está en tu librería (reproducciones repetidas, contenido ya visto).
        emit(ResolveState.Progress(tr("Buscando en tu librería de Real-Debrid…", "Looking in your Real-Debrid library…")))
        val existingId = realDebrid.findDownloaded(source.infoHash)
        if (existingId != null) {
            val info = realDebrid.torrentInfo(existingId)
            val link = pickFile(info.files, source)?.let { info.linkFor(it) }
            if (link != null) {
                emit(ResolveState.Progress(tr("Desbloqueando enlace…", "Unrestricting link…")))
                emit(ResolveState.Ready(realDebrid.unrestrict(link)))
                return@flow
            }
            // Está, pero con otro archivo seleccionado (p. ej. otro episodio del pack): seguimos.
        }

        // 2..5. Añadir y comprobar caché.
        emit(ResolveState.Progress(tr("Añadiendo a Real-Debrid…", "Adding to Real-Debrid…")))
        val torrentId = realDebrid.addMagnet(source.magnet)
        var keep = false
        try {
            val converted = poll(torrentId, MAGNET_TIMEOUT_MS) {
                it.status == RdTorrentInfo.STATUS_WAITING_SELECTION ||
                    it.status == RdTorrentInfo.STATUS_DOWNLOADED
            } ?: error(tr("Real-Debrid no pudo leer el magnet", "Real-Debrid couldn't read the magnet"))

            if (converted.status == RdTorrentInfo.STATUS_WAITING_SELECTION) {
                val target = pickFile(converted.files, source)
                    ?: error(tr("El torrent no contiene archivos de vídeo", "The torrent has no video files"))
                realDebrid.selectFiles(torrentId, listOf(target.id))
            }

            emit(ResolveState.Progress(tr("Comprobando caché de Real-Debrid…", "Checking Real-Debrid cache…")))
            val ready = poll(torrentId, CACHE_TIMEOUT_MS) { it.status == RdTorrentInfo.STATUS_DOWNLOADED }
            if (ready == null) {
                emit(ResolveState.Failed(tr("Esta fuente no está en la caché de Real-Debrid. Prueba otra.", "This source isn't cached on Real-Debrid. Try another."), notCached = true))
                return@flow // el finally borra el torrent
            }

            // Se vuelve a elegir el archivo sobre la info nueva: los flags `selected` han cambiado.
            val link = pickFile(ready.files, source)?.let { ready.linkFor(it) }
                ?: ready.links.firstOrNull()
                ?: error(tr("Real-Debrid no devolvió enlaces", "Real-Debrid returned no links"))

            emit(ResolveState.Progress(tr("Desbloqueando enlace…", "Unrestricting link…")))
            val url = realDebrid.unrestrict(link)
            keep = true
            emit(ResolveState.Ready(url))
        } finally {
            // Limpieza aunque la corrutina se cancele (usuario pulsa Atrás durante la resolución).
            if (!keep) withContext(NonCancellable) { realDebrid.deleteTorrent(torrentId) }
        }
    }.catch { e ->
        emit(
            ResolveState.Failed(
                e.message ?: tr("Error resolviendo la fuente", "Error resolving the source"),
                blocked = e is TorrentBlockedException,
            ),
        )
    }

    /**
     * Elige el archivo de vídeo que corresponde a la fuente:
     *  1. Por nombre (behaviorHints.filename): lo más fiable, imprescindible en packs de temporada.
     *  2. Por índice: Torrentio da fileIdx en base 0; RD numera los archivos en base 1.
     *  3. Vídeo más grande (películas de un solo archivo + extras/samples).
     */
    private fun pickFile(files: List<RdTorrentFile>, source: StreamSource): RdTorrentFile? {
        val videos = files.filter { it.isVideo }.ifEmpty { files }
        source.fileName?.let { name ->
            videos.firstOrNull { it.name.substringAfterLast('/').equals(name, ignoreCase = true) }
                ?.let { return it }
        }
        // Pack de temporada de los buscadores propios: el archivo del episodio por su nombre (S01E01, 1x01, E01…).
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

    /** Consulta el torrent cada [POLL_INTERVAL_MS] hasta que cumpla [done]. Null si expira [timeoutMs]. */
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
                error(tr("Real-Debrid marcó el torrent como '${info.status}'", "Real-Debrid marked the torrent as '${info.status}'"))
            }
            if (done(info)) return info
        }
        return null
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L

        /** Conversión de magnet: rápida si RD ya tiene los metadatos; si no, depende de peers. */
        const val MAGNET_TIMEOUT_MS = 15_000L

        /** Un torrent cacheado pasa a `downloaded` en 1–3 s tras selectFiles. */
        const val CACHE_TIMEOUT_MS = 6_000L
    }
}
