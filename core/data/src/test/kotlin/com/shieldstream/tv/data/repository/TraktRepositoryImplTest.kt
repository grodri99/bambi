package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.MyListChange
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.ScrobbleAction
import com.shieldstream.tv.domain.TraktCredentials
import com.shieldstream.tv.domain.TraktRatingTarget
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `recommendations return ordered TMDB ids for movies and shows`() {
        val fixture = fixture(expiresAt = System.currentTimeMillis() + 86_400_000)
        coEvery { fixture.client.get("/recommendations/movies?limit=40", "old-access") } returns TraktResponse(
            200,
            JsonArray(listOf(recommendation(20), recommendation(10), recommendation(20), recommendation(null))),
        )
        coEvery { fixture.client.get("/recommendations/shows?limit=40", "old-access") } returns TraktResponse(
            200,
            JsonArray(listOf(recommendation(30))),
        )

        val (movies, shows) = runBlocking {
            fixture.repository.recommendations(PROFILE, MediaType.MOVIE) to
                fixture.repository.recommendations(PROFILE, MediaType.TV)
        }

        assertEquals(listOf("20", "10"), movies)
        assertEquals(listOf("30"), shows)
    }

    @Test
    fun `episode rating lookup matches show TMDB id and episode numbers`() {
        val fixture = fixture(expiresAt = System.currentTimeMillis() + 86_400_000)
        coEvery {
            fixture.client.get("/sync/ratings/episodes?page=1&limit=1000", "old-access")
        } returns TraktResponse(
            200,
            JsonArray(
                listOf(
                    ratedEpisode(showId = 41, season = 2, episode = 3, rating = 4),
                    ratedEpisode(showId = 42, season = 2, episode = 2, rating = 5),
                    ratedEpisode(showId = 42, season = 2, episode = 3, rating = 9),
                ),
            ),
        )

        val rating = runBlocking {
            fixture.repository.getRating(TraktRatingTarget.Episode("42", 2, 3))
        }

        assertEquals(9, rating)
    }

    @Test
    fun `movie and show ratings use their respective payload collections`() {
        val fixture = fixture(expiresAt = System.currentTimeMillis() + 86_400_000)
        val bodies = mutableListOf<JsonElement>()
        coEvery { fixture.client.post("/sync/ratings", any(), "old-access") } coAnswers {
            bodies += secondArg<JsonElement>()
            TraktResponse(200, null)
        }

        runBlocking { fixture.repository.setRating(TraktRatingTarget.Movie("550"), 8) }
        runBlocking { fixture.repository.setRating(TraktRatingTarget.Show("1399"), 7) }

        val movie = bodies[0].jsonObject["movies"]!!.jsonArray[0].jsonObject
        assertEquals(550, movie["ids"]?.jsonObject?.get("tmdb")?.jsonPrimitive?.intOrNull)
        assertEquals(8, movie["rating"]?.jsonPrimitive?.intOrNull)
        val show = bodies[1].jsonObject["shows"]!!.jsonArray[0].jsonObject
        assertEquals(1399, show["ids"]?.jsonObject?.get("tmdb")?.jsonPrimitive?.intOrNull)
        assertEquals(7, show["rating"]?.jsonPrimitive?.intOrNull)
        assertNull(show["seasons"])
    }

    @Test
    fun `episode rating add and removal use nested show payload`() {
        val fixture = fixture(expiresAt = System.currentTimeMillis() + 86_400_000)
        val requests = mutableListOf<Pair<String, JsonElement>>()
        coEvery {
            fixture.client.post(match { it.startsWith("/sync/ratings") }, any(), "old-access")
        } coAnswers {
            requests += firstArg<String>() to secondArg<JsonElement>()
            TraktResponse(200, null)
        }
        val target = TraktRatingTarget.Episode("42", 2, 3)

        runBlocking { fixture.repository.setRating(target, 9) }
        runBlocking { fixture.repository.setRating(target, null) }

        assertEquals(listOf("/sync/ratings", "/sync/ratings/remove"), requests.map { it.first })
        val addEpisode = nestedEpisode(requests[0].second)
        assertEquals(9, addEpisode["rating"]?.jsonPrimitive?.intOrNull)
        assertNotNull(addEpisode["rated_at"])
        val removeEpisode = nestedEpisode(requests[1].second)
        assertEquals(3, removeEpisode["number"]?.jsonPrimitive?.intOrNull)
        assertNull(removeEpisode["rating"])
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

    private fun ratedEpisode(showId: Int, season: Int, episode: Int, rating: Int) = buildJsonObject {
        put("rating", rating)
        put("show", buildJsonObject { put("ids", buildJsonObject { put("tmdb", showId) }) })
        put("episode", buildJsonObject {
            put("season", season)
            put("number", episode)
        })
    }

    private fun nestedEpisode(body: JsonElement): JsonObject {
        val show = body.jsonObject["shows"]!!.jsonArray[0].jsonObject
        assertEquals(42, show["ids"]?.jsonObject?.get("tmdb")?.jsonPrimitive?.intOrNull)
        val season = show["seasons"]!!.jsonArray[0].jsonObject
        assertEquals(2, season["number"]?.jsonPrimitive?.intOrNull)
        return season["episodes"]!!.jsonArray[0].jsonObject
    }

    private fun recommendation(tmdbId: Int?) = buildJsonObject {
        if (tmdbId != null) put("ids", buildJsonObject { put("tmdb", tmdbId) })
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
