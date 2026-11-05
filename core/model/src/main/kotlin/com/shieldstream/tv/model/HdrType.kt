package com.shieldstream.tv.model

enum class HdrType(val rank: Int, val label: String) {
    SDR(0, "SDR"),
    HLG(1, "HLG"),
    HDR10(2, "HDR10"),
    HDR10_PLUS(3, "HDR10+"),
    DOLBY_VISION(4, "Dolby Vision");

    companion object {
        private val DV_REGEX = Regex("\\bdv\\b")

        fun fromName(name: String?): HdrType {
            val n = name?.lowercase() ?: return SDR
            return when {
                n.contains("dolby") || n.contains("profile 5") ||
                    n.contains("profile 7") || n.contains("profile 8") ||
                    DV_REGEX.containsMatchIn(n) -> DOLBY_VISION
                n.contains("hdr10+") || n.contains("hdr10plus") -> HDR10_PLUS
                n.contains("hdr10") || n.contains("hdr") -> HDR10
                n.contains("hlg") -> HLG
                else -> SDR
            }
        }
    }
}
