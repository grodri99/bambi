package com.shieldstream.tv.settings

import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.RequestRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.domain.TitleRequest
import com.shieldstream.tv.media.PlaybackPolicyProvider
import com.shieldstream.tv.model.DeviceOptimization
import com.shieldstream.tv.model.PreferredQuality
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.model.SourceExclusions
import com.shieldstream.tv.model.SubtitleAppearance
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val dispatcher = AddonsDispatcherRule()

    private val billboard = MutableStateFlow(true)

    @Test
    fun `billboard trailers start from the saved setting`() = runTest {
        assertTrue(viewModel().billboardTrailers.value)
    }

    @Test
    fun `turning billboard trailers off updates the state and saves it`() = runTest {
        val viewModel = viewModel()

        viewModel.setBillboardTrailers(false)
        advanceUntilIdle()

        assertFalse(viewModel.billboardTrailers.value)
        coVerify(exactly = 1) { settings.setBillboardTrailers(false) }
    }

    @Test
    fun `turning billboard trailers on again updates the state`() = runTest {
        val viewModel = viewModel()
        viewModel.setBillboardTrailers(false)
        advanceUntilIdle()

        viewModel.setBillboardTrailers(true)
        advanceUntilIdle()

        assertTrue(viewModel.billboardTrailers.value)
        coVerify(exactly = 1) { settings.setBillboardTrailers(true) }
    }

    private val settings = mockk<SettingsRepository>(relaxed = true) {
        every { maxBufferSeconds } returns MutableStateFlow(30)
        every { seekThumbnails } returns MutableStateFlow(false)
        every { debridService } returns MutableStateFlow(DebridService.REAL_DEBRID)
        every { deviceOptimization } returns MutableStateFlow(DeviceOptimization.AUTOMATIC)
        every { experimentalSubtitleSync } returns MutableStateFlow(true)

        every { lightweightUi } returns MutableStateFlow(false)
        every { lightweightUiEnabled } returns false
        every { introAlways } returns MutableStateFlow(false)
        every { introAlwaysEnabled } returns false
        every { softBackdrops } returns MutableStateFlow(false)
        every { softBackdropsEnabled } returns false
        every { highResBackdrops } returns MutableStateFlow(false)
        every { highResBackdropsEnabled } returns false
        every { billboardTrailers } returns billboard
        every { billboardTrailersEnabled } returns true
        every { homeRowItemLimit } returns MutableStateFlow(SettingsRepository.DEFAULT_HOME_ROW_ITEM_LIMIT)

        every { sourceProviders } returns MutableStateFlow(SourceProvider.entries.toSet())
        every { sourceExclusions } returns MutableStateFlow(SourceExclusions())
        every { brokenSourceIds } returns MutableStateFlow<Set<String>>(emptySet())
        every { matchContentFrameRate } returns MutableStateFlow(true)
        every { subtitleAppearance } returns MutableStateFlow(SubtitleAppearance())
        every { autoNextEpisode } returns MutableStateFlow(true)
        every { switchFromPickedSource } returns MutableStateFlow(true)
        every { autoSkipIntro } returns MutableStateFlow(false)
        every { showCast } returns MutableStateFlow(true)
        every { spanishFirst } returns MutableStateFlow(false)
        every { spanishOnlySources } returns MutableStateFlow(false)
        every { preferredQuality } returns MutableStateFlow(PreferredQuality.DV_4K)
        every { tracksPanelCard } returns MutableStateFlow(false)

        coEvery { setBillboardTrailers(any()) } coAnswers { billboard.value = firstArg<Boolean>() }
    }

    private val episodeTracking = mockk<EpisodeTrackingRepository> {
        every { notificationsEnabled } returns MutableStateFlow(false)
    }

    private val apiKeys = mockk<ApiKeysRepository> {
        every { keys } returns MutableStateFlow<Map<ApiService, String?>>(emptyMap())
    }

    private val profiles = mockk<ProfileRepository> {
        every { active } returns MutableStateFlow(Profile(id = "main", name = "Main", avatar = 0))
    }

    private val requests = mockk<RequestRepository> {
        every { this@mockk.requests } returns MutableStateFlow<List<TitleRequest>>(emptyList())
    }

    private fun viewModel() = SettingsViewModel(
        apiKeys = apiKeys,
        settings = settings,
        playbackPolicies = mockk<PlaybackPolicyProvider>(relaxed = true),
        episodeTracking = episodeTracking,
        texts = mockk<Texts>(relaxed = true),
        profiles = profiles,
        requests = requests,
    )
}
