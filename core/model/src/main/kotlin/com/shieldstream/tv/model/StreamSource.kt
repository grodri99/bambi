package com.shieldstream.tv.model

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
    val fileName: String? = null,
    val languages: List<String> = emptyList(),
    val videoCodec: VideoCodec? = null,
    val audioCodecs: Set<AudioCodec> = emptySet(),
    val softwareDecode: Boolean = false,
    val resolveUrl: String? = null,
    val debrid: String? = null,
    val provider: String = "",
    val season: Int? = null,
    val episode: Int? = null,
) {
    val displayLabel: String
        get() = buildString {
            append(quality.label)
            if (hdrType != HdrType.SDR) append(" · ").append(hdrType.label)
        }

    val isHdr: Boolean get() = hdrType != HdrType.SDR

    val spanishAudioScore: Int get() = SpanishAudio.score(this)

    val advertisesSpanishAudio: Boolean get() = spanishAudioScore >= SpanishAudio.CASTELLANO_MIN
}

/**
 * Detecta, por el nombre de la release (y las banderas que ponen los addons), si una fuente trae audio en español.
 *
 * Puntuación:
 *  - 3: lo dice claro: "Castellano", "Español", "ESP", "SPA", "[Cast]", "CAST", "Spanish Spain", "es-ES"…
 *  - 2: "Spanish" a secas, pares de idiomas ("ES-EN"), "[ES]" o la bandera 🇪🇸 sola o con un idioma más.
 *  - 1: español latino, o posible: "MULTi" (varios audios) o 🇪🇸 entre muchos idiomas (suele ser la lista de
 *       subtítulos de un remux). El reproductor lo comprueba con las pistas de audio reales.
 *  - 0: nada, o solo subtítulos ("VOSE", "Sub Español", "Spanish Subs", "Subtitulado").
 *
 * Desde [CASTELLANO_MIN] cuenta como castellano ("Castellano primero", "Solo en español").
 */
object SpanishAudio {
    const val CASTELLANO_MIN = 2

    // Límites por letras (no \b): "Spanish_English", "_castellano_" o "E05esp" también valen.
    private const val L = """(?<!\p{L})"""
    private const val R = """(?!\p{L})"""

    /** Castellano sin duda. */
    private val castellano = Regex(
        """(?i)$L(castellano|castilian|espa[ñn]ol[ ._-]+de[ ._-]+espa[ñn]a|spanish[ ._-]*(spain|castilian|esp)|esp?[-_]es)$R|[\[(]\s*cast\s*[\])]""",
    )
    /** "CAST" en mayúsculas ("Peli.2023.1080p.CAST.mkv"); en minúsculas sería "Cast Away" o el reparto. */
    private val castUpper = Regex("""(?<![A-Za-z])CAST(?![A-Za-z])(?![ ._-]*AWAY)""")
    /** "Español", "ESP": de webs españolas es castellano (si no dice también latino). */
    private val spanishLocal = Regex("""(?i)$L(espa[ñn]ol|esp)$R""")
    /** "SPA" / "spa" como código; "Spa" con mayúscula inicial es una palabra de un título ("Spa Night"). */
    private val spaCode = Regex("""$L(SPA|spa)$R""")
    /** "Spanish" a secas, "ES-EN", "[ES]": español, sin saber si castellano o latino. */
    private val spanishWord = Regex(
        """(?i)${L}spanish$R|(?<![\p{L}\p{N}])es[+/&-](en|eng|ing)$R|[\[(]\s*es\s*[\])]""",
    )
    private val latino = Regex("""(?i)$L(latino|latam|latinoam[eé]rica\w*|es[-_]?(la|mx|419)|spanish[ ._-]*(la|latin\w*))$R""")
    private val latUpper = Regex("""$L(LAT)$R""")
    /** Portugués de Brasil ("Dublado", "Legendado"): no es español aunque diga "Dual". */
    private val portuguese = Regex("""(?i)$L(dublado|legendado|portugu[eê]s\w*)$R""")
    /** Varios audios ("MULTi"): a menudo incluye español. No vale "Multi-Subs" ni el MULTi francés (VFF, TRUEFRENCH). */
    private val multiAudio = Regex("""(?i)${L}multi$R(?![ ._-]*sub)""")
    private val french = Regex("""(?i)$L(vff|vfq|vf2|vfi|vof|truefrench|french|vostfr)$R""")

    /**
     * Subtítulos en español, que NO son audio: se quitan del texto antes de puntuar, así
     * "Castellano-Inglés Subt Castellano" sigue contando y "Inglés Sub Español" no.
     */
    private val subtitlePhrases = listOf(
        Regex("""(?i)$L(vose|vos|subesp)$R"""),
        Regex("""(?i)$L(subs?|subt|subt[ií]tulos?|subtitles?|subtitulad[oa]s?)[\s._:-]*(en[\s._-]+)?(espa[ñn]ol|esp|spanish|spa|castellano|es)$R"""),
        Regex("""(?i)$L(espa[ñn]ol|esp|spanish|spa)[\s._-]*(subs?|subt|subt[ií]tulos?|subtitles?|subbed)$R"""),
        Regex("""(?i)${L}subtitulad[oa]s?$R"""),
    )

    fun score(source: StreamSource): Int =
        score(source.title + " " + source.fileName.orEmpty(), source.languages)

    fun score(text: String, languages: List<String> = emptyList()): Int {
        var subtitled = false
        var audio = text
        for (phrase in subtitlePhrases) {
            if (phrase.containsMatchIn(audio)) {
                subtitled = true
                audio = phrase.replace(audio, " ")
            }
        }
        val flagEs = "ES" in languages
        val isLatino = latino.containsMatchIn(audio) || latUpper.containsMatchIn(audio)
        return when {
            castellano.containsMatchIn(audio) || castUpper.containsMatchIn(audio) -> 3
            isLatino -> 1
            spanishLocal.containsMatchIn(audio) || spaCode.containsMatchIn(audio) -> 3
            portuguese.containsMatchIn(audio) -> 0
            spanishWord.containsMatchIn(audio) -> 2
            // Solo hablaba de subtítulos en español: la bandera 🇪🇸 se refiere a ellos.
            subtitled -> 0
            flagEs && languages.size <= 2 -> 2
            flagEs -> 1
            "LAT" in languages -> 1
            (multiAudio.containsMatchIn(audio) || "MULTI" in languages) && !french.containsMatchIn(audio) -> 1
            else -> 0
        }
    }
}
