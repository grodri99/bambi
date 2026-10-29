package com.shieldstream.tv.data.repository

import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.AllDebridRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.tr
import com.shieldstream.tv.network.api.AllDebridApi
import com.shieldstream.tv.network.dto.AllDebridLinkDto
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AllDebridRepositoryImpl @Inject constructor(
    private val api: AllDebridApi,
    private val store: TokenStore,
) : AllDebridRepository {

    private fun key(): String =
        store.getSecret(TokenStore.keyNameFor(ApiService.ALLDEBRID))?.trim()?.takeIf { it.isNotBlank() }
            ?: error(tr("Clave de AllDebrid no configurada", "AllDebrid key not set"))

    override suspend fun resolveMagnet(source: StreamSource): String {
        val apiKey = key()
        val uploadResp = api.uploadMagnet(apiKey = apiKey, magnet = source.magnet)
        val upload = uploadResp.data
        if (uploadResp.status != "success" || upload == null) {
            error(uploadResp.error?.message ?: tr("Error añadiendo el magnet a AllDebrid", "Error adding magnet to AllDebrid"))
        }
        val magnet = upload.magnets.firstOrNull()
            ?: error(tr("AllDebrid no devolvió el magnet", "AllDebrid returned no magnet"))
        magnet.error?.let { error(it.message ?: tr("Error en el magnet de AllDebrid", "AllDebrid magnet error")) }

        val magnetId = magnet.id
        var isReady = magnet.ready
        // Si no vino ready de inmediato: 4 consultas de 1,5 s. Un magnet cacheado se marca en 1–3 s.
        if (!isReady) {
            repeat(GRACE_ATTEMPTS) {
                delay(GRACE_INTERVAL_MS)
                val status = runCatching { api.magnetStatus(apiKey = apiKey, id = magnetId) }.getOrNull()
                if (status?.data?.magnets?.firstOrNull()?.statusCode == STATUS_READY) isReady = true
            }
        }
        if (!isReady) {
            // No está en caché: se borra para no ocupar huecos de la cuenta y se avisa para probar otra fuente.
            runCatching { api.deleteMagnet(apiKey = apiKey, id = magnetId) }
            error(tr("Esta fuente no está en la caché de AllDebrid. Prueba otra.", "This source isn't cached on AllDebrid. Try another."))
        }

        val magnetInfo = api.magnetStatus(apiKey = apiKey, id = magnetId).data?.magnets?.firstOrNull()
            ?: error(tr("No se pudo leer el estado del magnet en AllDebrid", "Couldn't read the magnet status from AllDebrid"))
        val target = pickFile(magnetInfo.links, source)
            ?: error(tr("El torrent no tiene archivos de vídeo en AllDebrid", "The torrent has no video files in AllDebrid"))

        val unlockResp = api.unlockLink(apiKey = apiKey, link = target.link)
        val unlocked = unlockResp.data
        if (unlockResp.status != "success" || unlocked == null || unlocked.link.isBlank()) {
            error(unlockResp.error?.message ?: tr("No se pudo desbloquear el enlace en AllDebrid", "Couldn't unlock the link in AllDebrid"))
        }
        return unlocked.link
    }

    /** Elige el archivo del torrent: por nombre (packs) → episodio (SxxEyy) → el vídeo más grande. */
    private fun pickFile(links: List<AllDebridLinkDto>, source: StreamSource): AllDebridLinkDto? {
        val videos = links.filter { l -> VIDEO_EXTENSIONS.any { l.filename.endsWith(".$it", ignoreCase = true) } }
        val pool = videos.ifEmpty { links }
        if (pool.isEmpty()) return null

        source.fileName?.let { name ->
            pool.firstOrNull { it.filename.substringAfterLast('/').equals(name, ignoreCase = true) }?.let { return it }
        }
        val season = source.season
        val episode = source.episode
        if (season != null && episode != null) {
            val tag = "s%02de%02d".format(season, episode)
            pool.firstOrNull { it.filename.lowercase().contains(tag) }?.let { return it }
        }
        return pool.maxByOrNull { it.size }
    }

    private companion object {
        const val GRACE_ATTEMPTS = 4
        const val GRACE_INTERVAL_MS = 1_500L

        /** `statusCode` de AllDebrid para un magnet ya listo para desbloquear. */
        const val STATUS_READY = 4

        val VIDEO_EXTENSIONS = listOf("mkv", "mp4", "avi", "mov", "m4v", "wmv", "flv", "webm", "ts")
    }
}
