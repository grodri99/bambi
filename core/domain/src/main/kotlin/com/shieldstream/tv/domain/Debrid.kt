package com.shieldstream.tv.domain


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

const val ITHIL_ALLDEBRID = "AllDebrid / Ithil"

enum class SourceProvider(val baseUrl: String?, val torrentioStyle: Boolean) {
    TORRENTIO("https://torrentio.strem.fun", true),
    PEERFLIX("https://addon.peerflix.mov", true),
    KNABEN("https://api.knaben.org", false),
    THEPIRATEBAY("https://apibay.org", false),
    NYAA("https://nyaa.si", false),
    COMET("https://comet.elfhosted.com", false),
    AIOSTREAMS(null, false),
    ITHIL(null, false),
    ;
}

fun enabledSourceProviders(savedProvidersOff: Set<SourceProvider>?): Set<SourceProvider> =
    SourceProvider.entries.toSet() -
        (if (savedProvidersOff == null) setOf(SourceProvider.COMET, SourceProvider.ITHIL) else emptySet()) -
        savedProvidersOff.orEmpty()
