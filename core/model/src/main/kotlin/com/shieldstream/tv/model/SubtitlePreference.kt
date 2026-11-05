package com.shieldstream.tv.model

enum class SubtitlePreference(val code: String?) {
    OFF(null),
    SPANISH("es"),
    ENGLISH("en");

    fun matches(language: String?): Boolean {
        val normalized = language?.trim()?.lowercase().orEmpty()
        return when (this) {
            OFF -> false
            SPANISH -> normalized == "es" || normalized.startsWith("es-") ||
                normalized == "spa" || normalized.startsWith("spa-")
            ENGLISH -> normalized == "en" || normalized.startsWith("en-") ||
                normalized == "eng" || normalized.startsWith("eng-")
        }
    }
}
