// Búsqueda de fuentes (todos los buscadores) en Kotlin puro: la usan la app de Android y la de escritorio.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:domain"))
    api(project(":core:api"))
    implementation(libs.kotlinx.coroutines.core)
    // org.json viene con Android; en escritorio lo pone la app (:desktop).
    compileOnly("org.json:json:20240303")
    testImplementation(libs.junit)
}
