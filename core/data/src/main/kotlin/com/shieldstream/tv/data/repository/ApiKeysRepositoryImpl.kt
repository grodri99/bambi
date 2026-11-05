package com.shieldstream.tv.data.repository

import com.shieldstream.tv.model.tr
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.data.subtitles.SubtitleHttp
import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.KeyCheck
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.network.api.RealDebridApi
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.api.TorrentioApi
import com.shieldstream.tv.domain.DebridService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeysRepositoryImpl @Inject constructor(
    private val store: TokenStore,
    private val settings: SettingsRepository,
    private val tmdb: TmdbApi,
    private val realDebrid: RealDebridApi,
    private val http: SubtitleHttp,
    private val torrentioApi: TorrentioApi,
) : ApiKeysRepository {

    private val _keys = MutableStateFlow(read())
    override val keys: StateFlow<Map<ApiService, String?>> = _keys.asStateFlow()

    private fun read(): Map<ApiService, String?> = ApiService.entries.associateWith { store.getSecret(it.storeKey) }

    override suspend fun set(service: ApiService, value: String?) {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() }
        if (service == ApiService.REAL_DEBRID) settings.setToken(clean) else store.setSecret(service.storeKey, clean)
        _keys.value = read()
    }

    override suspend fun check(service: ApiService, value: String): KeyCheck = withContext(Dispatchers.IO) {
        val key = value.trim()
        runCatching {
            when (service) {
                ApiService.REAL_DEBRID -> checkRealDebrid(key)
                ApiService.TMDB -> {
                    if (key.startsWith("eyJ") && key.length > 60) {
                        KeyCheck.Invalid(
                            tr(
                                "Eso es el «token de lectura» (v4). Bambi usa la «Clave de la API» (v3): 32 letras y números, en la misma página de TMDB (Ajustes → API).",
                                "That's the \"read access token\" (v4). Bambi uses the \"API Key\" (v3): 32 letters and numbers, on the same TMDB page (Settings → API).",
                            ),
                        )
                    } else {
                        tmdb.trendingAll(key)
                        KeyCheck.Valid()
                    }
                }
                ApiService.SUBDL -> checkSubDl(key)
                ApiService.AIOSTREAMS -> checkAioStreams(key)
                ApiService.TORBOX, ApiService.PREMIUMIZE, ApiService.ALLDEBRID,
                ApiService.DEBRID_LINK, ApiService.OFFCLOUD -> checkViaTorrentio(service, key)
            }
        }.getOrElse { describe(it) }
    }

    private suspend fun checkRealDebrid(token: String): KeyCheck {
        val user = realDebrid.user("Bearer $token")
        val days = user.premium / 86_400
        val detail = if (user.type == "premium" && user.premium > 0) {
            tr("${user.username} · Premium: quedan $days días", "${user.username} · Premium: $days days left")
        } else {
            tr("${user.username} · sin Premium", "${user.username} · no Premium")
        }
        return KeyCheck.Valid(detail)
    }

    private suspend fun checkViaTorrentio(service: ApiService, key: String): KeyCheck {
        val debrid = DebridService.of(service) ?: return KeyCheck.Invalid(tr("Servicio desconocido", "Unknown service"))
        val streams = torrentioApi.streamsWithConfig(torrentioConfig(debrid, key), "movie", "tt0137523").streams
        streams.firstOrNull { it.isDebridError() }?.let { return KeyCheck.Invalid(debridErrorMessage(it.title, debrid.title)) }
        if (streams.none { it.url?.contains("/resolve/") == true }) {
            return KeyCheck.Invalid(tr("Torrentio no devolvió enlaces de ${debrid.title}. Prueba más tarde.", "Torrentio returned no ${debrid.title} links. Try again later."))
        }
        return KeyCheck.Valid(tr("Conectado (vía Torrentio)", "Connected (via Torrentio)"))
    }

    private suspend fun checkAioStreams(url: String): KeyCheck {
        val bases = aioStreamBases(url)
        if (bases.isEmpty()) {
            return KeyCheck.Invalid(tr("Pega la URL del manifiesto (termina en /manifest.json). Varios: separados por espacios.", "Paste the manifest URL (ends in /manifest.json). Several: separated by spaces."))
        }
        val counts = bases.map { base -> runCatching { torrentioApi.streamsAt("$base/stream/movie/tt0111161.json").streams.size }.getOrNull() }
        val failed = counts.count { it == null }
        if (failed == bases.size) return KeyCheck.Invalid(tr("Ningún addon responde", "No addon is responding"))
        val total = counts.filterNotNull().sum()
        return KeyCheck.Valid(
            if (bases.size == 1) tr("Conectado · $total fuentes de prueba", "Connected · $total test sources")
            else tr("${bases.size - failed} de ${bases.size} addons · $total fuentes de prueba", "${bases.size - failed} of ${bases.size} addons · $total test sources"),
        )
    }

    private fun checkSubDl(key: String): KeyCheck {
        val request = Request.Builder()
            .url("https://api.subdl.com/api/v2/subtitles/search?imdb_id=tt0137523&type=movie&languages=ES")
            .header("Authorization", "Bearer $key")
            .header("User-Agent", "Bambi")
            .build()
        http.client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) return KeyCheck.Invalid(tr("Clave no válida", "Invalid key"))
            if (!response.isSuccessful) return KeyCheck.Invalid(tr("SubDL respondió con error ${response.code}", "SubDL responded with error ${response.code}"))
            val body = response.body?.string().orEmpty()
            val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            val status = json?.get("status")?.jsonPrimitive?.booleanOrNull
            if (status == false) {
                val message = json["error"]?.jsonPrimitive?.contentOrNull ?: json["message"]?.jsonPrimitive?.contentOrNull
                return KeyCheck.Invalid(message ?: tr("Clave no válida", "Invalid key"))
            }
            return KeyCheck.Valid()
        }
    }

    private fun describe(e: Throwable): KeyCheck.Invalid {
        val code = Regex("""HTTP (\d{3})""").find(e.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        return KeyCheck.Invalid(
            when {
                code == 401 || code == 403 -> tr("Clave no válida", "Invalid key")
                code != null -> tr("El servicio respondió con error $code", "The service responded with error $code")
                e is IOException -> tr("No se pudo conectar. Revisa la conexión.", "Couldn't connect. Check your connection.")
                else -> e.message ?: tr("No se pudo comprobar", "Couldn't verify")
            },
        )
    }

    private val ApiService.storeKey: String get() = TokenStore.keyNameFor(this)
}
