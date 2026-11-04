package com.shieldstream.tv.ui.component

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable

/**
 * Filas "como un reloj": al pasar de la última tarjeta se vuelve a la primera (y al revés).
 * Truco habitual: la fila tiene Int.MAX_VALUE posiciones "virtuales" y se empieza en el medio (alineado con
 * el primer elemento); el elemento real es posición % tamaño. Con menos de [LOOP_MIN_ITEMS] no se hace:
 * se verían repetidos en pantalla a la vez.
 */
const val LOOP_MIN_ITEMS = 8

fun shouldLoop(size: Int): Boolean = size >= LOOP_MIN_ITEMS

/** Posición virtual del primer elemento (en el medio, para poder ir hacia los dos lados). */
fun loopStartIndex(size: Int): Int {
    if (size <= 0) return 0
    val middle = Int.MAX_VALUE / 2
    return middle - middle % size
}

/** Posición virtual inicial de una fila (la del primer elemento). */
fun firstVirtualIndex(size: Int): Int = if (shouldLoop(size)) loopStartIndex(size) else 0

@Composable
// Prefetch only the next couple of nested cards: seven causes excessive composition and image work on low-power boxes.
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun rememberLoopingRowState(size: Int): LazyListState =
    rememberLazyListState(
        initialFirstVisibleItemIndex = firstVirtualIndex(size),
        prefetchStrategy = androidx.compose.foundation.lazy.LazyListPrefetchStrategy(nestedPrefetchItemCount = 2),
    )

/** [content] recibe (posición virtual, índice real, elemento). */
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
