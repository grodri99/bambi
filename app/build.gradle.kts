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
val seedKeys: Boolean = localProps.getProperty("seedKeys")?.trim()?.lowercase() == "true"
fun seed(name: String): String = if (seedKeys) localProps.getProperty(name) ?: "" else ""
val rdToken: String = seed("rdToken")
val subdlApiKey: String = seed("subdlApiKey")
val tmdbApiKey: String = seed("tmdbApiKey")
val traktClientId: String = localProps.getProperty("traktClientId")?.trim() ?: ""
val traktClientSecret: String = localProps.getProperty("traktClientSecret")?.trim() ?: ""
val traktRedirectUri: String = localProps.getProperty("traktRedirectUri")?.trim() ?: "urn:ietf:wg:oauth:2.0:oob"
val ithilMetadataUrl: String = localProps.getProperty("ithilMetadataUrl")?.trim() ?: ""
val ithilIndexKey: String = localProps.getProperty("ithilIndexKey")?.trim() ?: ""
val ithilArchivePrefix: String = localProps.getProperty("ithilArchivePrefix")?.trim() ?: ""

android {
    namespace = "com.shieldstream.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.shieldstream.tv"
        minSdk = 21
        targetSdk = 35
        versionCode = 121
        versionName = "1.0.21"
        manifestPlaceholders["appLabel"] = "@string/app_name"

        buildConfigField("String", "RD_TOKEN", "\"$rdToken\"")
        buildConfigField("String", "SUBDL_API_KEY", "\"$subdlApiKey\"")
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "TRAKT_CLIENT_ID", "\"$traktClientId\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"$traktClientSecret\"")
        buildConfigField("String", "TRAKT_REDIRECT_URI", "\"$traktRedirectUri\"")
        buildConfigField("String", "ITHIL_METADATA_URL", "\"$ithilMetadataUrl\"")
        buildConfigField("String", "ITHIL_INDEX_KEY", "\"$ithilIndexKey\"")
        buildConfigField("String", "ITHIL_ARCHIVE_PREFIX", "\"$ithilArchivePrefix\"")
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

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(localProps.getProperty("releaseStoreFile") ?: "keystore/butaca.keystore")
            storePassword = localProps.getProperty("releaseStorePassword") ?: "android"
            keyAlias = localProps.getProperty("releaseKeyAlias") ?: "androiddebugkey"
            keyPassword = localProps.getProperty("releaseKeyPassword") ?: "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-pruebas"
            manifestPlaceholders["appLabel"] = "Bambi (pruebas)"
        }
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
    implementation(project(":core:strings"))
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
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.tvprovider)
    implementation(libs.coil)
    implementation(files("libs/media3-decoder-av1-1.6.0.aar"))
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
