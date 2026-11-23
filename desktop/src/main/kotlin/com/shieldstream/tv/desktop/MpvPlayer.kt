package com.shieldstream.tv.desktop

import androidx.compose.ui.graphics.toArgb
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.UUID
import kotlin.concurrent.thread

/** Qué reproducir y desde dónde (segundos; 0 = desde el principio). */
data class PlayRequest(
    val source: DesktopSource,
    val item: CatalogItem,
    val episode: Episode?,
    val title: String,
    val startSec: Double = 0.0,
    /** Las siguientes fuentes, por si esta no arranca (como en la tele: se pasa a la siguiente). */
    val alternatives: List<DesktopSource> = emptyList(),
    /** Servicio debrid, para los mensajes. */
    val service: String = "",
    /** Intro, resumen y créditos de IntroDB (episodios). */
    val segments: com.shieldstream.tv.domain.SkipSegments? = null,
    /** Audio y subtítulos que elegiste la última vez en esta serie ("no" = sin subtítulos). */
    val audioLang: String? = null,
    val subLang: String? = null,
    /** Pasar solo al siguiente episodio en los créditos. */
    val autoNext: Boolean = false,
    /** Sincronizar con la frecuencia de la pantalla (lo más parecido a «ajustar frecuencia» de la tele). */
    val matchRefresh: Boolean = false,
    /** Ya se reintentó tras un corte a mitad. */
    val retried: Boolean = false,
)

/** Cómo acabó: por dónde ibas, cuánto dura, si llegaste al final o si no se pudo abrir. */
data class PlaybackResult(
    val positionSec: Double?,
    val durationSec: Double?,
    val finished: Boolean,
    val failed: Boolean,
    /** Lo que contó el script de Bambi: pistas elegidas, «siguiente episodio» o temporizador. */
    val report: BambiScript.Report = BambiScript.Report(null, null, next = false, sleep = false, sleepAtEnd = false),
)

/** Un mpv en marcha: se le mandan teclas y órdenes por su tubería de control (IPC). */
private val timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "mpv-espera").apply { isDaemon = true } }

