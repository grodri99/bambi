package com.shieldstream.tv.model

/** Qué manda al elegir fuente al pulsar Reproducir (y al saltar a otra si falla). */
enum class PlaybackPriority {
    /** La mejor imagen; el castellano solo desempata. */
    QUALITY_FIRST,

    /** Primero castellano (de mejor a peor calidad) y después el resto por calidad. */
    SPANISH_FIRST,

    /** Solo fuentes en castellano. */
    SPANISH_ONLY,
}

/**
 * Calidad preferida: la mejor que no pase de este tope va primero. Lo que lo pasa no se descarta, va detrás
 * (si no hay otra cosa, se reproduce igual).
 */
enum class PreferredQuality(val maxResolutionRank: Int, val maxHdrRank: Int) {
    DV_4K(Quality.UHD_2160.rank, HdrType.DOLBY_VISION.rank),
    HDR_4K(Quality.UHD_2160.rank, HdrType.HDR10_PLUS.rank),
    UHD_SDR(Quality.UHD_2160.rank, HdrType.SDR.rank),
    FHD(Quality.FHD_1080.rank, HdrType.DOLBY_VISION.rank),
    HD(Quality.HD_720.rank, HdrType.DOLBY_VISION.rank),
    ;

    fun allows(source: StreamSource): Boolean =
        source.quality.rank <= maxResolutionRank && source.hdrType.rank <= maxHdrRank
}

/** Calidad de una fuente: la que traiga y, si no se sabe, la que diga el nombre del fichero o de la release. */
fun StreamSource.withInferredQuality(): StreamSource {
    if (quality != Quality.UNKNOWN) return this
    val inferred = sequenceOf(fileName, title).map { Quality.fromName(it) }.firstOrNull { it != Quality.UNKNOWN }
    return if (inferred != null) copy(quality = inferred) else this
}

/**
 * Parecido entre fuentes, para saltar a la más parecida cuando falla una elegida a mano: mismo idioma (castellano),
 * misma resolución (o la más cercana, mejor hacia abajo que de golpe), mismo HDR, mismo audio, mismo códec y
 * tamaño parecido (mismo tipo de release).
 */
object SourceSimilarity {
    fun orderBySimilarity(picked: StreamSource, candidates: List<StreamSource>): List<StreamSource> {
        val pickedSpanish = picked.advertisesSpanishAudio
        return candidates.sortedWith(
            compareBy<StreamSource> { if (pickedSpanish && !it.advertisesSpanishAudio) 1 else 0 }
                .thenBy { resolutionDistance(picked.quality.rank, it.quality.rank) }
                .thenBy { kotlin.math.abs(picked.hdrType.rank - it.hdrType.rank) }
                .thenBy { if (picked.audioCodecs.isEmpty() || it.audioCodecs.any { c -> c in picked.audioCodecs }) 0 else 1 }
                .thenBy { if (picked.videoCodec == null || it.videoCodec == picked.videoCodec) 0 else 1 }
                .thenBy { sizeDistance(picked.sizeBytes, it.sizeBytes) },
        )
    }

    /** Igual = 0; una menos = 2; una más = 1 (subir no es peor, pero lo idéntico va antes); dos menos = 4… */
    private fun resolutionDistance(picked: Int, other: Int): Int {
        val diff = picked - other
        return if (diff >= 0) diff * 2 else (-diff) * 2 - 1
    }

    private fun sizeDistance(a: Long, b: Long): Double =
        if (a <= 0 || b <= 0) 10.0 else kotlin.math.abs(kotlin.math.ln(a.toDouble() / b.toDouble()))
}
