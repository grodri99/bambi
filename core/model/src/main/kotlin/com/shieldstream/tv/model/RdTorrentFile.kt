package com.shieldstream.tv.model

data class RdTorrentFile(
    val id: Int,
    val name: String,
    val sizeBytes: Long,
    val selected: Boolean,
    val link: String?,
) {
    val isVideo: Boolean
        get() = name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mkv", "mp4", "avi", "mov", "m2ts", "ts", "webm", "wmv", "m4v", "mpg", "mpeg",
        )
    }
}

data class SubtitleTrack(
    val url: String,
    val language: String,
    val name: String,
)
