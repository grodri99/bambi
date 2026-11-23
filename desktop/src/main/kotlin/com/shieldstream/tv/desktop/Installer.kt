package com.shieldstream.tv.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
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
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Instalar Bambi en el PC sin .bat ni permisos de administrador, como una app normal: se copia a
 * %LOCALAPPDATA%\Programs\Bambi, crea accesos directos en el menú Inicio y en el escritorio y aparece en
 * Configuración → Aplicaciones de Windows (desde ahí se desinstala con «Bambi.exe --desinstalar»).
 * Los ajustes y perfiles (%APPDATA%\Bambi) no se tocan.
 */
object Installer {
    private const val KEY = "HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Bambi"

    val isWindows get() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** Carpeta de Bambi (la de Bambi.exe), si se está ejecutando desde el paquete; null desde el código. */
    fun appRoot(): File? = runCatching {
        val jarDir = File(Installer::class.java.protectionDomain.codeSource.location.toURI()).parentFile
        val root = if (jarDir.name.equals("app", ignoreCase = true)) jarDir.parentFile else jarDir
        root.takeIf { File(it, "Bambi.exe").isFile }
    }.getOrNull()

    fun installDir(): File? = System.getenv("LOCALAPPDATA")?.let { File(it, "Programs\\Bambi") }

    fun isInstalled(): Boolean {
        val root = appRoot() ?: return false
        val dir = installDir() ?: return false
        return root.canonicalPath.equals(dir.canonicalPath, ignoreCase = true)
    }

    /** Copia Bambi a su sitio, crea los accesos y lo registra. Devuelve el Bambi.exe instalado. */
    fun install(): Result<File> = runCatching {
        val root = checkNotNull(appRoot()) { "Bambi no se está ejecutando desde su carpeta." }
        val dir = checkNotNull(installDir())
        if (!root.canonicalPath.equals(dir.canonicalPath, ignoreCase = true)) copyTree(root, dir)
        val exe = File(dir, "Bambi.exe")
        powershell(
            """
            ${'$'}exe = ${'$'}env:BAMBI_EXE; ${'$'}dir = ${'$'}env:BAMBI_DIR
            ${'$'}s = New-Object -ComObject WScript.Shell
            foreach (${'$'}p in @([Environment]::GetFolderPath('Programs'), [Environment]::GetFolderPath('Desktop'))) {
              ${'$'}l = ${'$'}s.CreateShortcut((Join-Path ${'$'}p 'Bambi.lnk'))
              ${'$'}l.TargetPath = ${'$'}exe; ${'$'}l.WorkingDirectory = ${'$'}dir; ${'$'}l.IconLocation = ${'$'}exe; ${'$'}l.Description = 'Bambi'
              ${'$'}l.Save()
            }
            New-Item -Path '$KEY' -Force | Out-Null
            Set-ItemProperty -Path '$KEY' -Name DisplayName -Value 'Bambi'
            Set-ItemProperty -Path '$KEY' -Name DisplayIcon -Value ${'$'}exe
            Set-ItemProperty -Path '$KEY' -Name DisplayVersion -Value ${'$'}env:BAMBI_VERSION
            Set-ItemProperty -Path '$KEY' -Name Publisher -Value 'Bambi'
            Set-ItemProperty -Path '$KEY' -Name InstallLocation -Value ${'$'}dir
            Set-ItemProperty -Path '$KEY' -Name UninstallString -Value ('"' + ${'$'}exe + '" --desinstalar')
            Set-ItemProperty -Path '$KEY' -Name NoModify -Value 1 -Type DWord
            Set-ItemProperty -Path '$KEY' -Name NoRepair -Value 1 -Type DWord
            """.trimIndent(),
            mapOf("BAMBI_EXE" to exe.absolutePath, "BAMBI_DIR" to dir.absolutePath, "BAMBI_VERSION" to Updater.VERSION),
        )
        exe
    }

    /** Quita accesos y registro, y borra la carpeta cuando Bambi se haya cerrado. */
    fun uninstall(): Result<Unit> = runCatching {
        val dir = installDir() ?: return@runCatching
        powershell(
            """
            Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path ([Environment]::GetFolderPath('Programs')) 'Bambi.lnk'), (Join-Path ([Environment]::GetFolderPath('Desktop')) 'Bambi.lnk')
            Remove-Item -Path '$KEY' -Recurse -Force -ErrorAction SilentlyContinue
            """.trimIndent(),
            emptyMap(),
        )
        // La carpeta se borra unos segundos después, cuando este Bambi ya se ha cerrado.
        ProcessBuilder(
            "powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command",
            "Start-Sleep -Seconds 3; Remove-Item -LiteralPath ${'$'}env:BAMBI_DIR -Recurse -Force -ErrorAction SilentlyContinue",
        ).apply { environment()["BAMBI_DIR"] = dir.absolutePath }.start()
        Unit
    }

    /** «Bambi.exe --desinstalar» (desde Aplicaciones de Windows): pregunta y desinstala, sin abrir Bambi. */
    fun uninstallFromWindows() {
        val answer = javax.swing.JOptionPane.showConfirmDialog(
            null,
            "¿Desinstalar Bambi?\nTus perfiles y ajustes se quedan guardados por si vuelves.",
            "Desinstalar Bambi",
            javax.swing.JOptionPane.YES_NO_OPTION,
        )
        if (answer != javax.swing.JOptionPane.YES_OPTION) return
        uninstall().onFailure {
            javax.swing.JOptionPane.showMessageDialog(null, "No se pudo desinstalar: ${it.message}", "Bambi", javax.swing.JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun copyTree(from: File, to: File) {
        val source = from.toPath()
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val target = to.toPath().resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(target)
                else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun powershell(script: String, env: Map<String, String>) {
        // El guion va codificado (UTF-16, como lo pide PowerShell): así las rutas con tildes llegan bien.
        val encoded = java.util.Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        val process = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
            .redirectErrorStream(true)
            .apply { environment().putAll(env) }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { output.lines().firstOrNull { it.isNotBlank() } ?: "PowerShell falló" }
    }
}

/** Ajustes → Bambi → Instalación. */
@Composable
fun InstallSection(onExit: () -> Unit) {
    if (!Installer.isWindows || Installer.appRoot() == null) return
    val installed = remember { Installer.isInstalled() }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    if (installed) {
        Section("Instalación", "Bambi está instalado en este PC (menú Inicio, escritorio y Aplicaciones de Windows).")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { Installer.uninstall() }
                        result.onSuccess { onExit() }.onFailure { status = "No se pudo desinstalar: ${it.message}"; busy = false }
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) { Text("Desinstalar Bambi") }
            Text("Tus perfiles y ajustes se conservan.", color = BambiColors.Muted, fontSize = 13.sp)
        }
    } else {
        Section("Instalación", "Instala Bambi en este PC: accesos en el menú Inicio y en el escritorio, y aparece en Aplicaciones de Windows. No pide permisos de administrador.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    status = "Instalando…"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { Installer.install() }
                        result.onSuccess { exe ->
                            status = "Listo. Abriendo Bambi instalado…"
                            runCatching { ProcessBuilder(exe.absolutePath).directory(exe.parentFile).start() }
                            onExit()
                        }.onFailure { status = "No se pudo instalar: ${it.message}"; busy = false }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BambiColors.Accent, contentColor = BambiColors.OnAccent),
            ) { Text("Instalar en este PC") }
        }
    }
    status?.let { Text(it, color = BambiColors.Muted, fontSize = 13.sp) }
}
