package com.shieldstream.tv.model

enum class Quality(val rank: Int, val label: String) {
    UNKNOWN(0, "?"),
    SD(1, "SD"),
    HD_720(2, "720p"),
    FHD_1080(3, "1080p"),
    QHD_1440(4, "1440p"),
    UHD_2160(5, "4K");

    companion object {
        fun fromName(name: String?): Quality {
            val n = name?.lowercase() ?: return UNKNOWN
            return when {
                n.contains("2160") || n.contains("4k") || n.contains("uhd") -> UHD_2160
                n.contains("1440") -> QHD_1440
                n.contains("1080") -> FHD_1080
                n.contains("720") -> HD_720
                n.contains("sd") -> SD
                else -> UNKNOWN
            }
        }
    }
}
