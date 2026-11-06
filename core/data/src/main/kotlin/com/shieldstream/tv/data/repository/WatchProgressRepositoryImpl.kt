package com.shieldstream.tv.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.WatchProgressClear
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.model.WatchProgress
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchProgressRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    profiles: ProfileRepository,
) : WatchProgressRepository {

    private data class ActiveStore(val profileId: String, val prefs: SharedPreferences)

    @Volatile
    private var store = storeFor(profiles.active.value.id)

    private val _progress = MutableStateFlow(load())
    override val progress: StateFlow<Map<String, WatchProgress>> = _progress.asStateFlow()

    private val _cleared = MutableSharedFlow<WatchProgressClear>(extraBufferCapacity = 32)
    override val cleared: SharedFlow<WatchProgressClear> = _cleared.asSharedFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            profiles.active.distinctUntilChangedBy { it.id }.drop(1).collect { profile ->
                store = storeFor(profile.id)
                _progress.value = load()
            }
        }
    }

    override fun get(key: String): WatchProgress? = _progress.value[key]

    override fun save(key: String, positionMs: Long, durationMs: Long, sourceId: String?) {
        if (durationMs <= 0) return
        val entry = WatchProgress(
            positionMs = positionMs.coerceIn(0L, durationMs),
            durationMs = durationMs,
            updatedAt = System.currentTimeMillis(),
            sourceId = sourceId ?: get(key)?.sourceId,
        )
        store.prefs.edit().putString(key, encode(entry)).apply()
        _progress.update { it + (key to entry) }
    }

    override fun clear(key: String) {
        val current = store
        current.prefs.edit().remove(key).apply()
        _progress.update { it - key }
        _cleared.tryEmit(WatchProgressClear(current.profileId, key))
    }

    override fun importRemote(entries: Map<String, WatchProgress>) {
        if (entries.isEmpty()) return
        val local = _progress.value
        val merged = entries.mapValues { (key, value) ->
            value.copy(sourceId = local[key]?.sourceId)
        }
        val editor = store.prefs.edit()
        merged.forEach { (key, value) -> editor.putString(key, encode(value)) }
        editor.apply()
        _progress.update { it + merged }
    }

    override fun removeRemote(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val editor = store.prefs.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
        _progress.update { it - keys.toSet() }
    }

    private fun storeFor(profileId: String) = ActiveStore(profileId, prefsFor(profileId))

    private fun prefsFor(profileId: String) = context.getSharedPreferences(prefsNameFor(profileId), Context.MODE_PRIVATE)

    private fun load(): Map<String, WatchProgress> = read(store.prefs)

    companion object {
        fun prefsNameFor(profileId: String) =
            if (profileId == Profile.DEFAULT_ID) "watch_progress" else "watch_progress_$profileId"

        internal fun readAll(context: Context, profileId: String): Map<String, WatchProgress> =
            read(context.getSharedPreferences(prefsNameFor(profileId), Context.MODE_PRIVATE))

        private fun read(prefs: SharedPreferences): Map<String, WatchProgress> =
            prefs.all.mapNotNull { (key, value) ->
                (value as? String)?.let { decode(it) }?.let { key to it }
            }.toMap()

        private fun encode(p: WatchProgress) = buildString {
            append(p.positionMs).append('|').append(p.durationMs).append('|').append(p.updatedAt)
            p.sourceId?.let { append('|').append(it) }
        }

        private fun decode(raw: String): WatchProgress? {
            val parts = raw.split('|', limit = 4)
            if (parts.size !in 3..4) return null
            return WatchProgress(
                positionMs = parts[0].toLongOrNull() ?: return null,
                durationMs = parts[1].toLongOrNull() ?: return null,
                updatedAt = parts[2].toLongOrNull() ?: return null,
                sourceId = parts.getOrNull(3)?.takeIf { it.isNotEmpty() },
            )
        }
    }
}
