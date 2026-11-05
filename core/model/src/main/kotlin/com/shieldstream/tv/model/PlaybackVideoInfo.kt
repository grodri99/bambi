package com.shieldstream.tv.model

/** Información del vídeo que Media3 está decodificando, no solo la anunciada por la fuente. */
data class PlaybackVideoInfo(
    val resolutionLabel: String?,
    val hdrType: HdrType,
    val codec: VideoCodec?,
    val hardwareDecode: Boolean?,
    val decoderName: String?,
)
