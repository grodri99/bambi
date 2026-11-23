package com.shieldstream.tv.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap

/**
 * Imágenes que van dentro de la app: las mismas de la tele (logo, letras de BAMBI, gatitos de perfil), que el
 * build de escritorio coge de core/ui y de feature/settings.
 */
object BundledImages {
    private val cache = HashMap<String, ImageBitmap?>()

    fun get(path: String): ImageBitmap? = synchronized(cache) {
        cache.getOrPut(path) {
            runCatching {
                BundledImages::class.java.classLoader.getResourceAsStream(path)?.use { input ->
                    org.jetbrains.skia.Image.makeFromEncoded(input.readBytes()).toComposeImageBitmap()
                }
            }.getOrNull()
        }
    }

    /** La misma imagen para AWT (icono de la bandeja de Windows). */
    fun awtImage(path: String): java.awt.Image? =
        runCatching { BundledImages::class.java.classLoader.getResourceAsStream(path)?.use { javax.imageio.ImageIO.read(it) } }.getOrNull()

    fun text(path: String): String? =
        BundledImages::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }

    /** Los iconos de perfil de la tele se guardan como file:///android_asset/…; aquí son recursos. */
    const val ANDROID_ASSET = "file:///android_asset/"
}
