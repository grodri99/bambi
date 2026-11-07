package com.shieldstream.tv.watchnext

import android.net.Uri
import com.shieldstream.tv.domain.ContinueEntry
import com.shieldstream.tv.model.CatalogItem

internal data class LauncherEntry(
    val profileId: String,
    val item: CatalogItem,
    val entry: ContinueEntry,
) {
    val internalId: String
        get() = launcherInternalId(profileId, entry)

    fun deepLink(): Uri = Uri.Builder()
        .scheme("shieldstream")
        .authority("resume")
        .appendPath(item.type.name.lowercase())
        .appendPath(item.id)
        .appendQueryParameter("profile", profileId)
        .apply {
            entry.season?.let { appendQueryParameter("season", it.toString()) }
            entry.episode?.let { appendQueryParameter("episode", it.toString()) }
        }
        .build()
}

internal fun launcherInternalId(profileId: String, entry: ContinueEntry): String = buildString {
    append(profileId)
    append(':').append(entry.type.name)
    append(':').append(entry.id)
    entry.season?.let { append(':').append(it) }
    entry.episode?.let { append(':').append(it) }
}
