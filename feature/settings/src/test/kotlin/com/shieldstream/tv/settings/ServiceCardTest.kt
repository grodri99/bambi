package com.shieldstream.tv.settings

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.Lang
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServiceCardTest {
    private val composeRule = createComposeRule()
    private val registerActivity = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
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
        Lang.current = AppLanguage.EN
    }

    @After
    fun restoreLanguage() {
        Lang.current = previousLanguage
    }

    @Test
    fun `Torrentio error does not squeeze provider details`() {
        val error = "AllDebrid isn't available right now (according to Torrentio). Try again later."
        render(ServiceStatus.Bad(error))

        assertEquals(1, textLayout("AllDebrid").lineCount)
        assertEquals(1, textLayout("Plays sources (through Torrentio)").lineCount)
        assertStatusBelowDetails(error)
        assertFullyVisible(error)
    }

    @Test
    fun `long errors wrap without truncation`() {
        val error = "AllDebrid isn't available right now (according to Torrentio). Try again later. ".repeat(4).trim()
        render(ServiceStatus.Bad(error))

        assertEquals(1, textLayout("AllDebrid").lineCount)
        assertTrue(textLayout(error).lineCount > 1)
        assertFullyVisible(error)
        assertStatusBelowDetails(error)
    }

    @Test
    fun `Spanish errors preserve provider layout`() {
        Lang.current = AppLanguage.ES
        val error = "AllDebrid no está disponible ahora mismo (según Torrentio). Inténtalo más tarde."
        render(ServiceStatus.Bad(error))

        assertEquals(1, textLayout("AllDebrid").lineCount)
        assertFullyVisible(error)
        assertStatusBelowDetails(error)
    }

    @Test
    fun `long successful status does not squeeze provider details`() {
        val detail = "Connected. ".repeat(30).trim()
        render(ServiceStatus.Ok(detail))

        assertEquals(1, textLayout("AllDebrid").lineCount)
        assertTrue(textLayout(detail).lineCount > 1)
        assertFullyVisible(detail)
        assertStatusBelowDetails(detail)
    }

    private fun render(status: ServiceStatus) {
        composeRule.setContent {
            // Settings uses 120 dp side margins on the TV's 960 dp-wide viewport.
            Box(Modifier.width(720.dp)) {
                ServiceCard(
                    ui = ServiceUi(ApiService.ALLDEBRID, maskedKey = null, status = status),
                    onClick = {},
                )
            }
        }
    }

    private fun textLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun assertFullyVisible(text: String) {
        val layout = textLayout(text)
        assertFalse(layout.didOverflowHeight)
        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        for (line in 0 until layout.lineCount) {
            assertFalse(layout.isLineEllipsized(line))
            assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
        }
    }

    private fun assertStatusBelowDetails(text: String) {
        val title = composeRule.onNodeWithText("AllDebrid", useUnmergedTree = true).fetchSemanticsNode()
        val status = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("Status should sit below the provider details", status.boundsInRoot.top > title.boundsInRoot.bottom)
    }
}
