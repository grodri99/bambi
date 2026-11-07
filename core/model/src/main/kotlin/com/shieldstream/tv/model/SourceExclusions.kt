package com.shieldstream.tv.model

/** App-wide source attributes that should be hidden and excluded from automatic playback. */
data class SourceExclusions(
    val qualities: Set<Quality> = emptySet(),
    val hdrTypes: Set<HdrType> = emptySet(),
    val audioCodecs: Set<AudioCodec> = emptySet(),
) {
    val isEmpty: Boolean get() = qualities.isEmpty() && hdrTypes.isEmpty() && audioCodecs.isEmpty()

    fun excludes(source: StreamSource): Boolean =
        source.quality in qualities || source.hdrType in hdrTypes || source.audioCodecs.any { it in audioCodecs }

    fun filter(sources: List<StreamSource>): List<StreamSource> = sources.filterNot(::excludes)
}
