package com.shieldstream.tv.domain

import java.net.URI
import java.security.MessageDigest

data class StremioAddonMetadata(
    val name: String? = null,
    val logoUrl: String? = null,
)

data class StremioAddon(
    val id: String,
    val manifestUrl: String,
    val baseUrl: String?,
    val host: String,
) {
    val isValid: Boolean get() = baseUrl != null
}

object StremioAddons {
    private val separator = Regex("[\\s,;]+")

    fun parse(raw: String): List<StremioAddon> = raw.trim()
        .takeIf { it.isNotEmpty() }
        ?.split(separator)
        .orEmpty()
        .filter { it.isNotBlank() }
        .map { token ->
            val manifestUrl = token.trim()
            val base = baseUrl(manifestUrl)
            val idSource = base ?: manifestUrl
            StremioAddon(
                id = stableId(idSource),
                manifestUrl = manifestUrl,
                baseUrl = base,
                host = hostOf(manifestUrl),
            )
        }
        .distinctBy { it.id }

    fun serialize(addons: List<StremioAddon>): String = addons.joinToString(" ") { it.manifestUrl }

    fun baseUrl(manifestUrl: String): String? {
        val normalized = manifestUrl.trim().replaceFirst("stremio://", "https://")
        if (!normalized.startsWith("http") || !normalized.endsWith("/manifest.json")) return null
        return normalized.removeSuffix("/manifest.json")
    }

    private fun hostOf(manifestUrl: String): String = runCatching {
        URI(manifestUrl).host?.removePrefix("www.") ?: "Invalid addon"
    }.getOrDefault("Invalid addon")

    private fun stableId(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(12)
        .joinToString("") { "%02x".format(it) }
}
