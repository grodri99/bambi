package com.shieldstream.tv.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.shieldstream.tv.domain.ApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        throw IllegalStateException("Secure credential storage is unavailable on this device", e)
    }

    private val memory = ConcurrentHashMap<String, String>()

    fun getSecret(name: String): String? =
        memory.getOrPut(name) { prefs.getString(name, null) ?: NONE }.takeIf { it != NONE }

    fun setSecret(name: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(name) else putString(name, value) }.apply()
        memory[name] = value ?: NONE
    }

    fun seedOnce(name: String, value: String) {
        val flag = "seeded_$name"
        if (prefs.getBoolean(flag, false)) return
        if (getSecret(name).isNullOrBlank() && value.isNotBlank()) setSecret(name, value)
        prefs.edit().putBoolean(flag, true).apply()
    }

    var token: String?
        get() = getSecret(RD_TOKEN)
        set(value) = setSecret(RD_TOKEN, value)

    val tmdbKey: String? get() = getSecret(TMDB_KEY)
    val subdlKey: String? get() = getSecret(SUBDL_KEY)

    companion object {
        const val RD_TOKEN = "rd_token"
        const val TMDB_KEY = "tmdb_key"
        const val SUBDL_KEY = "subdl_key"

        fun keyNameFor(service: ApiService): String = when (service) {
            ApiService.REAL_DEBRID -> RD_TOKEN
            ApiService.TMDB -> TMDB_KEY
            ApiService.SUBDL -> SUBDL_KEY
            ApiService.AIOSTREAMS -> "aiostreams_url"
            ApiService.TORBOX -> "torbox_key"
            ApiService.PREMIUMIZE -> "premiumize_key"
            ApiService.ALLDEBRID -> "alldebrid_key"
            ApiService.DEBRID_LINK -> "debridlink_key"
            ApiService.OFFCLOUD -> "offcloud_key"
        }
        private const val PREFS_NAME = "secure_prefs"
        private const val NONE = "\u0000"
    }
}
