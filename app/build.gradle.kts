plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
}

import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// seedKeys=false en local.properties → la app se compila SIN tus claves (APK para otra persona, o para probar la
// guía de configuración como en una instalación nueva). Por defecto (sin la línea) se incluyen como valores iniciales.
val seedKeys: Boolean = localProps.getProperty("seedKeys")?.trim()?.lowercase() != "false"
fun seed(name: String): String = if (seedKeys) localProps.getProperty(name) ?: "" else ""
val rdToken: String = seed("rdToken")
// API key gratuita de https://subdl.com (opcional): activa SubDL en "Subtítulos externos".
val subdlApiKey: String = seed("subdlApiKey")
val tmdbApiKey: String = seed("tmdbApiKey")
// App "Bambi" registrada en Trakt (app.trakt.tv/settings/apps). No es una clave personal: identifica a la app, así
// que va también en los APK sin claves (seedKeys=false). Desde oct-2026 Trakt solo da Client ID; el secreto es opcional.
val traktClientId: String = localProps.getProperty("traktClientId")?.trim() ?: ""
val traktClientSecret: String = localProps.getProperty("traktClientSecret")?.trim() ?: ""
// La misma Redirect URI que se puso al crear la app en developer.trakt.tv (las apps "Production" exigen https).
val traktRedirectUri: String = localProps.getProperty("traktRedirectUri")?.trim() ?: "urn:ietf:wg:oauth:2.0:oob"

android {
    namespace = "com.shieldstream.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.shieldstream.tv"
        minSdk = 21
        targetSdk = 35
        // Al publicar una versión nueva: subir versionCode (+1) y versionName (1.0.1, 1.1.0…).
        versionCode = 106
        versionName = "1.0.6"
        manifestPlaceholders["appLabel"] = "@string/app_name"

        buildConfigField("String", "RD_TOKEN", "\"$rdToken\"")
        buildConfigField("String", "SUBDL_API_KEY", "\"$subdlApiKey\"")
        // Solo valores iniciales: en la primera ejecución se guardan cifrados y desde ahí mandan los Ajustes.
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "TRAKT_CLIENT_ID", "\"$traktClientId\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"$traktClientSecret\"")
        buildConfigField("String", "TRAKT_REDIRECT_URI", "\"$traktRedirectUri\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Firma de la release: keystore/butaca.keystore (fuera de git; el bucle la crea copiando la clave con la que
    // ya estaba instalada la app, para actualizar sin desinstalar). ¡Haz copia de seguridad! Sin ella no se puede
    // actualizar "Bambi" sin desinstalarla (y perder el progreso).
    signingConfigs {
        create("release") {
            storeFile = rootProject.file(localProps.getProperty("releaseStoreFile") ?: "keystore/butaca.keystore")
            storePassword = localProps.getProperty("releaseStorePassword") ?: "android"
            keyAlias = localProps.getProperty("releaseKeyAlias") ?: "androiddebugkey"
            keyPassword = localProps.getProperty("releaseKeyPassword") ?: "android"
        }
    }

    buildTypes {
        // "Bambi (pruebas)": se instala junto a la estable, con sus propios datos. Aquí se prueban los cambios.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-pruebas"
            manifestPlaceholders["appLabel"] = "Bambi (pruebas)"
        }
        // "Bambi": la de diario. Sin R8/minificar a propósito: en una app personal apenas aporta y es una
        // fuente típica de cierres raros (reflexión de libgav1, JNI de dav1d, serialización).
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
        // Release-like performance testing without touching the stable app or enabling self-updates.
        // Shares the debug package/data, but is non-debuggable so AGP packages the baseline profile.
        create("benchmark") {
            initWith(getByName("release"))
            isDebuggable = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-benchmark"
            manifestPlaceholders["appLabel"] = "Bambi (benchmark)"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:media"))
    implementation(project(":core:ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:details"))
    implementation(project(":feature:player"))
    implementation(project(":feature:settings"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil)
    // Decoder AV1 por software (libgav1, compilado con _setup_av1.sh). Lo usa core:media por reflexión.
    implementation(files("libs/media3-decoder-av1-1.6.0.aar"))
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
