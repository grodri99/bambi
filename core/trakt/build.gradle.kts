// Trakt en Kotlin puro (cliente, sesión y sincronización): lo usan la app de Android y la de escritorio. Lo propio de
// cada plataforma (dónde se guardan los tokens, textos, registro) va detrás de TraktPlatform.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:domain"))
    api(project(":core:api"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
