package com.shieldstream.tv.desktop

/**
 * Mientras se ve la intro, la ventana le pasa aquí las teclas y los clics (y no llegan a la pantalla de debajo).
 * Así la intro puede ir encima del Inicio sin quitarle el foco: al terminar, todo sigue donde estaba.
 */
object IntroKeyGate {
    @Volatile
    var onKey: (() -> Unit)? = null
}
