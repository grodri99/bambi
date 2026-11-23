// Bambi para Windows (y Linux): Compose Multiplatform de escritorio sobre los mismos módulos puros que la app de
// Android (:core:model, :core:domain, :core:api). El vídeo se reproduce con mpv.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat


plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:api"))
    implementation(project(":core:streams"))
    implementation(project(":core:trakt"))
    implementation("org.json:json:20240303")
    // Identificador de ventana para dibujar mpv dentro de Bambi (--wid).
    implementation("net.java.dev.jna:jna:5.15.0")
    // DPAPI de Windows para guardar sesiones y claves cifradas.
    implementation("net.java.dev.jna:jna-platform:5.15.0")
    // DNS cifrado si la operadora bloquea un dominio (como la tele).
    implementation(libs.okhttp.dnsoverhttps)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}

// Las imágenes de la tele (logo, letras, gatitos de perfil) y la lista de iconos de Netflix, sin copiarlas.
sourceSets["main"].resources.srcDir("../core/ui/src/main/res/drawable-nodpi")
sourceSets["main"].resources.srcDir("../feature/settings/src/main/assets")

compose.desktop {
    application {
        mainClass = "com.shieldstream.tv.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Bambi"
            packageVersion = "1.0.8"
            windows {
                menuGroup = "Bambi"
                shortcut = true
                dirChooser = true
            }
        }
    }
}

// Capturas de las pantallas sin ventana (con datos de prueba), para revisar el diseño desde el bucle de trabajo.
tasks.register<JavaExec>("renderShots") {
    group = "verification"
    mainClass.set("com.shieldstream.tv.desktop.RenderShotsKt")
    classpath = sourceSets["main"].runtimeClasspath
    args(rootProject.layout.projectDirectory.dir("_shots/desktop").asFile.absolutePath)
    systemProperty("java.awt.headless", "true")
}

