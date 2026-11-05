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

    val advertisesSpanishAudio: Boolean get() = spanishAudioScore >= 2
}

object SpanishAudio {
    private val castellano = Regex("""(?i)(\bcastellano\b|espa[ñn]ol|\besp\b|\bspa\b|\bcast\b)""")
    private val spanishWord = Regex("""(?i)(\bspanish\b|\bdual\b)""")
    private val latino = Regex("""(?i)(\blatino\b|\blat\b|\blatam\b)""")
    private val subsOnly = Regex("""(?i)(\bvose\b|\bvos\b|\bsubs?\.?\s*(esp|spa|spanish)\b)""")

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
