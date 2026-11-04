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

/**
 * Claves y tokens de los servicios (Real-Debrid, TMDB, SubDL), cifrados con AES-256-GCM y la clave
 * maestra en el Android Keystore. Si el almacenamiento seguro no está disponible, falla en vez de guardar secretos en plano.
 *
 * Se guarda copia en memoria: TMDB lee su clave en cada petición y descifrar cada vez sería tirar CPU.
 */
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

    /**
     * Primera ejecución: guarda [value] (de local.properties, vía BuildConfig) si aún no hay clave.
     * Solo una vez por instalación: si luego se cambia o se quita en Ajustes, no se vuelve a poner sola.
     */
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

        /** Nombre con el que se guarda la clave de cada servicio. */
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
