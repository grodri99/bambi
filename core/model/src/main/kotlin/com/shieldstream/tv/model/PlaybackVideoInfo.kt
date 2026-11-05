package com.shieldstream.tv.model

data class PlaybackVideoInfo(
    val resolutionLabel: String?,
    val hdrType: HdrType,
    val codec: VideoCodec?,
    val hardwareDecode: Boolean?,
    val decoderName: String?,
)
