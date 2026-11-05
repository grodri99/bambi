package com.shieldstream.tv.data.repository

import android.content.Context
import android.os.Build
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.ProfileAvatars
import com.shieldstream.tv.model.tr
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Perfiles en SharedPreferences ("profiles"): la lista como JSON y el id del activo.
 * Son pocos y pequeños; se leen una vez al arrancar y se mantienen en memoria.
 */
@Singleton
class ProfileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokens: TokenStore,
) : ProfileRepository {

    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _profiles = MutableStateFlow(load())
    override val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _active = MutableStateFlow(
        _profiles.value.firstOrNull { it.id == prefs.getString(KEY_ACTIVE, null) } ?: _profiles.value.first(),
    )
    override val active: StateFlow<Profile> = _active.asStateFlow()

    override fun select(id: String) {
        val profile = _profiles.value.firstOrNull { it.id == id } ?: return
        prefs.edit().putString(KEY_ACTIVE, id).apply()
        _active.value = profile
    }

    override fun save(profile: Profile): Profile {
        val clean = profile.copy(
            name = profile.name.trim().take(MAX_NAME).ifEmpty { tr("Perfil", "Profile") },
            avatar = ProfileAvatars.safe(profile.avatar),
        )
        val list = _profiles.value
        val updated = if (list.any { it.id == clean.id }) {
            list.map { if (it.id == clean.id) clean else it }
        } else {
            if (list.size >= Profile.MAX) return clean
            list + clean
        }
        store(updated)
        if (_active.value.id == clean.id) _active.value = clean
        return clean
    }

    override fun draft(): Profile {
        val used = _profiles.value.map { it.avatar }.toSet()
        val avatar = ProfileAvatars.colors.indices.firstOrNull { it !in used } ?: 0
        return Profile(id = UUID.randomUUID().toString().take(8), name = "", avatar = avatar)
    }

    override fun delete(id: String) {
        val list = _profiles.value
        if (list.size <= 1 || list.none { it.id == id }) return
        val updated = list.filterNot { it.id == id }
        store(updated)
        // Lo suyo: progreso y cuenta de Trakt.
        for (name in listOf(WatchProgressRepositoryImpl.prefsNameFor(id), MyListRepositoryImpl.prefsNameFor(id))) {
            if (Build.VERSION.SDK_INT >= 24) {
                context.deleteSharedPreferences(name)
            } else {
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
            }
        }
        tokens.setSecret(TraktSession.storeKey(id), null)
        if (_active.value.id == id) select(updated.first().id)
    }

    private fun store(list: List<Profile>) {
        prefs.edit().putString(KEY_LIST, json.encodeToString(list.map { StoredProfile.from(it) })).apply()
        _profiles.value = list
    }

    private fun load(): List<Profile> {
        // Migrar los ajustes generales una sola vez; los perfiles infantiles empiezan solo en español.
        val legacy = context.getSharedPreferences(AppLanguage.PREFS, Context.MODE_PRIVATE)
        val spanishFirst = legacy.getBoolean("spanish_first", !Lang.isEnglish)
        val spanishOnly = legacy.getBoolean("spanish_only_sources", false)
        val hiddenCategories = legacy.getStringSet("hidden_categories", emptySet()).orEmpty().toSet()
            .ifEmpty { Category.DEFAULT_HIDDEN }
        val stored = prefs.getString(KEY_LIST, null)?.let { raw ->
            runCatching { json.decodeFromString<List<StoredProfile>>(raw) }.getOrNull()
        }?.map { it.toProfile(spanishFirst, spanishOnly, hiddenCategories) }.orEmpty()
        if (stored.isNotEmpty()) {
            prefs.edit().putString(KEY_LIST, json.encodeToString(stored.map { StoredProfile.from(it) })).apply()
            return stored
        }
        // Primera vez: un perfil con el progreso que ya había (ver Profile.DEFAULT_ID).
        val first = listOf(Profile(
            Profile.DEFAULT_ID, tr("Principal", "Main"), avatar = 0,
            spanishFirst = spanishFirst, spanishOnlySources = spanishOnly,
            hiddenCategories = hiddenCategories,
        ))
        prefs.edit().putString(KEY_LIST, json.encodeToString(first.map { StoredProfile.from(it) })).apply()
        return first
    }

    @Serializable
    private data class StoredProfile(
        val id: String,
        val name: String,
        val avatar: Int,
        val traktUser: String? = null,
        // Campos nuevos con valor por defecto: los perfiles guardados antes se leen igual.
        val pinHash: String? = null,
        val kids: Boolean = false,
        val spanishFirst: Boolean? = null,
        val spanishOnlySources: Boolean? = null,
        val hiddenCategories: Set<String>? = null,
    ) {
        fun toProfile(legacyFirst: Boolean, legacyOnly: Boolean, legacyHidden: Set<String>) = Profile(
            id, name, avatar, traktUser, pinHash, kids,
            spanishFirst = spanishFirst ?: (kids || legacyFirst),
            spanishOnlySources = spanishOnlySources ?: (kids || legacyOnly),
            hiddenCategories = hiddenCategories ?: legacyHidden,
        )

        companion object {
            fun from(p: Profile) = StoredProfile(
                p.id, p.name, p.avatar, p.traktUser, p.pinHash, p.kids,
                p.spanishFirst, p.spanishOnlySources, p.hiddenCategories,
            )
        }
    }

    private companion object {
        const val KEY_LIST = "list"
        const val KEY_ACTIVE = "active"
        const val MAX_NAME = 20
    }
}
