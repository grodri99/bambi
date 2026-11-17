package com.shieldstream.tv.model

enum class Quality(val rank: Int, val label: String) {
    UNKNOWN(0, "?"),
    SD(1, "SD"),
    HD_720(2, "720p"),
    FHD_1080(3, "1080p"),
    QHD_1440(4, "1440p"),
    UHD_2160(5, "4K");

    companion object {
        private val SD_REGEX = Regex("(?<![a-z0-9])(sd|480p?|576p?|dvdrip|dvd5|dvd9|xvid)(?![a-z0-9])")

        fun fromName(name: String?): Quality {
            // "Wolfmax4k" es el nombre de una web, no la calidad: sus fuentes de 1080p salían como 4K.
            val n = name?.lowercase()?.replace("wolfmax4k", "") ?: return UNKNOWN
            return when {
                n.contains("2160") || n.contains("4k") || n.contains("uhd") -> UHD_2160
                n.contains("1440") -> QHD_1440
                n.contains("1080") -> FHD_1080
                n.contains("720") -> HD_720
                // "sd" como palabra suelta (no dentro de "Wednesday" o "HDSD"); 480p/576p y rips de DVD también.
                SD_REGEX.containsMatchIn(n) -> SD
                else -> UNKNOWN
            }
        }
    }
}
