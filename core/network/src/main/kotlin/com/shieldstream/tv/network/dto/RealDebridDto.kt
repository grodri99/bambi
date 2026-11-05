package com.shieldstream.tv.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class RdUserDto(
    val id: Long = 0,
    val username: String = "",
    val premium: Long = 0,
    val type: String = "",
)

@Serializable
data class RdAddMagnetDto(
    val id: String = "",
    val uri: String = "",
)

@Serializable
data class RdTorrentInfoDto(
    val id: String = "",
    val hash: String = "",
    val status: String = "",
    val progress: Double = 0.0,
    val files: List<RdTorrentFileDto> = emptyList(),
    val links: List<String> = emptyList(),
)

@Serializable
data class RdTorrentFileDto(
    val id: Int = 0,
    val path: String = "",
    val bytes: Long = 0,
    val selected: Int = 0,
)

@Serializable
data class RdTorrentDto(
    val id: String = "",
    val hash: String = "",
    val filename: String = "",
    val status: String = "",
)

@Serializable
data class RdUnrestrictDto(
    val download: String = "",
    val filename: String = "",
    val filesize: Long = 0,
)
