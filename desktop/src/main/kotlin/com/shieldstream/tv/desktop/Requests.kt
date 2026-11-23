package com.shieldstream.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.model.CatalogItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Petición de un perfil infantil: un título que quiere ver y que tiene que aprobar un adulto. */
@Serializable
data class FamilyRequest(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val profileName: String,
    val item: SavedItem,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Peticiones familiares (como en la tele): el perfil infantil pide un título de fuera de su catálogo; un adulto lo
 * aprueba en Ajustes y aparece en Mi lista de quien lo pidió, o lo rechaza.
 */
class RequestStore(private val file: File = File(ConfigStore.defaultFile().parentFile, "requests.json")) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    var pending by mutableStateOf(load())
        private set

    fun isRequested(profileId: String, item: CatalogItem) =
        pending.any { it.profileId == profileId && it.item.id == item.id && it.item.type == item.type.name }

    fun request(profile: DesktopProfile, item: CatalogItem) {
        if (isRequested(profile.id, item)) return
        write(pending + FamilyRequest(profileId = profile.id, profileName = profile.name, item = SavedItem.of(item)))
    }

    /** Aprobar: entra en Mi lista del perfil que lo pidió. */
    fun approve(request: FamilyRequest, profiles: ProfileStore) {
        profiles.library(request.profileId).importMyList(listOf(request.item.copy(addedAt = System.currentTimeMillis())), emptyList())
        write(pending.filterNot { it.id == request.id })
    }

    fun reject(request: FamilyRequest) = write(pending.filterNot { it.id == request.id })

    private fun write(list: List<FamilyRequest>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(ListSerializer(FamilyRequest.serializer()), list))
        pending = list
    }

    private fun load(): List<FamilyRequest> = runCatching {
        json.decodeFromString(ListSerializer(FamilyRequest.serializer()), file.readText())
    }.getOrDefault(emptyList())
}

/** Ajustes → Peticiones (solo en perfiles de adulto). */
@Composable
fun RequestsSection(requests: RequestStore, profiles: ProfileStore) {
    Section("Peticiones", "Lo que han pedido los perfiles infantiles. Al aprobarlo, aparece en su Mi lista.")
    if (requests.pending.isEmpty()) {
        Text("No hay peticiones pendientes.", color = BambiColors.Muted, fontSize = 14.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        requests.pending.sortedByDescending { it.createdAt }.forEach { r ->
            Row(
                Modifier.fillMaxWidth().background(BambiColors.Surface, BambiShapes.Control).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.width(54.dp).height(80.dp).clip(RoundedCornerShape(6.dp)).background(BambiColors.Placeholder)) {
                    RemoteImage(r.item.posterUrl, Modifier.width(54.dp).height(80.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(r.item.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Lo pide ${r.profileName}", color = BambiColors.Muted, fontSize = 13.sp)
                }
                OutlinedButton(onClick = { requests.reject(r) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Rechazar") }
                Button(onClick = { requests.approve(r, profiles) }) { Text("Aprobar") }
            }
        }
    }
}
