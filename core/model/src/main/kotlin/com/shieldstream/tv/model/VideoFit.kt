package com.shieldstream.tv.model

/** Cómo encaja el vídeo en la pantalla cuando no tiene su misma forma (las bandas negras de las películas). */
enum class VideoFit {
    /** Imagen completa, con bandas negras si hace falta. */
    FIT,

    /** Sin bandas: amplía hasta llenar la pantalla y recorta lo que sobra (los lados). */
    ZOOM,

    /** Sin bandas: estira la imagen hasta llenar la pantalla (deforma). */
    STRETCH,
    ;

    fun next(): VideoFit = entries[(ordinal + 1) % entries.size]
}
