package com.shieldstream.tv.domain

import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import kotlinx.coroutines.flow.StateFlow

/**
 * Petición familiar: un perfil infantil pide un título que su catálogo no le deja ver (lo encuentra en Buscar) y
 * cualquier perfil adulto la aprueba o la descarta desde Ajustes. Los adultos ya pueden verlo todo, así que no piden.
 */
data class TitleRequest(
    val id: String,
    val profileId: String,
    val profileName: String,
    val type: MediaType,
    val titleId: String,
    val title: String,
    val posterUrl: String?,
    val requestedAt: Long,
)

interface RequestRepository {
    val requests: StateFlow<List<TitleRequest>>

    /** Pide el título para el perfil activo. No duplica peticiones del mismo perfil y título. */
    fun request(item: CatalogItem)

    /** El perfil activo ya lo ha pedido y está pendiente. */
    fun isPending(item: CatalogItem): Boolean

    /** Títulos aprobados para el perfil activo: dejan de estar bloqueados en su búsqueda. */
    fun isApproved(item: CatalogItem): Boolean

    /** Aprueba la petición: el título queda permitido para ese perfil, entra en su Mi lista y sale de la lista. */
    fun approve(id: String)

    fun dismiss(id: String)
}
