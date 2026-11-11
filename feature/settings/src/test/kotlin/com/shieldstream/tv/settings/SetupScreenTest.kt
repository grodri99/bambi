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
    fun `welcome renders Spanish text`() {
        renderStep(SetupStep.WELCOME, AppLanguage.ES)

        composeRule.onNodeWithText("Vamos a dejar Bambi listo").assertExists()
        composeRule.onNodeWithText("Idioma, audio y subtítulos").assertExists()
        composeRule.onNodeWithText("Empezar").assertExists()
        composeRule.onNodeWithText("Más tarde").assertExists()
    }

    @Test
    fun `welcome renders English text`() {
        renderStep(SetupStep.WELCOME, AppLanguage.EN)

        composeRule.onNodeWithText("Let's get Bambi ready").assertExists()
        composeRule.onNodeWithText("Language, audio & subtitles").assertExists()
        composeRule.onNodeWithText("Start").assertExists()
        composeRule.onNodeWithText("Later").assertExists()
    }

    @Test
    fun `welcome offers the optional phone setup path`() {
        renderStep(SetupStep.WELCOME, AppLanguage.EN, onOpenPhone = {})

        composeRule.onNodeWithText("Continue on phone").assertExists()
        composeRule.onNodeWithText("Start").assertExists()
        composeRule.onNodeWithText("Later").assertExists()
    }

    @Test
    fun `playback preferences render Spanish text`() {
        renderStep(SetupStep.PLAYBACK, AppLanguage.ES)

        composeRule.onNodeWithText("PASO 4 DE 4").assertExists()
        composeRule.onNodeWithText("AUDIO Y SUBTÍTULOS").assertExists()
        composeRule.onNodeWithText("Idioma de la interfaz").assertExists()
        composeRule.onNodeWithText("Audio preferido").assertExists()
        composeRule.onNodeWithText("Subtítulos automáticos").assertExists()
        composeRule.onNodeWithText("Siguiente").assertExists()
    }

    @Test
    fun `playback preferences render English text`() {
        renderStep(SetupStep.PLAYBACK, AppLanguage.EN)

        composeRule.onNodeWithText("STEP 4 OF 4").assertExists()
        composeRule.onNodeWithText("AUDIO & SUBTITLES").assertExists()
        composeRule.onNodeWithText("Interface language").assertExists()
        composeRule.onNodeWithText("Preferred audio").assertExists()
        composeRule.onNodeWithText("Automatic subtitles").assertExists()
        composeRule.onNodeWithText("Next").assertExists()
    }

    @Test
    fun `done summary shows selected language instead of pre-restart language`() {
        renderStep(
            step = SetupStep.DONE,
            selectedLanguage = AppLanguage.EN,
            currentLanguage = AppLanguage.ES,
        )

        composeRule.onNodeWithText("Idioma").assertExists()
        composeRule.onNodeWithText("English").assertExists()
    }

    private fun renderStep(
        step: SetupStep,
        selectedLanguage: AppLanguage,
        currentLanguage: AppLanguage = selectedLanguage,
        onOpenPhone: (() -> Unit)? = null,
    ) {
        Lang.current = currentLanguage
        val viewModel = mockk<SetupViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            SetupUiState.Ready(
                step = step,
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
                language = selectedLanguage,
            ),
        )

        composeRule.setContent {
            SetupScreen(onFinish = {}, onExit = {}, onOpenPhone = onOpenPhone, viewModel = viewModel)
        }
    }
}
