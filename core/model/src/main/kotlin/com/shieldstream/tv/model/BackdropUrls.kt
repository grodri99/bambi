package com.shieldstream.tv.model

/**
 * Picks the TMDB size for full-screen backdrops. Catalog URLs are stored at w1280, which is also
 * what row cards load, so the standard choice shares the image cache with them.
 */
object BackdropUrls {
    private const val STANDARD = "/w1280/"
    private const val LIGHTWEIGHT = "/w780/"
    private const val HIGH_RES = "/original/"

    /** Lightweight UI wins over high resolution: it exists to reduce image work on weak boxes. */
    fun fullScreen(url: String?, lightweight: Boolean, highRes: Boolean): String? = when {
        url == null -> null
        lightweight -> url.replace(STANDARD, LIGHTWEIGHT)
        highRes -> url.replace(STANDARD, HIGH_RES)
        else -> url
    }
}
