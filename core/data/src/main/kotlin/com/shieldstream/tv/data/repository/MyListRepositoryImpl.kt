package com.shieldstream.tv.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.shieldstream.tv.domain.MyListChange
import com.shieldstream.tv.domain.MyListEntry
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MyListRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    profiles: ProfileRepository,
) : MyListRepository {

    @Volatile
    private var prefs: SharedPreferences = prefsFor(profiles.active.value.id)

    private val _entries = MutableStateFlow(load())
    override val entries: StateFlow<List<MyListEntry>> = _entries.asStateFlow()

    private val _changes = MutableSharedFlow<MyListChange>(extraBufferCapacity = 32)
    override val changes: SharedFlow<MyListChange> = _changes.asSharedFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            profiles.active.distinctUntilChangedBy { it.id }.drop(1).collect { profile ->
                prefs = prefsFor(profile.id)
                _entries.value = load()
            }
        }
    }

    override fun contains(type: MediaType, id: String): Boolean =
        _entries.value.any { it.type == type && it.id == id }

    override fun add(type: MediaType, id: String) {
        if (contains(type, id)) return
        prefs.edit().putLong(MyListEntry.keyOf(type, id), System.currentTimeMillis()).apply()
        _entries.value = load()
        _changes.tryEmit(MyListChange(type, id, added = true))
    }

    override fun remove(type: MediaType, id: String) {
        if (!contains(type, id)) return
        prefs.edit().remove(MyListEntry.keyOf(type, id)).apply()
        _entries.value = load()
        _changes.tryEmit(MyListChange(type, id, added = false))
    }

    override fun importRemote(added: List<MyListEntry>, removedKeys: Collection<String>) {
        if (added.isEmpty() && removedKeys.isEmpty()) return
        val editor = prefs.edit()
        added.forEach { e -> if (!prefs.contains(e.key)) editor.putLong(e.key, e.addedAt) }
        removedKeys.forEach(editor::remove)
        editor.apply()
        _entries.value = load()
    }

    private fun prefsFor(profileId: String) = context.getSharedPreferences(prefsNameFor(profileId), Context.MODE_PRIVATE)

    private fun load(): List<MyListEntry> = prefs.all.mapNotNull { (key, value) ->
        val type = runCatching { MediaType.valueOf(key.substringBefore('_')) }.getOrNull() ?: return@mapNotNull null
        val id = key.substringAfter('_').takeIf { it.isNotBlank() } ?: return@mapNotNull null
        MyListEntry(type, id, (value as? Long) ?: 0L)
    }.sortedByDescending { it.addedAt }

    companion object {
        fun prefsNameFor(profileId: String) = "my_list_$profileId"
    }
}
