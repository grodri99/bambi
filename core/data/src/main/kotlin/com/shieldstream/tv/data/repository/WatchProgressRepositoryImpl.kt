package com.shieldstream.tv.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.shieldstream.tv.domain.ProfileRepository
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

    @Volatile
    private var prefs: SharedPreferences = prefsFor(profiles.active.value.id)

    private val _progress = MutableStateFlow(load())
    override val progress: StateFlow<Map<String, WatchProgress>> = _progress.asStateFlow()

    private val _cleared = MutableSharedFlow<String>(extraBufferCapacity = 32)
    override val cleared: SharedFlow<String> = _cleared.asSharedFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            profiles.active.distinctUntilChangedBy { it.id }.drop(1).collect { profile ->
                prefs = prefsFor(profile.id)
                _progress.value = load()
            }
        }
    }

    override fun get(key: String): WatchProgress? = _progress.value[key]

    override fun save(key: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val entry = WatchProgress(
            positionMs = positionMs.coerceIn(0L, durationMs),
            durationMs = durationMs,
            updatedAt = System.currentTimeMillis(),
        )
        prefs.edit().putString(key, encode(entry)).apply()
        _progress.update { it + (key to entry) }
    }

    override fun clear(key: String) {
        prefs.edit().remove(key).apply()
        _progress.update { it - key }
        _cleared.tryEmit(key)
    }

    override fun importRemote(entries: Map<String, WatchProgress>) {
        if (entries.isEmpty()) return
        val editor = prefs.edit()
        entries.forEach { (key, value) -> editor.putString(key, encode(value)) }
        editor.apply()
        _progress.update { it + entries }
    }

    override fun removeRemote(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val editor = prefs.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
        _progress.update { it - keys.toSet() }
    }

    private fun prefsFor(profileId: String) = context.getSharedPreferences(prefsNameFor(profileId), Context.MODE_PRIVATE)

    private fun load(): Map<String, WatchProgress> = read(prefs)

    companion object {
        fun prefsNameFor(profileId: String) =
            if (profileId == Profile.DEFAULT_ID) "watch_progress" else "watch_progress_$profileId"

        internal fun readAll(context: Context, profileId: String): Map<String, WatchProgress> =
            read(context.getSharedPreferences(prefsNameFor(profileId), Context.MODE_PRIVATE))

        private fun read(prefs: SharedPreferences): Map<String, WatchProgress> =
            prefs.all.mapNotNull { (key, value) ->
                (value as? String)?.let { decode(it) }?.let { key to it }
            }.toMap()

        private fun encode(p: WatchProgress) = "${p.positionMs}|${p.durationMs}|${p.updatedAt}"

        private fun decode(raw: String): WatchProgress? {
            val parts = raw.split('|')
            if (parts.size != 3) return null
            return WatchProgress(
                positionMs = parts[0].toLongOrNull() ?: return null,
                durationMs = parts[1].toLongOrNull() ?: return null,
                updatedAt = parts[2].toLongOrNull() ?: return null,
            )
        }
    }
}
