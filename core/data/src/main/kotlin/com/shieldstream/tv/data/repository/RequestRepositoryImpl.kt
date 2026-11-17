package com.shieldstream.tv.data.repository

import android.content.Context
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.RequestRepository
import com.shieldstream.tv.domain.TitleRequest
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val profiles: ProfileRepository,
    private val myList: MyListRepository,
) : RequestRepository {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _requests = MutableStateFlow(load())
    override val requests: StateFlow<List<TitleRequest>> = _requests.asStateFlow()

    override fun request(item: CatalogItem) {
        val profile = profiles.active.value
        if (!profile.kids) return // Un adulto ya puede verlo todo: no hay nada que pedir.
        val id = requestId(profile.id, item)
        if (_requests.value.any { it.id == id }) return
        val next = _requests.value + TitleRequest(
            id = id,
            profileId = profile.id,
            profileName = profile.name,
            type = item.type,
            titleId = item.id,
            title = item.title,
            posterUrl = item.posterUrl,
            requestedAt = System.currentTimeMillis(),
        )
        store(next)
    }

    override fun isPending(item: CatalogItem): Boolean =
        _requests.value.any { it.id == requestId(profiles.active.value.id, item) }

    override fun isApproved(item: CatalogItem): Boolean =
        titleKey(item.type, item.id) in approvedFor(profiles.active.value.id)

    override fun approve(id: String) {
        val request = _requests.value.firstOrNull { it.id == id } ?: return
        myList.addFor(request.profileId, request.type, request.titleId)
        val approved = approvedFor(request.profileId) + titleKey(request.type, request.titleId)
        prefs.edit().putStringSet(KEY_APPROVED_PREFIX + request.profileId, approved).apply()
        store(_requests.value.filterNot { it.id == id })
    }

    private fun approvedFor(profileId: String): Set<String> =
        prefs.getStringSet(KEY_APPROVED_PREFIX + profileId, emptySet()).orEmpty().toSet()

    private fun titleKey(type: MediaType, id: String) = "${type.name}:$id"

    private fun requestId(profileId: String, item: CatalogItem) = "$profileId:${item.type.name}:${item.id}"

    override fun dismiss(id: String) {
        if (_requests.value.none { it.id == id }) return
        store(_requests.value.filterNot { it.id == id })
    }

    private fun store(list: List<TitleRequest>) {
        prefs.edit().putString(KEY_REQUESTS, json.encodeToString(list.map { StoredRequest.from(it) })).apply()
        _requests.value = list
    }

    private fun load(): List<TitleRequest> = prefs.getString(KEY_REQUESTS, null)?.let { raw ->
        runCatching { json.decodeFromString<List<StoredRequest>>(raw) }.getOrNull()
    }.orEmpty().map { it.toRequest() }.sortedByDescending { it.requestedAt }

    @Serializable
    private data class StoredRequest(
        val id: String,
        val profileId: String,
        val profileName: String,
        val type: MediaType,
        val titleId: String,
        val title: String,
        val posterUrl: String? = null,
        val requestedAt: Long,
    ) {
        fun toRequest() = TitleRequest(id, profileId, profileName, type, titleId, title, posterUrl, requestedAt)

        companion object {
            fun from(r: TitleRequest) = StoredRequest(
                id = r.id,
                profileId = r.profileId,
                profileName = r.profileName,
                type = r.type,
                titleId = r.titleId,
                title = r.title,
                posterUrl = r.posterUrl,
                requestedAt = r.requestedAt,
            )
        }
    }

    private companion object {
        const val PREFS = "title_requests"
        const val KEY_REQUESTS = "requests"
        const val KEY_APPROVED_PREFIX = "approved_"
    }
}
