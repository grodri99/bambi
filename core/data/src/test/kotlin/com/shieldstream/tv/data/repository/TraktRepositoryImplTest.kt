package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.MyListChange
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.ScrobbleAction
import com.shieldstream.tv.domain.TraktCredentials
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TraktRepositoryImplTest {

    private val json = Json
    private lateinit var context: Context

    @Before
    fun clearSyncState() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("trakt_sync", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `stale refresh failure cannot erase a session renewed by another caller`() {
        val fixture = fixture(expiresAt = System.currentTimeMillis() + 1_000)
        val secondRefreshEntered = CountDownLatch(1)
        val refreshedSessionSaved = CountDownLatch(1)
        val refreshCall = AtomicInteger()
        fixture.onSessionSaved = { raw ->
            if ("new-access" in raw) refreshedSessionSaved.countDown()
        }
        coEvery { fixture.client.post("/oauth/token", any(), null) } coAnswers {
            val call = refreshCall.incrementAndGet()
            secondRefreshEntered.countDown()
            if (call == 1) {
                // A fixed per-profile refresh lock has no second HTTP request; let that path proceed too.
                secondRefreshEntered.await(250, TimeUnit.MILLISECONDS)
                TraktResponse(200, refreshedSession())
            } else {
                // Reproduce the stale single-use refresh response only after the new session is stored.
                refreshedSessionSaved.await(2, TimeUnit.SECONDS)
                TraktResponse(400, null)
            }
        }
        coEvery {
            fixture.client.post(match { it.startsWith("/scrobble/") }, any(), any())
        } returns TraktResponse(200, null)
        coEvery { fixture.client.post("/sync/history", any(), any()) } returns TraktResponse(200, null)

        fixture.repository.setWatched(listOf("movie:1"), watched = true)
        fixture.repository.scrobble("movie:1", 0.4f, ScrobbleAction.PAUSE)
        runBlocking { delay(750) }

        assertEquals(1, refreshCall.get())
        val saved = fixture.secrets[TraktSession.storeKey(PROFILE)]
        assertNotNull("sessions=${fixture.secrets}", saved)
        assertTrue("sessions=${fixture.secrets}", "new-access" in saved!!)
        assertEquals("viewer", fixture.profiles.active.value.traktUser)
    }

    @Test
    fun `scrobble requests complete in pause then stop order`() {
        val fixture = fixture(expiresAt = System.currentTimeMillis() + 86_400_000)
        val pauseStarted = CountDownLatch(1)
        val releasePause = CountDownLatch(1)
        val stopFinished = CountDownLatch(1)
        val completions = CopyOnWriteArrayList<ScrobbleAction>()
        coEvery { fixture.client.post("/scrobble/pause", any(), any()) } coAnswers {
            pauseStarted.countDown()
            releasePause.await(2, TimeUnit.SECONDS)
            completions += ScrobbleAction.PAUSE
            TraktResponse(200, null)
        }
        coEvery { fixture.client.post("/scrobble/stop", any(), any()) } coAnswers {
            completions += ScrobbleAction.STOP
            stopFinished.countDown()
            TraktResponse(200, null)
        }

        fixture.repository.scrobble("movie:1", 0.4f, ScrobbleAction.PAUSE)
        assertTrue(pauseStarted.await(1, TimeUnit.SECONDS))
        fixture.repository.scrobble("movie:1", 0.4f, ScrobbleAction.STOP)
        stopFinished.await(250, TimeUnit.MILLISECONDS)
        releasePause.countDown()
        runBlocking { delay(100) }

        assertEquals(listOf(ScrobbleAction.PAUSE, ScrobbleAction.STOP), completions)
    }

    @Test
    fun `watchlist removal keeps its originating profile after active profile changes`() {
        val fixture = fixture(expiresAt = System.currentTimeMillis() + 86_400_000)
        fixture.profiles.save(Profile(id = OTHER_PROFILE, name = "Other", avatar = 1))
        val removalSent = CountDownLatch(1)
        coEvery { fixture.client.post("/sync/watchlist/remove", any(), "old-access") } coAnswers {
            removalSent.countDown()
            TraktResponse(200, null)
        }
        fixture.profiles.select(OTHER_PROFILE)
        assertEquals(OTHER_PROFILE, fixture.profiles.active.value.id)

        runBlocking {
            (fixture.myList.changes as MutableSharedFlow<MyListChange>).emit(
                MyListChange(PROFILE, MediaType.MOVIE, "1", added = false),
            )
        }

        assertTrue("removal was not sent to the originating profile", removalSent.await(1, TimeUnit.SECONDS))
    }

    private fun fixture(expiresAt: Long): Fixture {
        val profile = Profile(id = PROFILE, name = "Viewer", avatar = 0, traktUser = "viewer")
        val profiles = FakeProfileRepository(profile)
        val secrets = ConcurrentHashMap<String, String>()
        val original = TraktSession("old-access", "old-refresh", expiresAt, "viewer")
        secrets[TraktSession.storeKey(PROFILE)] = json.encodeToString(TraktSession.serializer(), original)
        val fixture = Fixture(profiles = profiles, secrets = secrets)
        val tokens = mockk<TokenStore>()
        every { tokens.getSecret(any()) } answers { secrets[firstArg()] }
        every { tokens.setSecret(any(), any()) } answers {
            val value = secondArg<String?>()
            if (value == null) secrets.remove(firstArg()) else secrets[firstArg()] = value
            if (value != null) fixture.onSessionSaved(value)
        }
        val watchProgress = mockk<WatchProgressRepository>(relaxed = true) {
            every { progress } returns MutableStateFlow(emptyMap())
            every { cleared } returns MutableSharedFlow()
        }
        val myList = mockk<MyListRepository>(relaxed = true) {
            every { entries } returns MutableStateFlow(emptyList())
            every { changes } returns MutableSharedFlow()
        }
        fixture.myList = myList
        fixture.repository = TraktRepositoryImpl(
            context = context,
            client = fixture.client,
            credentials = TraktCredentials(clientId = "client-id"),
            profiles = profiles,
            progress = watchProgress,
            myList = myList,
            tokens = tokens,
            cleanup = ProfileDataCleaner(context, tokens),
            json = json,
            texts = mockk<Texts>(relaxed = true),
        )
        return fixture
    }

    private fun refreshedSession() = buildJsonObject {
        put("access_token", "new-access")
        put("refresh_token", "new-refresh")
        put("created_at", System.currentTimeMillis() / 1_000)
        put("expires_in", 86_400)
    }

    private class Fixture(
        val profiles: FakeProfileRepository,
        val secrets: MutableMap<String, String>,
    ) {
        val client = mockk<TraktClient>()
        lateinit var myList: MyListRepository
        lateinit var repository: TraktRepositoryImpl
        var onSessionSaved: (String) -> Unit = {}
    }

    private class FakeProfileRepository(initial: Profile) : ProfileRepository {
        override val profiles = MutableStateFlow(listOf(initial))
        override val active = MutableStateFlow(initial)
        override fun select(id: String) {
            profiles.value.firstOrNull { it.id == id }?.let { active.value = it }
        }
        override fun save(profile: Profile): Profile {
            profiles.value = if (profiles.value.any { it.id == profile.id }) {
                profiles.value.map { if (it.id == profile.id) profile else it }
            } else {
                profiles.value + profile
            }
            if (active.value.id == profile.id) active.value = profile
            return profile
        }
        override fun draft(): Profile = active.value
        override fun delete(id: String) = Unit
    }

    private companion object {
        const val PROFILE = "profile"
        const val OTHER_PROFILE = "other-profile"
    }
}
