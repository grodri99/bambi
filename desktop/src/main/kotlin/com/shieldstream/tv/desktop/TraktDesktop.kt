package com.shieldstream.tv.desktop

import com.shieldstream.tv.data.repository.TraktClient
import com.shieldstream.tv.data.repository.TraktPlatform
import com.shieldstream.tv.data.repository.TraktPrefs
import com.shieldstream.tv.data.repository.TraktSync
import com.shieldstream.tv.data.repository.TraktText
import com.shieldstream.tv.data.repository.traktSyncKeys
import com.shieldstream.tv.data.repository.TraktSession
import com.shieldstream.tv.domain.MyListChange
import com.shieldstream.tv.domain.MyListEntry
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.ScrobbleAction
import com.shieldstream.tv.domain.TraktAuthPoll
import com.shieldstream.tv.domain.TraktCredentials
import com.shieldstream.tv.domain.TraktDeviceCode
import com.shieldstream.tv.domain.TraktRatingTarget
import com.shieldstream.tv.domain.WatchProgressClear
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.model.ProfileTheme
import com.shieldstream.tv.model.WatchProgress
import com.shieldstream.tv.network.ApiFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonArray
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID

/**
 * Claves de progreso: escritorio guarda "MOVIE:id" / "TV:id:t:e" (con la ficha del título); Trakt y la tele usan
 * "movie:id" / "tv:id:t:e".
 */
object TraktKeys {
    fun toTrakt(desktopKey: String): String? {
        val p = desktopKey.split(':')
        return when {
            p.size == 2 && p[0] == MediaType.MOVIE.name -> WatchProgress.movieKey(p[1])
            p.size == 4 && p[0] == MediaType.TV.name -> WatchProgress.episodeKey(p[1], p[2].toIntOrNull() ?: return null, p[3].toIntOrNull() ?: return null)
            else -> null
        }
    }

    fun toDesktop(traktKey: String): String? {
        val p = traktKey.split(':')
        return when {
            p.size == 2 && p[0] == "movie" -> progressKey(MediaType.MOVIE.name, p[1], null, null)
            p.size == 4 && p[0] == "tv" -> progressKey(MediaType.TV.name, p[1], p[2].toIntOrNull() ?: return null, p[3].toIntOrNull() ?: return null)
            else -> null
        }
    }

    /** Progreso que llega de Trakt (solo ids) a progreso de escritorio; la ficha se completa luego con TMDB. */
    fun fromTrakt(traktKey: String, progress: WatchProgress): SavedProgress? {
        val p = traktKey.split(':')
        val (type, id, season, episode) = when {
            p.size == 2 && p[0] == "movie" -> Quad(MediaType.MOVIE, p[1], null, null)
            p.size == 4 && p[0] == "tv" -> Quad(MediaType.TV, p[1], p[2].toIntOrNull() ?: return null, p[3].toIntOrNull() ?: return null)
            else -> return null
        }
        val watched = progress.isWatched
        return SavedProgress(
            item = SavedItem(id = id, type = type.name, title = ""),
            season = season,
            episode = episode,
            positionSec = progress.positionMs / 1000.0,
            durationSec = progress.durationMs / 1000.0,
            updatedAt = progress.updatedAt,
            finished = watched,
        )
    }

    fun toWatchProgress(p: SavedProgress): WatchProgress {
        val duration = (p.durationSec * 1000).toLong()
        val position = if (p.finished) duration.coerceAtLeast(1) else (p.positionSec * 1000).toLong()
        return WatchProgress(position, duration.coerceAtLeast(if (p.finished) 1 else 0), p.updatedAt)
    }

    private data class Quad(val type: MediaType, val id: String, val season: Int?, val episode: Int?)
}

private fun DesktopProfile.toProfile() = Profile(
    id = id,
    name = name,
    avatar = avatar,
    traktUser = traktUser,
    pinHash = pinHash,
    kids = kids,
    theme = profileTheme,
    avatarImage = image,
)

/** Perfiles de escritorio vistos como el ProfileRepository de la tele (lo que usa la sincronización de Trakt). */
internal class DesktopProfileRepository(
    private val store: ProfileStore,
    session: Session,
    scope: CoroutineScope,
) : ProfileRepository {
    private val _profiles = MutableStateFlow(store.profiles.map { it.toProfile() })
    private val _active = MutableStateFlow((session.profile ?: store.profiles.first()).toProfile())
    override val profiles: StateFlow<List<Profile>> = _profiles
    override val active: StateFlow<Profile> = _active

    init {
        scope.launch { store.flow.collect { list -> _profiles.value = list.map { it.toProfile() } } }
        scope.launch {
            session.flow.filterNotNull().collect { p -> _active.value = (store.byId(p.id) ?: p).toProfile() }
        }
    }

    override fun select(id: String) = Unit

    /** La sincronización solo cambia el usuario de Trakt del perfil. */
    override fun save(profile: Profile): Profile {
        store.byId(profile.id)?.let { store.save(it.copy(traktUser = profile.traktUser)) }
        return profile
    }

    override fun draft(): Profile = Profile(id = UUID.randomUUID().toString(), name = "", avatar = 0, theme = ProfileTheme.BAMBI)

    override fun delete(id: String) {
        store.delete(id)
    }
}

