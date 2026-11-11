package com.shieldstream.tv.data.subtitles

import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.model.ExternalSubtitle
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.SubtitleQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

interface SubtitleProvider {
    val name: String

    val enabled: Boolean get() = true

    suspend fun search(query: SubtitleQuery): List<ExternalSubtitle>
}

internal const val USER_AGENT = "Bambi/1.0 (Android TV)"

internal val subtitleJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

internal suspend fun OkHttpClient.getString(request: Request): String = withContext(Dispatchers.IO) {
    newCall(request).execute().use { response ->
        check(response.isSuccessful) { "HTTP ${response.code}" }
        response.body?.string().orEmpty()
    }
}

// ---------------------------------------------------------------------------
// ---------------------------------------------------------------------------

@Serializable
internal data class StremioSubtitlesResponse(val subtitles: List<StremioSubtitleDto> = emptyList())

@Serializable
internal data class StremioSubtitleDto(
    val id: String = "",
    val url: String = "",
    val lang: String = "",
    val subtitleFileName: String? = null,
    val movieReleaseName: String? = null,
)

@Singleton
class OpenSubtitlesProvider @Inject constructor(
    private val http: SubtitleHttp,
) : SubtitleProvider {
    private val client get() = http.client
    override val name = "OpenSubtitles"

    override suspend fun search(query: SubtitleQuery): List<ExternalSubtitle> {
        val media = query.media
        val imdb = media.imdbId ?: return emptyList()
        val (type, id) = if (media.type == MediaType.TV && media.season != null && media.episode != null) {
            "series" to "$imdb:${media.season}:${media.episode}"
        } else {
            "movie" to imdb
        }
        val extra = query.fileName?.let { "/filename=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
        val request = Request.Builder()
            .url("$BASE/subtitles/$type/$id$extra.json")
            .header("User-Agent", USER_AGENT)
            .build()
        val body = client.getString(request)
        val response = subtitleJson.decodeFromString(StremioSubtitlesResponse.serializer(), body)
        return response.subtitles
            .filter { it.url.isNotBlank() && toIso1(it.lang) == query.language }
            .mapIndexed { index, s ->
                val latino = s.lang.lowercase() in LATIN_SPANISH
                val release = (s.subtitleFileName ?: s.movieReleaseName).orEmpty()
                ExternalSubtitle(
                    provider = name,
                    id = s.id.ifBlank { s.url },
                    release = if (latino) "(Latino) $release".trim() else release,
                    language = query.language,
                    hearingImpaired = false,
                    downloadUrl = s.url,
                    matchScore = if (query.fileName != null) (0.6f - index * 0.05f).coerceAtLeast(0f) else 0f,
                )
            }
    }

    private companion object {
        const val BASE = "https://opensubtitles-v3.strem.io"

        val LATIN_SPANISH = setOf("spl", "spn", "ea")

        fun toIso1(code: String): String? = when (code.lowercase()) {
            "spa", "spl", "spn", "es", "ea" -> "es"
            "eng", "en" -> "en"
            "cat", "ca" -> "ca"
            "fre", "fra", "fr" -> "fr"
            "ita", "it" -> "it"
            "por", "pob", "pt", "pb" -> "pt"
            "ger", "deu", "de" -> "de"
            else -> null
        }
    }
}

// ---------------------------------------------------------------------------
// ---------------------------------------------------------------------------

@Serializable
internal data class PodnapisiResponse(val data: List<PodnapisiItem> = emptyList())

@Serializable
internal data class PodnapisiItem(
    val id: String = "",
    val publish_id: String? = null,
    val language: String = "",
    val custom_releases: List<String> = emptyList(),
    val flags: List<String> = emptyList(),
)

@Singleton
class PodnapisiProvider @Inject constructor(
    private val http: SubtitleHttp,
) : SubtitleProvider {
    private val client get() = http.client
    override val name = "Podnapisi"

    override suspend fun search(query: SubtitleQuery): List<ExternalSubtitle> {
        val media = query.media
        val keywords = media.originalTitle?.takeIf { it.isNotBlank() } ?: media.title
        val url = "$BASE/subtitles/search/advanced".toHttpUrl().newBuilder()
            .addQueryParameter("keywords", keywords)
            .addQueryParameter("language", query.language)
            .apply {
                if (media.type == MediaType.TV) {
                    addQueryParameter("movie_type", "tv-series")
                    addQueryParameter("movie_type", "mini-series")
                    media.season?.let { addQueryParameter("seasons", it.toString()) }
                    media.episode?.let { addQueryParameter("episodes", it.toString()) }
                } else {
                    addQueryParameter("movie_type", "movie")
                    media.year?.let { addQueryParameter("year", it.toString()) }
                }
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        val body = client.getString(request)
        val response = subtitleJson.decodeFromString(PodnapisiResponse.serializer(), body)
        return response.data
            .filter { it.publish_id != null && it.language.equals(query.language, ignoreCase = true) }
            .map { item ->
                ExternalSubtitle(
                    provider = name,
                    id = item.publish_id!!,
                    release = bestRelease(item.custom_releases, query.fileName),
                    language = query.language,
                    hearingImpaired = "hearing_impaired" in item.flags,
                    downloadUrl = "$BASE/subtitles/${item.publish_id}/download",
                )
            }
    }

    private companion object {
        const val BASE = "https://www.podnapisi.net"
    }
}

// ---------------------------------------------------------------------------
// SubDL (proveedor de a4kSubtitles; necesita API key gratuita → local.properties: subdlApiKey=…)
// ---------------------------------------------------------------------------

@Serializable
internal data class SubDlResponse(val subtitles: List<SubDlItem> = emptyList())

@Serializable
internal data class SubDlItem(
    val release_name: String = "",
    val language: String = "",
    val hi: Boolean = false,
    val url: String = "",
)

@Singleton
class SubDlProvider @Inject constructor(
    private val http: SubtitleHttp,
    private val store: TokenStore,
) : SubtitleProvider {
    override val name = "SubDL"

    private val apiKey: String get() = store.subdlKey.orEmpty()
    private val client get() = http.client
    override val enabled: Boolean get() = apiKey.isNotBlank()

    override suspend fun search(query: SubtitleQuery): List<ExternalSubtitle> {
        val media = query.media
        val url = "$API/api/v2/subtitles/search".toHttpUrl().newBuilder()
            .addQueryParameter("languages", query.language.uppercase())
            .apply {
                media.imdbId?.let { addQueryParameter("imdb_id", it) }
                if (media.type == MediaType.TV) {
                    addQueryParameter("type", "tv")
                    addQueryParameter("film_name", media.originalTitle ?: media.title)
                    media.season?.let { addQueryParameter("season", it.toString()) }
                    media.episode?.let { addQueryParameter("episode", it.toString()) }
                } else {
                    addQueryParameter("type", "movie")
                    media.year?.let { addQueryParameter("year", it.toString()) }
                }
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", USER_AGENT)
            .build()
        val body = client.getString(request)
        val response = subtitleJson.decodeFromString(SubDlResponse.serializer(), body)
        return response.subtitles
            .filter { it.url.isNotBlank() && languageMatches(it.language, query.language) }
            .map { item ->
                ExternalSubtitle(
                    provider = name,
                    id = item.url,
                    release = item.release_name,
                    language = query.language,
                    hearingImpaired = item.hi,
                    downloadUrl = "$DOWNLOAD${item.url}",
                )
            }
    }

    private fun languageMatches(value: String, iso1: String): Boolean {
        val v = value.lowercase()
        return v == iso1 || v.startsWith(ENGLISH_NAMES[iso1] ?: "\u0000")
    }

    private companion object {
        const val API = "https://api.subdl.com"
        const val DOWNLOAD = "https://dl.subdl.com"
        val ENGLISH_NAMES = mapOf(
            "es" to "spanish", "en" to "english", "ca" to "catalan", "fr" to "french",
            "it" to "italian", "pt" to "portuguese", "de" to "german",
        )
    }
}

internal fun bestRelease(releases: List<String>, fileName: String?): String {
    if (releases.isEmpty()) return ""
    if (fileName == null) return releases.first()
    return releases.maxByOrNull { similarity(it, fileName) } ?: releases.first()
}

internal fun similarity(a: String, b: String): Float {
    fun tokens(s: String) = s.lowercase()
        .substringBeforeLast('.')
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length > 1 }
        .toSet()
    val ta = tokens(a)
    val tb = tokens(b)
    if (ta.isEmpty() || tb.isEmpty()) return 0f
    return (ta intersect tb).size.toFloat() / (ta union tb).size
}
