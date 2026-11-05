package com.shieldstream.tv.model

/**
 * Fuente de streaming resuelta (torrent/debrid) para un título concreto.
 */
data class StreamSource(
    val id: String,
    val title: String,
    val infoHash: String,
    val fileIdx: Int?,
    val magnet: String,
    val quality: Quality,
    val hdrType: HdrType,
    val sizeBytes: Long,
    val seeders: Int?,
    val isCached: Boolean,
    val directUrl: String? = null,
    /** Nombre del archivo dentro del torrent (behaviorHints.filename). Clave en packs de temporada. */
    val fileName: String? = null,
    /** Idiomas de audio que anuncia Torrentio con banderas (ES, LAT, EN…). Vacío = no indicado. */
    val languages: List<String> = emptyList(),
    /** Codec anunciado por la release, cuando se puede reconocer por su nombre. */
    val videoCodec: VideoCodec? = null,
    /** Valor inicial según la Shield; la priorización lo recalcula con los decodificadores del dispositivo. */
    val softwareDecode: Boolean = false,
    /** Debrid vía Torrentio: enlace /resolve/ que se convierte en el enlace directo al reproducir. */
    val resolveUrl: String? = null,
    /** Nombre del servicio debrid si no es Real-Debrid ("TorBox"…), para los textos. */
    val debrid: String? = null,
    /** Buscador que la encontró (sección en "Fuentes"). */
    val provider: String = "",
    /** Serie: temporada y episodio buscados (para elegir el archivo dentro de un pack de temporada). */
    val season: Int? = null,
    val episode: Int? = null,
) {
    val displayLabel: String
        get() = buildString {
            append(quality.label)
            if (hdrType != HdrType.SDR) append(" · ").append(hdrType.label)
        }

    val isHdr: Boolean get() = hdrType != HdrType.SDR

    /** Audio en español: 2 = castellano, 1 = latino, 0 = no consta. Ver [SpanishAudio]. */
    val spanishAudioScore: Int get() = SpanishAudio.score(this)

    /** Misma regla para el panel y para la cola automática. Las pistas reales se verifican al reproducir. */
    val advertisesSpanishAudio: Boolean get() = spanishAudioScore >= 2
}

/**
 * ¿Trae audio en español? Por la bandera que pone Torrentio ([StreamSource.languages]) o, si no la hay (muy
 * habitual en películas antiguas), por el nombre de la release: SPANISH, CASTELLANO, ESPAÑOL, ESP, SPA, DUAL…
 */
object SpanishAudio {
    /** Lo pone la release (webs españolas): lo más fiable. */
    private val castellano = Regex("""(?i)(\bcastellano\b|espa[ñn]ol|\besp\b|\bspa\b|\bcast\b)""")
    /** "Spanish" / "Dual": suele ser castellano, pero a veces latino o (dual en anime) japonés + inglés. */
    private val spanishWord = Regex("""(?i)(\bspanish\b|\bdual\b)""")
    private val latino = Regex("""(?i)(\blatino\b|\blat\b|\blatam\b)""")
    /** Versión original con subtítulos: NO trae audio en español. */
    private val subsOnly = Regex("""(?i)(\bvose\b|\bvos\b|\bsubs?\.?\s*(esp|spa|spanish)\b)""")

    /**
     * 3 = castellano en el nombre · 2 = "Spanish"/"Dual" o bandera 🇪🇸 con pocos idiomas · 1 = latino, o 🇪🇸 entre
     * muchos idiomas (suele ser la lista de subtítulos de un remux: la bandera sola engaña) · 0 = nada.
     * Aun así puede mentir: el reproductor lo comprueba con las pistas de audio reales (PlayerViewModel).
     */
    fun score(source: StreamSource): Int {
        val text = source.title + " " + source.fileName.orEmpty()
        val flagEs = "ES" in source.languages
        return when {
            castellano.containsMatchIn(text) -> 3
            subsOnly.containsMatchIn(text) && !spanishWord.containsMatchIn(text) -> 0
            spanishWord.containsMatchIn(text) -> 2
            flagEs && source.languages.size <= 3 -> 2
            flagEs -> 1
            "LAT" in source.languages || latino.containsMatchIn(text) -> 1
            else -> 0
        }
    }
}
