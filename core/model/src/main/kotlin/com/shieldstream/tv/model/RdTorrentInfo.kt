package com.shieldstream.tv.model

data class RdTorrentInfo(
    val id: String,
    val hash: String,
    val status: String,
    val files: List<RdTorrentFile>,
    val links: List<String>,
) {
    fun linkFor(file: RdTorrentFile): String? {
        file.link?.let { return it }
        val index = files.filter { it.selected }.indexOfFirst { it.id == file.id }
        return if (index >= 0) links.getOrNull(index) else null
    }

    companion object {
        const val STATUS_WAITING_SELECTION = "waiting_files_selection"
        const val STATUS_DOWNLOADED = "downloaded"
        val STATUS_FAILED = setOf("magnet_error", "error", "virus", "dead")
    }
}
