package com.shieldstream.tv.domain

import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.RdTorrentFile
import com.shieldstream.tv.model.RdTorrentInfo
import com.shieldstream.tv.model.StreamSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResolveStreamUseCaseTest {

    @Test
    fun `direct URL completes without contacting either resolver`() = runTest {
        val fixture = Fixture()

        val states = fixture.useCase(source(directUrl = "https://video.test/direct")).toList()

        assertEquals(listOf(ResolveState.Ready("https://video.test/direct")), states)
        assertEquals(0, fixture.realDebrid.addMagnetCalls)
        assertTrue(fixture.torrentio.requests.isEmpty())
    }

    @Test
    fun `resolve URL uses the source debrid service`() = runTest {
        val fixture = Fixture()
        fixture.torrentio.result = DebridLink.Ready("https://video.test/resolved")

        val states = fixture.useCase(source(resolveUrl = "https://resolve.test", debrid = "TorBox")).toList()

        assertEquals("https://resolve.test" to "TorBox", fixture.torrentio.requests.single())
        assertEquals(ResolveState.Ready("https://video.test/resolved"), states.last())
    }

    @Test
    fun `resolve URL problem preserves the not cached flag`() = runTest {
        val fixture = Fixture()
        fixture.torrentio.result = DebridLink.Problem("not available", notCached = true)

        val states = fixture.useCase(source(resolveUrl = "https://resolve.test")).toList()

        assertEquals(ResolveState.Failed("not available", notCached = true), states.last())
    }

    @Test
    fun `Ithil URL uses AllDebrid without calling Torrentio`() = runTest {
        val fixture = Fixture()
        fixture.allDebrid.result = DebridLink.Ready("https://cdn.test/video")

        val states = fixture.useCase(
            source(resolveUrl = "https://hoster.test/?abc", debrid = ITHIL_ALLDEBRID),
        ).toList()

        assertEquals(listOf("https://hoster.test/?abc"), fixture.allDebrid.requests)
        assertTrue(fixture.torrentio.requests.isEmpty())
        assertEquals(ResolveState.Ready("https://cdn.test/video"), states.last())
    }

    @Test
    fun `raw magnet uses AllDebrid when it is the selected service`() = runTest {
        val fixture = Fixture()
        fixture.selectedDebrid.value = DebridService.ALLDEBRID
        fixture.allDebridTorrents.currentInfo = info(
            status = RdTorrentInfo.STATUS_DOWNLOADED,
            files = listOf(file(id = 1, name = "movie.mkv", selected = true)),
            links = listOf("alldebrid-link"),
        )

        val states = fixture.useCase(source()).toList()

        assertEquals(ResolveState.Ready("unrestricted:alldebrid-link"), states.last())
        assertEquals(1, fixture.allDebridTorrents.addMagnetCalls)
        assertEquals(0, fixture.realDebrid.addMagnetCalls)
    }

    @Test
    fun `raw magnet does not fall back to RealDebrid for an unsupported service`() = runTest {
        val fixture = Fixture()
        fixture.selectedDebrid.value = DebridService.TORBOX

        val states = fixture.useCase(source()).toList()

        assertEquals(ResolveState.Failed("unsupported TorBox"), states.last())
        assertEquals(0, fixture.realDebrid.addMagnetCalls)
        assertEquals(0, fixture.allDebridTorrents.addMagnetCalls)
    }

    @Test
    fun `AllDebrid season pack selects the requested episode link`() = runTest {
        val fixture = Fixture()
        fixture.selectedDebrid.value = DebridService.ALLDEBRID
        fixture.allDebridTorrents.currentInfo = info(
            status = RdTorrentInfo.STATUS_DOWNLOADED,
            files = listOf(
                file(id = 1, name = "Show.S01E01.mkv", selected = true).copy(link = "ad-e1"),
                file(id = 2, name = "Show.S01E02.mkv", selected = true).copy(link = "ad-e2"),
            ),
        )

        val states = fixture.useCase(source(season = 1, episode = 2)).toList()

        assertEquals(ResolveState.Ready("unrestricted:ad-e2"), states.last())
    }

    @Test
    fun `uncached AllDebrid magnet is deleted after the cache timeout`() = runTest {
        val fixture = Fixture()
        fixture.selectedDebrid.value = DebridService.ALLDEBRID
        fixture.allDebridTorrents.currentInfo = info(status = "Downloading", files = emptyList())

        val states = fixture.useCase(source()).toList()

        val failure = states.filterIsInstance<ResolveState.Failed>().single()
        assertTrue(failure.notCached)
        assertEquals(listOf("torrent"), fixture.allDebridTorrents.deleted)
        assertEquals(0, fixture.realDebrid.addMagnetCalls)
    }

    @Test
    fun `cancelling AllDebrid resolution still deletes the upload`() = runTest {
        val fixture = Fixture()
        fixture.selectedDebrid.value = DebridService.ALLDEBRID
        val pollStarted = CompletableDeferred<Unit>()
        fixture.allDebridTorrents.currentInfo = info(status = "Downloading", files = emptyList())
        fixture.allDebridTorrents.onTorrentInfo = { pollStarted.complete(Unit) }
        val collection = backgroundScope.launch { fixture.useCase(source()).toList() }

        pollStarted.await()
        collection.cancelAndJoin()
        advanceUntilIdle()

        assertEquals(listOf("torrent"), fixture.allDebridTorrents.deleted)
        assertEquals(0, fixture.realDebrid.addMagnetCalls)
    }

    @Test
    fun `existing torrent uses the requested file index`() = runTest {
        val fixture = Fixture()
        val files = listOf(
            file(id = 1, name = "first.mkv", selected = true),
            file(id = 2, name = "second.mkv", selected = true),
        )
        fixture.realDebrid.downloadedId = "existing"
        fixture.realDebrid.currentInfo = info(status = RdTorrentInfo.STATUS_DOWNLOADED, files = files, links = listOf("first", "second"))

        val states = fixture.useCase(source(fileIdx = 1)).toList()

        assertEquals(ResolveState.Ready("unrestricted:second"), states.last())
        assertEquals(0, fixture.realDebrid.addMagnetCalls)
        assertTrue(fixture.realDebrid.deleted.isEmpty())
    }

    @Test
    fun `new season pack selects the exact episode and keeps the completed torrent`() = runTest {
        val fixture = Fixture()
        val files = listOf(
            file(id = 2, name = "Show.S01E01.mkv"),
            file(id = 3, name = "Show.S01E02.mkv"),
            file(id = 4, name = "Show.S01E03.mkv"),
        )
        fixture.realDebrid.currentInfo = info(RdTorrentInfo.STATUS_WAITING_SELECTION, files)
        fixture.realDebrid.onSelectFiles = {
            fixture.realDebrid.currentInfo = info(
                status = RdTorrentInfo.STATUS_DOWNLOADED,
                files = files.map { it.copy(selected = it.id == 3) },
                links = listOf("episode-two"),
            )
        }

        val states = fixture.useCase(source(season = 1, episode = 2)).toList()

        assertEquals(listOf(3), fixture.realDebrid.selectedFiles)
        assertEquals(ResolveState.Ready("unrestricted:episode-two"), states.last())
        assertEquals(1, fixture.realDebrid.addMagnetCalls)
        assertTrue(fixture.realDebrid.deleted.isEmpty())
    }

    @Test
    fun `uncached torrent is deleted after the cache timeout`() = runTest {
        val fixture = Fixture()
        fixture.realDebrid.currentInfo = info(
            RdTorrentInfo.STATUS_WAITING_SELECTION,
            listOf(file(id = 1, name = "movie.mkv")),
        )

        val states = fixture.useCase(source()).toList()

        val failure = states.filterIsInstance<ResolveState.Failed>().single()
        assertTrue(failure.notCached)
        assertEquals(listOf("torrent"), fixture.realDebrid.deleted)
    }

    @Test
    fun `cancelling resolution still deletes the new torrent`() = runTest {
        val fixture = Fixture()
        val selectionStarted = CompletableDeferred<Unit>()
        fixture.realDebrid.currentInfo = info(
            RdTorrentInfo.STATUS_WAITING_SELECTION,
            listOf(file(id = 1, name = "movie.mkv")),
        )
        fixture.realDebrid.onSelectFiles = { selectionStarted.complete(Unit) }
        val collection = backgroundScope.launch { fixture.useCase(source()).toList() }

        selectionStarted.await()
        collection.cancelAndJoin()
        advanceUntilIdle()

        assertEquals(listOf("torrent"), fixture.realDebrid.deleted)
    }

    private class Fixture {
        val realDebrid = FakeRealDebridRepository()
        val allDebridTorrents = FakeAllDebridRepository()
        val torrentio = FakeTorrentioLinkResolver()
        val allDebrid = FakeAllDebridLinkResolver()
        val selectedDebrid = MutableStateFlow(DebridService.REAL_DEBRID)
        val useCase = ResolveStreamUseCase(
            realDebrid,
            allDebridTorrents,
            torrentio,
            allDebrid,
            selectedDebrid,
            TestResolveTexts,
        )
    }

    private class FakeAllDebridLinkResolver : AllDebridAuthRepository {
        var result: DebridLink = DebridLink.Problem("unexpected AllDebrid resolve", notCached = false)
        val requests = mutableListOf<String>()

        override suspend fun unlock(url: String): DebridLink {
            requests += url
            return result
        }

        override suspend fun requestPin(): Result<AllDebridPin> = error("unused")
        override suspend fun pollPin(pin: AllDebridPin): AllDebridAuthPoll = error("unused")
    }

    private class FakeTorrentioLinkResolver : TorrentioLinkResolver {
        var result: DebridLink = DebridLink.Problem("unexpected resolve", notCached = false)
        val requests = mutableListOf<Pair<String, String>>()

        override suspend fun resolve(url: String, service: String): DebridLink {
            requests += url to service
            return result
        }
    }

    private open class FakeMagnetDebridRepository : MagnetDebridRepository {
        var downloadedId: String? = null
        var currentInfo: RdTorrentInfo? = null
        var onSelectFiles: () -> Unit = {}
        var onTorrentInfo: () -> Unit = {}
        var addMagnetCalls = 0
        var selectedFiles = listOf<Int>()
        val deleted = mutableListOf<String>()

        override suspend fun findDownloaded(infoHash: String): String? = downloadedId

        override suspend fun addMagnet(magnet: String): String {
            addMagnetCalls++
            return "torrent"
        }

        override suspend fun torrentInfo(torrentId: String): RdTorrentInfo {
            onTorrentInfo()
            return currentInfo ?: error("torrentInfo was not configured")
        }

        override suspend fun selectFiles(torrentId: String, fileIds: List<Int>) {
            selectedFiles = fileIds
            onSelectFiles()
        }

        override suspend fun deleteTorrent(torrentId: String) {
            deleted += torrentId
        }

        override suspend fun unrestrict(link: String): String = "unrestricted:$link"
    }

    private class FakeRealDebridRepository : FakeMagnetDebridRepository(), RealDebridRepository {
        override suspend fun validateToken(token: String): Boolean = error("unexpected validateToken")
        override suspend fun cachedHashes(): Set<String> = error("unexpected cachedHashes")
    }

    private class FakeAllDebridRepository : FakeMagnetDebridRepository(), AllDebridRepository {
        override val requiresFileSelection: Boolean = false
    }

    private object TestResolveTexts : ResolveStreamTexts {
        override fun requestingLink(service: String) = "requesting $service"
        override fun lookingInLibrary(service: String) = "library $service"
        override fun unrestrictingLink() = "unrestricting"
        override fun addingToDebrid(service: String) = "adding $service"
        override fun couldNotReadMagnet(service: String) = "magnet error $service"
        override fun noVideoFiles() = "no videos"
        override fun checkingCache(service: String) = "checking $service"
        override fun sourceNotCached(service: String) = "not cached $service"
        override fun noLinks(service: String) = "no links $service"
        override fun directMagnetUnsupported(service: String) = "unsupported $service"
        override fun resolutionError() = "resolution error"
        override fun torrentStatus(service: String, status: String) = "$service: $status"
    }

    private fun source(
        directUrl: String? = null,
        resolveUrl: String? = null,
        debrid: String? = null,
        fileIdx: Int? = null,
        season: Int? = null,
        episode: Int? = null,
    ) = StreamSource(
        id = "source",
        title = "Source",
        infoHash = "hash",
        fileIdx = fileIdx,
        magnet = "magnet",
        quality = Quality.FHD_1080,
        hdrType = HdrType.SDR,
        sizeBytes = 1_000,
        seeders = 1,
        isCached = false,
        directUrl = directUrl,
        resolveUrl = resolveUrl,
        debrid = debrid,
        season = season,
        episode = episode,
    )

    private fun file(id: Int, name: String, selected: Boolean = false) = RdTorrentFile(
        id = id,
        name = name,
        sizeBytes = id * 1_000L,
        selected = selected,
        link = null,
    )

    private fun info(
        status: String,
        files: List<RdTorrentFile>,
        links: List<String> = emptyList(),
    ) = RdTorrentInfo("torrent", "hash", status, files, links)
}
