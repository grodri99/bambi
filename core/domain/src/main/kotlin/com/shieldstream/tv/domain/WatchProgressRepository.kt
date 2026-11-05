package com.shieldstream.tv.domain

import com.shieldstream.tv.model.WatchProgress
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface WatchProgressRepository {
    val progress: StateFlow<Map<String, WatchProgress>>

    fun get(key: String): WatchProgress?

    fun save(key: String, positionMs: Long, durationMs: Long)

    fun clear(key: String)

    val cleared: SharedFlow<String>

    fun importRemote(entries: Map<String, WatchProgress>)

    fun removeRemote(keys: Collection<String>)
}
