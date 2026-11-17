package com.shieldstream.tv.home

import androidx.compose.runtime.key
import androidx.compose.ui.input.key.key
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.ContinueInfo
import com.shieldstream.tv.model.FeaturedItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.RowStyle

// Inicio → «Reproduce algo»: elige al azar qué ver.

/**
 * "Sorpréndeme": un título al azar de lo que hay en el Inicio, sin "Seguir viendo" ni "Mi lista" (eso ya lo
 * conoces) y con buena nota, para que la sorpresa merezca la pena.
 */
internal fun surprisePick(rows: List<CatalogRow>, featured: List<FeaturedItem>, random: kotlin.random.Random = kotlin.random.Random): CatalogItem? {
    val pool = (rows.filter { it.style == RowStyle.POSTER || it.style == RowStyle.TOP10 }.flatMap { it.items } + featured.map { it.item })
        .filter { it.posterUrl != null }
        .distinctBy { it.key }
    val good = pool.filter { (it.rating ?: 0f) >= SURPRISE_MIN_RATING }
    return good.ifEmpty { pool }.randomOrNull(random)
}

private const val SURPRISE_MIN_RATING = 6.8f

/** Resultado de «Reproduce algo»: primero lo que toca seguir, después lo que has guardado. */
internal sealed interface PlaySomethingPick {
    data class UpNext(val item: CatalogItem, val episode: Episode) : PlaySomethingPick
    data class NewEpisode(val item: CatalogItem, val episode: Episode) : PlaySomethingPick
    data class Continue(val item: CatalogItem, val info: ContinueInfo) : PlaySomethingPick
    data class Title(val item: CatalogItem) : PlaySomethingPick
}

/**
 * «Reproduce algo»: elige al azar el siguiente episodio de una serie que sigues (`A continuación`), un
 * estreno reciente, algo de `Seguir viendo`, de `Mi lista` o, como último recurso, del propio Inicio.
 */
internal fun playSomethingPick(
    rows: List<CatalogRow>,
    featured: List<FeaturedItem>,
    random: kotlin.random.Random = kotlin.random.Random,
): PlaySomethingPick? {
    rows.firstOrNull { it.style == RowStyle.UP_NEXT }?.let { row ->
        row.items
            .mapNotNull { item -> row.upNext[item.key]?.let { item to it.episode } }
            .randomOrNull(random)
            ?.let { return PlaySomethingPick.UpNext(it.first, it.second) }
    }
    rows.firstOrNull { it.style == RowStyle.NEW_EPISODES }?.let { row ->
        row.items
            .mapNotNull { item -> row.newEpisodes[item.key]?.let { item to it } }
            .randomOrNull(random)
            ?.let { return PlaySomethingPick.NewEpisode(it.first, it.second) }
    }
    rows.firstOrNull { it.style == RowStyle.CONTINUE }?.let { row ->
        row.items
            .mapNotNull { item -> row.progress[item.key]?.let { item to it } }
            .randomOrNull(random)
            ?.let { return PlaySomethingPick.Continue(it.first, it.second) }
    }
    val pool = (
        rows.filter { it.style == RowStyle.MY_LIST }.flatMap { it.items } +
            rows.filter { it.style == RowStyle.POSTER || it.style == RowStyle.TOP10 }.flatMap { it.items } +
            featured.map { it.item }
        )
        .filter { it.posterUrl != null }
        .distinctBy { it.key }
    return pool.randomOrNull(random)?.let { PlaySomethingPick.Title(it) }
}
