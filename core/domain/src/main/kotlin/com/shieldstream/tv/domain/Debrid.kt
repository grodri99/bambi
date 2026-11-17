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

    /**
     * Resolución propia de magnets en la app (sin Torrentio). Con el resto, los torrents de los buscadores propios se
     * resuelven a través de Torrentio con la clave del servicio (ver [TorrentioLinkResolver.resolveUrlFor]).
     */
    val supportsDirectMagnetResolution: Boolean get() = this == REAL_DEBRID || this == ALLDEBRID || this == TORBOX

    /** Nombre del servicio en la configuración de Comet (el mismo que usa Torrentio). */
    val cometKey: String get() = torrentioKey

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

    /**
     * Enlace de Torrentio que resuelve este torrent con la clave de [service], para los servicios sin resolución propia
     * en la app. Sin archivo concreto, Torrentio da el vídeo más grande. Null si no hay clave.
     */
    fun resolveUrlFor(service: DebridService, infoHash: String, fileIdx: Int?, fileName: String?): String? = null
}

enum class SourceProvider(val baseUrl: String?, val torrentioStyle: Boolean) {
    /**
     * Buscador propio de Bambi para castellano: junta las webs españolas de Torrentio (MejorTorrent, Wolfmax4k,
     * Cinecalidad) y búsquedas con el título en español en Knaben y The Pirate Bay, y deja solo lo que es de este
     * título y dice traer castellano. Va el primero para que su sección salga arriba.
     */
    CASTELLANO(null, false),
    TORRENTIO("https://torrentio.strem.fun", true),
    PEERFLIX("https://addon.peerflix.mov", true),
    KNABEN("https://api.knaben.org", false),
    THEPIRATEBAY("https://apibay.org", false),
    NYAA("https://nyaa.si", false),
    COMET("https://comet.elfhosted.com", false),
    AIOSTREAMS(null, false),
    ;
}

fun enabledSourceProviders(savedProvidersOff: Set<SourceProvider>?): Set<SourceProvider> =
    SourceProvider.entries.toSet() -
        (if (savedProvidersOff == null) setOf(SourceProvider.COMET) else emptySet()) -
        savedProvidersOff.orEmpty()
