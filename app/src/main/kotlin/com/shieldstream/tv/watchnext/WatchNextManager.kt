package com.shieldstream.tv.watchnext

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.ContinueEntry
import com.shieldstream.tv.domain.ContinueWatching
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.domain.mapConcurrent
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchNextManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profiles: ProfileRepository,
    private val watchProgress: WatchProgressRepository,
    private val catalog: CatalogRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null

    @OptIn(FlowPreview::class)
    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || syncJob != null) return
        syncJob = scope.launch {
            combine(profiles.active, watchProgress.progress) { profile, progress ->
                WatchNextSnapshot(
                    profileId = profile.id,
                    entries = ContinueWatching.entries(progress, HomeFilter.ALL).take(MAX_PROGRAMS),
                )
            }
                // Player progress is saved every 10 seconds. Waiting for a quiet period avoids
                // rewriting launcher rows and fetching metadata throughout playback.
                .debounce(SYNC_QUIET_PERIOD_MS)
                .collect { snapshot ->
                    runCatching { sync(snapshot) }
                }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun sync(snapshot: WatchNextSnapshot) {
        val candidateIds = snapshot.entries.mapTo(mutableSetOf()) { internalId(snapshot.profileId, it) }
        val loaded = snapshot.entries.mapConcurrent(concurrency = 4) { entry ->
            runCatching { catalog.getItem(entry.type, entry.id) }.getOrNull()?.let { item ->
                DesiredProgram(
                    internalId = internalId(snapshot.profileId, entry),
                    values = programValues(snapshot.profileId, item, entry),
                )
            }
        }.filterNotNull()

        val existing = readExistingPrograms()
        val existingByInternalId = existing.mapNotNull { program ->
            program.internalId?.let { it to program }
        }.toMap()

        existing.forEach { program ->
            // Keep an existing row when its metadata request failed. It will be retried on the
            // next meaningful progress change instead of disappearing during a network outage.
            if (program.internalId !in candidateIds) {
                context.contentResolver.delete(
                    TvContractCompat.buildWatchNextProgramUri(program.providerId),
                    null,
                    null,
                )
            }
        }

        loaded.forEach { desired ->
            val current = existingByInternalId[desired.internalId]
            if (current == null) {
                context.contentResolver.insert(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    desired.values,
                )
            } else {
                context.contentResolver.update(
                    TvContractCompat.buildWatchNextProgramUri(current.providerId),
                    desired.values,
                    null,
                    null,
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun readExistingPrograms(): List<ExistingProgram> {
        val programs = mutableListOf<ExistingProgram>()
        context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            null,
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val program = WatchNextProgram.fromCursor(cursor)
                if (program.id >= 0L) {
                    programs += ExistingProgram(program.id, program.internalProviderId)
                }
            }
        }
        return programs
    }

    private data class WatchNextSnapshot(
        val profileId: String,
        val entries: List<ContinueEntry>,
    )

    private data class DesiredProgram(
        val internalId: String,
        val values: android.content.ContentValues,
    )

    private data class ExistingProgram(
        val providerId: Long,
        val internalId: String?,
    )

    companion object {
        private const val MAX_PROGRAMS = 10
        private const val SYNC_QUIET_PERIOD_MS = 15_000L

        internal fun internalId(profileId: String, entry: ContinueEntry): String = buildString {
            append(profileId)
            append(':').append(entry.type.name)
            append(':').append(entry.id)
            entry.season?.let { append(':').append(it) }
            entry.episode?.let { append(':').append(it) }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        internal fun programValues(
            profileId: String,
            item: CatalogItem,
            entry: ContinueEntry,
        ): android.content.ContentValues {
            val deepLink = Uri.Builder()
                .scheme("shieldstream")
                .authority("resume")
                .appendPath(item.type.name.lowercase())
                .appendPath(item.id)
                .appendQueryParameter("profile", profileId)
                .apply {
                    entry.season?.let { appendQueryParameter("season", it.toString()) }
                    entry.episode?.let { appendQueryParameter("episode", it.toString()) }
                }
                .build()

            return WatchNextProgram.Builder()
                .setType(
                    if (item.type == MediaType.MOVIE) {
                        TvContractCompat.WatchNextPrograms.TYPE_MOVIE
                    } else {
                        TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
                    },
                )
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setTitle(item.title)
                .setDescription(item.overview)
                .setIntentUri(deepLink)
                .setInternalProviderId(internalId(profileId, entry))
                .setLastEngagementTimeUtcMillis(entry.progress.updatedAt)
                .setLastPlaybackPositionMillis(entry.progress.positionMs.asProviderMillis())
                .setDurationMillis(entry.progress.durationMs.asProviderMillis())
                .apply {
                    item.posterUrl?.takeIf { it.isNotBlank() }?.let { setPosterArtUri(Uri.parse(it)) }
                    entry.season?.let(::setSeasonNumber)
                    entry.episode?.let(::setEpisodeNumber)
                }
                .build()
                .toContentValues()
        }

        private fun Long.asProviderMillis(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
}