/** Progreso del perfil activo visto como el WatchProgressRepository de la tele. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DesktopProgressRepository(
    private val store: ProfileStore,
    private val session: Session,
    scope: CoroutineScope,
    private val onImported: (LibraryStore) -> Unit,
) : WatchProgressRepository {
    private val _progress = MutableStateFlow<Map<String, WatchProgress>>(emptyMap())
    private val _cleared = MutableSharedFlow<WatchProgressClear>(extraBufferCapacity = 64)
    override val progress: StateFlow<Map<String, WatchProgress>> = _progress
    override val cleared: SharedFlow<WatchProgressClear> = _cleared

    init {
        scope.launch {
            session.flow.filterNotNull().flatMapLatest { p -> store.library(p.id).state.map { state -> state.progress } }
                .collect { list -> _progress.value = toMap(list) }
        }
        scope.launch {
            session.flow.filterNotNull().flatMapLatest { p -> store.library(p.id).cleared.map { key -> p.id to key } }
                .collect { (profileId, key) -> TraktKeys.toTrakt(key)?.let { _cleared.emit(WatchProgressClear(profileId, it)) } }
        }
    }

    private fun library(): LibraryStore? = session.profile?.let { store.library(it.id) }

    override fun get(key: String): WatchProgress? = _progress.value[key]

    override fun save(key: String, positionMs: Long, durationMs: Long, sourceId: String?) {
        val lib = library() ?: return
        val entry = TraktKeys.fromTrakt(key, WatchProgress(positionMs, durationMs, System.currentTimeMillis())) ?: return
        lib.importProgress(listOf(entry))
    }

    override fun clear(key: String) {
        TraktKeys.toDesktop(key)?.let { library()?.clear(it) }
    }

    override fun importRemote(entries: Map<String, WatchProgress>) {
        val lib = library() ?: return
        lib.importProgress(entries.mapNotNull { (key, p) -> TraktKeys.fromTrakt(key, p) })
        onImported(lib)
    }

    override fun removeRemote(keys: Collection<String>) {
        library()?.removeProgress(keys.mapNotNull { TraktKeys.toDesktop(it) })
    }

    companion object {
        fun toMap(list: List<SavedProgress>): Map<String, WatchProgress> =
            list.mapNotNull { p -> TraktKeys.toTrakt(p.key)?.let { it to TraktKeys.toWatchProgress(p) } }.toMap()
    }
}

/** Mi lista del perfil activo vista como el MyListRepository de la tele (= watchlist de Trakt). */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DesktopMyListRepository(
    private val store: ProfileStore,
    private val session: Session,
    scope: CoroutineScope,
    private val onImported: (LibraryStore) -> Unit,
) : MyListRepository {
    private val _entries = MutableStateFlow<List<MyListEntry>>(emptyList())
    private val _changes = MutableSharedFlow<MyListChange>(extraBufferCapacity = 64)
    override val entries: StateFlow<List<MyListEntry>> = _entries
    override val changes: SharedFlow<MyListChange> = _changes

    init {
        scope.launch {
            session.flow.filterNotNull().flatMapLatest { p -> store.library(p.id).state.map { it.myList } }
                .collect { list -> _entries.value = list.map { MyListEntry(MediaType.valueOf(it.type), it.id, it.addedAt) } }
        }
        scope.launch {
            session.flow.filterNotNull().flatMapLatest { p -> store.library(p.id).myListChanges.map { p.id to it } }
                .collect { (profileId, change) ->
                    val (item, added) = change
                    _changes.emit(MyListChange(profileId, MediaType.valueOf(item.type), item.id, added))
                }
        }
    }

    private fun library(): LibraryStore? = session.profile?.let { store.library(it.id) }

    override fun contains(type: MediaType, id: String): Boolean = _entries.value.any { it.type == type && it.id == id }

    override fun add(type: MediaType, id: String) {
        if (!contains(type, id)) library()?.toggleMyList(CatalogItem(id, null, type, "", "", null, null, null, null))
    }

    override fun addFor(profileId: String, type: MediaType, id: String) {
        store.library(profileId).importMyList(listOf(SavedItem(id = id, type = type.name, title = "", addedAt = System.currentTimeMillis())), emptyList())
    }

    override fun remove(type: MediaType, id: String) {
        if (contains(type, id)) library()?.toggleMyList(CatalogItem(id, null, type, "", "", null, null, null, null))
    }

    override fun importRemote(added: List<MyListEntry>, removedKeys: Collection<String>) {
        val lib = library() ?: return
        val removed = removedKeys.mapNotNull { key ->
            val type = key.substringBefore('_', "")
            val id = key.substringAfter('_', "")
            (type to id).takeIf { type.isNotEmpty() && id.isNotEmpty() }
        }
        lib.importMyList(added.map { SavedItem(id = it.id, type = it.type.name, title = "", addedAt = it.addedAt) }, removed)
        onImported(lib)
    }
}

