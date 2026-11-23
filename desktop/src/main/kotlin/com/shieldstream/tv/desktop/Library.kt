package com.shieldstream.tv.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.model.ProfilePin
import com.shieldstream.tv.model.ProfileTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Un título guardado (Mi lista, «Seguir viendo»): lo justo para pintar su póster y abrir su ficha. */
@Serializable
data class SavedItem(
    val id: String,
    val imdbId: String? = null,
    val type: String,
    val title: String,
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val rating: Float? = null,
    /** Cuándo entró en Mi lista (para ordenarla igual que Trakt). */
    val addedAt: Long = 0,
) {
    fun toItem() = CatalogItem(id, imdbId, MediaType.valueOf(type), title, overview, posterUrl, backdropUrl, year, rating)

    companion object {
        fun of(item: CatalogItem) = SavedItem(
            item.id, item.imdbId, item.type.name, item.title, item.overview, item.posterUrl, item.backdropUrl, item.year, item.rating,
        )
    }
}

/** Por dónde vas en una película o episodio, en segundos. */
@Serializable
data class SavedProgress(
    val item: SavedItem,
    val season: Int? = null,
    val episode: Int? = null,
    val positionSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val updatedAt: Long = 0,
    val finished: Boolean = false,
) {
    val key: String get() = progressKey(item.type, item.id, season, episode)
    val fraction: Float get() = if (durationSec > 0) (positionSec / durationSec).toFloat().coerceIn(0f, 1f) else 0f

    /** Como en la tele: se reanuda desde los 30 s y hasta el 93 %. */
    val resumable: Boolean get() = !finished && positionSec >= 30 && (durationSec <= 0 || fraction < 0.93f)
}

fun progressKey(type: String, id: String, season: Int?, episode: Int?) =
    if (season != null && episode != null) "$type:$id:$season:$episode" else "$type:$id"

@Serializable
data class DesktopProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatar: Int = 0,
    val image: String? = null,
    /** PIN de 4 cifras, guardado como hash (el mismo cálculo que en la tele: [ProfilePin]). */
    val pinHash: String? = null,
    /** Perfil infantil: Inicio, portada y búsqueda solo con títulos para niños. */
    val kids: Boolean = false,
    /** Acento del perfil (Bambi, Océano, Bosque, Oro, Violeta, Rosa): focos, progreso y reproductor. */
    val theme: String = ProfileTheme.BAMBI.name,
    /** Usuario de Trakt conectado en este perfil (la sesión va cifrada aparte). */
    val traktUser: String? = null,
    /** Categorías del Inicio ocultas en este perfil (como en la tele; por defecto, las mismas). */
    val hiddenCategories: Set<String> = com.shieldstream.tv.model.Category.DEFAULT_HIDDEN,
) {
    val initial: String get() = name.trim().take(1).uppercase().ifBlank { "?" }
    val locked: Boolean get() = pinHash != null
    val profileTheme: ProfileTheme get() = ProfileTheme.entries.firstOrNull { it.name == theme } ?: ProfileTheme.BAMBI

    fun withPin(pin: String?): DesktopProfile = copy(pinHash = pin?.let { ProfilePin.hash(id, it) })

    fun pinMatches(pin: String): Boolean = pinHash == null || pinHash == ProfilePin.hash(id, pin)

    companion object {
        /** Como en la tele: hasta 5 perfiles. */
        const val MAX = Profile.MAX

        fun isValidPin(pin: String) = pin.length == ProfilePin.LENGTH && pin.all { it.isDigit() }
    }
}

@Serializable
data class LibraryFile(
    val myList: List<SavedItem> = emptyList(),
    val progress: List<SavedProgress> = emptyList(),
    /** Audio y subtítulos elegidos por serie (clave: "TV:id"), como «memoria de pistas» en la tele. */
    val tracks: Map<String, TrackPreference> = emptyMap(),
)

@Serializable
data class TrackPreference(val audioLang: String? = null, val subLang: String? = null)

private val libraryJson = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

