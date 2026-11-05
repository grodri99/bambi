package com.shieldstream.tv.domain

import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.VideoCodec

/**
 * Ordena las fuentes priorizando:
 *  1. Disponibilidad en Real-Debrid (cacheado).
 *  2. Decodificación por hardware y compatibilidad HDR/DV con el display + calidad.
 *  3. Seeders y tamaño como desempate.
 *
 * Regla de oro: nunca degradar a SDR si existe una fuente HDR/DV compatible y cacheada.
 */
class PrioritizeSourcesUseCase {

    operator fun invoke(
        sources: List<StreamSource>,
        supportedHdr: Set<HdrType>,
        /** Vacío conserva el comportamiento de la Shield: AV1 por CPU y sin AV1 4K. */
        hardwareVideoCodecs: Set<VideoCodec> = emptySet(),
        preferCached: Boolean = true,
        /** Ajuste "Castellano primero": tras las que están en caché (arrancan al instante), las de audio en español. */
        preferSpanish: Boolean = false,
    ): List<StreamSource> {
        return sources
            // AV1 4K por CPU no es reproducible con fluidez; con decoder hardware sí puede priorizarse.
            .filterNot {
                it.videoCodec == VideoCodec.AV1 && AV1_4K.containsMatchIn(it.title) &&
                    !it.title.contains("1080p", ignoreCase = true) && VideoCodec.AV1 !in hardwareVideoCodecs
            }
            .map { source ->
                source.copy(
                    softwareDecode = source.softwareDecode &&
                        (source.videoCodec == null || source.videoCodec !in hardwareVideoCodecs),
                )
            }
            .sortedWith(
                compareByDescending<StreamSource> { if (preferCached) it.isCached else true }
                    .thenByDescending { if (preferSpanish) it.spanishAudioScore else 0 }
                    // AV1 = decodificación por CPU: solo si no hay nada por hardware.
                    .thenBy { it.softwareDecode }
                    .thenByDescending { effectiveHdrRank(it.hdrType, supportedHdr) }
                    .thenByDescending { it.quality.rank }
                    .thenByDescending { it.seeders ?: 0 }
                    .thenByDescending { it.sizeBytes },
            )
    }

    private companion object {
        // Se mira la release, no la etiqueta del proveedor: DS4K/4KDS pueden ser 1080p reescalado.
        val AV1_4K = Regex("(?<![a-z0-9])(2160p?|4k|uhd)(?![a-z0-9])", RegexOption.IGNORE_CASE)
    }

    /**
     * Rango HDR efectivo: el mejor tipo HDR que la fuente puede satisfacer dado el display.
     * Si el display no soporta ningún HDR, una fuente HDR se degrada (rank 0) por debajo
     * de sus equivalentes SDR... salvo que no exista alternativa.
     */
    private fun effectiveHdrRank(sourceHdr: HdrType, supported: Set<HdrType>): Int {
        if (supported.isEmpty()) return sourceHdr.rank
        return supported
            .filter { it.rank <= sourceHdr.rank }
            .maxOfOrNull { it.rank }
            ?: 0
    }
}
