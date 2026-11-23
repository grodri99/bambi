package com.shieldstream.tv.desktop

import com.shieldstream.tv.data.repository.StreamSecrets
import com.shieldstream.tv.data.repository.StreamSettings
import com.shieldstream.tv.data.repository.StreamTexts
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.SourceProvider

/** La búsqueda de fuentes de la Shield (:core:streams) con los ajustes y textos de escritorio. */
class DesktopStreamPlatform(private val config: ConfigStore) : StreamTexts, StreamSecrets, StreamSettings {
    override fun providerTitle(provider: SourceProvider) = when (provider) {
        SourceProvider.CASTELLANO -> "En castellano"
        SourceProvider.TORRENTIO -> "Torrentio"
        SourceProvider.PEERFLIX -> "Peerflix"
        SourceProvider.KNABEN -> "Knaben"
        SourceProvider.THEPIRATEBAY -> "The Pirate Bay"
        SourceProvider.NYAA -> "Nyaa"
        SourceProvider.COMET -> "Comet"
        SourceProvider.AIOSTREAMS -> "Addon de Stremio"
    }

    /** Como en la tele: Knaben y Nyaa van en la sección de The Pirate Bay. */
    override fun providerSection(provider: SourceProvider) = when (provider) {
        SourceProvider.KNABEN, SourceProvider.THEPIRATEBAY, SourceProvider.NYAA -> providerTitle(SourceProvider.THEPIRATEBAY)
        else -> providerTitle(provider)
    }

    override fun imdbMissing(title: String) = "No se encontró el id de IMDb de «$title»"
    override fun pickEpisode() = "Elige un episodio"
    override fun notResponding() = "No responde ahora mismo"
    override fun noSources() = "Sin fuentes para esto"
    override fun providerUnavailable(names: String) = "$names no responde ahora mismo. Prueba en un rato."
    override fun debridKeyMissing(service: String) = "Falta la clave de $service. Añádela en Ajustes."
    override fun stremioAddon() = "el addon de Stremio"
    override fun subscriptionExpired(service: String) = "La suscripción de $service ha caducado o no es válida."
    override fun invalidServiceKey(service: String) = "Clave de $service no válida. Revísala en Ajustes."
    override fun serviceBlocked(service: String) = "$service ha bloqueado el acceso: revisa tu cuenta o el correo."
    override fun serviceUnavailableTorrentio(service: String) = "$service no está disponible ahora mismo (según Torrentio). Prueba más tarde."

    override fun secret(service: ApiService): String? {
        val cfg = config.current
        return when (service) {
            cfg.debridService.apiService -> cfg.debridKey.trim()
            ApiService.AIOSTREAMS -> cfg.addons.trim()
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    override suspend fun sourceProviders(): Set<SourceProvider> = SourceProvider.entries.toSet()
    override suspend fun debridService(): DebridService = config.current.debridService
    override suspend fun spanishFirst(): Boolean = config.current.spanishFirst

    /** En escritorio todo se reproduce a través de Torrentio: solo capítulos sueltos, nunca packs. */
    override suspend fun canPickFile(): Boolean = false
}
