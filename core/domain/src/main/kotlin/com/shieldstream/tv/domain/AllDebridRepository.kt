package com.shieldstream.tv.domain

import com.shieldstream.tv.model.StreamSource

/**
 * Resuelve un torrent en la nube de AllDebrid: comprueba si está en caché, elige el archivo correcto
 * y obtiene el enlace directo de streaming.
 */
interface AllDebridRepository {
    suspend fun resolveMagnet(source: StreamSource): String
}
