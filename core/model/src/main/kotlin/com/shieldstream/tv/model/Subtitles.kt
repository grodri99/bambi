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
    val all: List<String> = listOf("es", "en", "ca", "fr", "it", "pt", "de")
}
