package com.shieldstream.tv.data.repository

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import android.net.Uri
import android.util.Log
import com.shieldstream.tv.domain.DebridLink
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.StremioAddons
import com.shieldstream.tv.domain.TorrentioLinkResolver
import com.shieldstream.tv.network.dto.TorrentioStreamDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentioLinkResolverImpl @Inject constructor(
    base: OkHttpClient,
    private val texts: Texts,
    private val store: com.shieldstream.tv.data.security.TokenStore,
) : TorrentioLinkResolver {

    override fun resolveUrlFor(service: DebridService, infoHash: String, fileIdx: Int?, fileName: String?): String? {
        val key = store.getSecret(com.shieldstream.tv.data.security.TokenStore.keyNameFor(service.apiService))
            ?.takeIf { it.isNotBlank() } ?: return null
        // Mismo formato que los enlaces que da Torrentio: /resolve/servicio/clave/hash/info/índice/archivo.
        return "$TORRENTIO_BASE/resolve/${service.torrentioKey}/${Uri.encode(key)}/${infoHash.lowercase()}/null/" +
            "${fileIdx ?: "null"}/${Uri.encode(fileName ?: "video")}"
    }

    private val client = base.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(100, TimeUnit.SECONDS)
        .build()

    override suspend fun resolve(url: String, service: String): DebridLink = withContext(Dispatchers.IO) {
        var current = url
        repeat(MAX_HOPS) {
            val request = Request.Builder().url(current).get().header("User-Agent", "Bambi").header("Range", "bytes=0-0").build()
            val next: String? = client.newCall(request).execute().use { response ->
                val location = response.header("Location")
                when {
                    response.isRedirect && location != null ->
                        response.request.url.resolve(location)?.toString() ?: location
                    response.isSuccessful -> {
                        val type = response.header("Content-Type").orEmpty().lowercase()
                        if (type.startsWith("text/") || "json" in type) {
                            val host = Uri.parse(current).host
                            return@withContext DebridLink.Problem(
                                texts.get(StringsR.string.data_no_video_for_source, host.toString()),
                                notCached = false,
                            )
                        }
                        return@withContext DebridLink.Ready(current)
                    }
                    else -> {
                        return@withContext DebridLink.Problem(
                        texts.get(StringsR.string.data_link_http_error, Uri.parse(current).host.toString(), response.code, service),
                        notCached = false,
                    )
                    }
                }
            }
            checkNotNull(next)
            staticVideoProblem(next, service)?.let { return@withContext it }
            if (isTorrentio(url) && !isTorrentio(next)) return@withContext DebridLink.Ready(next)
            current = next
        }
        DebridLink.Problem(texts.get(StringsR.string.data_too_many_redirects, service), notCached = false)
    }

    private fun isTorrentio(url: String) = Uri.parse(url).host?.contains("torrentio") == true

    private fun staticVideoProblem(url: String, service: String): DebridLink.Problem? {
        if (!isTorrentio(url) || !url.contains("/videos/")) return null
        val file = url.substringAfterLast('/')
        return when {
            file.startsWith("downloading") -> DebridLink.Problem(
                texts.get(StringsR.string.data_download_started, service),
                notCached = true,
            )
            file.startsWith("download_failed") -> DebridLink.Problem(texts.get(StringsR.string.data_download_failed, service), notCached = true)
            file.startsWith("failed_access") -> DebridLink.Problem(
                texts.get(StringsR.string.data_invalid_or_expired_key, service),
                notCached = false,
            )
            file.startsWith("failed_rar") -> DebridLink.Problem(texts.get(StringsR.string.data_this_source_is_packed_in_a_rar), notCached = true)
            file.startsWith("failed_too_big") -> DebridLink.Problem(texts.get(StringsR.string.data_source_too_big, service), notCached = true)
            file.startsWith("failed_opening") -> DebridLink.Problem(texts.get(StringsR.string.data_open_source_failed, service), notCached = true)
            file.startsWith("failed_infringement") -> DebridLink.Problem(texts.get(StringsR.string.data_source_copyright, service), notCached = true)
            file.startsWith("limits_exceeded") -> DebridLink.Problem(texts.get(StringsR.string.data_download_limit, service), notCached = false)
            file.startsWith("blocked_access") -> DebridLink.Problem(texts.get(StringsR.string.data_service_blocked, service), notCached = false)
            file.startsWith("failed_unavailable") -> DebridLink.Problem(texts.get(StringsR.string.data_service_unavailable, service), notCached = false)
            else -> DebridLink.Problem(texts.get(StringsR.string.data_torrentio_link_failed, service), notCached = true)
        }
    }

    private companion object {
        const val MAX_HOPS = 4
        const val TORRENTIO_BASE = "https://torrentio.strem.fun"
    }
}
