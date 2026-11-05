package com.shieldstream.tv.domain

import com.shieldstream.tv.model.MediaType
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class MyListEntry(val type: MediaType, val id: String, val addedAt: Long) {
    val key: String get() = keyOf(type, id)

    companion object {
        fun keyOf(type: MediaType, id: String) = "${type.name}_$id"
    }
}

data class MyListChange(val type: MediaType, val id: String, val added: Boolean)

interface MyListRepository {
    val entries: StateFlow<List<MyListEntry>>

    fun contains(type: MediaType, id: String): Boolean

    fun add(type: MediaType, id: String)

    fun remove(type: MediaType, id: String)

    val changes: SharedFlow<MyListChange>

    fun importRemote(added: List<MyListEntry>, removedKeys: Collection<String>)
}
