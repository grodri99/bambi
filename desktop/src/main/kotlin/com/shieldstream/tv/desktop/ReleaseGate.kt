package com.shieldstream.tv.desktop

import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.network.dto.TmdbReleaseDatesDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/**
 * «Ocultar lo que aún no se puede ver en España»: una película cuenta como disponible cuando ya ha salido en digital,
 * en físico o en la tele en España (TMDB guarda las fechas por país y tipo). Es lo que suele traer el castellano. Si
 * TMDB no tiene fechas de España, vale la de cualquier país. Las de hace más de un año ni se miran, y las series
 * siempre se enseñan. Lo comprobado se guarda (las ya disponibles, un mes; las que no, un día).
 */
class ReleaseGate(
    private val fetch: suspend (Int) -> TmdbReleaseDatesDto,
    private val file: File? = File(ConfigStore.defaultFile().parentFile, "estrenos-digitales.json"),
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    @Serializable
    data class Entry(val available: Boolean, val checkedAt: Long)

    private val json = Json { ignoreUnknownKeys = true }
    private val cache: MutableMap<String, Entry> = load()
    private val limit = Semaphore(6)

    suspend fun filter(items: List<CatalogItem>): List<CatalogItem> = coroutineScope {
        items.map { item -> async { item.takeIf { isAvailable(it) } } }.awaitAll().filterNotNull()
    }

    suspend fun isAvailable(item: CatalogItem): Boolean {
        if (item.type != MediaType.MOVIE) return true
        val id = item.id.toIntOrNull() ?: return true
        val year = item.year ?: return true
        if (year < today().year - 1) return true
        val now = System.currentTimeMillis()
        synchronized(cache) { cache[item.id] }?.let { e ->
            val ttl = if (e.available) MONTH_MS else DAY_MS
            if (now - e.checkedAt < ttl) return e.available
        }
        // Si TMDB no contesta, se enseña (mejor una carátula de más que un Inicio vacío).
        val dates = runCatching { limit.withPermit { fetch(id) } }.getOrNull() ?: return true
        val available = available(dates, today())
        synchronized(cache) { cache[item.id] = Entry(available, now) }
        save()
        return available
    }

    private fun load(): MutableMap<String, Entry> = runCatching {
        json.decodeFromString(MapSerializer(String.serializer(), Entry.serializer()), file!!.readText()).toMutableMap()
    }.getOrDefault(HashMap())

    private fun save() {
        val f = file ?: return
        runCatching {
            val copy = synchronized(cache) { HashMap(cache) }
            f.parentFile?.mkdirs()
            f.writeText(json.encodeToString(MapSerializer(String.serializer(), Entry.serializer()), copy))
        }
    }

    companion object {
        private const val DAY_MS = 24 * 3600_000L
        private const val MONTH_MS = 30 * DAY_MS
        private val HOME_TYPES = setOf(4, 5, 6)

        /** Lo que tarda de normal en llegar a digital en España tras el estreno en cines (TMDB no siempre lo apunta). */
        private const val CINEMA_TO_HOME_DAYS = 90L

        /**
         * Ya ha salido en digital, físico o tele en España; o se estrenó en cines en España hace más de tres meses; o,
         * sin datos de España, ha salido en digital en algún sitio.
         */
        fun available(dates: TmdbReleaseDatesDto, today: LocalDate): Boolean {
            fun date(d: com.shieldstream.tv.network.dto.TmdbReleaseDateDto) = runCatching { LocalDate.parse(d.release_date.take(10)) }.getOrNull()
            fun released(country: com.shieldstream.tv.network.dto.TmdbReleaseCountryDto) = country.release_dates.any { d ->
                d.type in HOME_TYPES && date(d)?.let { !it.isAfter(today) } == true
            }
            val spain = dates.results.firstOrNull { it.iso_3166_1 == "ES" }
            if (spain == null || spain.release_dates.isEmpty()) return dates.results.any { released(it) }
            if (released(spain)) return true
            val cinemas = spain.release_dates.filter { it.type in 1..3 }.mapNotNull { date(it) }.minOrNull()
            return cinemas != null && !cinemas.plusDays(CINEMA_TO_HOME_DAYS).isAfter(today)
        }
    }
}
