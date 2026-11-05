package com.shieldstream.tv.data.repository

import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.EpisodeReleaseRepository
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.NewEpisodes
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.domain.mapConcurrent
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.ShowEpisodeStatus
import com.shieldstream.tv.model.WatchProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodeReleaseRepositoryImpl @Inject constructor(
    private val catalog: CatalogRepository,
    private val watchProgress: WatchProgressRepository,
    private val tracking: EpisodeTrackingRepository,
) : EpisodeReleaseRepository {

    override suspend fun status(showId: String): ShowEpisodeStatus? {
        val airing = catalog.getShowAiring(showId) ?: return null
        val now = System.currentTimeMillis()
        val (cutoff, today) = NewEpisodes.dateWindow(now)
        val watched = watchProgress.progress.value.filterValues { it.isWatched }.keys
        val dismissed = tracking.dismissedEpisodeKeys.value
        val latest = airing.latestEpisode

        val candidates = if (latest?.airDate?.let { it in cutoff..today } == true) {
            runCatching { catalog.getEpisodes(airing.item, latest.season) }
                .getOrElse { listOf(latest) }
                .ifEmpty { listOf(latest) }
        } else {
            emptyList()
        }
        val available = candidates
            .filter { NewEpisodes.isNew(showId, it, watched, cutoff, today, dismissed) }
            .sortedWith(compareByDescending<Episode> { it.airDate }.thenByDescending { it.number })
        val upcoming = airing.nextEpisode?.takeIf {
            NewEpisodes.isUpcoming(it, today, NewEpisodes.futureDate(now))
        }
        return ShowEpisodeStatus(airing.item, available, upcoming)
    }

    override suspend fun isComplete(showId: String): Boolean {
        val airing = catalog.getShowAiring(showId) ?: return false
        if (airing.seasons.isEmpty()) return false
        val today = NewEpisodes.dateWindow(System.currentTimeMillis()).second
        val seasonResults = airing.seasons.mapConcurrent(concurrency = 4) { season ->
            runCatching { catalog.getEpisodes(airing.item, season.number) }
        }
        // Una temporada que no se pudo descargar no demuestra que esté vista: el estado es desconocido.
        if (seasonResults.any { it.isFailure }) return false
        val episodes = seasonResults.flatMap { it.getOrThrow() }.filter { episode ->
            episode.airDate?.let { it <= today } == true
        }
        if (episodes.isEmpty()) return false
        val watched = watchProgress.progress.value.filterValues { it.isWatched }.keys
        return episodes.all { WatchProgress.episodeKey(showId, it.season, it.number) in watched }
    }
}
