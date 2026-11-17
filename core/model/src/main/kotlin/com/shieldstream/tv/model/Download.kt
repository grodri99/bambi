package com.shieldstream.tv.model

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, DONE, FAILED }

/** Tope de calidad al descargar: lo mejor que haya, o menos para que ocupe menos. */
enum class DownloadQuality(val maxRank: Int) {
    BEST(Int.MAX_VALUE),
    FHD(Quality.FHD_1080.rank),
    HD(Quality.HD_720.rank),
}

/** Un título (película o episodio) descargado o descargándose para verlo sin conexión. */
data class Download(
    /** La misma clave que el progreso de visionado ([WatchProgress.movieKey] / [WatchProgress.episodeKey]). */
    val id: String,
    val itemId: String,
    val type: MediaType,
    val title: String,
    val episodeLabel: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val imdbId: String? = null,
    val year: Int? = null,
    val originalTitle: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    /** Enlace directo; vacío hasta que le toca descargarse (se resuelve entonces para que no caduque en la cola). */
    val url: String = "",
    /** La fuente elegida, para resolver (o volver a resolver si caduca) el enlace al descargar. */
    val source: StreamSource? = null,
    val filePath: String,
    val sourceTitle: String,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val error: String? = null,
    val createdAt: Long = 0,
) {
    val progress: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val isDone: Boolean get() = status == DownloadStatus.DONE
    val isActive: Boolean get() = status == DownloadStatus.QUEUED || status == DownloadStatus.RUNNING
}

/** Un sitio donde guardar descargas: la memoria interna o un USB/tarjeta montado. */
data class StorageOption(
    val path: String,
    val label: String,
    val removable: Boolean,
    val freeBytes: Long,
    val totalBytes: Long,
)

object DownloadNames {
    private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "m4v", "ts", "m2ts", "mov", "webm", "wmv", "mpg", "mpeg")

    /** "Título (2020).mkv" o "Serie - S01E02 - Título del episodio.mkv", sin caracteres que rompan el sistema de ficheros. */
    fun fileName(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        episodeTitle: String?,
        extension: String,
    ): String {
        val base = if (season != null && episode != null) {
            buildString {
                append(title).append(" - S%02dE%02d".format(season, episode))
                episodeTitle?.takeIf { it.isNotBlank() }?.let { append(" - ").append(it) }
            }
        } else {
            if (year != null) "$title ($year)" else title
        }
        val clean = base.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), " ").replace(Regex("""\s+"""), " ").trim().take(150)
        return "${clean.ifBlank { "video" }}.$extension"
    }

    /** Extensión del vídeo según el nombre del fichero de la fuente o la URL; mkv si no se sabe. */
    fun extensionFor(fileName: String?, url: String): String {
        val candidates = listOfNotNull(fileName, url.substringBefore('?').substringAfterLast('/'))
        return candidates.firstNotNullOfOrNull { name ->
            name.substringAfterLast('.', "").lowercase().takeIf { it in VIDEO_EXTENSIONS }
        } ?: "mkv"
    }
}
