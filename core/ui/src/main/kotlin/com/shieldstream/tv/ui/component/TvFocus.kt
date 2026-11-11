package com.shieldstream.tv.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
private class PivotBringIntoViewSpec(private val leadingPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val childSize = abs(size)
        val target = if (childSize <= containerSize && containerSize - leadingPx < childSize) {
            containerSize - childSize
        } else {
            leadingPx
        }
        return offset - target
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProvidePivotScroll(leading: Dp, content: @Composable () -> Unit) {
    val px = with(LocalDensity.current) { leading.toPx() }
    val spec = remember(px) { PivotBringIntoViewSpec(px) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

@OptIn(ExperimentalFoundationApi::class)
private class MinimalBringIntoViewSpec(private val marginPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, rawSize: Float, containerSize: Float): Float {
        val offset = offset - marginPx
        val size = rawSize + 2 * marginPx
        val trailing = offset + size
        return when {
            offset >= 0f && trailing <= containerSize -> 0f
            offset < 0f && trailing > containerSize -> 0f
            abs(offset) < abs(trailing - containerSize) -> offset
            else -> trailing - containerSize
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProvideMinimalScroll(margin: Dp = 0.dp, content: @Composable () -> Unit) {
    val px = with(LocalDensity.current) { margin.toPx() }
    val spec = remember(px) { MinimalBringIntoViewSpec(px) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/** Requests focus after layout and tolerates a target that is still being attached during a view swap. */
suspend fun FocusRequester.requestFocusWithRetry() {
    withFrameNanos { }
    repeat(4) { attempt ->
        if (runCatching { requestFocus() }.getOrDefault(false)) return
        if (attempt < 3) delay(60)
    }
}

/**
 * Consumes the OK release that opened a long-press overlay, before it can click its focused action.
 * The next OK press is delivered normally.
 */
@Composable
fun Modifier.consumeInitialConfirmationKeyUp(): Modifier {
    var waitingForInitialKeyUp by remember { mutableStateOf(true) }
    return onPreviewKeyEvent { event ->
        val confirmationKey = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (waitingForInitialKeyUp && confirmationKey && event.type == KeyEventType.KeyUp) {
            waitingForInitialKeyUp = false
            true
        } else {
            false
        }
    }
}

fun Modifier.onBackKey(enabled: Boolean, onBack: () -> Unit): Modifier = onKeyEvent { event ->
    if (!enabled || (event.key != Key.Back && event.key != Key.Escape)) return@onKeyEvent false
    if (event.type == KeyEventType.KeyUp) onBack()
    true
}

fun Modifier.tvClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, enabled = enabled, onClick = onClick)

fun Modifier.tvClickable(onLongClick: (() -> Unit)?, onClick: () -> Unit): Modifier =
    if (onLongClick == null) {
        tvClickable(onClick = onClick)
    } else {
        combinedClickable(interactionSource = null, indication = null, onLongClick = onLongClick, onClick = onClick)
    }
