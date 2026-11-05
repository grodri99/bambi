package com.shieldstream.tv.model

data class PlaybackMedia(
    val type: MediaType,
    val imdbId: String?,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val season: Int? = null,
    val episode: Int? = null,
)

data class SubtitleQuery(
    val media: PlaybackMedia,
    val language: String,
    val fileName: String?,
)

data class ExternalSubtitle(
    val provider: String,
    val id: String,
    val release: String,
    /** ISO 639-1 ("es", "en"…). */
    val language: String,
    val hearingImpaired: Boolean,
    val downloadUrl: String,
    val matchScore: Float = 0f,
)

data class SubtitleFile(
    val path: String,
    val mimeType: String,
    val language: String,
    val label: String,
)

object SubtitleLanguages {
    val all: List<Pair<String, String>> = listOf(
        "es" to tr("Español", "Spanish"),
        "en" to tr("Inglés", "English"),
        "ca" to tr("Catalán", "Catalan"),
        "fr" to tr("Francés", "French"),
        "it" to tr("Italiano", "Italian"),
        "pt" to tr("Portugués", "Portuguese"),
        "de" to tr("Alemán", "German"),
    )

    fun label(code: String): String = all.firstOrNull { it.first == code }?.second ?: code.uppercase()
}
