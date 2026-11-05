package com.shieldstream.tv.settings.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.model.tr
import com.shieldstream.tv.settings.QrCode
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.TvButton
import kotlinx.coroutines.delay

@Composable
fun TraktConnectScreen(onDone: () -> Unit, viewModel: TraktConnectViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val buttonRequester = remember { FocusRequester() }
    LaunchedEffect(state::class) {
        delay(60)
        runCatching { buttonRequester.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(ProfileBackground)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
        ) {
            Column(Modifier.width(500.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).background(TraktRed, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("t", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(18.dp))
                    Text(tr("Conectar con Trakt", "Connect Trakt"), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(32.dp))
                when (val s = state) {
                    TraktConnectUiState.Loading -> {
                        Text(tr("Pidiendo un código a Trakt…", "Getting a code from Trakt…"), color = ProfileMuted, fontSize = 22.sp)
                        Spacer(Modifier.height(24.dp))
                        TvButton(tr("Cancelar", "Cancel"), onClick = onDone, modifier = Modifier.focusRequester(buttonRequester))
                    }
                    is TraktConnectUiState.ShowCode -> {
                        Step("1", tr("En el móvil o el ordenador, entra en", "On your phone or computer, go to"))
                        Text(
                            s.url.removePrefix("https://").removePrefix("www."),
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 46.dp, top = 4.dp),
                        )
                        Spacer(Modifier.height(24.dp))
                        Step("2", tr("Inicia sesión y escribe este código:", "Sign in and enter this code:"))
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 46.dp),
                        ) {
                            s.userCode.forEach { c ->
                                Box(
                                    Modifier
                                        .size(width = 46.dp, height = 60.dp)
                                        .background(Color(0xFF1A1C27), RoundedCornerShape(10.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        c.toString(),
                                        color = Color.White,
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            tr("Esperando… El código caduca en ", "Waiting… The code expires in ") +
                                "%d:%02d".format(s.secondsLeft / 60, s.secondsLeft % 60),
                            color = ProfileMuted,
                            fontSize = 18.sp,
                        )
                        Spacer(Modifier.height(24.dp))
                        TvButton(tr("Cancelar", "Cancel"), onClick = onDone, modifier = Modifier.focusRequester(buttonRequester))
                    }
                    is TraktConnectUiState.Connected -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).background(Color(0xFF46D369), RoundedCornerShape(50)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(AppIcons.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                tr("Conectado como @${s.username}", "Connected as @${s.username}"),
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            tr(
                                "Lo que ya habías visto en esta Shield se sube a Trakt, y a partir de ahora \"Seguir viendo\" " +
                                    "va sincronizado entre tus Shields.",
                                "What you'd already watched on this Shield is uploaded to Trakt, and from now on \"Continue " +
                                    "watching\" is synced across your Shields.",
                            ),
                            color = ProfileMuted,
                            fontSize = 19.sp,
                        )
                        Spacer(Modifier.height(28.dp))
                        TvButton(tr("Listo", "Done"), onClick = onDone, modifier = Modifier.focusRequester(buttonRequester))
                    }
                    TraktConnectUiState.Expired -> {
                        Text(tr("El código ha caducado.", "The code has expired."), color = Color.White, fontSize = 24.sp)
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TvButton(tr("Pedir otro código", "Get a new code"), onClick = viewModel::start, modifier = Modifier.focusRequester(buttonRequester))
                            TvButton(tr("Cancelar", "Cancel"), onClick = onDone)
                        }
                    }
                    is TraktConnectUiState.Error -> {
                        Text(s.message, color = Color(0xFFFF5A5F), fontSize = 22.sp)
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TvButton(tr("Reintentar", "Retry"), onClick = viewModel::start, modifier = Modifier.focusRequester(buttonRequester))
                            TvButton(tr("Cancelar", "Cancel"), onClick = onDone)
                        }
                    }
                }
            }
            val code = state as? TraktConnectUiState.ShowCode
            if (code != null) {
                Spacer(Modifier.width(44.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color(0xFF1A1C27), RoundedCornerShape(18.dp))
                        .padding(22.dp),
                ) {
                    Box(
                        Modifier.size(190.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp),
                    ) {
                        QrCode(code.url, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(tr("O escanéalo con el móvil", "Or scan it with your phone"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = ProfileMuted, fontSize = 21.sp)
    }
}
