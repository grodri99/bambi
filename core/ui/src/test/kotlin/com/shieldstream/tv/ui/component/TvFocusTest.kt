package com.shieldstream.tv.ui.component

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvFocusTest {
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

    @Test
    fun `focus retry waits for a target attached after the first request`() {
        var targetFocused by mutableStateOf(false)
        composeRule.setContent {
            val requester = remember { FocusRequester() }
            var attached by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                requester.requestFocusWithRetry()
            }
            LaunchedEffect(Unit) {
                delay(100)
                attached = true
            }
            Box(
                Modifier
                    .size(24.dp)
                    .testTag("lateTarget")
                    .then(
                        if (attached) {
                            Modifier
                                .focusRequester(requester)
                                .onFocusChanged { targetFocused = it.isFocused }
                                .focusTarget()
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        composeRule.waitUntil(timeoutMillis = 2_000) { targetFocused }
    }

    @Test
    fun `long press menu ignores the opening OK release but accepts the next press`() {
        var clicks = 0
        composeRule.setContent {
            ActionMenuDialog(
                title = "Menu",
                onDismiss = {},
                consumeOpeningKeyRelease = true,
                actions = listOf(ActionMenuItem("Action", icon = null, onClick = { clicks++ })),
            )
        }
        composeRule.waitForIdle()
        val action = composeRule.onNode(
            hasClickAction().and(hasAnyDescendant(hasText("Action"))),
            useUnmergedTree = true,
        )
        action.assertIsFocused()

        action.performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        composeRule.waitForIdle()
        assertEquals(0, clicks)

        action.performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun `normal click menu accepts its first OK press`() {
        var clicks = 0
        composeRule.setContent {
            ActionMenuDialog(
                title = "Menu",
                onDismiss = {},
                actions = listOf(ActionMenuItem("Action", icon = null, onClick = { clicks++ })),
            )
        }
        composeRule.waitForIdle()
        val action = composeRule.onNode(
            hasClickAction().and(hasAnyDescendant(hasText("Action"))),
            useUnmergedTree = true,
        )
        action.assertIsFocused()

        action.performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }
}
