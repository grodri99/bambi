package com.shieldstream.tv.domain

import com.shieldstream.tv.model.tr

enum class DebridService(
    val title: String,
    val torrentioKey: String,
    val shortName: String,
    val apiService: ApiService,
) {
    REAL_DEBRID("Real-Debrid", "realdebrid", "RD", ApiService.REAL_DEBRID),
    TORBOX("TorBox", "torbox", "TB", ApiService.TORBOX),
    PREMIUMIZE("Premiumize", "premiumize", "PM", ApiService.PREMIUMIZE),
    ALLDEBRID("AllDebrid", "alldebrid", "AD", ApiService.ALLDEBRID),
    DEBRID_LINK("Debrid-Link", "debridlink", "DL", ApiService.DEBRID_LINK),
    OFFCLOUD("Offcloud", "offcloud", "OC", ApiService.OFFCLOUD),
    ;

    val viaTorrentio: Boolean get() = this != REAL_DEBRID

    companion object {
        fun of(service: ApiService): DebridService? = entries.firstOrNull { it.apiService == service }
    }
}

class TorrentBlockedException(message: String) : IllegalStateException(message)

sealed interface DebridLink {
    data class Ready(val url: String) : DebridLink

    data class Problem(val message: String, val notCached: Boolean) : DebridLink
}

interface TorrentioLinkResolver {
    suspend fun resolve(url: String, service: String): DebridLink
}

enum class SourceProvider(val title: String, val baseUrl: String?, val torrentioStyle: Boolean) {
    TORRENTIO("Torrentio", "https://torrentio.strem.fun", true),
    PEERFLIX("Peerflix", "https://addon.peerflix.mov", true),
    KNABEN("Knaben", "https://api.knaben.org", false),
    THEPIRATEBAY("The Pirate Bay", "https://apibay.org", false),
    NYAA("Nyaa", "https://nyaa.si", false),
    COMET("Comet", "https://comet.elfhosted.com", false),
    AIOSTREAMS(tr("Addon de Stremio", "Stremio addon"), null, false),
    ;

    val section: String get() = when (this) {
        TORRENTIO, PEERFLIX, COMET, AIOSTREAMS -> title
        else -> "The Pirate Bay"
    }
}

fun enabledSourceProviders(savedProvidersOff: Set<SourceProvider>?): Set<SourceProvider> =
    SourceProvider.entries.toSet() -
        (if (savedProvidersOff == null) setOf(SourceProvider.COMET) else emptySet()) -
        savedProvidersOff.orEmpty()
