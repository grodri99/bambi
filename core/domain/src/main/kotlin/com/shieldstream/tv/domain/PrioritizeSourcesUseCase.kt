package com.shieldstream.tv.domain

import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.PreferredQuality
import com.shieldstream.tv.model.withInferredQuality
import com.shieldstream.tv.model.SpanishAudio
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.VideoCodec

class PrioritizeSourcesUseCase {

    /**
     * Orden de las fuentes, que es a la vez el de la lista de Fuentes y el de "Reproducir" (la primera es la que se
     * reproduce): en caché > decodificable por hardware > dentro de la [preferred] calidad preferida > resolución >
     * HDR > (desempate) castellano, seeds y tamaño. Con [preferSpanish], el castellano va antes como grupo (ver
     * [spanishFirst]); dentro de él también manda la calidad, no lo seguro que esté el detector de idioma.
     */
    operator fun invoke(
        sources: List<StreamSource>,
        supportedHdr: Set<HdrType>,
        hardwareVideoCodecs: Set<VideoCodec> = emptySet(),
        preferCached: Boolean = true,
        preferSpanish: Boolean = false,
        preferred: PreferredQuality = PreferredQuality.DV_4K,
    ): List<StreamSource> {
        val sorted = sources
            .filterNot {
                it.videoCodec == VideoCodec.AV1 && AV1_4K.containsMatchIn(it.title) &&
                    !it.title.contains("1080p", ignoreCase = true) && VideoCodec.AV1 !in hardwareVideoCodecs
            }
            .map { source ->
                source.withInferredQuality().copy(
                    softwareDecode = source.softwareDecode &&
                        (source.videoCodec == null || source.videoCodec !in hardwareVideoCodecs),
                )
            }
            .sortedWith(
                compareByDescending<StreamSource> { if (preferCached) it.isCached else true }
                    .thenBy { it.softwareDecode }
                    .thenByDescending { preferred.allows(it) }
                    .thenByDescending { it.quality.rank }
                    .thenByDescending { effectiveHdrRank(it.hdrType, supportedHdr) }
                    .thenByDescending { it.spanishAudioScore }
                    .thenByDescending { it.seeders ?: 0 }
                    .thenByDescending { it.sizeBytes },
            )
        return if (preferSpanish) spanishFirst(sorted) else sorted
    }

    /**
     * "Castellano primero" de verdad. Con solo "en caché > en castellano", en series casi nada en castellano está
     * en caché y "Reproducir" acababa en una fuente en inglés aunque hubiera castellano.
     *
     * Orden: castellano en caché > hasta [SPANISH_UNCACHED_FIRST] en castellano sin caché (si no arrancan, el
     * reproductor pasa a la siguiente) > el resto en caché > las demás en castellano > el resto.
     * "Castellano" = puntuación de [SpanishAudio] de 2 o más; latino, "MULTi" y VOSE no entran en ese grupo.
     * Dentro de cada grupo se mantiene el orden de [sorted] (calidad, HDR, seeds…).
     */
    private fun spanishFirst(sorted: List<StreamSource>): List<StreamSource> {
        // Sin reordenar por puntuación: una 4K "probablemente castellano" va antes que una 720p "seguro castellano".
        val spanish = sorted.filter { it.spanishAudioScore >= SpanishAudio.CASTELLANO_MIN }
        val (spanishCached, spanishUncached) = spanish.partition { it.isCached }
        val others = sorted.filter { it.spanishAudioScore < SpanishAudio.CASTELLANO_MIN }
        val (othersCached, othersUncached) = others.partition { it.isCached }
        return spanishCached +
            spanishUncached.take(SPANISH_UNCACHED_FIRST) +
            othersCached +
            spanishUncached.drop(SPANISH_UNCACHED_FIRST) +
            othersUncached
    }

    private companion object {
        /** Cuántas en castellano sin caché se prueban antes que una en otro idioma que sí está en caché. */
        const val SPANISH_UNCACHED_FIRST = 3

        val AV1_4K = Regex("(?<![a-z0-9])(2160p?|4k|uhd)(?![a-z0-9])", RegexOption.IGNORE_CASE)
    }

    private fun effectiveHdrRank(sourceHdr: HdrType, supported: Set<HdrType>): Int {
        if (supported.isEmpty()) return sourceHdr.rank
        return supported
            .filter { it.rank <= sourceHdr.rank }
            .maxOfOrNull { it.rank }
            ?: 0
    }
}
