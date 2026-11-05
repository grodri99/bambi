package com.shieldstream.tv.domain

import com.shieldstream.tv.model.tr
import kotlinx.coroutines.flow.StateFlow

enum class ApiService(
    val title: String,
    val keyName: String,
    val whereToGet: String,
    val url: String,
    val usedFor: String,
    val required: Boolean,
    val isDebrid: Boolean = false,
) {
    REAL_DEBRID(
        title = "Real-Debrid",
        keyName = tr("Token de API", "API token"),
        whereToGet = "real-debrid.com/apitoken",
        url = "https://real-debrid.com/apitoken",
        usedFor = tr("Reproduce las fuentes a máxima velocidad", "Plays sources at full speed"),
        required = true,
        isDebrid = true,
    ),
    TMDB(
        title = "TMDB",
        keyName = tr("Clave de API (v3)", "API key (v3)"),
        whereToGet = "themoviedb.org/settings/api",
        url = "https://www.themoviedb.org/settings/api",
        usedFor = tr("Catálogo, portadas, sinopsis, temporadas y edades", "Catalog, artwork, synopses, seasons and age ratings"),
        required = true,
    ),
    SUBDL(
        title = "SubDL",
        keyName = tr("Clave de API", "API key"),
        whereToGet = "subdl.com/panel/api",
        url = "https://subdl.com/panel/api",
        usedFor = tr("Más subtítulos externos", "More external subtitles"),
        required = false,
    ),
    AIOSTREAMS(
        title = tr("Addon de Stremio", "Stremio addon"),
        keyName = tr("URL del manifiesto del addon", "Addon manifest URL"),
        whereToGet = "aiostreams.elfhosted.com/stremio/configure",
        url = "https://aiostreams.elfhosted.com/stremio/configure",
        usedFor = tr(
            "Más fuentes: AIOStreams o cualquier addon de Stremio (pega la URL de su manifiesto; varios, separados por espacios)",
            "More sources: AIOStreams or any Stremio addon (paste its manifest URL; several, separated by spaces)",
        ),
        required = false,
    ),
    TORBOX(
        title = "TorBox",
        keyName = tr("Clave de API", "API key"),
        whereToGet = "torbox.app/settings",
        url = "https://torbox.app/settings",
        usedFor = tr("Reproduce las fuentes (a través de Torrentio)", "Plays sources (through Torrentio)"),
        required = false,
        isDebrid = true,
    ),
    PREMIUMIZE(
        title = "Premiumize",
        keyName = tr("Clave de API", "API key"),
        whereToGet = "premiumize.me/account",
        url = "https://www.premiumize.me/account",
        usedFor = tr("Reproduce las fuentes (a través de Torrentio)", "Plays sources (through Torrentio)"),
        required = false,
        isDebrid = true,
    ),
    ALLDEBRID(
        title = "AllDebrid",
        keyName = tr("Clave de API", "API key"),
        whereToGet = "alldebrid.com/apikeys",
        url = "https://alldebrid.com/apikeys/",
        usedFor = tr("Reproduce las fuentes (a través de Torrentio)", "Plays sources (through Torrentio)"),
        required = false,
        isDebrid = true,
    ),
    DEBRID_LINK(
        title = "Debrid-Link",
        keyName = tr("Clave de API", "API key"),
        whereToGet = "debrid-link.com/webapp/apikey",
        url = "https://debrid-link.com/webapp/apikey",
        usedFor = tr("Reproduce las fuentes (a través de Torrentio)", "Plays sources (through Torrentio)"),
        required = false,
        isDebrid = true,
    ),
    OFFCLOUD(
        title = "Offcloud",
        keyName = tr("Clave de API", "API key"),
        whereToGet = tr("offcloud.com (Cuenta → API key)", "offcloud.com (Account → API key)"),
        url = "https://offcloud.com/#/account",
        usedFor = tr("Reproduce las fuentes (a través de Torrentio)", "Plays sources (through Torrentio)"),
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
}
