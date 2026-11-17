package com.shieldstream.tv.ui.component

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Copia local de los iconos de perfil (se descargan una vez a la memoria interna, ver ProfileIconWarmer en la app):
 * con ella el icono sale al instante y sin red. Si aún no está, se usa la URL.
 */
object ProfileIconFiles {
    private const val DIR = "profile_icons"

    fun dir(context: Context): File = File(context.filesDir, DIR)

    fun file(context: Context, url: String): File = File(dir(context), sha1(url) + ".img")

    /** Lo que se le pasa a Coil: el archivo local si ya está descargado, o la URL. */
    fun model(context: Context, url: String): Any = file(context, url).takeIf { it.isFile && it.length() > 0 } ?: url

    private fun sha1(text: String): String = MessageDigest.getInstance("SHA-1")
        .digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
