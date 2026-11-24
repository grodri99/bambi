package com.shieldstream.tv.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import java.io.File

/**
 * Actualizaciones desde las releases de GitHub, como «Buscar actualizaciones» en la tele: se mira al abrir y cada
 * 6 horas; si hay una versión nueva para Windows (MSI o zip), se avisa, y solo al pulsar se descarga y se instala.
 */
object Updater {
    const val VERSION = "1.0.9"
    const val REPO = "grodri99/bambi"
    const val CHECK_EVERY_MS = 6 * 3_600_000L

    data class Update(val version: String, val url: String, val assetName: String, val notes: String)

    /** "v1.0.10" > "1.0.9", "windows-1.0.9-beta" > "1.0.8": cuenta el primer número con puntos que aparezca. */
    fun isNewer(candidate: String, current: String = VERSION): Boolean {
        fun parts(v: String) = (Regex("\\d+(\\.\\d+)*").find(v)?.value ?: "0").split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** De la respuesta de GitHub, la versión y el fichero de Windows (MSI primero, si no el zip). */
    fun parse(body: String): Update? {
        val o = Json.parseToJsonElement(body) as? JsonObject ?: return null
        val tag = (o["tag_name"] as? JsonPrimitive)?.content ?: return null
        val assets = (o["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { a ->
            val name = (a["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val url = (a["browser_download_url"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            name to url
        }
        // El instalador de Windows (Bambi-Windows-Setup-x.y.z.exe); si no, un .msi o el zip de antes.
        val asset = assets.firstOrNull { it.first.endsWith(".exe", true) && it.first.contains("windows", true) }
            ?: assets.firstOrNull { it.first.endsWith(".msi", true) }
            ?: assets.firstOrNull { it.first.endsWith(".zip", true) && it.first.contains("windows", true) }
            ?: return null
        val version = Regex("\\d+(\\.\\d+)*").find(tag)?.value ?: tag.removePrefix("v")
        return Update(version, asset.second, asset.first, (o["body"] as? JsonPrimitive)?.content.orEmpty())
    }

    /**
     * De la lista de releases, la de Windows más nueva. No vale «la última» de GitHub: suele ser la de la tele y no
     * cuenta las pre-releases (las de Windows son beta). Se saltan los borradores.
     */
    fun newestWindows(body: String): Update? =
        (Json.parseToJsonElement(body) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { (it["draft"] as? JsonPrimitive)?.content != "true" }
            .mapNotNull { parse(it.toString()) }
            .filter { it.assetName.contains("windows", true) || it.assetName.endsWith(".msi", true) }
            .maxWithOrNull { a, b -> if (isNewer(a.version, b.version)) 1 else if (isNewer(b.version, a.version)) -1 else 0 }

    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("https://api.github.com/repos/$REPO/releases?per_page=30")
                .header("Accept", "application/vnd.github+json").header("User-Agent", "Bambi").build()
            DesktopHttp.client.newCall(request).execute().use { r -> if (r.isSuccessful) newestWindows(r.body?.string().orEmpty()) else null }
        }.getOrNull()?.takeIf { isNewer(it.version) }
    }

    /** Descarga e instala (solo al pulsar). El MSI se abre con el instalador de Windows; el zip lo coloca un .bat al cerrar Bambi. */
    suspend fun install(update: Update): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(System.getProperty("java.io.tmpdir"), update.assetName)
            DesktopHttp.client.newCall(Request.Builder().url(update.url).header("User-Agent", "Bambi").build()).execute().use { r ->
                check(r.isSuccessful) { "HTTP ${r.code}" }
                file.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
            }
            if (file.name.endsWith(".exe", true)) {
                // El instalador nuevo se instala encima del que hay (mismo producto, versión mayor).
                ProcessBuilder(file.absolutePath).start()
            } else if (file.name.endsWith(".msi", true)) {
                ProcessBuilder("msiexec", "/i", file.absolutePath).start()
            } else {
                val jarDir = File(Updater::class.java.protectionDomain.codeSource.location.toURI()).parentFile
                // Bambi.exe lleva el jar en «app»; la carpeta de Bambi es la de arriba.
                val appDir = if (jarDir.name.equals("app", true)) jarDir.parentFile else jarDir
                val bat = File(System.getProperty("java.io.tmpdir"), "bambi-actualizar.bat")
                bat.writeText(
                    """
                    @echo off
                    timeout /t 3 /nobreak >nul
                    powershell -NoProfile -Command "Expand-Archive -Force '${file.absolutePath}' '${appDir.parentFile.absolutePath}'"
                    start "" "${File(appDir, "Bambi.exe").absolutePath}"
                    """.trimIndent().replace("\n", "\r\n"),
                )
                ProcessBuilder("cmd", "/c", "start", "Bambi", "/min", bat.absolutePath).start()
            }
            Unit
        }
    }
}

/** Ajustes → Actualizaciones. */
@Composable
fun UpdatesSection(available: Updater.Update?, onExit: () -> Unit) {
    Section("Actualizaciones", "Versión ${Updater.VERSION}. Bambi mira cada 6 horas si hay otra nueva en GitHub.")
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf(available) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val update = found
        if (update == null) {
            OutlinedButton(onClick = {
                status = "Buscando…"
                scope.launch {
                    found = Updater.check()
                    status = if (found == null) "Tienes la última versión." else null
                }
            }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Buscar actualizaciones") }
        } else {
            OutlinedButton(onClick = {
                status = "Descargando ${update.version}…"
                scope.launch {
                    Updater.install(update).onSuccess { onExit() }.onFailure { status = "No se pudo descargar: ${it.message}" }
                }
            }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Instalar la ${update.version}") }
        }
        status?.let { Text(it, color = BambiColors.Muted, fontSize = 13.sp) }
    }
}
