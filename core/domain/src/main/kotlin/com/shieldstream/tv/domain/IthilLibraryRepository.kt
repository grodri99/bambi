package com.shieldstream.tv.domain

data class IthilSource(
    val url: String,
    val quality: String,
    val audio: String,
    val info: String,
    val updated: String,
)

data class IthilLibraryInfo(
    val version: String,
    val sourceSha: String,
    val movieLinks: Int,
    val episodeLinks: Int,
)

interface IthilLibraryRepository {
    suspend fun install(
        downloadUrl: String,
        sourceSha: String = "",
        onProgress: (Float) -> Unit = {},
    ): IthilLibraryInfo

    fun isReady(): Boolean

    fun info(): IthilLibraryInfo?

    fun movieSources(tmdb: Int): List<IthilSource>

    fun episodeSources(tmdb: Int, season: Int, episode: Int): List<IthilSource>

    suspend fun delete()
}
