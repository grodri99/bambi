package com.shieldstream.tv.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.domain.TraktAuthPoll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Copia un texto al portapapeles de Windows. */
fun copyToClipboard(text: String) {
    runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null) }
}

/** Abre una web en el navegador del PC (solo al pulsar un botón). */
fun openInBrowser(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}

private sealed interface Connect {
    data object Idle : Connect
    data object Loading : Connect
    data class Code(val userCode: String, val url: String, val secondsLeft: Int) : Connect
    data class Error(val message: String) : Connect
}

/**
 * Trakt en Ajustes: primero la aplicación propia (Client ID y Secret, como en la tele vienen en la compilación) y luego,
 * por perfil, conectar con código y QR, sincronizar o desconectar.
 */
@Composable
fun TraktSection(
    draft: DesktopConfig,
    onChange: (DesktopConfig) -> Unit,
    profile: DesktopProfile?,
    trakt: TraktManager?,
) {
    Section("Trakt", "Sincroniza «Seguir viendo», lo visto y Mi lista con la tele y con otros PC, por perfil.")
    if (draft.traktClientId.isBlank()) {
        Text(
            "1. Crea una aplicación gratis en trakt.tv/oauth/applications (Redirect URI: urn:ietf:wg:oauth:2.0:oob).\n" +
                "2. Copia aquí su Client ID y su Client Secret.",
            color = BambiColors.Muted,
            fontSize = 13.sp,
        )
        OutlinedButton(onClick = { openInBrowser("https://trakt.tv/oauth/applications/new") }, colors = whiteOutlined()) {
            Text("Abrir trakt.tv")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = draft.traktClientId,
            onValueChange = { onChange(draft.copy(traktClientId = it.trim())) },
            label = { Text("Client ID") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = draft.traktClientSecret,
            onValueChange = { onChange(draft.copy(traktClientSecret = it.trim())) },
            label = { Text("Client Secret") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.weight(1f),
        )
    }
    if (trakt == null || profile == null || draft.traktClientId.isBlank()) return
    if (trakt.isConnected(profile)) {
        Connected(profile, trakt)
    } else {
        ConnectFlow(profile, trakt)
    }
}

@Composable
private fun Connected(profile: DesktopProfile, trakt: TraktManager) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    StatusLine(KeyStatus(true, "«${profile.name}» está conectado a Trakt como ${profile.traktUser}"))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { trakt.syncNow(force = true) }, colors = whiteOutlined()) { Text("Sincronizar ahora") }
        OutlinedButton(
            onClick = {
                busy = true
                scope.launch { trakt.disconnect(profile.id); busy = false }
            },
            enabled = !busy,
            colors = whiteOutlined(),
        ) { Text("Desconectar") }
    }
}

@Composable
private fun ConnectFlow(profile: DesktopProfile, trakt: TraktManager) {
    var state by remember(profile.id) { mutableStateOf<Connect>(Connect.Idle) }
    var attempt by remember { mutableStateOf(0) }
    if (attempt > 0) {
        LaunchedEffect(attempt) {
            state = Connect.Loading
            val code = trakt.requestCode().getOrElse {
                state = Connect.Error(it.message ?: "No se pudo conectar con Trakt")
                return@LaunchedEffect
            }
            // Igual que la tele: se pregunta cada «intervalo» hasta que lo aceptes en Trakt o caduque el código.
            val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
            var interval = code.intervalSeconds.coerceAtLeast(1) * 1000L
            var nextPoll = System.currentTimeMillis() + interval
            while (true) {
                val now = System.currentTimeMillis()
                if (now >= deadline) {
                    state = Connect.Error("El código ha caducado. Pide otro.")
                    return@LaunchedEffect
                }
                state = Connect.Code(code.userCode, code.verificationUrl, ((deadline - now) / 1000).toInt())
                if (now >= nextPoll) {
                    when (val result = trakt.poll(profile.id, code)) {
                        is TraktAuthPoll.Connected -> {
                            state = Connect.Idle
                            return@LaunchedEffect
                        }
                        TraktAuthPoll.Pending -> Unit
                        TraktAuthPoll.SlowDown -> interval += 1000
                        TraktAuthPoll.Expired -> {
                            state = Connect.Error("El código ha caducado. Pide otro.")
                            return@LaunchedEffect
                        }
                        TraktAuthPoll.Denied -> {
                            state = Connect.Error("Has rechazado el acceso en Trakt.")
                            return@LaunchedEffect
                        }
                        is TraktAuthPoll.Failed -> {
                            state = Connect.Error(result.message)
                            return@LaunchedEffect
                        }
                    }
                    nextPoll = System.currentTimeMillis() + interval
                }
                delay(1000)
            }
        }
    }
    when (val s = state) {
        Connect.Idle -> OutlinedButton(onClick = { attempt++ }, colors = whiteOutlined()) { Text("Conectar «${profile.name}» con Trakt") }
        Connect.Loading -> CircularProgressIndicator(Modifier.size(24.dp), color = BambiColors.Accent, strokeWidth = 2.dp)
        is Connect.Error -> {
            StatusLine(KeyStatus(false, s.message))
            OutlinedButton(onClick = { attempt++ }, colors = whiteOutlined()) { Text("Probar otra vez") }
        }
        is Connect.Code -> Column(
            Modifier.fillMaxWidth().background(BambiColors.SurfaceHigh, BambiShapes.Panel).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Abre ${s.url} y escribe este código:", color = BambiColors.Text, fontSize = 15.sp)
            Text(s.userCode, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { copyToClipboard(s.userCode); openInBrowser(s.url) }, colors = whiteOutlined()) { Text("Copiar código y abrir Trakt") }
            }
            Text("Caduca en ${s.secondsLeft / 60}:${"%02d".format(s.secondsLeft % 60)}. Bambi se conecta solo en cuanto lo aceptes.", color = BambiColors.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun whiteOutlined() = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
