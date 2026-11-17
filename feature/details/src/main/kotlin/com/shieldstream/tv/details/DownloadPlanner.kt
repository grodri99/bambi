package com.shieldstream.tv.details

import android.util.Log
import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.DownloadEnqueueResult
import com.shieldstream.tv.domain.DownloadsRepository
import com.shieldstream.tv.domain.PrioritizeSourcesUseCase
import com.shieldstream.tv.domain.RealDebridRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.media.DeviceCapabilityDetector
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.DeviceOptimization
import com.shieldstream.tv.model.Download
import com.shieldstream.tv.model.DownloadNames
import com.shieldstream.tv.model.DownloadQuality
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.WatchProgress
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import com.shieldstream.tv.strings.episodeCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** "Preparando descargas: 3 de 10". */
data class PlannerProgress(val title: String, val done: Int, val total: Int)

/**
 * Prepara descargas (una película, un episodio o una temporada entera): busca las fuentes de cada título, elige la
 * mejor que ya esté en el debrid con el tope de calidad pedido y la pone en cola. Vive fuera de la ficha, así que
 * sigue aunque se salga de ella. El enlace no se pide aquí: lo pide la descarga cuando le toca.
 */
@Singleton
class DownloadPlanner @Inject constructor(
    private val catalog: CatalogRepository,
    private val debridCache: com.shieldstream.tv.domain.DebridCacheRepository,
    private val prioritize: PrioritizeSourcesUseCase,
    private val deviceCapabilities: DeviceCapabilityDetector,
    private val settings: SettingsRepository,
    private val downloads: DownloadsRepository,
    private val texts: Texts,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val oneBatchAtATime = Mutex()
    private val _progress = MutableStateFlow<PlannerProgress?>(null)
    val progress: StateFlow<PlannerProgress?> = _progress.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** [episodes] vacío = película. */
    fun plan(item: CatalogItem, originalTitle: String?, episodes: List<Episode>, quality: DownloadQuality) {
        scope.launch {
            oneBatchAtATime.withLock {
                val targets: List<Episode?> = episodes.ifEmpty { listOf(null) }
                var started = 0
                var skipped = 0
                var lastProblem: String? = null
                targets.forEachIndexed { index, episode ->
                    _progress.value = PlannerProgress(item.title, index, targets.size)
                    val key = episode?.let { WatchProgress.episodeKey(item.id, it.season, it.number) } ?: WatchProgress.movieKey(item.id)
                    val existing = downloads.find(key)
                    if (existing != null && (existing.isDone || existing.isActive)) {
                        skipped++
                        return@forEachIndexed
                    }
                    val source = runCatching { pickSource(item, episode, quality) }
                        .onFailure { Log.w(TAG, "Fuentes para descargar: ${it.message}") }
                        .getOrNull()
                    if (source == null) {
                        lastProblem = texts.get(StringsR.string.downloads_no_source)
                        return@forEachIndexed
                    }
                    val extension = DownloadNames.extensionFor(source.fileName ?: source.title, source.directUrl ?: "")
                    val request = Download(
                        id = key,
                        itemId = item.id,
                        type = item.type,
                        title = item.title,
                        episodeLabel = episode?.let { "${texts.episodeCode(it)} · ${it.title}" },
                        season = episode?.season,
                        episode = episode?.number,
                        imdbId = item.imdbId,
                        year = item.year,
                        originalTitle = originalTitle,
                        posterUrl = item.posterUrl,
                        backdropUrl = episode?.stillUrl ?: item.backdropUrl,
                        source = source,
                        filePath = DownloadNames.fileName(item.title, item.year, episode?.season, episode?.number, episode?.title, extension),
                        sourceTitle = source.title,
                    )
                    when (val result = downloads.enqueue(request, source.sizeBytes)) {
                        DownloadEnqueueResult.Started -> started++
                        DownloadEnqueueResult.AlreadyDownloaded, DownloadEnqueueResult.AlreadyRunning -> skipped++
                        DownloadEnqueueResult.NoStorage -> lastProblem = texts.get(StringsR.string.downloads_no_storage)
                        is DownloadEnqueueResult.NoSpace -> {
                            lastProblem = texts.get(StringsR.string.downloads_no_space, gb(result.neededBytes), gb(result.freeBytes))
                            // Sin sitio no tiene sentido seguir con el resto de la temporada.
                            _progress.value = null
                            _messages.tryEmit(summary(started, skipped, lastProblem))
                            return@withLock
                        }
                    }
                }
                _progress.value = null
                _messages.tryEmit(summary(started, skipped, lastProblem))
            }
        }
    }

    private fun summary(started: Int, skipped: Int, problem: String?): String = when {
        started == 0 && problem != null -> problem
        started == 0 && skipped > 0 -> texts.get(StringsR.string.downloads_already_done)
        started == 1 -> texts.get(StringsR.string.downloads_started)
        else -> texts.get(StringsR.string.downloads_started_many, started)
    }

    /** La misma elección que al reproducir (castellano primero, exclusiones, fuentes rotas), solo entre las cacheadas. */
    private suspend fun pickSource(item: CatalogItem, episode: Episode?, quality: DownloadQuality): StreamSource? {
        val spanishFirst = settings.spanishFirst.first()
        val spanishOnly = settings.spanishOnlySources.first()
        val exclusions = settings.sourceExclusions.first()
        val broken = settings.brokenSourceIds.first()
        val capabilities = deviceCapabilities.capabilities
        val hardwareCodecs = when (settings.deviceOptimization.first()) {
            DeviceOptimization.AUTOMATIC -> capabilities.hardwareVideoCodecs
            DeviceOptimization.CUSTOM -> emptySet()
        }
        val results = withTimeoutOrNull(STREAMS_TIMEOUT_MS) { catalog.getStreams(item, episode).toList() }
            ?.lastOrNull() ?: return null
        val rdCached = if (results.sources.any { it.resolveUrl == null }) {
            runCatching { debridCache.cachedAmong(results.sources.filter { it.resolveUrl == null }.map { it.infoHash }) }
                .getOrDefault(emptySet())
        } else {
            emptySet()
        }
        val known = rdCached + results.sources.filter { it.isCached }.map { it.infoHash.lowercase() }
        val enriched = results.sources.map { if (it.resolveUrl == null) it.copy(isCached = it.infoHash.lowercase() in known) else it }
        val preferred = settings.preferredQuality.first()
        return prioritize(
            enriched,
            capabilities.supportedHdr,
            hardwareVideoCodecs = hardwareCodecs,
            preferSpanish = spanishFirst || spanishOnly,
            preferred = preferred,
        )
            .filter { !it.isMarkedBroken(broken) && !exclusions.excludes(it) && (!spanishOnly || it.advertisesSpanishAudio) }
            .filter { it.isCached || it.directUrl != null }
            .filter { it.quality.rank <= quality.maxRank }
            .firstOrNull()
    }

    private fun gb(bytes: Long) = String.format(Locale.getDefault(), "%.1f GB", bytes / 1_073_741_824.0)

    private companion object {
        const val TAG = "DownloadPlanner"
        const val STREAMS_TIMEOUT_MS = 60_000L
    }
}
