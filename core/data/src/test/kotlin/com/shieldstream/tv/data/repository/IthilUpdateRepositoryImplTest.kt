package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.domain.IthilConfig
import com.shieldstream.tv.domain.IthilLibraryInfo
import com.shieldstream.tv.domain.IthilLibraryRepository
import com.shieldstream.tv.domain.IthilLibraryState
import com.shieldstream.tv.domain.IthilSource
import com.shieldstream.tv.network.api.IthilApi
import com.shieldstream.tv.network.api.IthilContentDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IthilUpdateRepositoryImplTest {
    private lateinit var context: Context
    private lateinit var api: FakeApi
    private lateinit var library: FakeLibrary
    private lateinit var repository: IthilUpdateRepositoryImpl
    private val remote = IthilContentDto("fixture_3_3_11.zm3", "new", 100, "https://raw.test/new.zm3")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("ithil_updates", Context.MODE_PRIVATE).edit().clear().commit()
        api = FakeApi()
        library = FakeLibrary()
        repository = IthilUpdateRepositoryImpl(
            context,
            IthilConfig("https://api.test/repo", "secret", "fixture"),
            api,
            library,
        )
    }

    @Test
    fun unchangedShaSkipsDownloadAndReportsUpToDate() = runTest {
        library.current = library.current.copy(sourceSha = "old")
        api.entries = listOf(remote.copy(sha = "old"))

        val result = repository.downloadNow()

        assertEquals(library.current, result)
        assertEquals(0, library.installCalls)
        assertTrue(repository.state.value is IthilLibraryState.UpToDate)
    }

    @Test
    fun changedShaReportsAvailableAndThrottlesChecks() = runTest {
        api.entries = listOf(remote.copy(name = "fixture_3_3_10.zm3", sha = "older"), remote)

        repository.checkForUpdates()

        val available = repository.state.value as IthilLibraryState.Available
        assertEquals("new", available.update.sha)
        assertEquals("3.3.11", available.update.version)
        assertEquals(0, library.installCalls)
        repository.checkForUpdates()
        assertEquals(1, api.calls)
        library.ready = false
        repository.checkForUpdates()
        assertEquals(1, api.calls)
    }

    @Test
    fun failedCheckUsesBackoffWithoutHammeringMetadata() = runTest {
        api.error = IOException("offline")
        var now = 1_000L
        repository.clock = { now }

        repository.checkForUpdates()
        repository.checkForUpdates()
        assertEquals(1, api.calls)

        now += 60L * 60L * 1000L
        repository.checkForUpdates()
        assertEquals(2, api.calls)
    }

    @Test
    fun deleteClearsLibraryAndUpdateState() = runTest {
        repository.delete()

        assertEquals(IthilLibraryState.NotInstalled, repository.state.value)
        assertEquals(1, library.deleteCalls)
    }

    private class FakeApi : IthilApi {
        var entries = listOf<IthilContentDto>()
        var error: IOException? = null
        var calls = 0

        override suspend fun contents(url: String): List<IthilContentDto> {
            calls++
            error?.let { throw it }
            return entries
        }
    }

    private class FakeLibrary : IthilLibraryRepository {
        var current = IthilLibraryInfo("3.3.11", "old", 8, 7)
        var ready = true
        var installCalls = 0
        var deleteCalls = 0

        override suspend fun install(downloadUrl: String, sourceSha: String, onProgress: (Float) -> Unit): IthilLibraryInfo {
            installCalls++
            onProgress(1f)
            current = current.copy(sourceSha = sourceSha)
            ready = true
            return current
        }

        override fun isReady(): Boolean = ready
        override fun info(): IthilLibraryInfo? = current.takeIf { ready }
        override fun movieSources(tmdb: Int): List<IthilSource> = emptyList()
        override fun episodeSources(tmdb: Int, season: Int, episode: Int): List<IthilSource> = emptyList()

        override suspend fun delete() {
            deleteCalls++
            ready = false
        }
    }
}
