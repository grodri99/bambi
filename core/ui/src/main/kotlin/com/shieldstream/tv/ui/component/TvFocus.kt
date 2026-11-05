package com.shieldstream.tv.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
