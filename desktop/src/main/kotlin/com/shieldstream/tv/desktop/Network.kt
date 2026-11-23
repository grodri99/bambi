package com.shieldstream.tv.desktop

import com.shieldstream.tv.network.CloudflareFallbackDns
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Red de Bambi en escritorio, resistente a bloqueos como en la tele: si el DNS de la operadora no resuelve un dominio
 * o lo manda a 0.0.0.0 / 127.0.0.1, se pregunta por DNS cifrado (DoH de Cloudflare); y si es una IP de Cloudflare
 * bloqueada, se prueban otras del mismo rango (CloudflareFallbackDns, el mismo código que la tele).
 */
object DesktopHttp {
    private val bootstrap = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build()

    private val doh: Dns = DnsOverHttps.Builder()
        .client(bootstrap)
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
        .build()

    val dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val system = runCatching { CloudflareFallbackDns.lookup(hostname) }.getOrNull()
            if (!system.isNullOrEmpty() && system.none(::looksBlocked)) return system
            val secure = runCatching { doh.lookup(hostname) }.getOrNull()
            return secure?.takeIf { it.isNotEmpty() } ?: system?.takeIf { it.isNotEmpty() } ?: throw UnknownHostException(hostname)
        }
    }

    /** Respuestas típicas de un DNS que bloquea: «ninguna parte» o el propio equipo. */
    fun looksBlocked(address: InetAddress) = address.isAnyLocalAddress || address.isLoopbackAddress

    val client: OkHttpClient = OkHttpClient.Builder()
        .dns(dns)
        .eventListener(CloudflareFallbackDns.failureListener)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        CloudflareFallbackDns.logger = { System.err.println("DNS: $it") }
    }
}

/** Qué hay detrás de un enlace de fuente antes de dárselo al reproductor. */
sealed interface LinkCheck {
    data class Ready(val url: String) : LinkCheck

    /** [skip]: probar la siguiente fuente; [stop]: el servicio pide calma, mejor no seguir probando. */
    data class Problem(val message: String, val stop: Boolean = false) : LinkCheck
}

/**
 * Sigue las redirecciones de un enlace /resolve/ de Torrentio (o de Comet, o de un addon) sin descargar el vídeo, como
 * TorrentioLinkResolverImpl en la tele: así se detectan los vídeos de aviso de Torrentio («descargando», «clave no
 * válida», «límite»…) y las respuestas que no son vídeo, y «Reproducir» salta a la siguiente fuente.
 */
class LinkResolver(base: OkHttpClient = DesktopHttp.client) {
    private val client = base.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(100, TimeUnit.SECONDS)
        .build()

    fun resolve(url: String, service: String): LinkCheck {
        var current = url
        repeat(MAX_HOPS) {
            val request = Request.Builder().url(current).get().header("User-Agent", "Bambi").header("Range", "bytes=0-0").build()
            val next: String = runCatching {
                client.newCall(request).execute().use { response ->
                    val location = response.header("Location")
                    when {
                        response.isRedirect && location != null -> response.request.url.resolve(location)?.toString() ?: location
                        response.isSuccessful -> {
                            val type = response.header("Content-Type").orEmpty().lowercase()
                            return if (type.startsWith("text/") || "json" in type) {
                                LinkCheck.Problem("${host(current)} no ha dado un vídeo para esta fuente.")
                            } else {
                                LinkCheck.Ready(current)
                            }
                        }
                        response.code == 429 -> return LinkCheck.Problem("$service pide calma: demasiadas peticiones. Prueba en un minuto.", stop = true)
                        else -> return LinkCheck.Problem("${host(current)} respondió ${response.code} ($service).")
                    }
                }
            }.getOrElse { return LinkCheck.Problem("No se pudo abrir el enlace (${it.message ?: "sin conexión"}).") }
            staticVideoProblem(next, service)?.let { return it }
            // Torrentio ya ha entregado el enlace del servicio: ese es el vídeo.
            if (isTorrentio(url) && !isTorrentio(next)) return LinkCheck.Ready(next)
            current = next
        }
        return LinkCheck.Problem("Demasiadas redirecciones ($service).")
    }

    private fun host(url: String) = runCatching { URI(url).host }.getOrNull() ?: "El servidor"

    private fun isTorrentio(url: String) = host(url).contains("torrentio")

    /** Los vídeos de aviso de Torrentio, con el mismo significado que en la tele. */
    private fun staticVideoProblem(url: String, service: String): LinkCheck.Problem? {
        if (!isTorrentio(url) || !url.contains("/videos/")) return null
        val file = url.substringAfterLast('/')
        return when {
            file.startsWith("downloading") -> LinkCheck.Problem("$service lo está descargando: aún no está en caché.")
            file.startsWith("download_failed") -> LinkCheck.Problem("$service no pudo descargarlo.")
            file.startsWith("failed_access") -> LinkCheck.Problem("Clave de $service no válida o caducada.", stop = true)
            file.startsWith("failed_rar") -> LinkCheck.Problem("Esta fuente viene en un RAR.")
            file.startsWith("failed_too_big") -> LinkCheck.Problem("Demasiado grande para $service.")
            file.startsWith("failed_opening") -> LinkCheck.Problem("$service no pudo abrir esta fuente.")
            file.startsWith("failed_infringement") -> LinkCheck.Problem("Retirada por derechos de autor.")
            file.startsWith("limits_exceeded") -> LinkCheck.Problem("Has llegado al límite de descargas de $service.", stop = true)
            file.startsWith("blocked_access") -> LinkCheck.Problem("$service ha bloqueado el acceso: revisa tu cuenta.", stop = true)
            file.startsWith("failed_unavailable") -> LinkCheck.Problem("$service no está disponible ahora mismo.", stop = true)
            else -> LinkCheck.Problem("Torrentio no pudo preparar esta fuente con $service.")
        }
    }

    private companion object {
        const val MAX_HOPS = 4
    }
}
