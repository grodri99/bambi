package com.shieldstream.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.domain.StremioAddons
import kotlinx.coroutines.launch

/** Lo que dice el manifest de un addon. */
data class AddonInfo(val name: String?, val logoUrl: String?, val needsConfiguration: Boolean, val hasStreams: Boolean)

/**
 * Gestor de addons de Stremio, como en la tele: cada uno con su nombre y logo, probar, cambiar o quitar. Al añadir
 * uno se lee su manifest: si el addon exige configurarlo antes (configurationRequired), se dice claramente con el
 * enlace a su página de configuración, en vez de un «no responde».
 */
@Composable
fun AddonsSection(raw: String, repository: DesktopRepository, onChange: (String) -> Unit) {
    Section("Addons de Stremio", "Opcional: más fuentes con tus addons (la URL del manifest), como en la tele.")
    val addons = remember(raw) { StremioAddons.parse(raw) }
    val scope = rememberCoroutineScope()
    val tests = remember { mutableStateMapOf<String, String>() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        addons.forEach { addon ->
            val info by produceState<AddonInfo?>(null, addon.manifestUrl) { value = runCatching { repository.addonInfo(addon.manifestUrl) }.getOrNull() }
            Row(
                Modifier.fillMaxWidth().background(BambiColors.Surface, BambiShapes.Control).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(BambiColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                    Text((info?.name ?: addon.host).take(1).uppercase(), color = BambiColors.Muted, fontWeight = FontWeight.Bold)
                    info?.logoUrl?.let { RemoteImage(it, Modifier.size(40.dp)) }
                }
                Column(Modifier.weight(1f)) {
                    Text(info?.name ?: addon.host, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val note = tests[addon.id] ?: when {
                        !addon.isValid -> "No es una URL de manifest (debe acabar en /manifest.json)"
                        info?.needsConfiguration == true -> "Hay que configurarlo primero en su web"
                        info != null && !info!!.hasStreams -> "No da fuentes (solo catálogos)"
                        else -> addon.host
                    }
                    Text(note, color = BambiColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (info?.needsConfiguration == true) {
                    TextButton(onClick = { addon.baseUrl?.let { openInBrowser("$it/configure") } }) { Text("Configurar", color = BambiColors.Accent) }
                }
                TextButton(onClick = {
                    tests[addon.id] = "Probando…"
                    scope.launch {
                        tests[addon.id] = runCatching { repository.testAddon(addon.manifestUrl) }
                            .fold({ n -> if (n > 0) "Funciona: $n fuentes de prueba" else "Responde, pero sin fuentes de prueba" }, { "No responde ahora mismo" })
                    }
                }) { Text("Probar", color = Color.White) }
                TextButton(onClick = { onChange(StremioAddons.serialize(addons.filterNot { it.id == addon.id })) }) { Text("Quitar", color = BambiColors.Muted) }
            }
        }
        var adding by remember { mutableStateOf("") }
        var problem by remember { mutableStateOf<String?>(null) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = adding,
                onValueChange = { adding = it.trim(); problem = null },
                label = { Text("Añadir addon (URL del manifest)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                val parsed = StremioAddons.parse(adding).firstOrNull()
                when {
                    parsed == null || !parsed.isValid -> problem = "Pega la URL que acaba en /manifest.json."
                    addons.any { it.id == parsed.id } -> problem = "Ese addon ya está."
                    else -> scope.launch {
                        val info = runCatching { repository.addonInfo(parsed.manifestUrl) }.getOrNull()
                        problem = when {
                            info == null -> "No se pudo leer el addon. Revisa la URL."
                            info.needsConfiguration -> "Este addon hay que configurarlo primero en su web (${parsed.baseUrl}/configure). Copia luego la URL que te da."
                            else -> {
                                onChange(StremioAddons.serialize(addons + parsed))
                                adding = ""
                                null
                            }
                        }
                    }
                }
            }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Añadir") }
        }
        problem?.let { Text(it, color = BambiColors.Accent, fontSize = 13.sp) }
    }
}
