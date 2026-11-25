package com.shieldstream.tv.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tiempos del tráiler de la portada, en milisegundos. */
internal object BillboardTrailerTiming {
    /** Espera desde que la portada tiene el foco hasta que arranca el vídeo. */
    const val START_DELAY_MS = 2_500L

    /** Corte del tráiler: pasados estos segundos la portada vuelve a rotar. */
    const val MAX_DURATION_MS = 45_000L

    /** Avance automático del carrusel cuando no hay tráiler en curso. */
    const val ROTATION_MS = 10_000L
}

/**
 * Clave que debe sonar ahora, o `null` si no se cumplen todas las condiciones:
 * ajuste activo, interfaz normal, portada enfocada y app en primer plano.
 */
internal fun trailerToPlay(
    key: String?,
    enabled: Boolean,
    lightweight: Boolean,
    focused: Boolean,
    resumed: Boolean,
): String? = key?.takeIf { it.isNotBlank() && enabled && !lightweight && focused && resumed }

/** La portada solo rota sola si no hay tráiler en curso (y hay más de un destacado). */
internal fun shouldRotateCarousel(
    lightweight: Boolean,
    itemCount: Int,
    trailerActive: Boolean,
): Boolean = !lightweight && itemCount > 1 && !trailerActive

/**
 * Programa el tráiler de la portada. Cada petición cancela la anterior:
 * - [request] con clave espera [startDelayMs] y avisa con la clave; a los [maxDurationMs] avisa con `null`.
 * - [request] con `null` (o [dispose]) corta lo que estuviera sonando y no programa nada.
 *
 * Es una clase aparte de Compose para poder probar los tiempos y las cancelaciones sin un TV.
 */
internal class BillboardTrailerSession(
    private val scope: CoroutineScope,
    private val startDelayMs: Long = BillboardTrailerTiming.START_DELAY_MS,
    private val maxDurationMs: Long = BillboardTrailerTiming.MAX_DURATION_MS,
    private val onPlaying: (String?) -> Unit,
) {
    private var job: Job? = null

    var playingKey: String? = null
        private set

    fun request(key: String?) {
        job?.cancel()
        job = null
        stopPlaying()
        if (key == null) return
        job = scope.launch {
            delay(startDelayMs)
            playingKey = key
            onPlaying(key)
            delay(maxDurationMs)
            playingKey = null
            onPlaying(null)
        }
    }

    fun dispose() {
        job?.cancel()
        job = null
        stopPlaying()
    }

    private fun stopPlaying() {
        if (playingKey == null) return
        playingKey = null
        onPlaying(null)
    }
}
