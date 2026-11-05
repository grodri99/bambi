package com.shieldstream.tv.settings.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.domain.AppUpdate
import com.shieldstream.tv.domain.UpdateState
import com.shieldstream.tv.model.tr
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.tvClickable
import kotlinx.coroutines.delay

private val Panel = Color(0xFF1A1C27)
private val Muted = Color.White.copy(alpha = 0.6f)
private val Red = Color(0xFFE50914)

@Composable
fun UpdatePrompt(viewModel: UpdateViewModel = hiltViewModel()) {
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }
    val visible = prompt as? UpdatePromptUiState.Visible ?: return
    val context = LocalContext.current
    Dialog(
        onDismissRequest = viewModel::later,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .width(620.dp)
                .background(Panel, RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(32.dp),
        ) {
            when (val s = visible.state) {
                is UpdateState.Available -> UpdateBody(
                    title = tr("Hay una versión nueva: ${s.update.version}", "A new version is available: ${s.update.version}"),
                    subtitle = tr("Tienes la ${s.current}. Se actualiza sin perder perfiles, progreso ni claves.", "You have ${s.current}. Updating keeps your profiles, progress and keys."),
                    update = s.update,
                ) {
                    Buttons(primary = tr("Actualizar", "Update"), onPrimary = viewModel::update, secondary = tr("Ahora no", "Not now"), onSecondary = viewModel::later)
                }
                is UpdateState.Downloading -> UpdateBody(
                    title = tr("Descargando ${s.update.version}…", "Downloading ${s.update.version}…"),
                    subtitle = "${(s.progress * 100).toInt()} %",
                    update = null,
                ) {
                    ProgressBar(s.progress)
                    Spacer(Modifier.height(20.dp))
                    Buttons(primary = tr("Seguir en segundo plano", "Keep downloading in background"), onPrimary = viewModel::later)
                }
                is UpdateState.Installing -> UpdateBody(
                    title = tr("Listo para instalar", "Ready to install"),
                    subtitle = tr(
                        "Pulsa \"Instalar\" en la ventana de Android. Bambi se cerrará y al abrirla ya estará la ${s.update.version}.",
                        "Press \"Install\" in Android's window. Bambi will close and reopen with ${s.update.version}.",
                    ),
                    update = null,
                ) {
                    Buttons(primary = tr("Volver a abrir el instalador", "Open the installer again"), onPrimary = viewModel::update, secondary = tr("Cerrar", "Close"), onSecondary = viewModel::later)
                }
                is UpdateState.NeedsPermission -> UpdateBody(
                    title = tr("Falta un permiso (solo la primera vez)", "One permission needed (first time only)"),
                    subtitle = tr(
                        "Android pide permitir que Bambi instale apps. En la pantalla que se abre, activa Bambi y vuelve con Atrás.",
                        "Android asks to allow Bambi to install apps. In the screen that opens, turn Bambi on and come back with Back.",
                    ),
                    update = null,
                ) {
                    Buttons(
                        primary = tr("Abrir ajustes", "Open settings"),
                        onPrimary = { viewModel.openInstallPermission(context) },
                        secondary = tr("Ya está: actualizar", "Done: update"),
                        onSecondary = viewModel::update,
                    )
                }
                is UpdateState.Failed -> UpdateBody(
                    title = tr("No se pudo actualizar", "Couldn't update"),
                    subtitle = s.message,
                    update = null,
                ) {
                    Buttons(primary = tr("Reintentar", "Retry"), onPrimary = viewModel::update, secondary = tr("Ahora no", "Not now"), onSecondary = viewModel::later)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun UpdateBody(title: String, subtitle: String, update: AppUpdate?, actions: @Composable () -> Unit) {
    Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = Muted, fontSize = 17.sp)
    val notes = update?.notes?.let(::cleanNotes).orEmpty()
    if (notes.isNotBlank()) {
        Spacer(Modifier.height(18.dp))
        Text(tr("Novedades", "What's new"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(notes, color = Muted, fontSize = 16.sp, maxLines = 9, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(26.dp))
    actions()
}

private fun cleanNotes(raw: String): String = raw
    .substringBefore("### Instalar")
    .lines()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") }
    .joinToString("\n") { it.replace("**", "").replace("`", "") }

@Composable
private fun Buttons(primary: String, onPrimary: () -> Unit, secondary: String? = null, onSecondary: () -> Unit = {}) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(primary) {
        repeat(3) {
            runCatching { requester.requestFocus() }
            delay(100)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        TvButton(text = primary, onClick = onPrimary, modifier = Modifier.focusRequester(requester))
        if (secondary != null) TvButton(text = secondary, onClick = onSecondary)
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(8.dp)
                .background(Red, RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
fun UpdateCard(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val (title, subtitle) = when (val s = state) {
        UpdateState.Unsupported -> tr("Actualizaciones", "Updates") to
            tr("La app de pruebas no se actualiza sola: se instala desde el ordenador.", "The testing app doesn't self-update: it's installed from the computer.")
        UpdateState.Idle -> tr("Buscar actualizaciones", "Check for updates") to
            tr("Bambi mira sola si hay versión nueva al abrirse. OK para mirar ahora.", "Bambi checks for new versions when it opens. OK to check now.")
        UpdateState.Checking -> tr("Buscando actualizaciones…", "Checking for updates…") to ""
        is UpdateState.UpToDate -> tr("Estás al día", "You're up to date") to
            tr("Tienes la última versión (${s.current}). OK para volver a mirar.", "You have the latest version (${s.current}). OK to check again.")
        is UpdateState.Available -> tr("Versión nueva: ${s.update.version}", "New version: ${s.update.version}") to
            tr("OK para descargarla e instalarla.", "OK to download and install it.")
        is UpdateState.Downloading -> tr("Descargando ${s.update.version}…", "Downloading ${s.update.version}…") to "${(s.progress * 100).toInt()} %"
        is UpdateState.NeedsPermission -> tr("Falta permiso para instalar", "Install permission needed") to
            tr("OK abre la lista: activa Bambi y vuelve con Atrás.", "OK opens the list: turn Bambi on and come back with Back.")
        is UpdateState.Installing -> tr("Listo para instalar", "Ready to install") to
            tr("Pulsa \"Instalar\" en la ventana de Android.", "Press \"Install\" in Android's window.")
        is UpdateState.Failed -> tr("No se pudo actualizar", "Couldn't update") to s.message
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Color(0xFF2A2D3D) else Panel, RoundedCornerShape(14.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .tvClickable {
                when (val s = state) {
                    is UpdateState.Available -> viewModel.update()
                    is UpdateState.NeedsPermission -> if (!viewModel.openInstallPermission(context)) viewModel.update()
                    is UpdateState.Failed -> if (s.update != null) viewModel.update() else viewModel.checkNow()
                    UpdateState.Idle, is UpdateState.UpToDate -> viewModel.checkNow()
                    else -> Unit
                }
            }
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotEmpty()) Text(subtitle, color = Muted, fontSize = 15.sp)
        }
        if (state !is UpdateState.Unsupported) {
            Spacer(Modifier.width(16.dp))
            Text("OK ›", color = Muted, fontSize = 16.sp)
        }
    }
}