/**
 * Perfiles de escritorio (como en la tele: cada uno con su icono, su lista y su progreso). Siempre hay al menos uno:
 * la primera vez, o al venir de una versión sin perfiles (library.json), se crea «Principal» con lo que hubiera.
 */
class ProfileStore(private val dir: File = ConfigStore.defaultFile().parentFile) {
    private val file = File(dir, "profiles.json")
    var profiles by mutableStateOf(load())
        private set

    /** Lo mismo que [profiles], para quien lo sigue fuera de la interfaz (Trakt). */
    val flow = MutableStateFlow(profiles)

    private val libraries = HashMap<String, LibraryStore>()

    init {
        if (profiles.isEmpty()) migrate()
    }

    val canAdd: Boolean get() = profiles.size < DesktopProfile.MAX

    /** Guarda un perfil nuevo o editado. Uno nuevo cuando ya hay [DesktopProfile.MAX] no se añade (false). */
    fun save(profile: DesktopProfile): Boolean {
        val list = profiles.toMutableList()
        val index = list.indexOfFirst { it.id == profile.id }
        when {
            index >= 0 -> list[index] = profile
            list.size >= DesktopProfile.MAX -> return false
            else -> list += profile
        }
        write(list)
        return true
    }

    /** Borra un perfil con su lista y su progreso. El último no se puede borrar (false). */
    fun delete(id: String): Boolean {
        if (profiles.none { it.id == id } || profiles.size <= 1) return false
        write(profiles.filterNot { it.id == id })
        synchronized(libraries) { libraries.remove(id) }
        File(dir, "library-$id.json").delete()
        return true
    }

    fun byId(id: String): DesktopProfile? = profiles.firstOrNull { it.id == id }

    private fun migrate() {
        val main = DesktopProfile(id = Profile.DEFAULT_ID, name = "Principal")
        val legacy = File(dir, "library.json")
        val target = File(dir, "library-${main.id}.json")
        if (legacy.isFile && !target.exists()) {
            dir.mkdirs()
            legacy.copyTo(target)
            legacy.renameTo(File(dir, "library.json.migrado"))
        }
        write(listOf(main))
    }

    private fun write(list: List<DesktopProfile>) {
        dir.mkdirs()
        file.writeText(libraryJson.encodeToString(ListSerializer(DesktopProfile.serializer()), list))
        profiles = list
        flow.value = list
    }

    private fun load(): List<DesktopProfile> = runCatching {
        libraryJson.decodeFromString(ListSerializer(DesktopProfile.serializer()), file.readText())
    }.getOrDefault(emptyList())

    /** Biblioteca del perfil: siempre la misma instancia, para que la interfaz y Trakt vean los mismos cambios. */
    fun library(profile: DesktopProfile): LibraryStore = library(profile.id)

    fun library(profileId: String): LibraryStore = synchronized(libraries) {
        libraries.getOrPut(profileId) { LibraryStore(File(dir, "library-$profileId.json")) }
    }
}

/** Mi lista y el progreso de un perfil, guardados en library-<perfil>.json. */
class LibraryStore(private val file: File) {
    private var data by mutableStateOf(load())

    /** Lo mismo que [myList]/[progress], para quien lo sigue fuera de la interfaz (Trakt). */
    val state = MutableStateFlow(data)

    /** Cambios en Mi lista hechos por el usuario (no los que llegan de Trakt), para subirlos a Trakt. */
    val myListChanges = MutableSharedFlow<Pair<SavedItem, Boolean>>(extraBufferCapacity = 64)

    /** Claves quitadas a mano de «Seguir viendo», para quitarlas también de Trakt. */
    val cleared = MutableSharedFlow<String>(extraBufferCapacity = 64)

    val myList: List<SavedItem> get() = data.myList
    val progress: List<SavedProgress> get() = data.progress

    fun inMyList(item: CatalogItem) = data.myList.any { it.id == item.id && it.type == item.type.name }

    fun toggleMyList(item: CatalogItem) {
        val added = !inMyList(item)
        val saved = SavedItem.of(item).copy(addedAt = System.currentTimeMillis())
        val list = if (!added) data.myList.filterNot { it.id == item.id && it.type == item.type.name } else listOf(saved) + data.myList
        write(data.copy(myList = list))
        myListChanges.tryEmit(saved to added)
    }