class MpvSession internal constructor(
    private val process: Process,
    private val pipe: String,
    /** Se pidió salir (aunque haya que cerrarlo a la fuerza): no cuenta como fallo ni se reintenta. */
    private val quitting: java.util.concurrent.atomic.AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean(false),
) {
    val alive: Boolean get() = process.isAlive

    /** Como pulsar la tecla en mpv ("SPACE", "LEFT", "j"…). */
    fun key(name: String) = command("[\"keypress\",\"${name.replace("\\", "\\\\").replace("\"", "\\\"")}\"]")

    /** Cambia una propiedad de mpv (el valor, en JSON). */
    fun setProperty(name: String, valueJson: String) = command("[\"set_property\",\"$name\",$valueJson]")

    /**
     * Escucha lo que pide el script de Bambi por la tubería de control: "fs" (pantalla completa) y "esc". Al conectar
     * llama a [onConnected] (para contarle al script si la ventana está a pantalla completa).
     */
    fun listen(onConnected: () -> Unit, onRequest: (String) -> Unit) {
        thread(name = "mpv-ipc", isDaemon = true) {
            val deadline = System.currentTimeMillis() + 20_000
            while (alive && System.currentTimeMillis() < deadline) {
                if (runCatching { readEvents(onConnected, onRequest) }.isSuccess) return@thread
                Thread.sleep(250)
            }
        }
    }

    private fun readEvents(onConnected: () -> Unit, onRequest: (String) -> Unit) {
        val observe = "{\"command\":[\"observe_property_string\",1,\"user-data/bambi/request\"]}\n".toByteArray()
        if (File.separatorChar == '\\') {
            RandomAccessFile(pipe, "rw").use { f ->
                f.write(observe)
                System.err.println("mpv: tubería de control conectada")
                onConnected()
                var last: String? = null
                while (true) {
                    val line = f.readLine() ?: break
                    // mpv repite el valor cuando cambia otra cosa de user-data: cada petición («fs#3») cuenta una vez.
                    val token = requestIn(line) ?: continue
                    if (token == last) continue
                    last = token
                    onRequest(token.substringBefore('#'))
                }
            }
        } else {
            java.nio.channels.SocketChannel.open(java.net.StandardProtocolFamily.UNIX).use { channel ->
                channel.connect(java.net.UnixDomainSocketAddress.of(pipe))
                channel.write(java.nio.ByteBuffer.wrap(observe))
                onConnected()
                var last: String? = null
                java.nio.channels.Channels.newInputStream(channel).bufferedReader().forEachLine { line ->
                    val token = requestIn(line)
                    if (token != null && token != last) {
                        last = token
                        onRequest(token.substringBefore('#'))
                    }
                }
            }
        }
    }

    /** Sale guardando la posición (como la tecla q). */
    fun quit() {
        quitting.set(true)
        if (!process.isAlive) return
        if (!command("[\"quit\"]")) process.destroy()
        // Si en 3 s no se ha ido (vídeo atascado en la red), se cierra a la fuerza.
        thread(isDaemon = true, name = "mpv-quit") {
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
        }
    }

    private fun command(json: String): Boolean = commands(json)

    /** Varias órdenes seguidas por la misma conexión (llegan en orden: mover el ratón y luego pulsar). */
    fun commands(vararg json: String): Boolean {
        val bytes = json.joinToString("") { "{\"command\":$it}\n" }.toByteArray()
        // Si justo hay otra conexión abriéndose, Windows dice «ocupado» un instante: se reintenta.
        repeat(12) { attempt ->
            if (write(bytes)) return true
            if (!alive) return false
            Thread.sleep(10L + attempt * 10L)
        }
        return false
    }

    private fun write(bytes: ByteArray): Boolean = runCatching {
        if (File.separatorChar == '\\') {
            RandomAccessFile(pipe, "rw").use { f ->
                // Si mpv no contesta en 2 s (atascado), se corta para no dejar esperando al resto.
                val watchdog = timer.schedule({ runCatching { f.close() } }, 2, java.util.concurrent.TimeUnit.SECONDS)
                try {
                    f.write(bytes)
                    // mpv contesta a cada orden cuando la ha hecho (y manda también sus avisos: se saltan). Si la
                    // respuesta no llega, la orden ya está enviada: no se repite.
                    runCatching {
                        var pending = bytes.count { it == '\n'.code.toByte() }
                        while (pending > 0) {
                            val line = f.readLine() ?: break
                            if (line.contains("\"error\"")) pending--
                        }
                    }
                } finally {
                    watchdog.cancel(false)
                }
            }
        } else {
            val address = java.net.UnixDomainSocketAddress.of(pipe)
            java.nio.channels.SocketChannel.open(java.net.StandardProtocolFamily.UNIX).use {
                it.connect(address)
                it.write(java.nio.ByteBuffer.wrap(bytes))
            }
        }
        true
    }.getOrDefault(false)
}

/**
 * Reproduce con mpv (mpv.io), que en Windows decodifica por hardware y abre casi cualquier formato: dentro de la
 * ventana de Bambi (si se le da [wid], el identificador de una zona de la ventana) o a pantalla completa aparte;
 * castellano primero en audio y subtítulos y retomando donde lo dejaste. Al cerrarlo, mpv guarda la posición
 * (--save-position-on-quit) en una carpeta temporal propia; de ahí y de su registro sale el progreso.
 */
