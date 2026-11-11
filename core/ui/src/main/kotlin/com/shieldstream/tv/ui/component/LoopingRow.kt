package com.shieldstream.tv.ui.component

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable

const val LOOP_MIN_ITEMS = 8

fun shouldLoop(size: Int): Boolean = size >= LOOP_MIN_ITEMS

fun loopStartIndex(size: Int): Int {
    if (size <= 0) return 0
    val middle = Int.MAX_VALUE / 2
    return middle - middle % size
}

fun firstVirtualIndex(size: Int): Int = if (shouldLoop(size)) loopStartIndex(size) else 0

@Composable
// Prefetch only the next couple of nested cards: seven causes excessive composition and image work on low-power boxes.
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun rememberLoopingRowState(size: Int): LazyListState =
    rememberLazyListState(
        initialFirstVisibleItemIndex = firstVirtualIndex(size),
        prefetchStrategy = androidx.compose.foundation.lazy.LazyListPrefetchStrategy(nestedPrefetchItemCount = 2),
    )

fun <T> LazyListScope.loopingItems(
    items: List<T>,
    content: @Composable LazyItemScope.(virtualIndex: Int, index: Int, item: T) -> Unit,
) {
    val size = items.size
    if (size == 0) return
    val count = if (shouldLoop(size)) Int.MAX_VALUE else size
    items(count = count, key = { it }) { virtual ->
        val index = virtual % size
        content(virtual, index, items[index])
    }
}
