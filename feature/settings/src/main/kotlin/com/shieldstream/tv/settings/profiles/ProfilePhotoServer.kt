package com.shieldstream.tv.settings.profiles

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Servidor de un solo uso en la red de casa para subir la foto de un perfil desde el móvil (se llega con el QR).
 * La página recorta la foto en cuadrado y la reduce en el propio móvil; aquí solo se comprueba que es una imagen y se
 * guarda en la memoria interna. Dura 10 minutos y pide el código secreto que va en el QR (después de "#", así que no
 * viaja en la petición de la página).
 */
internal class ProfilePhotoServer(
    private val context: Context,
    private val profileId: String,
    private val page: String,
) {
    data class Connection(val url: String, val displayUrl: String)

    private var server: Server? = null
    private val _uploaded = MutableStateFlow<String?>(null)

    /** URI (file://) de la foto recién subida. */
    val uploaded: StateFlow<String?> = _uploaded.asStateFlow()

    @Synchronized
    fun start(): Connection {
        stop()
        val token = ByteArray(24).also(SecureRandom()::nextBytes)
            .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
        val address = localIpv4Address() ?: throw IOException("no_network")
        val logo = runCatching { context.assets.open("phone_config_logo.png").use { it.readBytes() } }.getOrNull()
        val s = Server(address, token, System.currentTimeMillis() + SESSION_MS, logo)
        s.start(5_000)
        server = s
        val display = "http://$address:${s.listeningPort}"
        return Connection("$display/#$token", display)
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
    }

    private fun save(bytes: ByteArray): String {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "${profileId}_${System.currentTimeMillis()}.jpg")
        file.writeBytes(bytes)
        // Solo la última foto de cada perfil.
        dir.listFiles()?.filter { it.name.startsWith("${profileId}_") && it != file }?.forEach { it.delete() }
        return Uri.fromFile(file).toString()
    }

    private inner class Server(
        address: String,
        private val token: String,
        private val expiresAt: Long,
        private val logo: ByteArray?,
    ) : NanoHTTPD(address, 0) {
        override fun serve(session: IHTTPSession): Response {
            if (System.currentTimeMillis() >= expiresAt) return text(Response.Status.GONE, "expired")
            if (session.method == Method.GET && (session.uri == "/" || session.uri == "/index.html")) {
                return html(page)
            }
            if (session.method == Method.GET && session.uri == "/logo.png" && logo != null) {
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "image/png", ByteArrayInputStream(logo), logo.size.toLong())
            }
            if (session.uri != "/api/photo" || session.method != Method.POST) return text(Response.Status.NOT_FOUND, "not_found")
            val supplied = session.headers["authorization"]?.removePrefix("Bearer ")?.toByteArray() ?: ByteArray(0)
            if (!MessageDigest.isEqual(supplied, token.toByteArray())) return text(Response.Status.UNAUTHORIZED, "unauthorized")
            val length = session.headers["content-length"]?.toIntOrNull() ?: return text(Response.Status.LENGTH_REQUIRED, "length_required")
            if (length <= 0 || length > MAX_BYTES) return text(Response.Status.PAYLOAD_TOO_LARGE, "too_large")
            return try {
                val bytes = ByteArray(length)
                DataInputStream(session.inputStream).readFully(bytes)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return text(Response.Status.BAD_REQUEST, "not_an_image")
                _uploaded.value = save(bytes)
                text(Response.Status.OK, "ok")
            } catch (_: Exception) {
                text(Response.Status.INTERNAL_ERROR, "error")
            }
        }

        private fun html(body: String): Response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
            addHeader("Referrer-Policy", "no-referrer")
            addHeader(
                "Content-Security-Policy",
                "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; " +
                    "img-src 'self' data: blob:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
            )
        }

        private fun text(status: Response.Status, body: String): Response =
            NanoHTTPD.newFixedLengthResponse(status, "text/plain; charset=utf-8", body).apply { addHeader("Cache-Control", "no-store") }
    }

    private fun localIpv4Address(): String? = NetworkInterface.getNetworkInterfaces().toList()
        .sortedBy { iface -> if (iface.name == "wlan0") 0 else if (iface.name == "eth0") 1 else 2 }
        .flatMap { iface -> iface.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
        ?.hostAddress
        ?.substringBefore('%')

    companion object {
        const val DIR = "profile_photos"
        private const val SESSION_MS = 10 * 60 * 1000L
        private const val MAX_BYTES = 4 * 1024 * 1024
    }
}
