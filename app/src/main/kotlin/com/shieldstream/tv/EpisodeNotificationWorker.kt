package com.shieldstream.tv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shieldstream.tv.domain.EpisodeReleaseRepository
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.model.WatchProgress
import com.shieldstream.tv.model.tr
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class EpisodeNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val releases: EpisodeReleaseRepository,
    private val tracking: EpisodeTrackingRepository,
    private val trakt: TraktRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!tracking.notificationsEnabled.value) return Result.success()
        val followed = tracking.followedShowIds.value
        if (followed.isEmpty()) return Result.success()
        trakt.syncActive(force = false)
        val statuses = followed.mapNotNull { showId ->
            runCatching { releases.status(showId) }.getOrNull()
        }
        if (statuses.isEmpty()) return Result.retry()

        val current = statuses.flatMap { status ->
            status.availableEpisodes.map { episode ->
                Triple(status, episode, WatchProgress.episodeKey(status.item.id, episode.season, episode.number))
            }
        }
        val notified = tracking.notifiedEpisodeKeys.value
        if (notified.isEmpty()) {
            tracking.markNotified(current.map { it.third })
            return Result.success()
        }
        val fresh = current.filterNot { it.third in notified }
        if (fresh.isEmpty()) return Result.success()

        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return Result.success()
        createChannel()
        val delivered = mutableListOf<String>()
        fresh.groupBy { it.first.item.key }.values.take(MAX_NOTIFICATIONS).forEach { entries ->
                val status = entries.first().first
                val latest = entries.maxBy { it.second.airDate.orEmpty() }.second
                val text = if (entries.size == 1) {
                    "${latest.code} · ${latest.title}"
                } else {
                    tr("${entries.size} episodios nuevos · Último: ${latest.code}", "${entries.size} new episodes · Latest: ${latest.code}")
                }
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pending = PendingIntent.getActivity(
                    applicationContext,
                    status.item.id.toIntOrNull() ?: status.item.key.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(status.item.title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setNumber(entries.size)
                    .setGroup(GROUP_KEY)
                    .build()
                val posted = runCatching {
                    NotificationManagerCompat.from(applicationContext).notify(status.item.key.hashCode(), notification)
                }.isSuccess
                if (posted) delivered += entries.map { it.third }
        }
        tracking.markNotified(delivered)
        return Result.success()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            tr("Nuevos episodios", "New episodes"),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "new_episodes"
        const val GROUP_KEY = "com.shieldstream.tv.NEW_EPISODES"
        const val MAX_NOTIFICATIONS = 5
    }
}

object EpisodeNotificationScheduler {
    private const val UNIQUE_NAME = "new_episode_checks"

    fun sync(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<EpisodeNotificationWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
