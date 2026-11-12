package com.shieldstream.tv.data.repository

import android.content.Context
import android.util.Log
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.FeaturedBadge
import com.shieldstream.tv.model.FeaturedItem
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.RowStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Última versión del Inicio (filas de TMDB y destacados) guardada en la Shield. Al abrir la app se enseña al
 * instante en vez de esperar a la red, y por detrás se actualiza (como hace Netflix).
 *
 * Solo guarda filas de catálogo: "Seguir viendo", episodios nuevos, recomendaciones, etc. se calculan aparte.
 */
@Singleton
class HomeDiskCache @Inject constructor(@ApplicationContext context: Context) {

    class Entry<T>(val savedAt: Long, val value: T)

    private val dir = File(context.filesDir, "home_cache")

    fun readRows(key: String): Entry<List<CatalogRow>>? = read(key) { json ->
        json.getJSONArray("rows").objects().map { it.toRow() }
    }

    fun writeRows(key: String, rows: List<CatalogRow>) = write(key) {
        put("rows", JSONArray().apply { rows.forEach { put(it.toJson()) } })
    }

    fun readFeatured(key: String): Entry<List<FeaturedItem>>? = read(key) { json ->
        json.getJSONArray("featured").objects().map { it.toFeatured() }
    }

    fun writeFeatured(key: String, items: List<FeaturedItem>) = write(key) {
        put("featured", JSONArray().apply { items.forEach { put(it.toJson()) } })
    }

    private fun <T> read(key: String, parse: (JSONObject) -> T): Entry<T>? = runCatching {
        val file = file(key)
        if (!file.isFile) return null
        val json = JSONObject(file.readText())
        if (json.optInt("v") != VERSION) return null
        Entry(json.getLong("savedAt"), parse(json))
    }.onFailure { Log.w(TAG, "No se pudo leer la caché $key: ${it.message}") }.getOrNull()

    private fun write(key: String, fill: JSONObject.() -> Unit) {
        runCatching {
            dir.mkdirs()
            val json = JSONObject().apply {
                put("v", VERSION)
                put("savedAt", System.currentTimeMillis())
                fill()
            }
            // Se escribe aparte y se renombra: si la app se cierra a medias no queda un archivo roto.
            val tmp = File(dir, "${safe(key)}.tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file(key))) {
                file(key).delete()
                tmp.renameTo(file(key))
            }
        }.onFailure { Log.w(TAG, "No se pudo guardar la caché $key: ${it.message}") }
    }

    private fun file(key: String) = File(dir, "${safe(key)}.json")

    private fun safe(key: String) = key.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun CatalogRow.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("style", style.name)
        put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
    }

    private fun JSONObject.toRow() = CatalogRow(
        id = getString("id"),
        title = getString("title"),
        items = getJSONArray("items").objects().map { it.toItem() },
        style = RowStyle.valueOf(getString("style")),
    )

    private fun FeaturedItem.toJson() = JSONObject().apply {
        put("item", item.toJson())
        put("badge", badge.name)
        putOpt("logoUrl", logoUrl)
        putOpt("genre", genre)
        putOpt("durationText", durationText)
        putOpt("certification", certification)
    }

    private fun JSONObject.toFeatured() = FeaturedItem(
        item = getJSONObject("item").toItem(),
        badge = FeaturedBadge.valueOf(getString("badge")),
        logoUrl = str("logoUrl"),
        genre = str("genre"),
        durationText = str("durationText"),
        certification = str("certification"),
    )

    private fun CatalogItem.toJson() = JSONObject().apply {
        put("id", id)
        putOpt("imdbId", imdbId)
        put("type", type.name)
        put("title", title)
        put("overview", overview)
        putOpt("posterUrl", posterUrl)
        putOpt("backdropUrl", backdropUrl)
        year?.let { put("year", it) }
        rating?.let { put("rating", it.toDouble()) }
        put("genres", JSONArray(genres))
    }

    private fun JSONObject.toItem() = CatalogItem(
        id = getString("id"),
        imdbId = str("imdbId"),
        type = MediaType.valueOf(getString("type")),
        title = getString("title"),
        overview = optString("overview"),
        posterUrl = str("posterUrl"),
        backdropUrl = str("backdropUrl"),
        year = if (has("year")) getInt("year") else null,
        rating = if (has("rating")) getDouble("rating").toFloat() else null,
        genres = optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
    )

    /** optString devuelve "null" (texto) para valores nulos: aquí se devuelve null de verdad. */
    private fun JSONObject.str(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null

    private companion object {
        const val TAG = "HomeDiskCache"
        const val VERSION = 1
    }
}
