package com.shieldstream.tv.watchnext

import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import com.shieldstream.tv.domain.ContinueEntry
import com.shieldstream.tv.domain.EpisodeTarget
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContinueWatchingChannelTest {

    @Test
    fun `preview program preserves progress metadata and resume deep link`() {
        val item = CatalogItem(
            id = "1399",
            imdbId = "tt0944947",
            type = MediaType.TV,
            title = "Game of Thrones",
            overview = "Overview",
            posterUrl = "https://image.tmdb.org/poster.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w1280/backdrop.jpg",
            year = 2011,
            rating = null,
        )
        val entry = ContinueEntry(
            type = MediaType.TV,
            id = item.id,
            season = 2,
            episode = 7,
            progress = WatchProgress(
                positionMs = 90_000L,
                durationMs = 3_600_000L,
                updatedAt = 123_456L,
            ),
        )

        val values = ContinueWatchingChannel.programValues(
            channelId = 42L,
            weight = 3,
            launcherEntry = LauncherEntry("profile with spaces", item, entry),
        )

        assertEquals(42L, values.getAsLong(TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID))
        assertEquals(3, values.getAsInteger(TvContractCompat.PreviewPrograms.COLUMN_WEIGHT))
        assertEquals(90_000, values.getAsInteger(TvContractCompat.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS))
        assertEquals(3_600_000, values.getAsInteger(TvContractCompat.PreviewPrograms.COLUMN_DURATION_MILLIS))
        assertEquals("2", values.getAsString("season_display_number"))
        assertEquals("7", values.getAsString("episode_display_number"))
        assertEquals(
            "https://image.tmdb.org/t/p/w1280/backdrop.jpg",
            values.getAsString(TvContractCompat.PreviewPrograms.COLUMN_THUMBNAIL_URI),
        )
        assertEquals(
            TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9,
            values.getAsInteger(TvContractCompat.PreviewPrograms.COLUMN_THUMBNAIL_ASPECT_RATIO),
        )
        assertEquals(
            "profile with spaces:TV:1399:2:7",
            values.getAsString(TvContractCompat.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID),
        )

        val request = parseWatchNextRequest(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(values.getAsString(TvContractCompat.PreviewPrograms.COLUMN_INTENT_URI)),
            ),
        )
        assertEquals("profile with spaces", request?.profileId)
        assertEquals(MediaType.TV, request?.type)
        assertEquals("1399", request?.mediaId)
        assertEquals(EpisodeTarget(season = 2, number = 7), request?.target)
    }
}
