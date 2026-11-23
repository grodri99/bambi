package com.shieldstream.tv.data.repository

import android.content.Context
import android.util.Log
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.TraktCredentials
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.model.WatchProgress
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Trakt en la tele: la lógica está en [TraktSync] (:core:trakt, compartida con escritorio); aquí solo lo de Android. */
@Singleton
class TraktRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    client: TraktClient,
    credentials: TraktCredentials,
    profiles: ProfileRepository,
    progress: WatchProgressRepository,
    myList: MyListRepository,
    tokens: TokenStore,
    cleanup: ProfileDataCleaner,
    json: Json,
    texts: Texts,
) : TraktRepository by TraktSync(
    client = client,
    credentials = credentials,
    profiles = profiles,
    progress = progress,
    myList = myList,
    json = json,
    platform = AndroidTraktPlatform(context, tokens, cleanup, texts),
)

internal class AndroidTraktPlatform(
    private val context: Context,
    private val tokens: TokenStore,
    private val cleanup: ProfileDataCleaner,
    private val texts: Texts,
) : TraktPlatform {
    private val prefs = context.getSharedPreferences(TRAKT_SYNC_PREFS, Context.MODE_PRIVATE)

    override val syncPrefs: TraktPrefs = object : TraktPrefs {
        override fun getString(key: String): String? = prefs.getString(key, null)
        override fun getLong(key: String): Long = prefs.getLong(key, 0L)
        override fun getStringSet(key: String): Set<String> = prefs.getStringSet(key, null).orEmpty()
        override fun put(values: Map<String, Any?>) {
            val edit = prefs.edit()
            values.forEach { (key, value) ->
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    null -> edit.remove(key)
                    is String -> edit.putString(key, value)
                    is Long -> edit.putLong(key, value)
                    is Set<*> -> edit.putStringSet(key, HashSet(value as Set<String>))
                    else -> error("Tipo no admitido: ${value::class}")
                }
            }
            edit.apply()
        }
    }

    override fun secret(key: String): String? = tokens.getSecret(key)
    override fun setSecret(key: String, value: String?) = tokens.setSecret(key, value)
    override fun readAllProgress(profileId: String): Map<String, WatchProgress> = WatchProgressRepositoryImpl.readAll(context, profileId)
    override fun clearTrakt(profileId: String) = cleanup.clearTrakt(profileId)
    override fun text(text: TraktText): String = when (text) {
        TraktText.NOT_CONFIGURED -> texts.get(StringsR.string.data_trakt_isn_t_set_up_in_this)
        TraktText.INVALID_CODE -> texts.get(StringsR.string.data_invalid_code_get_a_new_one)
        TraktText.CODE_ALREADY_USED -> texts.get(StringsR.string.data_that_code_was_already_used_get_a)
    }
    override fun log(message: String) {
        Log.w("Trakt", message)
    }
}