// ---------------------------------------------------------------------------------------------------------------
// Paquete para Windows sin instalar nada: bambi.jar (con las librerías nativas de Windows y Linux) + Java 17 de
// Eclipse Temurin + Bambi.bat. Se deja descomprimido en _windows/Bambi (fuera de git) y en un zip para pasarlo.
// ---------------------------------------------------------------------------------------------------------------
val fatJar by tasks.registering(Jar::class) {
    group = "distribution"
    archiveFileName.set("bambi.jar")
    destinationDirectory.set(layout.buildDirectory.dir("fatjar"))
    manifest { attributes["Main-Class"] = "com.shieldstream.tv.desktop.MainKt" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val runtime = configurations.runtimeClasspath
    dependsOn(runtime)
    from(sourceSets["main"].output)
    from({
        val jars = runtime.get().filter { it.name.endsWith(".jar") }.toMutableList()
        // Las librerías nativas de Skia para Windows, de la misma versión que las de este sistema.
        val skiko = jars.firstOrNull { it.name.startsWith("skiko-awt-runtime-") }
        val version = skiko?.name?.substringAfterLast("-x64-")?.removeSuffix(".jar")
        if (version != null) {
            jars += project.configurations.detachedConfiguration(
                project.dependencies.create("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:$version"),
            ).apply { isTransitive = false }.files
        }
        jars.map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class", "META-INF/versions/*/module-info.class")
}

val windowsJre by tasks.registering {
    group = "distribution"
    val zip = layout.buildDirectory.file("jre/temurin17-jre-windows-x64.zip")
    outputs.file(zip)
    doLast {
        val file = zip.get().asFile
        if (file.length() > 0) return@doLast
        file.parentFile.mkdirs()
        val url = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk"
        uri(url).toURL().openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
    }
}

// Java para Windows descomprimido en build/ (lo usan jpackage y la prueba con datos reales).
val windowsJreUnpacked by tasks.registering(Copy::class) {
    group = "distribution"
    dependsOn(windowsJre)
    val target = layout.buildDirectory.dir("winjre")
    onlyIf { !target.get().file("bin/javaw.exe").asFile.exists() }
    into(target)
    from({ zipTree(windowsJre.get().outputs.files.singleFile) }) {
        // El zip trae una carpeta "jdk-17.x-jre/": se quita.
        eachFile { path = path.substringAfter('/') }
        includeEmptyDirs = false
    }
}

// jpackage (del JDK 17 oficial de Eclipse Temurin para Windows, como el Java que lleva Bambi) solo para construir
// Bambi.exe; no va dentro del paquete.
val windowsJdk by tasks.registering {
    group = "distribution"
    val zip = layout.buildDirectory.file("jdk/temurin17-jdk-windows-x64.zip")
    outputs.file(zip)
    doLast {
        val file = zip.get().asFile
        if (file.length() > 0) return@doLast
        file.parentFile.mkdirs()
        val url = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"
        val tmp = File(file.path + ".part")
        uri(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        tmp.renameTo(file)
    }
}

val windowsJdkUnpacked by tasks.registering(Copy::class) {
    group = "distribution"
    dependsOn(windowsJdk)
    val target = layout.buildDirectory.dir("winjdk")
    onlyIf { !target.get().file("bin/jpackage.exe").asFile.exists() }
    into(target)
    from({ zipTree(windowsJdk.get().outputs.files.singleFile) }) {
        eachFile { path = path.substringAfter('/') }
        includeEmptyDirs = false
    }
}

fun windowsPath(file: File): String =
    providers.exec { commandLine("wslpath", "-w", file.absolutePath) }.standardOutput.asText.get().trim()

val bambiVersion: String = Regex("VERSION = \"([0-9.]+)\"")
    .find(file("src/main/kotlin/com/shieldstream/tv/desktop/Updates.kt").readText())?.groupValues?.get(1) ?: "1.0.0"

// Bambi.exe: el lanzador de Windows de verdad (icono de Bambi, sin consola), con Java dentro («runtime»).
val windowsExe by tasks.registering(Exec::class) {
    group = "distribution"
    dependsOn(fatJar, windowsJreUnpacked, windowsJdkUnpacked)
    val input = layout.buildDirectory.dir("jpackage-input")
    val out = layout.buildDirectory.dir("jpackage")
    executable = layout.buildDirectory.file("winjdk/bin/jpackage.exe").get().asFile.absolutePath
    doFirst {
        delete(input, out)
        copy { from(fatJar); into(input) }
        out.get().asFile.mkdirs()
        args(
            "--type", "app-image",
            "--name", "Bambi",
            "--app-version", bambiVersion,
            "--vendor", "Bambi",
            "--description", "Bambi",
            "--input", windowsPath(input.get().asFile),
            "--main-jar", "bambi.jar",
            "--main-class", "com.shieldstream.tv.desktop.MainKt",
            "--runtime-image", windowsPath(layout.buildDirectory.dir("winjre").get().asFile),
            "--icon", windowsPath(file("windows/bambi.ico")),
            "--java-options", "-Dsun.java2d.uiScale.enabled=true",
            "--dest", windowsPath(out.get().asFile),
        )
    }
}

// mpv va dentro de Bambi (carpeta "mpv"), como el reproductor de cualquier app: no hay que instalar nada.
// Es la build oficial de Windows que publica el propio proyecto mpv en GitHub (zip, versión fija).
val mpvVersion = "0.42.0"
val windowsMpv by tasks.registering {
    group = "distribution"
    val zip = layout.buildDirectory.file("mpv/mpv-v$mpvVersion-x86_64-pc-windows-msvc.zip")
    outputs.file(zip)
    doLast {
        val file = zip.get().asFile
        if (file.length() > 0) return@doLast
        file.parentFile.mkdirs()
        val url = "https://github.com/mpv-player/mpv/releases/download/v$mpvVersion/mpv-v$mpvVersion-x86_64-pc-windows-msvc.zip"
        val tmp = File(file.path + ".part")
        uri(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        tmp.renameTo(file)
    }
}

val windowsMpvDir by tasks.registering(Copy::class) {
    group = "distribution"
    dependsOn(windowsMpv)
    val target = rootProject.layout.projectDirectory.dir("_windows/Bambi/mpv")
    // Igual que Java: si Bambi está reproduciendo, Windows bloquea mpv.exe.
    onlyIf { !target.file("mpv.exe").asFile.exists() }
    into(target)
    from({ zipTree(windowsMpv.get().outputs.files.singleFile) })
}

val windowsDist by tasks.registering(Copy::class) {
    group = "distribution"
    dependsOn(windowsExe, windowsMpvDir)
    val target = rootProject.layout.projectDirectory.dir("_windows/Bambi").asFile
    into(target)
    // Si Bambi está abierto, Windows bloquea su Java y su .exe: Java solo se copia si falta (se mira una vez, antes).
    var runtimePresent = false
    from(layout.buildDirectory.dir("jpackage/Bambi")) {
        exclude { e -> e.path.startsWith("runtime/") && runtimePresent }
        exclude { e -> e.path == "Bambi.exe" && File(target, "Bambi.exe").length() == e.file.length() }
    }
    from(layout.projectDirectory.dir("windows")) { include("LEEME.txt") }
    doFirst {
        val jvm = File(target, "runtime/bin/server/jvm.dll")
        if (!jvm.exists()) runCatching { delete(File(target, "runtime")) }
        runtimePresent = jvm.exists()
        // Lo del paquete anterior (Java suelto, bambi.jar y los .bat): ya no hace falta.
        // (Si ese Bambi antiguo está abierto, Windows no deja borrarlo: se queda, pero no va en el zip.)
        listOf("jre", "bambi.jar", "Bambi.bat", "Instalar Bambi.bat", "Desinstalar Bambi.bat", "bambi.ico")
            .forEach { name -> runCatching { delete(File(target, name)) } }
    }
}

tasks.register<Zip>("windowsZip") {
    group = "distribution"
    dependsOn(windowsDist)
    archiveFileName.set("Bambi-Windows.zip")
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("_windows"))
    from(rootProject.layout.projectDirectory.dir("_windows/Bambi")) {
        into("Bambi")
        exclude("jre/**", "bambi.jar", "*.bat", "bambi.ico")
    }
}

// Busca un jpackage.exe de Windows ya instalado (Android Studio, Temurin, Oracle, Microsoft, IntelliJ) para hacer
// Bambi.exe. Solo lista lo que encuentra.
tasks.register("findJpackage") {
    doLast {
        val roots = listOf(
            "/mnt/c/Program Files/Android",
            "/mnt/c/Program Files/Eclipse Adoptium",
            "/mnt/c/Program Files/Java",
            "/mnt/c/Program Files/Microsoft",
            "/mnt/c/Program Files/JetBrains",
            "/mnt/c/Program Files/Zulu",
            "/mnt/c/Program Files/BellSoft",
            "/mnt/c/Program Files/Amazon Corretto",
            "/mnt/c/Users/Gonzalo Rodríguez/.jdks",
            "/mnt/c/Users/Gonzalo Rodríguez/AppData/Local/Programs",
            "/mnt/c/Users/Gonzalo Rodríguez/AppData/Local/JetBrains",
        )
        roots.map(::File).filter { it.isDirectory }.forEach { root ->
            root.walkTopDown().maxDepth(5).filter { it.name.equals("jpackage.exe", true) }.forEach { println("JPACKAGE " + it.absolutePath) }
            println("buscado en " + root.absolutePath)
        }
    }
}

// Abre el Bambi.exe del paquete en modo comprobación (sin ventana) y enseña qué Java y qué mpv usa.
tasks.register<Exec>("windowsExeCheck") {
    group = "verification"
    dependsOn(windowsDist)
    val dist = rootProject.layout.projectDirectory.dir("_windows/Bambi").asFile
    val report = layout.buildDirectory.file("bambi-comprobar.txt").get().asFile
    executable = File(dist, "Bambi.exe").absolutePath
    doFirst {
        report.delete()
        args("--comprobar", windowsPath(report))
    }
    doLast { println("COMPROBAR\n" + report.readText()) }
}

// ---------------------------------------------------------------------------------------------------------------
// Instalador de Windows (Bambi-Windows-Setup-<versión>.exe), como cualquier programa: asistente, carpeta a elegir,
// accesos en el menú Inicio y el escritorio, y desinstalación desde Configuración → Aplicaciones. Sin permisos de
// administrador (se instala para el usuario). Lo hace jpackage con WiX 3 (la versión oficial del proyecto WiX en
// GitHub, en zip: solo se usa para construir).
// ---------------------------------------------------------------------------------------------------------------
val windowsWix by tasks.registering {
    group = "distribution"
    val zip = layout.buildDirectory.file("wix/wix314-binaries.zip")
    outputs.file(zip)
    doLast {
        val file = zip.get().asFile
        if (file.length() > 0) return@doLast
        file.parentFile.mkdirs()
        val tmp = File(file.path + ".part")
        uri("https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip").toURL().openStream()
            .use { input -> tmp.outputStream().use { input.copyTo(it) } }
        tmp.renameTo(file)
    }
}

val windowsWixUnpacked by tasks.registering(Copy::class) {
    group = "distribution"
    dependsOn(windowsWix)
    val target = layout.buildDirectory.dir("wix/bin")
    onlyIf { !target.get().file("candle.exe").asFile.exists() }
    into(target)
    from({ zipTree(windowsWix.get().outputs.files.singleFile) })
}

/** Identificador fijo del producto: con él, cada versión nueva se instala encima de la anterior. */
val bambiUpgradeUuid = "62bfdda8-f99f-41bb-8a19-d7664aecfacd"

val windowsInstaller by tasks.registering(Exec::class) {
    group = "distribution"
    dependsOn(windowsExe, windowsMpv, windowsWixUnpacked, windowsJdkUnpacked)
    val image = layout.buildDirectory.dir("installer-image/Bambi")
    val out = layout.buildDirectory.dir("installer")
    val dest = rootProject.layout.projectDirectory.dir("_windows")
    executable = layout.buildDirectory.file("winjdk/bin/jpackage.exe").get().asFile.absolutePath
    doFirst {
        // La app ya preparada (Bambi.exe, app, runtime) más mpv al lado.
        delete(layout.buildDirectory.dir("installer-image"), out)
        copy { from(layout.buildDirectory.dir("jpackage/Bambi")); into(image) }
        copy { from(zipTree(windowsMpv.get().outputs.files.singleFile)); into(image.get().dir("mpv")) }
        out.get().asFile.mkdirs()
        // WiX tiene que estar en el PATH que ve jpackage (un programa de Windows lanzado desde WSL).
        val wixBin = layout.buildDirectory.dir("wix/bin").get().asFile.absolutePath
        environment("PATH", wixBin + ":" + System.getenv("PATH"))
        environment("WSLENV", listOfNotNull(System.getenv("WSLENV")?.takeIf { it.isNotBlank() }, "PATH/l").joinToString(":"))
        args(
            "--type", "exe",
            "--app-image", windowsPath(image.get().asFile),
            "--name", "Bambi",
            "--app-version", bambiVersion,
            "--vendor", "Bambi",
            "--description", "Bambi",
            "--win-per-user-install",
            "--win-dir-chooser",
            "--win-menu",
            "--win-menu-group", "Bambi",
            "--win-shortcut",
            "--win-upgrade-uuid", bambiUpgradeUuid,
            "--resource-dir", windowsPath(file("windows/installer")),
            "--dest", windowsPath(out.get().asFile),
            "--verbose",
        )
    }
    doLast {
        val built = out.get().asFile.listFiles().orEmpty().firstOrNull { it.name.endsWith(".exe", true) }
            ?: error("jpackage no ha dejado el instalador en ${out.get().asFile}")
        dest.asFile.listFiles().orEmpty().filter { it.name.startsWith("Bambi-Windows-Setup-") }.forEach { it.delete() }
        val final = File(dest.asFile, "Bambi-Windows-Setup-$bambiVersion.exe")
        built.copyTo(final, overwrite = true)
        println("INSTALADOR " + final.absolutePath + " (" + final.length() / 1_000_000 + " MB)")
    }
}
