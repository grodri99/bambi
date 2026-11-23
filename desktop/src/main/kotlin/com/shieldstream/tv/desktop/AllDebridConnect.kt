package com.shieldstream.tv.desktop

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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.network.ApiConfig
import com.shieldstream.tv.network.ApiFactory
import com.shieldstream.tv.network.api.AllDebridApi
import kotlinx.coroutines.delay

/**
 * AllDebrid sin copiar la clave, como en la tele: Bambi pide un código, lo validas en alldebrid.com/pin (o con el QR
 * en el móvil) y la clave se guarda sola.
 */
@Composable
fun AllDebridConnect(onKey: (String) -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    var pin by remember { mutableStateOf<Pair<String, String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    if (attempt > 0) {
        LaunchedEffect(attempt) {
            val api = ApiFactory.retrofit(ApiConfig.ALLDEBRID_BASE_URL, DesktopHttp.client, ApiFactory.json()).create(AllDebridApi::class.java)
            message = null
            val got = runCatching { api.getPin().data }.getOrNull()
            if (got == null) {
                message = "No se pudo pedir el código a AllDebrid."
                return@LaunchedEffect
            }
            pin = got.pin to got.user_url
            val deadline = System.currentTimeMillis() + got.expires_in * 1000L
            while (System.currentTimeMillis() < deadline) {
                delay(5_000)
                val check = runCatching { api.checkPin(got.pin, got.check).data }.getOrNull() ?: continue
                val key = check.apikey
                if (check.activated && !key.isNullOrBlank()) {
                    pin = null
                    message = "AllDebrid conectado."
                    onKey(key)
                    return@LaunchedEffect
                }
            }
            pin = null
            message = "El código ha caducado. Pide otro."
        }
    }
    val current = pin
    if (current == null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { attempt++ }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                Text("Conectar AllDebrid con un código")
            }
            message?.let { Text(it, color = BambiColors.Muted, fontSize = 13.sp) }
        }
    } else {
        val (code, url) = current
        Column(
            Modifier.fillMaxWidth().background(BambiColors.SurfaceHigh, BambiShapes.Panel).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Abre alldebrid.com/pin y escribe:", color = BambiColors.Text, fontSize = 15.sp)
            Text(code, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
            OutlinedButton(onClick = { copyToClipboard(code); openInBrowser(url) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                Text("Copiar código y abrir AllDebrid")
            }
        }
    }
}
