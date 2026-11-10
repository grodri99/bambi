package com.shieldstream.tv.settings

import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.KeyCheck
import com.shieldstream.tv.domain.StremioAddons
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement

@OptIn(ExperimentalCoroutinesApi::class)
class AddonsViewModelTest {

    @get:Rule
    val dispatcher = AddonsDispatcherRule()

    @Test
    fun `add and replace preserve unmodified existing manifests`() = runTest {
        val existing = "stremio://one.example/manifest.json https://two.example/manifest.json"
        val apiKeys = mockk<ApiKeysRepository>(relaxed = true)
        val keys = MutableStateFlow<Map<ApiService, String?>>(mapOf(ApiService.AIOSTREAMS to existing))
        every { apiKeys.keys } returns keys
        coEvery { apiKeys.set(ApiService.AIOSTREAMS, any()) } coAnswers {
            keys.value = mapOf(ApiService.AIOSTREAMS to secondArg<String?>())
        }
        coEvery { apiKeys.check(ApiService.AIOSTREAMS, any()) } returns KeyCheck.Valid()
        val viewModel = AddonsViewModel(apiKeys, FakeTexts())
        val replaceId = StremioAddons.parse(existing).first().id

        viewModel.openEditor()
        viewModel.save("https://new.example/manifest.json")
        coVerify(exactly = 1) { apiKeys.set(ApiService.AIOSTREAMS, "$existing https://new.example/manifest.json") }

        viewModel.openEditor(replaceId)
        viewModel.save("https://replacement.example/manifest.json")
        coVerify(exactly = 1) {
            apiKeys.set(
                ApiService.AIOSTREAMS,
                "https://replacement.example/manifest.json https://two.example/manifest.json https://new.example/manifest.json",
            )
        }
    }

    @Test
    fun `failed validation leaves existing configuration unchanged`() = runTest {
        val existing = "https://one.example/manifest.json"
        val apiKeys = mockk<ApiKeysRepository>(relaxed = true)
        every { apiKeys.keys } returns MutableStateFlow(mapOf(ApiService.AIOSTREAMS to existing))
        coEvery { apiKeys.check(ApiService.AIOSTREAMS, any()) } returns KeyCheck.Invalid("Offline")
        val viewModel = AddonsViewModel(apiKeys, FakeTexts())

        viewModel.openEditor()
        viewModel.save("https://bad.example/manifest.json")

        assertTrue(viewModel.editor.value?.error == "Offline")
        coVerify(exactly = 0) { apiKeys.set(any(), any()) }
    }

    @Test
    fun `testing an addon reports the result without saving changes`() = runTest {
        val existing = StremioAddons.parse("https://one.example/manifest.json").first()
        val apiKeys = mockk<ApiKeysRepository>(relaxed = true)
        every { apiKeys.keys } returns MutableStateFlow(mapOf(ApiService.AIOSTREAMS to existing.manifestUrl))
        coEvery { apiKeys.check(ApiService.AIOSTREAMS, existing.manifestUrl) } returns KeyCheck.Valid("12 sources")
        val viewModel = AddonsViewModel(apiKeys, FakeTexts())

        viewModel.test(existing.id)

        assertTrue(viewModel.testStatus.value[existing.id]?.detail == "12 sources")
        coVerify(exactly = 0) { apiKeys.set(any(), any()) }
    }

    private class FakeTexts : Texts {
        override fun get(id: Int, vararg formatArgs: Any): String = id.toString()
        override fun quantity(id: Int, count: Int, vararg formatArgs: Any): String = id.toString()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddonsDispatcherRule : TestWatcher() {
    private val dispatcher = UnconfinedTestDispatcher()

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            Dispatchers.setMain(dispatcher)
            try {
                base.evaluate()
            } finally {
                Dispatchers.resetMain()
            }
        }
    }
}
