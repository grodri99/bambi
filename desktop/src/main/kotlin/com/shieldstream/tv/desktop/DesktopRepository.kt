package com.shieldstream.tv.desktop

import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.Quality

data class HomeRow(val title: String, val items: List<CatalogItem>)

data class SeasonInfo(val number: Int, val name: String, val episodeCount: Int, val airDate: String? = null)

data class TitleDetails(
    val item: CatalogItem,
    val logoUrl: String? = null,
    val tagline: String? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val seasons: List<SeasonInfo> = emptyList(),
    /** Edad recomendada (España; si no, EE. UU.): «13», «PG-13»… */
    val ageRating: String? = null,
    val cast: List<CastMember> = emptyList(),
    /** Vídeo de YouTube del tráiler (en español si lo hay). */
    val trailerKey: String? = null,
    /** La saga a la que pertenece (TMDB «colección»), en orden de estreno. */
    val saga: HomeRow? = null,
    /** Fecha de estreno completa (película) o del primer episodio (serie), «2024-03-14». */
    val releaseDate: String? = null,
    /** Nota de IMDb («8.1»), de Cinemeta (sin clave). */
    val imdbRating: String? = null,
)

data class CastMember(val name: String, val role: String?, val photoUrl: String?, val id: Int? = null)

/** Ficha de una persona del reparto: sus datos y en qué ha salido (o qué ha dirigido), lo más conocido primero. */
data class PersonDetails(
    val id: Int,
    val name: String,
    val photoUrl: String?,
    val biography: String,
    val knownFor: String?,
    val birthday: String?,
    val placeOfBirth: String?,
    val credits: List<CatalogItem>,
)

/** Una fuente lista para reproducir: [url] es el enlace que resuelve el servicio debrid. */
data class DesktopSource(
    val title: String,
    val detail: String,
    val quality: Quality,
    val cached: Boolean,
    val url: String,
    /** Sección de la lista, como en la tele: «En castellano», «Torrentio», «The Pirate Bay», «Comet»… */
    val section: String = "",
    /** Audio en castellano (según el detector de la tele): para «Solo en español». */
    val spanish: Boolean = false,
    /** Tamaño que anuncia la fuente (para reconocer los vídeos de aviso de los addons). */
    val sizeBytes: Long = 0,
)

/** Lo que llevan encontrado los buscadores: van llegando poco a poco, como en la tele. */
data class SourcesState(
    val sources: List<DesktopSource>,
    val pending: Set<String> = emptySet(),
    val unavailable: Map<String, String> = emptyMap(),
)

/** Lo que necesitan las pantallas de escritorio. La real usa TMDB y Torrentio; las capturas, datos de prueba. */
/** Qué filas enseña el Inicio: todo, solo series o solo películas (las pestañas de arriba, como en la tele). */
enum class Catalog { ALL, SHOWS, MOVIES }

interface DesktopRepository {
    /** Filas del Inicio; con [kids], solo títulos para niños (como el perfil infantil de la tele). */
    suspend fun homeRows(catalog: Catalog = Catalog.ALL, kids: Boolean = false): List<HomeRow>
    suspend fun search(query: String, kids: Boolean = false): List<CatalogItem>
    suspend fun details(item: CatalogItem): TitleDetails
    suspend fun episodes(item: CatalogItem, season: Int): List<Episode>
    fun sources(item: CatalogItem, episode: Episode?): kotlinx.coroutines.flow.Flow<SourcesState>

    /** Prueba las claves de Ajustes contra TMDB y Torrentio, como «Comprobar» en la tele. */
    /** Para el perfil infantil: lo que no es para niños, que se puede «Pedir» a un adulto. */
    suspend fun searchOutsideKids(query: String): List<CatalogItem> = emptyList()

    /** Intro, resumen y créditos del episodio (IntroDB), para «Omitir» y «Siguiente episodio». */
    suspend fun skipSegments(item: CatalogItem, episode: Episode?): com.shieldstream.tv.domain.SkipSegments? = null

    /** Nombre, logo y si hay que configurarlo, leído del manifest del addon. */
    suspend fun addonInfo(manifestUrl: String): AddonInfo? = null

    /** Cuántas fuentes da el addon para una película de prueba. */
    suspend fun testAddon(manifestUrl: String): Int = 0

    /** Capítulos recientes y próximos de una serie (temporada actual y la que viene), con su fecha. */
    suspend fun airingEpisodes(show: CatalogItem): List<ReleaseEpisode> = emptyList()

    /** Top 10 de hoy (TMDB, tendencias del día). */
    suspend fun top10(catalog: Catalog = Catalog.ALL, kids: Boolean = false): List<CatalogItem> = emptyList()

    /** Filas de una categoría del Inicio (las mismas categorías que la tele). */
    suspend fun categoryRows(category: com.shieldstream.tv.model.Category, catalog: Catalog): List<HomeRow> = emptyList()

    /** Un título al azar entre los populares bien valorados («Sorpréndeme»). */
    suspend fun surprise(kids: Boolean = false): CatalogItem? = null

    /** Una persona del reparto y su filmografía (películas y series). */
    suspend fun person(id: Int): PersonDetails? = null

    suspend fun checkKeys(config: DesktopConfig): KeysCheck =
        KeysCheck(KeyStatus(true, "TMDB: clave correcta"), KeyStatus(true, "${config.debridService.title}: clave correcta"))
}

/** Resultado de probar una clave: si vale y qué decirle al usuario. */
data class KeyStatus(val ok: Boolean, val message: String)

data class KeysCheck(val tmdb: KeyStatus, val debrid: KeyStatus)
