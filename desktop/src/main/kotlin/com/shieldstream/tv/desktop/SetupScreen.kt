package com.shieldstream.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.domain.DebridService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Dónde sacar la clave de cada servicio (lo que abre el QR y el botón). */
fun keyPage(service: DebridService): String = when (service) {
    DebridService.REAL_DEBRID -> "https://real-debrid.com/apitoken"
    DebridService.TORBOX -> "https://torbox.app/settings"
    DebridService.PREMIUMIZE -> "https://www.premiumize.me/account"
    DebridService.ALLDEBRID -> "https://alldebrid.com/apikeys"
    DebridService.DEBRID_LINK -> "https://debrid-link.com/webapp/apikey"
    DebridService.OFFCLOUD -> "https://offcloud.com/#/account"
}

const val TMDB_KEY_PAGE = "https://www.themoviedb.org/settings/api"

/**
 * Guía del primer arranque, como en la tele: bienvenida, clave de TMDB y servicio debrid, cada paso con su QR (para
 * sacar la clave desde el móvil) y comprobando la clave al momento. Al terminar, al Inicio.
 */
@Composable
fun SetupScreen(config: ConfigStore, repository: DesktopRepository, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf(config.current) }
    var status by remember { mutableStateOf<KeysCheck?>(null) }
    fun update(next: DesktopConfig) {
        draft = next
        config.save(next)
    }
    LaunchedEffect(draft.tmdbKey, draft.debridKey, draft.debrid) {
        if (draft.tmdbKey.isBlank() && draft.debridKey.isBlank()) return@LaunchedEffect
        delay(700)
        status = runCatching { withContext(Dispatchers.IO) { repository.checkKeys(draft) } }.getOrNull()
    }
    Box(
        Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(BambiColors.Accent.copy(alpha = 0.18f), BambiColors.Background),
                radius = 1400f,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(780.dp).background(BambiColors.Surface.copy(alpha = 0.96f), BambiShapes.Panel)
                .border(1.dp, BambiColors.Stroke, BambiShapes.Panel).padding(44.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BundledImages.get("bambi_logo.png")?.let { androidx.compose.foundation.Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
                Spacer(Modifier.weight(1f))
                // Indicador de pasos: el actual alargado y del color de Bambi.
                repeat(4) { i ->
                    Box(
                        Modifier.height(6.dp).width(if (i == step) 28.dp else 6.dp)
                            .background(if (i <= step) BambiColors.Accent else BambiColors.SurfaceHigh, BambiShapes.Pill),
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
            when (step) {
                0 -> {
                    Title("Bienvenido a Bambi")
                    Body(
                        "Tu catálogo de películas y series con tu servicio debrid (Real-Debrid, TorBox, AllDebrid…). " +
                            "Solo necesitas dos claves: la de TMDB, que es gratis, y la de tu servicio. Son dos minutos.",
                    )
                }
                1 -> {
                    Title("Clave de TMDB")
                    Body("Crea una cuenta gratis en themoviedb.org, ve a Ajustes → API y copia la «Clave de la API».")
                    KeyStep(TMDB_KEY_PAGE, "Abrir themoviedb.org") {
                        OutlinedTextField(
                            value = draft.tmdbKey,
                            onValueChange = { update(draft.copy(tmdbKey = it.trim())) },
                            label = { Text("Clave de TMDB") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        status?.tmdb?.takeIf { draft.tmdbKey.isNotBlank() }?.let { StatusLine(it) }
                    }
                }
                2 -> {
                    Title("Tu servicio debrid")
                    Body("Elige el que usas y pega su clave. Con AllDebrid también puedes conectar con un código.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DebridService.entries.forEach { service ->
                            FilterChip(selected = draft.debridService == service, onClick = { update(draft.copy(debrid = service.name)) }, label = { Text(service.title) })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    KeyStep(keyPage(draft.debridService), "Dónde está mi clave de ${draft.debridService.title}") {
                        OutlinedTextField(
                            value = draft.debridKey,
                            onValueChange = { update(draft.copy(debridKey = it.trim())) },
                            label = { Text("Clave de ${draft.debridService.title}") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (draft.debridService == DebridService.ALLDEBRID) AllDebridConnect { update(draft.copy(debridKey = it)) }
                        status?.debrid?.takeIf { draft.debridKey.isNotBlank() }?.let { StatusLine(it) }
                    }
                }
                else -> {
                    Title("Todo listo")
                    Body("Ya puedes ver lo que quieras. Trakt, addons, perfiles y lo demás está en Ajustes (el engranaje de arriba).")
                    status?.let {
                        StatusLine(it.tmdb)
                        StatusLine(it.debrid)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) OutlinedButton(onClick = { step-- }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Atrás") }
                Spacer(Modifier.weight(1f))
                val canGo = when (step) {
                    1 -> draft.tmdbKey.isNotBlank()
                    2 -> draft.debridKey.isNotBlank()
                    else -> true
                }
                Button(onClick = { if (step < 3) step++ else onDone() }, enabled = canGo) {
                    Text(if (step < 3) "Continuar" else "Empezar a ver")
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)

@Composable
private fun Body(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, color = BambiColors.Text.copy(alpha = 0.85f), fontSize = 16.sp, lineHeight = 23.sp)
    Spacer(Modifier.height(18.dp))
}

/** Un paso: el campo para pegar la clave y el enlace a la página donde se saca. */
@Composable
private fun KeyStep(url: String, linkLabel: String, fields: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        fields()
        LinkButton(linkLabel, url)
    }
}
