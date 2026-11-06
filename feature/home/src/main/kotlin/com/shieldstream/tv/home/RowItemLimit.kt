package com.shieldstream.tv.home

import com.shieldstream.tv.model.CatalogRow

internal fun CatalogRow.withItemLimit(maxItems: Int): CatalogRow {
    if (items.size <= maxItems) return this
    val limitedItems = items.take(maxItems)
    val visibleKeys = limitedItems.mapTo(mutableSetOf()) { it.key }
    return copy(
        items = limitedItems,
        progress = progress.filterKeys { it in visibleKeys },
        newEpisodes = newEpisodes.filterKeys { it in visibleKeys },
        newEpisodeCounts = newEpisodeCounts.filterKeys { it in visibleKeys },
        upNext = upNext.filterKeys { it in visibleKeys },
    )
}
