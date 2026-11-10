package com.shieldstream.tv.domain

import kotlinx.coroutines.flow.StateFlow

enum class ApiService(
    val url: String,
    val required: Boolean,
    val isDebrid: Boolean = false,
) {
    REAL_DEBRID(
        url = "https://real-debrid.com/apitoken",
        required = true,
        isDebrid = true,
    ),
    TMDB(
        url = "https://www.themoviedb.org/settings/api",
        required = true,
    ),
    SUBDL(
        url = "https://subdl.com/panel/api",
        required = false,
    ),
    AIOSTREAMS(
        url = "https://aiostreams.elfhosted.com/stremio/configure",
        required = false,
    ),
    TORBOX(
        url = "https://torbox.app/settings",
        required = false,
        isDebrid = true,
    ),
    PREMIUMIZE(
        url = "https://www.premiumize.me/account",
        required = false,
        isDebrid = true,
    ),
    ALLDEBRID(
        url = "https://alldebrid.com/apikeys/",
        required = false,
        isDebrid = true,
    ),
    DEBRID_LINK(
        url = "https://debrid-link.com/webapp/apikey",
        required = false,
        isDebrid = true,
    ),
    OFFCLOUD(
        url = "https://offcloud.com/#/account",
        required = false,
        isDebrid = true,
    ),
}

sealed interface KeyCheck {
    data class Valid(val detail: String? = null) : KeyCheck
    data class Invalid(val message: String) : KeyCheck
}

interface ApiKeysRepository {
    val keys: StateFlow<Map<ApiService, String?>>

    suspend fun set(service: ApiService, value: String?)

    suspend fun check(service: ApiService, value: String): KeyCheck

    suspend fun addonMetadata(manifestUrl: String): StremioAddonMetadata? = null
}
