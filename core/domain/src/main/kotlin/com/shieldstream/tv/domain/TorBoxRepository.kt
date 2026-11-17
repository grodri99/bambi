package com.shieldstream.tv.domain

/**
 * TorBox con resolución propia (sin pasar por Torrentio): así los buscadores de la app (The Pirate Bay, Knaben, Nyaa
 * y castellano) funcionan con TorBox igual que con Real-Debrid, y además se puede preguntar qué torrents tiene ya en
 * caché para marcarlos con ⚡.
 */
interface TorBoxRepository : MagnetDebridRepository {
    /** De estos hashes, los que TorBox tiene ya descargados (en minúsculas). */
    suspend fun cachedAmong(hashes: Collection<String>): Set<String>
}
