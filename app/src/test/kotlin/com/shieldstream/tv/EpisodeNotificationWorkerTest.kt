package com.shieldstream.tv

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerParameters
import com.shieldstream.tv.domain.EpisodeReleaseRepository
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.ShowEpisodeStatus
import com.shieldstream.tv.model.WatchProgress
import com.shieldstream.tv.strings.Texts
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpisodeNotificationWorkerTest {

    private lateinit var notificationManager: NotificationManagerCompat

    @Before
    fun setUpNotifications() {
        mockkStatic(NotificationManagerCompat::class)
        notificationManager = mockk(relaxed = true)
        every { NotificationManagerCompat.from(any()) } returns notificationManager
    }

    @After
    fun tearDownNotifications() {
        unmockkStatic(NotificationManagerCompat::class)
    }

    @Test
    fun `episodes stay pending when system notifications are disabled`() = runTest {
        every { notificationManager.areNotificationsEnabled() } returns false
        val tracking = FakeEpisodeTrackingRepository(followed = linkedSetOf("1"))
        val worker = worker(tracking, releasesFor(1))

        worker.doWork()

        assertEquals(setOf(BASELINE), tracking.notifiedEpisodeKeys.value)
    }

    @Test
    fun `episodes beyond notification limit stay pending for a later run`() = runTest {
        every { notificationManager.areNotificationsEnabled() } returns true
        val ids = (1..6).toList()
        val tracking = FakeEpisodeTrackingRepository(followed = ids.mapTo(linkedSetOf()) { it.toString() })
        val worker = worker(tracking, releasesFor(*ids.toIntArray()))

        worker.doWork()

        verify(exactly = 5) { notificationManager.notify(any(), any()) }
        assertEquals(
            setOf(BASELINE) + (1..5).map { WatchProgress.episodeKey(it.toString(), 1, 1) },
            tracking.notifiedEpisodeKeys.value,
        )
    }

    private fun worker(
        tracking: EpisodeTrackingRepository,
        releases: EpisodeReleaseRepository,
    ) = EpisodeNotificationWorker(
        context = ApplicationProvider.getApplicationContext(),
        params = mockk<WorkerParameters>(relaxed = true),
        releases = releases,
        tracking = tracking,
        trakt = mockk<TraktRepository>(relaxed = true),
        texts = mockk<Texts>(relaxed = true),
    )

    private fun releasesFor(vararg ids: Int) = object : EpisodeReleaseRepository {
        override suspend fun status(showId: String): ShowEpisodeStatus? =
            ids.firstOrNull { it.toString() == showId }?.let { status(it) }

        override suspend fun isComplete(showId: String): Boolean = false
    }

    private fun status(id: Int): ShowEpisodeStatus {
        val item = CatalogItem(
            id = id.toString(),
            imdbId = null,
            type = MediaType.TV,
            title = "Show $id",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            year = null,
            rating = null,
        )
        return ShowEpisodeStatus(
            item = item,
            availableEpisodes = listOf(Episode(1, 1, "Episode", "", null, 45, "2026-01-01")),
        )
    }

    private class FakeEpisodeTrackingRepository(
        followed: Set<String>,
    ) : EpisodeTrackingRepository {
        override val followedShowIds = MutableStateFlow(followed)
        override val dismissedEpisodeKeys = MutableStateFlow<Set<String>>(emptySet())
        override val notifiedEpisodeKeys = MutableStateFlow(setOf(BASELINE))
        override val notificationsEnabled = MutableStateFlow(true)

        override fun isFollowing(showId: String): Boolean = showId in followedShowIds.value
        override fun setFollowing(showId: String, following: Boolean) = Unit
        override fun dismissEpisodes(keys: Collection<String>) = Unit
        override fun setNotificationsEnabled(enabled: Boolean) = Unit

        override fun markNotified(keys: Collection<String>) {
            notifiedEpisodeKeys.value = notifiedEpisodeKeys.value + keys
        }
    }

    private companion object {
        const val BASELINE = "baseline"
    }
}
