package com.shieldstream.tv.data.repository

import com.shieldstream.tv.model.tr
import android.net.Uri
import android.util.Log
import com.shieldstream.tv.domain.DebridLink
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.TorrentioLinkResolver
import com.shieldstream.tv.network.dto.TorrentioStreamDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal fun torrentioConfig(debrid: DebridService, key: String): String = "${debrid.torrentioKey}=${Uri.encode(key)}"

internal fun TorrentioStreamDto.isDebridError(): Boolean =
    url?.contains("/videos/") == true && name.contains("error", ignoreCase = true)

internal fun debridErrorMessage(title: String, service: String): String = when {
    title.contains("expired", ignoreCase = true) -> tr("La suscripción de $service ha caducado o no es válida.", "Your $service subscription has expired or isn't valid.")
    title.contains("invalid", ignoreCase = true) -> tr("Clave de $service no válida. Revísala en Ajustes.", "Invalid $service key. Check it in Settings.")
    title.contains("blocked", ignoreCase = true) -> tr("$service ha bloqueado el acceso: revisa tu cuenta o el correo.", "$service has blocked access: check your account or email.")
    else -> tr("$service no está disponible ahora mismo (según Torrentio). Prueba más tarde.", "$service isn't available right now (according to Torrentio). Try again later.")
}

private val HASH = Regex("^[0-9a-fA-F]{40}$")

internal fun parseResolveUrl(url: String): Pair<String, Int?>? {
    val segments = Uri.parse(url).pathSegments
    val start = segments.indexOf("resolve").takeIf { it >= 0 } ?: return null
    val hash = segments.getOrNull(start + 3)?.takeIf { HASH.matches(it) }
        ?: segments.drop(start + 3).firstOrNull { HASH.matches(it) }
        ?: return null
    val fileIdx = segments.getOrNull(start + 5)?.toIntOrNull()
    return hash.lowercase() to fileIdx
}

@Singleton
class TorrentioLinkResolverImpl @Inject constructor(base: OkHttpClient) : TorrentioLinkResolver {

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
                                tr("$host no ha devuelto un vídeo para esta fuente.", "$host didn't return a video for this source."),
                                notCached = false,
                            )
                        }
                        return@withContext DebridLink.Ready(current)
                    }
                    else -> {
                        return@withContext DebridLink.Problem(
                        tr("${Uri.parse(current).host} respondió con error ${response.code} al pedir el enlace a $service.", "${Uri.parse(current).host} responded with error ${response.code} when requesting the $service link."),
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
        DebridLink.Problem(tr("Demasiadas redirecciones al pedir el enlace a $service.", "Too many redirects while requesting the $service link."), notCached = false)
    }

    private fun isTorrentio(url: String) = Uri.parse(url).host?.contains("torrentio") == true

    private fun staticVideoProblem(url: String, service: String): DebridLink.Problem? {
        if (!isTorrentio(url) || !url.contains("/videos/")) return null
        val file = url.substringAfterLast('/')
        return when {
            file.startsWith("downloading") -> DebridLink.Problem(
                tr("$service ha empezado a descargar esta fuente (no estaba en su caché). Prueba en unos minutos o elige una con ⚡.", "$service has started downloading this source (it wasn't cached). Try again in a few minutes or pick one with ⚡."),
                notCached = true,
            )
            file.startsWith("download_failed") -> DebridLink.Problem(tr("$service no pudo descargar esta fuente.", "$service couldn't download this source."), notCached = true)
            file.startsWith("failed_access") -> DebridLink.Problem(
                tr("Clave de $service no válida o suscripción caducada. Revísala en Ajustes.", "Invalid $service key or expired subscription. Check it in Settings."),
                notCached = false,
            )
            file.startsWith("failed_rar") -> DebridLink.Problem(tr("Esta fuente viene en RAR y no se puede reproducir.", "This source is packed in a RAR and can't be played."), notCached = true)
            file.startsWith("failed_too_big") -> DebridLink.Problem(tr("Esta fuente es demasiado grande para $service.", "This source is too big for $service."), notCached = true)
            file.startsWith("failed_opening") -> DebridLink.Problem(tr("$service no pudo abrir esta fuente.", "$service couldn't open this source."), notCached = true)
            file.startsWith("failed_infringement") -> DebridLink.Problem(tr("$service ha bloqueado esta fuente por derechos de autor.", "$service blocked this source for copyright reasons."), notCached = true)
            file.startsWith("limits_exceeded") -> DebridLink.Problem(tr("Has llegado al límite de descargas de $service.", "You've reached your $service download limit."), notCached = false)
            file.startsWith("blocked_access") -> DebridLink.Problem(tr("$service ha bloqueado el acceso: revisa tu cuenta o el correo.", "$service has blocked access: check your account or email."), notCached = false)
            file.startsWith("failed_unavailable") -> DebridLink.Problem(tr("$service no está disponible ahora mismo.", "$service isn't available right now."), notCached = false)
            else -> DebridLink.Problem(tr("Torrentio no pudo conseguir el enlace de $service.", "Torrentio couldn't get the $service link."), notCached = true)
        }
    }

    private companion object {
        const val MAX_HOPS = 4
    }
}

internal fun aioStreamBases(raw: String): List<String> =
    raw.split(Regex("[\\s,;]+")).mapNotNull { aioStreamBase(it) }.distinct()

internal fun aioStreamBase(manifestUrl: String): String? {
    val u = manifestUrl.trim().replaceFirst("stremio://", "https://")
    if (!u.startsWith("http") || !u.endsWith("/manifest.json")) return null
    return u.removeSuffix("/manifest.json")
}
