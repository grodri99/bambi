package com.shieldstream.tv.model

import java.util.Locale

/**
 * Vídeo de TMDB reducido a lo que importa para la portada (`videos.results`).
 *
 * Vive en `core:model` para poder probar la elección sin Android ni Retrofit.
 */
data class TrailerVideo(
    val key: String,
    val site: String,
    val type: String,
    val official: Boolean = false,
    val language: String? = null,
)

/**
 * Elige el tráiler que se reproduce solo en la portada.
 *
 * Reglas, pensadas para un avance **mudo** de 2–3 s de espera:
 * - Solo YouTube (es lo único que TMDB sirve hoy para tráilers).
 * - `Trailer` antes que `Teaser`; se descartan clips, featurettes y extras: suelen destripar la trama.
 * - El idioma de la app solo desempata, porque el avance va sin sonido.
 * - A igualdad, oficial antes que no oficial.
 *
 * Devuelve la clave de YouTube o `null` si no hay nada utilizable.
 */
fun pickTrailer(videos: List<TrailerVideo>, language: String): String? {
    val appLanguage = language.lowercase(Locale.ROOT).substringBefore('-')
    return videos.asSequence()
        .filter { it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank() }
        .mapNotNull { video -> rank(video, appLanguage)?.let { video to it } }
        .maxByOrNull { it.second }
        ?.first
        ?.key
}

/** Mayor es mejor. `null` = no es un tráiler/teaser y no se usa. */
private fun rank(video: TrailerVideo, appLanguage: String): Int? {
    val typeRank = when (video.type.lowercase(Locale.ROOT)) {
        "trailer" -> 2
        "teaser" -> 1
        else -> return null
    }
    val videoLanguage = video.language?.lowercase(Locale.ROOT)?.substringBefore('-')
    val languageRank = when {
        videoLanguage == null || videoLanguage.isEmpty() -> 1
        videoLanguage == appLanguage -> 3
        videoLanguage == "en" -> 2
        else -> 0
    }
    return typeRank * 100 + languageRank * 10 + if (video.official) 1 else 0
}
