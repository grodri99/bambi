package com.shieldstream.tv.data.repository

import com.shieldstream.tv.domain.DebridCacheRepository
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.RealDebridRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.TorBoxRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Ver [DebridCacheRepository]: pregunta al servicio activo con lo que cada uno permite. */
@Singleton
class DebridCacheRepositoryImpl @Inject constructor(
    private val settings: SettingsRepository,
    private val realDebrid: RealDebridRepository,
    private val torBox: TorBoxRepository,
) : DebridCacheRepository {
    // La lista de la cuenta de Real-Debrid es la misma para todas las tandas de una búsqueda: se guarda un minuto.
    @Volatile private var rdLibrary: Pair<Long, Set<String>>? = null

    override suspend fun cachedAmong(hashes: Collection<String>): Set<String> = when (settings.debridService.first()) {
        // Real-Debrid: los torrents que ya están en la cuenta (como hasta ahora).
        DebridService.REAL_DEBRID -> {
            val now = System.currentTimeMillis()
            val library = rdLibrary?.takeIf { now - it.first < RD_LIBRARY_TTL_MS }?.second
                ?: realDebrid.cachedHashes().map { it.lowercase() }.toSet().also { rdLibrary = now to it }
            hashes.map { it.lowercase() }.filter { it in library }.toSet()
        }
        DebridService.TORBOX -> torBox.cachedAmong(hashes)
        // El resto no tiene consulta propia en la app (sus fuentes de Torrentio y Comet ya llegan marcadas).
        else -> emptySet()
    }

    private companion object {
        const val RD_LIBRARY_TTL_MS = 60_000L
    }
}
