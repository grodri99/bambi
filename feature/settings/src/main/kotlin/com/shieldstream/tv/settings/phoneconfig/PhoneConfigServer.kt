package com.shieldstream.tv.settings.phoneconfig

import android.content.Context
import android.util.Base64
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SESSION_DURATION_MS = 10 * 60 * 1000L
private const val MAX_BODY_BYTES = 64 * 1024L

data class PhoneConfigConnection(
    val url: String,
    val displayUrl: String,
    val expiresAt: Long,
)

class PhoneConfigServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: PhoneConfigController,
    private val json: Json,
    private val texts: Texts,
) {
    private var server: LocalServer? = null
    private val _lastConfig = MutableStateFlow<PhoneConfigDto?>(null)
    val lastConfig: StateFlow<PhoneConfigDto?> = _lastConfig.asStateFlow()
    private val _lastSavedConfig = MutableStateFlow<PhoneConfigDto?>(null)
    val lastSavedConfig: StateFlow<PhoneConfigDto?> = _lastSavedConfig.asStateFlow()

    @Synchronized
    internal fun start(
        advertisedAddress: String? = null,
        mode: PhoneConfigMode = PhoneConfigMode.EDIT,
    ): PhoneConfigConnection {
        stop()
        _lastConfig.value = null
        _lastSavedConfig.value = null
        val token = ByteArray(32).also(SecureRandom()::nextBytes).toUrlToken()
        val expiresAt = System.currentTimeMillis() + SESSION_DURATION_MS
        val html = context.assets.open("phone_config.html").bufferedReader().use { it.readText() }
        val logo = context.assets.open("phone_config_logo.png").use { it.readBytes() }
        val address = advertisedAddress ?: localIpv4Address() ?: throw IOException(texts.get(StringsR.string.settings_phone_config_no_network))
        val localServer = LocalServer(address, html, logo, token, expiresAt, mode)
        try {
            localServer.start(5_000)
            server = localServer
            val displayUrl = "http://$address:${localServer.listeningPort}"
            return PhoneConfigConnection("$displayUrl/#$token", displayUrl, expiresAt)
        } catch (e: Exception) {
            localServer.stop()
            throw e
        }
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
    }

    private inner class LocalServer(
        address: String,
        private val html: String,
        private val logo: ByteArray,
        private val token: String,
        private val expiresAt: Long,
        private val mode: PhoneConfigMode,
    ) : NanoHTTPD(address, 0) {
        override fun serve(session: IHTTPSession): Response {
            if (System.currentTimeMillis() >= expiresAt) return jsonResponse(Response.Status.GONE, "{\"error\":\"expired\"}")
            if (session.method == Method.GET && (session.uri == "/" || session.uri == "/index.html")) {
                return response(Response.Status.OK, "text/html; charset=utf-8", html)
            }
            if (session.method == Method.GET && session.uri == "/logo.png") {
                return imageResponse(logo)
            }
            if (session.uri != "/api/config") return jsonResponse(Response.Status.NOT_FOUND, "{\"error\":\"not_found\"}")
            if (!authorized(session.headers["authorization"])) return jsonResponse(Response.Status.UNAUTHORIZED, "{\"error\":\"unauthorized\"}")
            return when (session.method) {
                Method.GET -> {
                    val config = runBlocking { controller.snapshot(mode) }
                    _lastConfig.value = config
                    jsonResponse(Response.Status.OK, config)
                }
                Method.PUT -> update(session)
                else -> jsonResponse(Response.Status.METHOD_NOT_ALLOWED, "{\"error\":\"method_not_allowed\"}")
            }
        }

        private fun update(session: IHTTPSession): Response {
            val length = session.headers["content-length"]?.toLongOrNull()
                ?: return jsonResponse(Response.Status.LENGTH_REQUIRED, "{\"error\":\"length_required\"}")
            if (length > MAX_BODY_BYTES) return jsonResponse(Response.Status.PAYLOAD_TOO_LARGE, "{\"error\":\"too_large\"}")
            return try {
                val body = HashMap<String, String>()
                session.parseBody(body)
                val rawJson = body["postData"] ?: body["content"]?.let(::File)?.readText().orEmpty()
                val request = json.decodeFromString(PhoneConfigUpdateDto.serializer(), rawJson)
                when (val result = runBlocking { controller.update(request, mode) }) {
                    is PhoneConfigUpdateResult.Success -> {
                        _lastConfig.value = result.config
                        _lastSavedConfig.value = result.config
                        jsonResponse(Response.Status.OK, result.config)
                    }
                    is PhoneConfigUpdateResult.ValidationFailed -> jsonResponse(Response.Status.BAD_REQUEST, mapOf("errors" to result.errors))
                }
            } catch (e: IllegalArgumentException) {
                jsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to (e.message ?: texts.get(StringsR.string.settings_phone_config_invalid_request))))
            } catch (_: Exception) {
                jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error" to texts.get(StringsR.string.settings_phone_config_save_failed)))
            }
        }

        private fun authorized(header: String?): Boolean {
            val supplied = header?.removePrefix("Bearer ")?.toByteArray(Charsets.UTF_8) ?: return false
            return MessageDigest.isEqual(supplied, token.toByteArray(Charsets.UTF_8))
        }
    }

    private fun imageResponse(body: ByteArray): Response =
        NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "image/png", ByteArrayInputStream(body), body.size.toLong()).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
        }

    private inline fun <reified T> jsonResponse(status: Response.Status, value: T): Response =
        response(status, "application/json; charset=utf-8", json.encodeToString(value))

    private fun response(status: Response.Status, mimeType: String, body: String): Response =
        NanoHTTPD.newFixedLengthResponse(status, mimeType, body).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
            addHeader("Referrer-Policy", "no-referrer")
            addHeader("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'")
        }

    private fun ByteArray.toUrlToken(): String = Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun localIpv4Address(): String? = NetworkInterface.getNetworkInterfaces().toList()
        .sortedBy { iface -> if (iface.name == "wlan0") 0 else if (iface.name == "eth0") 1 else 2 }
        .flatMap { iface -> iface.inetAddresses.toList() }
        .firstOrNull { address -> address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress }
        ?.hostAddress
        ?.substringBefore('%')
}