    fun progressFor(item: CatalogItem, season: Int? = null, episode: Int? = null): SavedProgress? =
        data.progress.firstOrNull { it.key == progressKey(item.type.name, item.id, season, episode) }

    /** Lo último de cada título que no has terminado, lo más reciente primero. */
    fun continueWatching(): List<SavedProgress> = data.progress
        .filter { it.resumable }
        .sortedByDescending { it.updatedAt }
        .distinctBy { it.item.type + it.item.id }

    fun record(entry: SavedProgress) {
        val rest = data.progress.filterNot { it.key == entry.key }
        write(data.copy(progress = (listOf(entry) + rest).take(MAX_PROGRESS)))
    }

    /** Quitar de «Seguir viendo» (a mano): se olvida el progreso de esa película o episodio. */
    fun clear(key: String) {
        if (data.progress.none { it.key == key }) return
        write(data.copy(progress = data.progress.filterNot { it.key == key }))
        cleared.tryEmit(key)
    }

    fun tracksFor(item: CatalogItem): TrackPreference? = data.tracks["${item.type.name}:${item.id}"]

    fun saveTracks(item: CatalogItem, preference: TrackPreference) {
        if (preference.audioLang == null && preference.subLang == null) return
        write(data.copy(tracks = data.tracks + ("${item.type.name}:${item.id}" to preference)))
    }

    // --------------------------------------------------------------- lo que llega de Trakt (sin volver a subirlo)

    /** Progreso de fuera: entra si es más reciente que el de aquí. */
    fun importProgress(entries: Collection<SavedProgress>) {
        if (entries.isEmpty()) return
        val byKey = data.progress.associateBy { it.key }.toMutableMap()
        for (entry in entries) {
            val mine = byKey[entry.key]
            if (mine != null && mine.updatedAt >= entry.updatedAt) continue
            // Si ya lo teníamos con su ficha (póster, título), se conserva.
            byKey[entry.key] = if (mine != null && entry.item.title.isBlank()) entry.copy(item = mine.item) else entry
        }
        write(data.copy(progress = byKey.values.sortedByDescending { it.updatedAt }.take(MAX_PROGRESS)))
    }

    fun removeProgress(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val set = keys.toSet()
        write(data.copy(progress = data.progress.filterNot { it.key in set }))
    }

    fun importMyList(added: Collection<SavedItem>, removed: Collection<Pair<String, String>>) {
        if (added.isEmpty() && removed.isEmpty()) return
        val gone = removed.toSet()
        val kept = data.myList.filterNot { (it.type to it.id) in gone }
        val fresh = added.filter { a -> kept.none { it.type == a.type && it.id == a.id } }
        write(data.copy(myList = (fresh + kept).sortedByDescending { it.addedAt }))
    }

    /** Completa la ficha (título, póster) de lo que llegó de Trakt solo con su id. */
    fun updateItem(item: SavedItem) {
        fun SavedItem.same() = type == item.type && id == item.id
        write(
            data.copy(
                myList = data.myList.map { if (it.same()) item.copy(addedAt = it.addedAt) else it },
                progress = data.progress.map { if (it.item.same()) it.copy(item = item) else it },
            ),
        )
    }

    /** Títulos guardados sin ficha (llegados de Trakt), para buscar su póster y su nombre. */
    fun itemsWithoutDetails(): List<SavedItem> =
        (data.myList + data.progress.map { it.item }).filter { it.title.isBlank() }.distinctBy { it.type + it.id }

    @Synchronized
    private fun write(value: LibraryFile) {
        file.parentFile?.mkdirs()
        file.writeText(libraryJson.encodeToString(LibraryFile.serializer(), value))
        data = value
        state.value = value
    }

    private fun load(): LibraryFile = runCatching {
        libraryJson.decodeFromString(LibraryFile.serializer(), file.readText())
    }.getOrDefault(LibraryFile())

    private companion object {
        const val MAX_PROGRESS = 2000
    }
}
