package com.shieldstream.tv.data.repository

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import android.content.Context
import android.util.Log
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.MyListChange
import com.shieldstream.tv.domain.MyListEntry
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.domain.ScrobbleAction
import com.shieldstream.tv.domain.TraktAuthPoll
import com.shieldstream.tv.domain.TraktCredentials
import com.shieldstream.tv.domain.TraktDeviceCode
import com.shieldstream.tv.domain.TraktRatingTarget
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.model.WatchProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: TraktClient,
    private val credentials: TraktCredentials,
    private val profiles: ProfileRepository,
    private val progress: WatchProgressRepository,
    private val myList: MyListRepository,
    private val tokens: TokenStore,
    private val cleanup: ProfileDataCleaner,
    private val json: Json,
    private val texts: Texts,
) : TraktRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val tokenLocks = ConcurrentHashMap<String, Mutex>()
    private val scrobbleLocks = ConcurrentHashMap<String, Mutex>()
    private val syncPrefs = context.getSharedPreferences(TRAKT_SYNC_PREFS, Context.MODE_PRIVATE)

    private val playbackIds = ConcurrentHashMap<String, Long>()
    @Volatile private var lastSyncAttempt = 0L

    override val isConfigured: Boolean get() = credentials.isConfigured
    override val isConnected: Boolean get() = session(profiles.active.value.id) != null

    init {
        scope.launch {
            profiles.active.distinctUntilChangedBy { it.id }.drop(1).collect {
                playbackIds.clear()
                syncActive(force = true)
            }
        }
        scope.launch {
            progress.cleared.collect { change -> removePlayback(change.profileId, change.key) }
        }
        scope.launch {
            myList.changes.collect { pushWatchlist(it) }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putSecret() {
        if (credentials.clientSecret.isNotBlank()) put("client_secret", credentials.clientSecret)
    }

    override suspend fun requestDeviceCode(): Result<TraktDeviceCode> = runCatching {
        check(isConfigured) { texts.get(StringsR.string.data_trakt_isn_t_set_up_in_this) }
        val response = client.post("/oauth/device/code", buildJsonObject { put("client_id", credentials.clientId) }, null)
        if (!response.ok) throw TraktHttpException(response.code)
        val body = response.body!!.jsonObject
        TraktDeviceCode(
            deviceCode = body.string("device_code")!!,
            userCode = body.string("user_code")!!,
            verificationUrl = body.string("verification_url") ?: "https://trakt.tv/activate",
            expiresInSeconds = body.int("expires_in") ?: 600,
            intervalSeconds = body.int("interval") ?: 5,
        )
    }

    override suspend fun pollDeviceToken(profileId: String, code: TraktDeviceCode): TraktAuthPoll {
        val response = runCatching {
            client.post(
                "/oauth/device/token",
                buildJsonObject {
                    put("code", code.deviceCode)
                    put("client_id", credentials.clientId)
                    putSecret()
                },
                null,
            )
        }.getOrElse { return TraktAuthPoll.Pending }
        return when (response.code) {
            200 -> {
                val body = response.body?.jsonObject ?: return TraktAuthPoll.Failed("Trakt: respuesta vacía")
                val access = body.string("access_token") ?: return TraktAuthPoll.Failed("Trakt: sin token")
                val username = fetchUsername(access) ?: "Trakt"
                saveSession(profileId, body, username)
                profiles.profiles.value.firstOrNull { it.id == profileId }?.let { profiles.save(it.copy(traktUser = username)) }
                scope.launch {
                    uploadLocal(profileId)
                    if (profiles.active.value.id == profileId) syncActive(force = true)
                }
                TraktAuthPoll.Connected(username)
            }
            400 -> TraktAuthPoll.Pending
            429 -> TraktAuthPoll.SlowDown
            410 -> TraktAuthPoll.Expired
            418 -> TraktAuthPoll.Denied
            404 -> TraktAuthPoll.Failed(texts.get(StringsR.string.data_invalid_code_get_a_new_one))
            409 -> TraktAuthPoll.Failed(texts.get(StringsR.string.data_that_code_was_already_used_get_a))
            else -> TraktAuthPoll.Failed("Trakt HTTP ${response.code}")
        }
    }

    override suspend fun disconnect(profileId: String) {
        session(profileId)?.let { s ->
            runCatching {
                client.post(
                    "/oauth/revoke",
                    buildJsonObject {
                        put("token", s.accessToken)
                        put("client_id", credentials.clientId)
                        putSecret()
                    },
                    s.accessToken,
                )
            }
        }
        forget(profileId)
    }

    override suspend fun recommendations(profileId: String, type: MediaType): List<String> {
        val token = validToken(profileId) ?: return emptyList()
        val kind = if (type == MediaType.MOVIE) "movies" else "shows"
        val response = client.get("/recommendations/$kind?limit=$RECOMMENDATIONS_LIMIT", token)
        if (!response.ok) throw TraktHttpException(response.code)
        return (response.body as? JsonArray).orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.obj("ids")?.int("tmdb")?.toString()
        }.distinct()
    }

    override suspend fun getRating(target: TraktRatingTarget): Int? {
        val token = validToken(profiles.active.value.id) ?: throw TraktHttpException(401)
        val kind = when (target) {
            is TraktRatingTarget.Movie -> "movies"
            is TraktRatingTarget.Show -> "shows"
            is TraktRatingTarget.Episode -> "episodes"
        }
        var page = 1
        while (true) {
            val response = client.get("/sync/ratings/$kind?page=$page&limit=$RATINGS_PAGE_LIMIT", token)
            if (!response.ok) throw TraktHttpException(response.code)
            val items = (response.body as? JsonArray).orEmpty()
            for (element in items) {
                val item = element as? JsonObject ?: continue
                if (item.matches(target)) return item.int("rating")?.takeIf { it in 1..10 }
            }
            if (items.size < RATINGS_PAGE_LIMIT) break
            page++
        }
        return null
    }

    override suspend fun setRating(target: TraktRatingTarget, rating: Int?) {
        require(rating == null || rating in 1..10) { "Trakt ratings must be between 1 and 10" }
        val token = validToken(profiles.active.value.id) ?: throw TraktHttpException(401)
        val path = if (rating == null) "/sync/ratings/remove" else "/sync/ratings"
        val response = client.post(path, ratingBody(target, rating), token)
        if (!response.ok) throw TraktHttpException(response.code)
    }

    override fun syncActive(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSyncAttempt < MIN_SYNC_INTERVAL_MS) return
        lastSyncAttempt = now
        val profileId = profiles.active.value.id
        scope.launch {
            syncMutex.withLock {
                runCatching { pull(profileId, force) }
                    .onFailure { Log.w(TAG, "Sincronización fallida: ${it.message}") }
            }
        }
    }

    private suspend fun pull(profileId: String, force: Boolean) {
        val token = validToken(profileId) ?: return
        val activities = client.get("/sync/last_activities", token)
        if (!activities.ok) throw TraktHttpException(activities.code)
        val marker = activities.body?.jsonObject?.string("all")
        val markerKey = "all_$profileId"
        if (!force && marker != null && marker == syncPrefs.getString(markerKey, null)) return

        val local = progress.progress.value
        val incoming = HashMap<String, WatchProgress>()

        // 1) A medias (sync/playback).
        val remotePlayback = HashSet<String>()
        for (kind in listOf("movies", "episodes")) {
            val response = client.get("/sync/playback/$kind?extended=full", token)
            if (!response.ok) throw TraktHttpException(response.code)
            for (element in (response.body as? JsonArray).orEmpty()) {
                val item = element.jsonObject
                val key = keyOf(item) ?: continue
                val pausedAt = TraktClient.parseDate(item.string("paused_at")) ?: continue
                val fraction = ((item.double("progress") ?: continue) / 100.0).coerceIn(0.0, 1.0)
                item.long("id")?.let { playbackIds[key] = it }
                remotePlayback += key
                val mine = local[key]
                if (mine != null && mine.updatedAt >= pausedAt - CLOCK_SLACK_MS) continue
                val duration = mine?.durationMs?.takeIf { it > 1 } ?: runtimeMs(item) ?: continue
                incoming[key] = WatchProgress((fraction * duration).toLong(), duration, pausedAt)
            }
        }

        addWatched(client.get("/sync/watched/movies", token), local, incoming) { item ->
            listOf(keyOf(item) to item.string("last_watched_at"))
        }
        addWatched(client.get("/sync/watched/shows", token), local, incoming) { item ->
            val showId = item.obj("show")?.obj("ids")?.int("tmdb") ?: return@addWatched emptyList()
            item.array("seasons").flatMap { season ->
                val s = season.jsonObject.int("number") ?: return@flatMap emptyList()
                season.jsonObject.array("episodes").map { ep ->
                    val e = ep.jsonObject
                    e.int("number")?.let { WatchProgress.episodeKey(showId.toString(), s, it) } to e.string("last_watched_at")
                }
            }
        }

        val lastSync = syncPrefs.getLong("last_$profileId", 0L)
        val previousRemote = syncPrefs.getStringSet("remote_$profileId", null).orEmpty()
        val gone = local.filter { (key, p) ->
            p.isResumable && key in previousRemote && key !in remotePlayback && key !in incoming && p.updatedAt < lastSync
        }.keys

        val remoteList = ArrayList<MyListEntry>()
        for ((kind, type, field) in listOf(Triple("movies", MediaType.MOVIE, "movie"), Triple("shows", MediaType.TV, "show"))) {
            val response = client.get("/sync/watchlist/$kind", token)
            if (!response.ok) throw TraktHttpException(response.code)
            for (element in (response.body as? JsonArray).orEmpty()) {
                val item = element.jsonObject
                val id = item.obj(field)?.obj("ids")?.int("tmdb") ?: continue
                remoteList += MyListEntry(type, id.toString(), TraktClient.parseDate(item.string("listed_at")) ?: 0L)
            }
        }
        val remoteListKeys = remoteList.map { it.key }.toSet()
        val previousList = syncPrefs.getStringSet("wl_$profileId", null).orEmpty()
        val localList = myList.entries.value.map { it.key }.toSet()
        val listAdded = remoteList.filter { it.key !in localList }
        val listGone = localList.filter { it in previousList && it !in remoteListKeys }

        if (profiles.active.value.id != profileId) return
        progress.importRemote(incoming)
        progress.removeRemote(gone)
        myList.importRemote(listAdded, listGone)
        syncPrefs.edit()
            .putString(markerKey, marker)
            .putLong("last_$profileId", System.currentTimeMillis())
            .putStringSet("remote_$profileId", HashSet(remotePlayback))
            .putStringSet("wl_$profileId", HashSet(remoteListKeys))
            .apply()
        Log.i(TAG, "Sincronizado: ${incoming.size} cambios, ${gone.size} quitados")
    }

    private inline fun addWatched(
        response: TraktResponse,
        local: Map<String, WatchProgress>,
        into: MutableMap<String, WatchProgress>,
        entries: (JsonObject) -> List<Pair<String?, String?>>,
    ) {
        if (!response.ok) throw TraktHttpException(response.code)
        for (element in (response.body as? JsonArray).orEmpty()) {
            for ((key, date) in entries(element.jsonObject)) {
                if (key == null) continue
                val at = TraktClient.parseDate(date) ?: continue
                val mine = local[key]
                if (mine != null && (mine.isWatched || mine.updatedAt >= at - CLOCK_SLACK_MS)) continue
                if (into[key]?.let { it.updatedAt >= at } == true) continue
                into[key] = WatchProgress(positionMs = 1, durationMs = 1, updatedAt = at) // 100 % = visto
            }
        }
    }

    private suspend fun uploadLocal(profileId: String) {
        val token = validToken(profileId) ?: return
        val all = WatchProgressRepositoryImpl.readAll(context, profileId)
        runCatching {
            val watched = all.filter { it.value.isWatched }
            if (watched.isNotEmpty()) client.post("/sync/history", historyBody(watched), token)
            if (profiles.active.value.id == profileId && myList.entries.value.isNotEmpty()) {
                delay(POST_SPACING_MS)
                client.post("/sync/watchlist", watchlistBody(myList.entries.value.map { it.type to it.id }), token)
            }
            all.filter { it.value.isResumable }
                .entries.sortedByDescending { it.value.updatedAt }
                .take(MAX_UPLOAD_RESUMABLE)
                .forEach { (key, p) ->
                    delay(POST_SPACING_MS)
                    val item = itemBody(key) ?: return@forEach
                    client.post("/scrobble/pause", item.with("progress", JsonPrimitive(p.fraction * 100f)), token)
                }
        }.onFailure { Log.w(TAG, "Subida inicial fallida: ${it.message}") }
    }

    private fun pushWatchlist(change: MyListChange) {
        val profileId = change.profileId
        if (session(profileId) == null) return
        val path = if (change.added) "/sync/watchlist" else "/sync/watchlist/remove"
        scope.launch {
            val token = validToken(profileId) ?: return@launch
            runCatching { client.post(path, watchlistBody(listOf(change.type to change.id)), token) }
                .onSuccess { if (!it.ok) Log.w(TAG, "Watchlist $path: HTTP ${it.code}") }
                .onFailure { Log.w(TAG, "Watchlist $path fallida: ${it.message}") }
        }
    }

    private fun ratingBody(target: TraktRatingTarget, rating: Int?): JsonObject {
        val ratedAt = rating?.let { TraktClient.formatDate(System.currentTimeMillis()) }
        fun media(tmdbId: String) = buildJsonObject {
            put("ids", buildJsonObject { put("tmdb", tmdbId.toIntOrNull() ?: error("Invalid TMDB id")) })
            rating?.let { put("rating", it) }
            ratedAt?.let { put("rated_at", it) }
        }
        return when (target) {
            is TraktRatingTarget.Movie -> buildJsonObject {
                put("movies", buildJsonArray { add(media(target.tmdbId)) })
            }
            is TraktRatingTarget.Show -> buildJsonObject {
                put("shows", buildJsonArray { add(media(target.tmdbId)) })
            }
            is TraktRatingTarget.Episode -> buildJsonObject {
                put("shows", buildJsonArray {
                    add(buildJsonObject {
                        put("ids", buildJsonObject {
                            put("tmdb", target.showTmdbId.toIntOrNull() ?: error("Invalid TMDB id"))
                        })
                        put("seasons", buildJsonArray {
                            add(buildJsonObject {
                                put("number", target.season)
                                put("episodes", buildJsonArray {
                                    add(buildJsonObject {
                                        put("number", target.episode)
                                        rating?.let { put("rating", it) }
                                        ratedAt?.let { put("rated_at", it) }
                                    })
                                })
                            })
                        })
                    })
                })
            }
        }
    }

    private fun JsonObject.matches(target: TraktRatingTarget): Boolean = when (target) {
        is TraktRatingTarget.Movie -> obj("movie")?.obj("ids")?.int("tmdb")?.toString() == target.tmdbId
        is TraktRatingTarget.Show -> obj("show")?.obj("ids")?.int("tmdb")?.toString() == target.tmdbId
        is TraktRatingTarget.Episode -> {
            val episode = obj("episode")
            obj("show")?.obj("ids")?.int("tmdb")?.toString() == target.showTmdbId &&
                episode?.int("season") == target.season && episode.int("number") == target.episode
        }
    }

    private fun watchlistBody(items: List<Pair<MediaType, String>>): JsonObject {
        fun list(type: MediaType) = buildJsonArray {
            items.filter { it.first == type }.mapNotNull { it.second.toIntOrNull() }.forEach { id ->
                add(buildJsonObject { put("ids", buildJsonObject { put("tmdb", id) }) })
            }
        }
        return buildJsonObject {
            put("movies", list(MediaType.MOVIE))
            put("shows", list(MediaType.TV))
        }
    }

    private fun historyBody(watched: Map<String, WatchProgress>): JsonObject {
        val movies = buildJsonArray {
            watched.forEach { (key, p) ->
                val id = key.removePrefix("movie:").takeIf { key.startsWith("movie:") }?.toIntOrNull() ?: return@forEach
                add(buildJsonObject {
                    put("ids", buildJsonObject { put("tmdb", id) })
                    put("watched_at", TraktClient.formatDate(p.updatedAt))
                })
            }
        }
        val episodes = watched.mapNotNull { (key, p) ->
            val parts = key.split(':')
            if (parts.size != 4 || parts[0] != "tv") return@mapNotNull null
            val show = parts[1].toIntOrNull() ?: return@mapNotNull null
            Triple(show, (parts[2].toIntOrNull() ?: return@mapNotNull null) to (parts[3].toIntOrNull() ?: return@mapNotNull null), p)
        }
        val shows = buildJsonArray {
            episodes.groupBy { it.first }.forEach { (show, eps) ->
                add(buildJsonObject {
                    put("ids", buildJsonObject { put("tmdb", show) })
                    put("seasons", buildJsonArray {
                        eps.groupBy { it.second.first }.forEach { (season, list) ->
                            add(buildJsonObject {
                                put("number", season)
                                put("episodes", buildJsonArray {
                                    list.forEach { (_, se, p) ->
                                        add(buildJsonObject {
                                            put("number", se.second)
                                            put("watched_at", TraktClient.formatDate(p.updatedAt))
                                        })
                                    }
                                })
                            })
                        }
                    })
                })
            }
        }
        return buildJsonObject {
            put("movies", movies)
            put("shows", shows)
        }
    }

    private suspend fun removePlayback(profileId: String, key: String) {
        val token = validToken(profileId) ?: return
        runCatching {
            val id = playbackIds.remove(key) ?: findPlaybackId(key, token) ?: return
            client.delete("/sync/playback/$id", token)
        }.onFailure { Log.w(TAG, "No se pudo quitar de Trakt: ${it.message}") }
    }

    private suspend fun findPlaybackId(key: String, token: String): Long? {
        val kind = if (key.startsWith("movie:")) "movies" else "episodes"
        val response = client.get("/sync/playback/$kind", token)
        if (!response.ok) return null
        return (response.body as? JsonArray).orEmpty()
            .map { it.jsonObject }
            .firstOrNull { keyOf(it) == key }
            ?.long("id")
    }

    // ------------------------------------------------------------------ scrobble

    override fun scrobble(progressKey: String, fraction: Float, action: ScrobbleAction) {
        val profileId = profiles.active.value.id
        if (session(profileId) == null) return
        val item = itemBody(progressKey) ?: return
        val percent = (fraction * 100f).coerceIn(0f, 100f)
        scope.launch {
            scrobbleLocks.getOrPut(profileId) { Mutex() }.withLock {
                val token = validToken(profileId) ?: return@withLock
                val path = when (action) {
                    ScrobbleAction.START -> "/scrobble/start"
                    ScrobbleAction.PAUSE -> "/scrobble/pause"
                    ScrobbleAction.STOP -> "/scrobble/stop"
                }
                runCatching { client.post(path, item.with("progress", JsonPrimitive(percent)), token) }
                    .onSuccess { if (!it.ok && it.code != 409) Log.w(TAG, "Scrobble $action: HTTP ${it.code}") }
                    .onFailure { Log.w(TAG, "Scrobble $action fallido: ${it.message}") }
                playbackIds.remove(progressKey)
            }
        }
    }

    override fun setWatched(progressKeys: Collection<String>, watched: Boolean) {
        val profileId = profiles.active.value.id
        if (session(profileId) == null || progressKeys.isEmpty()) return
        val now = System.currentTimeMillis()
        val wasResumable = if (watched) progressKeys.filter { progress.progress.value[it]?.isResumable == true } else emptyList()
        val body = historyBody(progressKeys.associateWith { WatchProgress(positionMs = 1, durationMs = 1, updatedAt = now) })
        val path = if (watched) "/sync/history" else "/sync/history/remove"
        scope.launch {
            val token = validToken(profileId) ?: return@launch
            runCatching { client.post(path, body, token) }
                .onSuccess { if (!it.ok) Log.w(TAG, "Historial $path: HTTP ${it.code}") }
                .onFailure { Log.w(TAG, "Historial $path fallido: ${it.message}") }
            wasResumable.forEach { key ->
                delay(POST_SPACING_MS)
                removePlayback(profileId, key)
            }
        }
    }

    private fun itemBody(key: String): JsonObject? {
        val parts = key.split(':')
        return when {
            parts.size == 2 && parts[0] == "movie" -> {
                val id = parts[1].toIntOrNull() ?: return null
                buildJsonObject { put("movie", buildJsonObject { put("ids", buildJsonObject { put("tmdb", id) }) }) }
            }
            parts.size == 4 && parts[0] == "tv" -> {
                val id = parts[1].toIntOrNull() ?: return null
                val season = parts[2].toIntOrNull() ?: return null
                val episode = parts[3].toIntOrNull() ?: return null
                buildJsonObject {
                    put("show", buildJsonObject { put("ids", buildJsonObject { put("tmdb", id) }) })
                    put("episode", buildJsonObject {
                        put("season", season)
                        put("number", episode)
                    })
                }
            }
            else -> null
        }
    }

    private fun keyOf(item: JsonObject): String? {
        item.obj("movie")?.obj("ids")?.int("tmdb")?.let { return WatchProgress.movieKey(it.toString()) }
        val show = item.obj("show")?.obj("ids")?.int("tmdb") ?: return null
        val episode = item.obj("episode") ?: return null
        return WatchProgress.episodeKey(show.toString(), episode.int("season") ?: return null, episode.int("number") ?: return null)
    }

    private fun runtimeMs(item: JsonObject): Long? {
        val minutes = (item.obj("episode") ?: item.obj("movie"))?.int("runtime") ?: return null
        return if (minutes > 0) minutes * 60_000L else null
    }

    // ------------------------------------------------------------------ tokens

    private fun session(profileId: String): TraktSession? =
        tokens.getSecret(TraktSession.storeKey(profileId))?.let { raw ->
            runCatching { json.decodeFromString(TraktSession.serializer(), raw) }.getOrNull()
        }

    private fun saveSession(profileId: String, body: JsonObject, username: String) {
        val createdAt = body.long("created_at")?.times(1000) ?: System.currentTimeMillis()
        val session = TraktSession(
            accessToken = body.string("access_token")!!,
            refreshToken = body.string("refresh_token").orEmpty(),
            expiresAt = createdAt + (body.long("expires_in") ?: DEFAULT_EXPIRES_S) * 1000,
            username = username,
        )
        tokens.setSecret(TraktSession.storeKey(profileId), json.encodeToString(TraktSession.serializer(), session))
    }

    private suspend fun validToken(profileId: String): String? = tokenLocks.getOrPut(profileId) { Mutex() }.withLock {
        // Otro pudo renovar mientras esperábamos: se vuelve a leer ya dentro del candado.
        val s = session(profileId) ?: return@withLock null
        if (s.expiresAt - System.currentTimeMillis() > REFRESH_MARGIN_MS) return@withLock s.accessToken
        val response = runCatching {
            client.post(
                "/oauth/token",
                buildJsonObject {
                    put("refresh_token", s.refreshToken)
                    put("client_id", credentials.clientId)
                    putSecret()
                    put("redirect_uri", credentials.redirectUri)
                    put("grant_type", "refresh_token")
                },
                null,
            )
        }.getOrElse { return@withLock s.accessToken.takeIf { s.expiresAt > System.currentTimeMillis() } }
        when {
            response.ok -> {
                // El refresh token es de un solo uso: se guarda siempre el nuevo.
                saveSession(profileId, response.body!!.jsonObject, s.username)
                session(profileId)?.accessToken
            }
            // Solo se olvida si la respuesta corresponde a la sesión que sigue guardada. Una respuesta
            // antigua no puede borrar una sesión renovada o enlazada mientras estaba en vuelo.
            response.code == 400 || response.code == 401 -> {
                val current = session(profileId)
                if (current?.refreshToken == s.refreshToken) {
                    forget(profileId)
                    null
                } else {
                    current?.takeIf { it.expiresAt > System.currentTimeMillis() }?.accessToken
                }
            }
            else -> s.accessToken.takeIf { s.expiresAt > System.currentTimeMillis() }
        }
    }

    private fun forget(profileId: String) {
        cleanup.clearTrakt(profileId)
        profiles.profiles.value.firstOrNull { it.id == profileId }?.let { profiles.save(it.copy(traktUser = null)) }
    }

    private suspend fun fetchUsername(token: String): String? = runCatching {
        client.get("/users/settings", token).body?.jsonObject?.obj("user")?.string("username")
    }.getOrNull()

    private companion object {
        const val TAG = "Trakt"
        const val MIN_SYNC_INTERVAL_MS = 2 * 60_000L
        const val REFRESH_MARGIN_MS = 60 * 60_000L
        const val DEFAULT_EXPIRES_S = 24 * 3_600L
        const val CLOCK_SLACK_MS = 2_000L
        const val POST_SPACING_MS = 1_100L
        const val MAX_UPLOAD_RESUMABLE = 20
        const val RECOMMENDATIONS_LIMIT = 40
        const val RATINGS_PAGE_LIMIT = 1000
    }
}

// ------------------------------------------------------------------ JSON
private fun JsonObject.obj(name: String): JsonObject? = (this[name] as? JsonObject)
private fun JsonObject.array(name: String): JsonArray = (this[name] as? JsonArray) ?: JsonArray(emptyList())
private fun JsonObject.prim(name: String): JsonPrimitive? = this[name] as? JsonPrimitive
private fun JsonObject.string(name: String): String? = prim(name)?.contentOrNull
private fun JsonObject.int(name: String): Int? = prim(name)?.intOrNull
private fun JsonObject.long(name: String): Long? = prim(name)?.longOrNull
private fun JsonObject.double(name: String): Double? = prim(name)?.doubleOrNull
private fun JsonObject.with(name: String, value: JsonElement) = JsonObject(this + (name to value))
