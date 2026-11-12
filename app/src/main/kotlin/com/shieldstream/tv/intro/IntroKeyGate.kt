package com.shieldstream.tv.intro

/**
 * Mientras se ve la intro, la actividad le pasa aquí las teclas del mando (y no llegan a la pantalla de debajo).
 * Así la intro puede ir encima del Inicio sin quitarle el foco: al terminar, todo sigue donde estaba.
 */
object IntroKeyGate {
    @Volatile
    var onKey: (() -> Unit)? = null
}
