package com.shieldstream.tv.domain

/**
 * Qué torrents tiene ya listos para reproducir el servicio debrid activo. Se usa para marcar con ⚡ las fuentes de los
 * buscadores propios (que llegan sin esa información) y para que "Reproducir" y las descargas prefieran esas.
 * Los servicios que no lo dicen devuelven un conjunto vacío: sus fuentes simplemente no salen marcadas.
 */
interface DebridCacheRepository {
    suspend fun cachedAmong(hashes: Collection<String>): Set<String>
}
