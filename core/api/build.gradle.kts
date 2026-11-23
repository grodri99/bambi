// APIs y DTOs (Retrofit + kotlinx.serialization) en Kotlin puro: los usan la app de Android y la de escritorio.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:model"))
    api(libs.retrofit)
    api(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
