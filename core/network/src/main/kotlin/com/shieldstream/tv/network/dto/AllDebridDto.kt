package com.shieldstream.tv.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class AllDebridResponse<T>(
    val status: String,
    val data: T? = null,
    val error: AllDebridError? = null,
)

@Serializable
data class AllDebridError(
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class AllDebridUploadData(
    val magnets: List<AllDebridUploadMagnet> = emptyList(),
)

@Serializable
data class AllDebridUploadMagnet(
    val id: Long = 0L,
    val hash: String = "",
    val name: String = "",
    val size: Long = 0L,
    val ready: Boolean = false,
    val error: AllDebridError? = null,
)

@Serializable
data class AllDebridStatusData(
    val magnets: List<AllDebridStatusMagnet> = emptyList(),
)

@Serializable
data class AllDebridStatusMagnet(
    val id: Long = 0L,
    val filename: String = "",
    val size: Long = 0L,
    val status: String = "",
    val statusCode: Int = 0,
    val links: List<AllDebridLinkDto> = emptyList(),
)

@Serializable
data class AllDebridLinkDto(
    val link: String = "",
    val filename: String = "",
    val size: Long = 0L,
)

@Serializable
data class AllDebridUnlockData(
    val link: String = "",
    val filename: String = "",
    val filesize: Long = 0L,
)
