package com.shieldstream.tv.model

enum class AudioCodec(val label: String, private vararg val patterns: Regex) {
    DOLBY_ATMOS("Dolby Atmos", Regex("(?i)(?<![a-z0-9])atmos(?![a-z])")),
    DOLBY_TRUEHD("Dolby TrueHD", Regex("(?i)(?<![a-z0-9])(?:dolby[ ._-]+)?true[ ._-]?hd(?![a-z])")),
    DOLBY_DIGITAL_PLUS(
        "Dolby Digital+",
        Regex("(?i)(?<![a-z0-9])(?:e[ ._-]?ac[ ._-]?3|dd[ ._-]?p|dd\\+|dolby[ ._-]+digital[ ._-]+(?:plus|\\+))(?![a-z])"),
    ),
    DOLBY_DIGITAL(
        "Dolby Digital",
        Regex("(?i)(?<![a-z0-9])(?:(?<!e[ ._-])ac[ ._-]?3|dd(?!\\+|[ ._-]?p)|dolby[ ._-]+digital(?![ ._-]*(?:plus|\\+)))(?![a-z])"),
    ),
    DTS_X("DTS:X", Regex("(?i)(?<![a-z0-9])dts[ .:_-]*x(?![a-z])")),
    DTS_HD("DTS-HD", Regex("(?i)(?<![a-z0-9])dts[ .:_-]*hd(?:[ .:_-]*ma)?(?![a-z])")),
    DTS_EXPRESS("DTS Express", Regex("(?i)(?<![a-z0-9])dts[ .:_-]*express(?![a-z])")),
    DTS("DTS", Regex("(?i)(?<![a-z0-9])dts")),
    AAC("AAC", Regex("(?i)(?<![a-z0-9])aac(?![a-z])")),
    OPUS("Opus", Regex("(?i)(?<![a-z0-9])opus(?![a-z])")),
    FLAC("FLAC", Regex("(?i)(?<![a-z0-9])flac(?![a-z])")),
    MP3("MP3", Regex("(?i)(?<![a-z0-9])mp[ ._-]?3(?![a-z])")),
    PCM("PCM", Regex("(?i)(?<![a-z0-9])l?pcm(?![a-z])"));

    companion object {
        fun parse(text: String): Set<AudioCodec> = entries.filterTo(mutableSetOf()) { codec ->
            codec.patterns.any { it.containsMatchIn(text) }
        }
    }
}
