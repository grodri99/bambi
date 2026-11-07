package com.shieldstream.tv.domain

interface RealDebridRepository : MagnetDebridRepository {
    suspend fun validateToken(token: String): Boolean

    suspend fun cachedHashes(): Set<String>

    fun isBlocked(infoHash: String): Boolean = false
}
