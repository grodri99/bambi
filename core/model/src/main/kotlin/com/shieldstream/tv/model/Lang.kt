package com.shieldstream.tv.model

import java.util.Locale

enum class AppLanguage(val code: String, val tmdb: String, val region: String, val label: String) {
    ES("es", "es-ES", "ES", "Español"),
    EN("en", "en-US", "US", "English"),
    ;

    val locale: Locale get() = Locale(code, region)

    companion object {
        const val PREFS = "settings"
        const val PREF_KEY = "app_language"

        fun default(): AppLanguage = if (Locale.getDefault().language == "es") ES else EN

        fun fromName(name: String?): AppLanguage? = entries.firstOrNull { it.name == name }
    }
}

object Lang {
    @Volatile
    var current: AppLanguage = AppLanguage.ES

    val isEnglish: Boolean get() = current == AppLanguage.EN

    val locale: Locale get() = current.locale
}

fun tr(es: String, en: String): String = if (Lang.current == AppLanguage.EN) en else es