/** Preferencias de la sincronización en un JSON (trakt-sync.json). */
internal class JsonTraktPrefs(private val file: File) : TraktPrefs {
    private val json = Json { prettyPrint = true }
    private val values: MutableMap<String, JsonElement> = runCatching {
        json.decodeFromString(MapSerializer(String.serializer(), JsonElement.serializer()), file.readText()).toMutableMap()
    }.getOrDefault(mutableMapOf())

    @Synchronized override fun getString(key: String): String? = (values[key] as? JsonPrimitive)?.contentOrNull
    @Synchronized override fun getLong(key: String): Long = (values[key] as? JsonPrimitive)?.longOrNull ?: 0L
    @Synchronized override fun getStringSet(key: String): Set<String> =
        (values[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet().orEmpty()

    @Synchronized
    override fun put(values: Map<String, Any?>) {
        values.forEach { (key, value) ->
            when (value) {
                null -> this.values.remove(key)
                is String -> this.values[key] = JsonPrimitive(value)
                is Long -> this.values[key] = JsonPrimitive(value)
                is Set<*> -> this.values[key] = buildJsonArray { value.forEach { add(JsonPrimitive(it.toString())) } }
                else -> error("Tipo no admitido: ${value::class}")
            }
        }
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(MapSerializer(String.serializer(), JsonElement.serializer()), this.values))
    }
}

internal class DesktopTraktPlatform(
    private val secrets: SecretStore,
    private val store: ProfileStore,
    override val syncPrefs: TraktPrefs,
) : TraktPlatform {
    override fun secret(key: String): String? = secrets.get(key)
    override fun setSecret(key: String, value: String?) = secrets.set(key, value)

    override fun readAllProgress(profileId: String): Map<String, WatchProgress> =
        DesktopProgressRepository.toMap(store.library(profileId).progress)

    override fun clearTrakt(profileId: String) {
        secrets.set(TraktSession.storeKey(profileId), null)
        syncPrefs.put(traktSyncKeys(profileId).associateWith { null })
    }

    override fun text(text: TraktText): String = when (text) {
        TraktText.NOT_CONFIGURED -> "Trakt no está configurado: pon el Client ID en Ajustes."
        TraktText.INVALID_CODE -> "Código no válido. Pide uno nuevo."
        TraktText.CODE_ALREADY_USED -> "Ese código ya se ha usado. Pide uno nuevo."
    }

    override fun log(message: String) = System.err.println("Trakt: $message")
}

/**
 * Trakt en escritorio: la misma sincronización que la tele ([TraktSync]) sobre los perfiles y la biblioteca de
 * escritorio. Se crea cuando hay Client ID en Ajustes y se rehace si cambia.
 */
class TraktManager(
    private val config: ConfigStore,
    private val profiles: ProfileStore,
    private val session: Session,
    private val repository: DesktopRepository,
    private val secrets: SecretStore = SecretStore.default(),
    private val dir: File = ConfigStore.defaultFile().parentFile,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var current: Pair<TraktCredentials, Running>? = null

    private inner class Running(credentials: TraktCredentials) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sync = TraktSync(
            client = TraktClient(OkHttpClient(), ApiFactory.json(), credentials, userAgent = "Bambi/1.0 (Windows)"),
            credentials = credentials,
            profiles = DesktopProfileRepository(profiles, session, scope),
            progress = DesktopProgressRepository(profiles, session, scope) { fillDetails(it) },
            myList = DesktopMyListRepository(profiles, session, scope) { fillDetails(it) },
            json = ApiFactory.json(),
            platform = DesktopTraktPlatform(secrets, profiles, JsonTraktPrefs(File(dir, "trakt-sync.json"))),
        )

        fun close() {
            sync.close()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    private fun credentials() = TraktCredentials(config.current.traktClientId.trim(), config.current.traktClientSecret.trim())

    /** La sincronización con las credenciales de ahora; null si no hay Client ID. */
    @Synchronized
    private fun sync(): TraktSync? {
        val wanted = credentials()
        if (!wanted.isConfigured) {
            current?.second?.close()
            current = null
            return null
        }
        current?.let { (creds, running) -> if (creds == wanted) return running.sync else running.close() }
        return Running(wanted).also { current = wanted to it }.sync
    }

    val isConfigured: Boolean get() = credentials().isConfigured

    fun isConnected(profile: DesktopProfile): Boolean =
        profile.traktUser != null && secrets.get(TraktSession.storeKey(profile.id)) != null

    suspend fun requestCode(): Result<TraktDeviceCode> = sync()?.requestDeviceCode()
        ?: Result.failure(IllegalStateException("Pon el Client ID de Trakt en Ajustes."))

    suspend fun poll(profileId: String, code: TraktDeviceCode): TraktAuthPoll =
        sync()?.pollDeviceToken(profileId, code) ?: TraktAuthPoll.Failed("Trakt no está configurado")

    suspend fun disconnect(profileId: String) {
        sync()?.disconnect(profileId)
    }

    /** Sincroniza el perfil activo (al entrar en él, al volver de un vídeo o a mano desde Ajustes). */
    fun syncNow(force: Boolean = false) {
        val profile = session.profile ?: return
        if (isConnected(profile)) sync()?.syncActive(force)
        // Lo que llegó de Trakt sin ficha (de otra sesión o de antes) se completa también.
        fillDetails(profiles.library(profile))
    }

    // --------------------------------------------------------------- reproducción

    /** Empieza un vídeo: Trakt lo marca como «viendo». */
    fun playbackStarted(request: PlayRequest, durationSec: Double?) {
        val key = traktKey(request) ?: return
        val fraction = durationSec?.takeIf { it > 0 }?.let { (request.startSec / it).toFloat() } ?: 0f
        if (session.profile?.let(::isConnected) == true) sync()?.scrobble(key, fraction, ScrobbleAction.START)
    }

    /** Termina: pausa (sigue en «Seguir viendo» de Trakt) o, desde el 80 %, stop (Trakt lo da por visto). */
    fun playbackEnded(request: PlayRequest, result: PlaybackResult) {
        if (result.failed) return
        val key = traktKey(request) ?: return
        val duration = result.durationSec?.takeIf { it > 0 } ?: return
        val fraction = if (result.finished) 1f else ((result.positionSec ?: 0.0) / duration).toFloat()
        val action = if (fraction >= SCROBBLE_WATCHED) ScrobbleAction.STOP else ScrobbleAction.PAUSE
        if (session.profile?.let(::isConnected) == true) sync()?.scrobble(key, fraction, action)
    }

    /** Marcar visto o no visto a mano (película, episodio o temporada entera). */
    fun setWatched(desktopKeys: Collection<String>, watched: Boolean) {
        if (session.profile?.let(::isConnected) != true) return
        sync()?.setWatched(desktopKeys.mapNotNull { TraktKeys.toTrakt(it) }, watched)
    }

    private fun traktKey(request: PlayRequest): String? = TraktKeys.toTrakt(
        progressKey(request.item.type.name, request.item.id, request.episode?.season, request.episode?.number),
    )

    // --------------------------------------------------------------- Inicio y fichas

    /** Recomendaciones de Trakt para el perfil activo, ya como títulos de TMDB. */
    suspend fun recommendations(type: MediaType, limit: Int = 20): List<CatalogItem> {
        val profile = session.profile ?: return emptyList()
        if (!isConnected(profile) || profile.kids) return emptyList()
        val ids = runCatching { sync()?.recommendations(profile.id, type, limit) }.getOrNull().orEmpty()
        return coroutineScope {
            ids.map { id -> async { runCatching { repository.details(blank(type, id)).item }.getOrNull() } }
                .awaitAll().filterNotNull().filter { it.posterUrl != null }
        }
    }

    suspend fun rating(target: TraktRatingTarget): Int? = sync()?.getRating(target)

    suspend fun rate(target: TraktRatingTarget, rating: Int?) {
        sync()?.setRating(target, rating)
    }

    fun canRate(): Boolean = session.profile?.let(::isConnected) == true

    /** Busca en TMDB el título y el póster de lo que llegó de Trakt solo con su id. */
    private fun fillDetails(library: LibraryStore) {
        val missing = library.itemsWithoutDetails()
        if (missing.isEmpty()) return
        scope.launch {
            for (item in missing) {
                val details = runCatching { repository.details(blank(MediaType.valueOf(item.type), item.id)).item }.getOrNull() ?: continue
                library.updateItem(SavedItem.of(details))
            }
        }
    }

    private fun blank(type: MediaType, id: String) = CatalogItem(id, null, type, "", "", null, null, null, null)

    companion object {
        /** Como en la tele: desde el 80 % Trakt da el vídeo por visto. */
        const val SCROBBLE_WATCHED = 0.8f
    }
}
