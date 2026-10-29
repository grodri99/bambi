package com.shieldstream.tv.domain

import com.shieldstream.tv.model.tr

/**
 * Servicios debrid que Bambi sabe usar.
 *
 *  - Real-Debrid: API propia de RD, directamente desde la Shield (la clave no sale de casa). Es el camino probado.
 *  - El resto: a través de la integración debrid de Torrentio (la misma que usa Stremio). Torrentio recibe la
 *    clave, marca qué fuentes están ya en la caché del servicio ("[TB+]", "[PM+]"…) y convierte cada fuente en un
 *    enlace /resolve/ que redirige al enlace directo del debrid. Así no hace falta una integración propia por
 *    servicio (que no se podría probar sin cuenta).
 *
 * [torrentioKey] y [shortName] son los que usa Torrentio (addon/moch/moch.js).
 */
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

    /** true = se usa a través de Torrentio (todos menos Real-Debrid). */
    val viaTorrentio: Boolean get() = this != REAL_DEBRID

    companion object {
        fun of(service: ApiService): DebridService? = entries.firstOrNull { it.apiService == service }
    }
}

/**
 * Real-Debrid rechaza el torrent al añadirlo (451, error_code 35: bloqueado por derechos de autor). Es instantáneo
 * y no deja nada en la cuenta, así que el reproductor no lo cuenta como intento (ver PlayerViewModel).
 */
class TorrentBlockedException(message: String) : IllegalStateException(message)

/** Resultado de pedir a Torrentio el enlace directo de una fuente. */
sealed interface DebridLink {
    data class Ready(val url: String) : DebridLink

    /** [notCached] = el problema es de esta fuente (no estaba en caché, RAR…): conviene probar otra. */
    data class Problem(val message: String, val notCached: Boolean) : DebridLink
}

/** Convierte un enlace /resolve/ de Torrentio en el enlace directo del servicio debrid. */
interface TorrentioLinkResolver {
    suspend fun resolve(url: String, service: String): DebridLink
}

/**
 * Buscadores de fuentes (addons de Stremio). Se consultan a la vez los activados en Ajustes y en "Fuentes" sale
 * una sección por buscador. [torrentioStyle]: misma API que Torrentio (sirve con cualquier debrid); los demás solo
 * dan torrents (infoHash) y solo valen con Real-Debrid, que los resuelve desde la Shield.
 */
enum class SourceProvider(val title: String, val baseUrl: String?, val torrentioStyle: Boolean) {
    TORRENTIO("Torrentio", "https://torrentio.strem.fun", true),
    /**
     * Addon de Stremio en español: DonTorrent, MejorTorrent, Wolfmax4k… (castellano de verdad) y algo en inglés.
     * Ahora responde en addon.peerflix.mov (26-oct-2026; el 24-oct no). Acepta configuración en la ruta como
     * Torrentio (`language=es-ES`, `<servicio>=CLAVE`), así que vale con Real-Debrid y con AllDebrid.
     */
    PEERFLIX("Peerflix", "https://addon.peerflix.mov", true),
    // KnightCrawler (ElfHosted) y ThePirateBay+ se probaron en la Shield (24-oct-2026) y no responden como addon
    // (HTML, dominio inexistente): fuera. Cualquier otro va por el addon propio.
    /** Buscadores propios de Bambi (como los scrapers de Kodi): consultan la web directamente. Solo Real-Debrid. */
    /** Metabuscador (1337x, TPB, Nyaa, TorrentGalaxy…): el equivalente a los scrapers de CocoScrapers en uno. */
    KNABEN("Knaben", "https://api.knaben.org", false),
    THEPIRATEBAY("The Pirate Bay", "https://apibay.org", false),
    // Mismas webs y búsquedas que CocoScrapers (Umbrella, Kodi) v1.0.32: los enlaces magnet de la página de resultados.
    // Bitsearch: el operador lo bloquea por DNS (127.0.0.1) y por la otra IP de Cloudflare da 403: fuera (25-oct-2026).
    // TorrentGalaxy (ya no pone magnets en la lista) y EZTV (403, Cloudflare) se quitaron el 25-oct-2026.
    /** Solo anime (con otros títulos mezclaba, p. ej., "Lupin III" en "Lupin"). */
    NYAA("Nyaa", "https://nyaa.si", false),
    /** Como en CocoScrapers: Comet con tu token de Real-Debrid (sale hacia comet.elfhosted.com). */
    COMET("Comet", "https://comet.elfhosted.com", false),
    /** Addon propio del usuario (AIOStreams, Comet, MediaFusion…): su URL en Ajustes → Otros servicios. */
    AIOSTREAMS(tr("Addon de Stremio", "Stremio addon"), null, false),
    ;

    /** Sección en Fuentes y en Ajustes: los buscadores propios van todos juntos bajo "The Pirate Bay". */
    val section: String get() = when (this) {
        TORRENTIO, PEERFLIX, AIOSTREAMS -> title
        else -> "The Pirate Bay"
    }
}
