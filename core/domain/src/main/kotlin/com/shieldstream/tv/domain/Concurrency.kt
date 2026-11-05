package com.shieldstream.tv.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

suspend fun <T, R> Iterable<T>.mapConcurrent(
    concurrency: Int = 4,
    block: suspend (T) -> R,
): List<R> = coroutineScope {
    require(concurrency > 0)
    val semaphore = Semaphore(concurrency)
    map { element ->
        async { semaphore.withPermit { block(element) } }
    }.awaitAll()
}