object MpvPlayer {
    /** La sesión, o el mensaje para el usuario si no se pudo abrir. [onExit] llega en otro hilo al cerrar mpv. */
    fun start(mpvPath: String, request: PlayRequest, wid: Long?, onExit: (PlaybackResult) -> Unit): Result<MpvSession> {
        val dir = Files.createTempDirectory("bambi-mpv").toFile()
        val log = File(dir, "mpv.log")
        val pipe = if (File.separatorChar == '\\') "\\\\.\\pipe\\bambi-mpv-${UUID.randomUUID()}" else File(dir, "ipc.sock").absolutePath
        val command = buildList {
            add(executable(mpvPath))
            if (wid != null) {
                add("--wid=$wid")
                add("--force-window=immediate")
            } else {
                add("--fs")
            }
            add("--force-media-title=${request.title}")
            // Castellano primero; si en esta serie elegiste otra cosa, eso va delante.
            val languages = if (com.shieldstream.tv.model.Lang.isEnglish) "eng,en,spa,es,esp" else "spa,es,esp,eng,en"
            add("--alang=" + listOfNotNull(request.audioLang, languages).joinToString(","))
            if (request.subLang == "no") add("--sid=no")
            else add("--slang=" + listOfNotNull(request.subLang, languages).joinToString(","))
            // HDR: con el HDR de Windows activado, el vídeo sale en HDR; si no, mpv lo convierte a la pantalla.
            add("--target-colorspace-hint=yes")
            if (request.matchRefresh) add("--video-sync=display-resample")
            add("--script=${BambiScript.write(dir).absolutePath}")
            add("--hwdec=auto-safe")
            add("--keep-open=no")
            add("--cursor-autohide=3000")
            // Los controles los dibuja el script de Bambi (como Netflix); los de mpv, fuera.
            add("--osc=no")
            add("--osd-bar=no")
            add("--osd-on-seek=no")
            add("--input-vo-keyboard=yes")
            // Subtítulos como los de Netflix: letra de Windows, blanca, con contorno fino y sombra suave.
            add("--sub-font=Segoe UI Semibold")
            add("--sub-font-size=42")
            add("--sub-outline-size=2.2")
            add("--sub-shadow-offset=1.2")
            add("--sub-shadow-color=0.0/0.0/0.0/0.55")
            add("--sub-margin-y=46")
            add("--sub-scale=%.2f".format(java.util.Locale.US, PlayerPrefs.subtitleStyle.size / 100.0))
            add(
                "--script-opts=" + BambiScript.options(
                    request.segments,
                    request.autoNext,
                    slateCheck = request.source.sizeBytes >= 150_000_000L && request.alternatives.isNotEmpty(),
                    accent = "%06X".format(BambiColors.Accent.toArgb() and 0xFFFFFF),
                    autoSubs = PlayerPrefs.autoSubtitles,
                    subStyle = PlayerPrefs.subtitleStyle.encode(),
                ).joinToString(","),
            )
            add("--osd-font=Segoe UI")
            add("--input-ipc-server=$pipe")
            if (System.getProperty("bambi.tour") != null) add("--mute=yes")
            add("--save-position-on-quit")
            add("--watch-later-dir=${dir.absolutePath}")
            add("--log-file=${log.absolutePath}")
            add("--term-playing-msg=bambi-duration=\${=duration}")
            if (request.startSec >= 1) add("--start=%.1f".format(java.util.Locale.US, request.startSec))
            add(request.source.url)
        }
        val quitting = java.util.concurrent.atomic.AtomicBoolean(false)
        val process = runCatching {
            ProcessBuilder(command).redirectErrorStream(true).start()
        }.getOrElse { e ->
            dir.deleteRecursively()
            System.err.println("mpv no arranca (${command.first()}): $e")
            return Result.failure(
                IllegalStateException("No se pudo abrir el reproductor (falta la carpeta «mpv» junto a Bambi). Vuelve a descomprimir Bambi-Windows.zip entero."),
            )
        }
        thread(name = "mpv", isDaemon = true) {
            val output = runCatching { process.inputStream.bufferedReader().readText() }.getOrDefault("")
            runCatching { process.waitFor() }
            val text = output + "\n" + runCatching { log.readText() }.getOrDefault("")
            val duration = Regex("bambi-duration=([0-9.]+)").findAll(text).lastOrNull()?.groupValues?.get(1)?.toDoubleOrNull()
            val reason = Regex("Exiting\\.\\.\\. \\(([^)]+)\\)").findAll(text).lastOrNull()?.groupValues?.get(1)
            val position = dir.listFiles().orEmpty().filter { it.isFile && it != log }.firstNotNullOfOrNull { f ->
                f.readLines().firstNotNullOfOrNull { Regex("^start=([0-9.]+)").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
            }
            // Errores del script de Bambi o de mpv, al registro de errores (para revisarlos).
            text.lines().filter { it.contains("bambi", ignoreCase = true) && (it.contains("error", ignoreCase = true) || it.contains("[e]")) }
                .take(10).forEach { System.err.println("mpv: $it") }
            if (reason != "Quit" && reason != "End of file") {
                System.err.println("mpv terminó (${reason ?: "sin motivo"}), código ${runCatching { process.exitValue() }.getOrNull()}:")
                text.lines().filter { it.isNotBlank() }.takeLast(12).forEach { System.err.println("  mpv> $it") }
            }
            dir.deleteRecursively()
            onExit(
                PlaybackResult(
                    positionSec = position,
                    durationSec = duration,
                    finished = reason == "End of file",
                    failed = !quitting.get() &&
                        (reason?.contains("Errors", ignoreCase = true) == true || (duration == null && position == null && reason != "Quit")),
                    report = BambiScript.parse(text),
                ),
            )
        }
        return Result.success(MpvSession(process, pipe, quitting))
    }

    /**
     * mpv que se usa: primero el que va dentro de Bambi (carpeta "mpv", viene en el paquete); si no, el de Ajustes
     * (si existe) y luego donde lo dejan winget, scoop o un instalador. Si no aparece, "mpv" a secas (el PATH).
     */
    private fun executable(configured: String): String {
        val name = configured.trim().ifBlank { "mpv" }
        val appDir = runCatching { File(MpvPlayer::class.java.protectionDomain.codeSource.location.toURI()).parentFile }.getOrNull()
        val bundled = listOfNotNull(
            appDir?.let { File(it, "mpv/mpv.exe") },
            // Bambi.exe: el jar va en «app» y mpv junto a Bambi.exe.
            appDir?.parentFile?.let { File(it, "mpv/mpv.exe") },
            File(System.getProperty("user.dir"), "mpv/mpv.exe"),
        )
        val local = System.getenv("LOCALAPPDATA")
        val installed = listOfNotNull(
            File(name).takeIf { name != "mpv" },
            appDir?.let { File(it, "mpv.exe") },
            local?.let { File(it, "Microsoft/WinGet/Links/mpv.exe") },
            System.getenv("ProgramFiles")?.let { File(it, "mpv/mpv.exe") },
            local?.let { File(it, "Programs/mpv/mpv.exe") },
            System.getenv("USERPROFILE")?.let { File(it, "scoop/shims/mpv.exe") },
            System.getenv("USERPROFILE")?.let { File(it, "scoop/apps/mpv/current/mpv.exe") },
        ) + wingetPackages(local)
        return (bundled + installed).firstOrNull { it.isFile }?.absolutePath ?: name
    }

    /** winget deja los paquetes portables en WinGet\Packages\<id>_…\, a veces dentro de otra carpeta. */
    private fun wingetPackages(local: String?): List<File> {
        val root = local?.let { File(it, "Microsoft/WinGet/Packages") } ?: return emptyList()
        return root.listFiles { f -> f.isDirectory && f.name.contains("mpv", ignoreCase = true) }.orEmpty()
            .flatMap { dir -> dir.walkTopDown().maxDepth(3).filter { it.isFile && it.name.equals("mpv.exe", ignoreCase = true) }.toList() }
    }

    /** Ruta de mpv si está instalado (lo configurado, junto a Bambi, winget, scoop o el PATH); null si no aparece. */
    fun find(configured: String): String? {
        val exe = executable(configured)
        File(exe).takeIf { it.isFile }?.let { return it.absolutePath }
        val names = if (exe.endsWith(".exe", ignoreCase = true)) listOf(exe) else listOf("$exe.exe", exe)
        return System.getenv("PATH").orEmpty().split(File.pathSeparator).asSequence()
            .filter { it.isNotBlank() }
            .flatMap { dir -> names.asSequence().map { File(dir, it) } }
            .firstOrNull { it.isFile }?.absolutePath
    }

    /**
     * Abre una ventana de consola con «winget install shinchiro.mpv»: solo cuando el usuario pulsa el botón de
     * Ajustes. La ventana se queda abierta para que vea el resultado y acepte lo que winget le pida.
     */
    fun install(): Result<Unit> = runCatching {
        check(System.getProperty("os.name").orEmpty().startsWith("Windows")) { "Solo en Windows" }
        ProcessBuilder("cmd", "/c", "start", "Instalar mpv", "cmd", "/k", "winget", "install", "--id", "shinchiro.mpv", "-e")
            .start()
        Unit
    }
}

/** De una línea de la tubería de mpv, la petición del script tal cual ("fs#3"; mpv la puede mandar entre comillas). */
internal fun requestIn(line: String): String? = runCatching {
    val o = kotlinx.serialization.json.Json.parseToJsonElement(line) as? kotlinx.serialization.json.JsonObject ?: return null
    if ((o["event"] as? kotlinx.serialization.json.JsonPrimitive)?.content != "property-change") return null
    if ((o["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content != "user-data/bambi/request") return null
    val data = (o["data"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    data.trim().trim('"').takeIf { it.contains('#') && it.substringBefore('#').isNotBlank() }
}.getOrNull()
