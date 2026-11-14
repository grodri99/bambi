package com.shieldstream.tv.ui.component

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.FeaturedBadge
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.strings.localizedCode
import com.shieldstream.tv.strings.localizedLabel
import com.shieldstream.tv.strings.localizedSubtitleLanguage
import com.shieldstream.tv.strings.localizedTitle
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedComponentsLocaleTest {
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
    fun saveLanguage() {
        previousLanguage = Lang.current
    }

    @After
    fun restoreLanguage() {
        Lang.current = previousLanguage
    }

    @Test
    fun `episode badges use Spanish singular plural and upcoming text`() {
        Lang.current = AppLanguage.ES
        renderBadges()

        composeRule.onNodeWithText("NUEVO").assertExists()
        composeRule.onNodeWithText("3 NUEVOS").assertExists()
        composeRule.onNodeWithText("2 EPISODIOS NUEVOS").assertExists()
        composeRule.onNodeWithText("PRÓXIMAMENTE").assertExists()
    }

    @Test
    fun `episode badges use English singular plural and upcoming text`() {
        Lang.current = AppLanguage.EN
        renderBadges()

        composeRule.onNodeWithText("NEW").assertExists()
        composeRule.onNodeWithText("3 NEW").assertExists()
        composeRule.onNodeWithText("2 NEW EPISODES").assertExists()
        composeRule.onNodeWithText("COMING SOON").assertExists()
    }

    @Test
    fun `top navigation renders Spanish labels`() {
        Lang.current = AppLanguage.ES
        renderTopBar()

        composeRule.onNodeWithText("Inicio").assertExists()
        composeRule.onNodeWithText("Episodios").assertExists()
        composeRule.onNodeWithText("Series").assertExists()
        composeRule.onNodeWithText("Mi lista").assertExists()
    }

    @Test
    fun `top navigation renders English labels`() {
        Lang.current = AppLanguage.EN
        renderTopBar()

        composeRule.onNodeWithText("Home").assertExists()
        composeRule.onNodeWithText("Episodes").assertExists()
        composeRule.onNodeWithText("TV Shows").assertExists()
        composeRule.onNodeWithText("My list").assertExists()
    }

    @Test
    fun `rail icons expose Spanish accessibility labels`() {
        Lang.current = AppLanguage.ES
        renderRail()

        composeRule.onNodeWithContentDescription("Inicio").assertExists()
        composeRule.onNodeWithContentDescription("Ajustes").assertExists()
    }

    @Test
    fun `rail icons expose English accessibility labels`() {
        Lang.current = AppLanguage.EN
        renderRail()

        composeRule.onNodeWithContentDescription("Home").assertExists()
        composeRule.onNodeWithContentDescription("Settings").assertExists()
    }

    @Test
    fun `model labels render in Spanish`() {
        Lang.current = AppLanguage.ES
        renderModelLabels()

        composeRule.onNodeWithText("Bélicas e históricas").assertExists()
        composeRule.onNodeWithText("Recién llegada").assertExists()
        composeRule.onNodeWithText("T2:E7").assertExists()
        composeRule.onNodeWithText("Francés").assertExists()
    }

    @Test
    fun `model labels render in English`() {
        Lang.current = AppLanguage.EN
        renderModelLabels()

        composeRule.onNodeWithText("War & history").assertExists()
        composeRule.onNodeWithText("New arrival").assertExists()
        composeRule.onNodeWithText("S2:E7").assertExists()
        composeRule.onNodeWithText("French").assertExists()
    }

    @Test
    fun `up next card renders series episode and release metadata`() {
        composeRule.setContent {
            UpNextCard(
                item = testItem,
                episode = Episode(2, 1, "Pilot", "", null, 45, "2026-11-05"),
                label = "S2:E1 · Pilot",
                releaseLabel = "Nov 5",
                badgeText = "New season",
                onClick = {},
            )
        }

        composeRule.onNodeWithText(testItem.title).assertExists()
        composeRule.onNodeWithText("S2:E1 · Pilot").assertExists()
        composeRule.onNodeWithText("Nov 5").assertExists()
        composeRule.onNodeWithText("New season").assertExists()
    }

    @Test
    fun `catalog poster keeps its title for accessibility through focus changes`() {
        // Estilo Bambi: el póster va sin texto debajo; el título queda como descripción de la imagen.
        val requester = FocusRequester()
        val item = testItem.copy(posterUrl = "https://invalid.local/poster.jpg", year = 2026)
        composeRule.setContent {
            CatalogPosterCard(
                item = item,
                onClick = {},
                modifier = Modifier.focusRequester(requester),
            )
        }

        composeRule.onNodeWithContentDescription(item.title, useUnmergedTree = true).assertExists()
        composeRule.runOnIdle { requester.requestFocus() }
        composeRule.onNodeWithContentDescription(item.title, useUnmergedTree = true).assertExists()
    }

    private fun renderBadges() {
        composeRule.setContent {
            Column {
                PosterCard(item = testItem, onClick = {}, newEpisodeCount = 1)
                PosterCard(item = testItem, onClick = {}, newEpisodeCount = 3)
                NewEpisodeCard(item = testItem, label = "Episode", onClick = {}, count = 2)
                NewEpisodeCard(item = testItem, label = "Episode", onClick = {}, count = 0)
            }
        }
    }

    private fun renderTopBar() {
        composeRule.setContent {
            TopBar(
                selected = TopTab.HOME,
                onSelect = {},
                onSettings = {},
                selectedRequester = FocusRequester(),
            )
        }
    }

    private fun renderRail() {
        composeRule.setContent {
            NavRail(
                selected = RailDestination.HOME,
                onSelect = {},
                selectedRequester = FocusRequester(),
            )
        }
    }

    private fun renderModelLabels() {
        composeRule.setContent {
            Column {
                Text(Category.WAR_HISTORY.localizedTitle())
                Text(FeaturedBadge.NEW.localizedLabel())
                Text(Episode(2, 7, "Episode", "", null, 45).localizedCode())
                Text(localizedSubtitleLanguage("fr"))
            }
        }
    }

    private val testItem = CatalogItem(
        id = "1",
        imdbId = null,
        type = MediaType.TV,
        title = "Show",
        overview = "",
        posterUrl = null,
        backdropUrl = null,
        year = null,
        rating = null,
    )
}
