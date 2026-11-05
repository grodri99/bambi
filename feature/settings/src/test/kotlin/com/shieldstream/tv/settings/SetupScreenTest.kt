package com.shieldstream.tv.settings

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.SubtitlePreference
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SetupScreenTest {

    private val composeRule = createComposeRule()
    private val registerActivity = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<android.content.Context>()
                shadowOf(context.packageManager).addActivityIfNotPresent(
                    ComponentName(context, ComponentActivity::class.java),
                )
                base.evaluate()
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(registerActivity).around(composeRule)

    private lateinit var previousLanguage: AppLanguage

    @Before
    fun useSpanishBeforeRestart() {
        previousLanguage = Lang.current
        Lang.current = AppLanguage.ES
    }

    @After
    fun restoreLanguage() {
        Lang.current = previousLanguage
    }

    @Test
    fun `done summary shows selected language instead of pre-restart language`() {
        val viewModel = mockk<SetupViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            SetupUiState.Ready(
                step = SetupStep.DONE,
                debrid = DebridService.REAL_DEBRID,
                keys = mapOf(
                    ApiService.TMDB to "tmdb",
                    ApiService.REAL_DEBRID to "debrid",
                    ApiService.SUBDL to "subdl",
                ),
                details = emptyMap(),
                checking = false,
                error = null,
                audio = SetupAudioPreference.ORIGINAL,
                subtitles = SubtitlePreference.OFF,
                language = AppLanguage.EN,
            ),
        )

        composeRule.setContent {
            SetupScreen(onFinish = {}, onExit = {}, viewModel = viewModel)
        }

        composeRule.onNodeWithText("Idioma").assertExists()
        composeRule.onNodeWithText("English").assertExists()
    }
}
