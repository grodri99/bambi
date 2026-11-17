package com.shieldstream.tv.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Temporizador de apagado del reproductor: la opción elegida, los minutos que quedan y la cuenta atrás. Al vencer (o
 * al acabar el episodio, si es esa la opción) llama a [onFinished], que pausa y vuelve a Inicio.
 */
internal class SleepTimerController(
    private val scope: CoroutineScope,
    private val onFinished: () -> Unit,
) {
    private val _timer = MutableStateFlow<SleepTimer>(SleepTimer.Off)
    val timer: StateFlow<SleepTimer> = _timer.asStateFlow()

    /** Minutos que quedan, redondeados hacia arriba (null si no hay cuenta atrás). */
    private val _remainingMinutes = MutableStateFlow<Int?>(null)
    val remainingMinutes: StateFlow<Int?> = _remainingMinutes.asStateFlow()
    private var job: Job? = null

    val endsWithEpisode: Boolean get() = _timer.value == SleepTimer.EndOfEpisode

    /** Pasa a la siguiente opción (desactivado → 15 → 30 → 45 → 60 → 90 → fin del episodio → desactivado). */
    fun cycle() {
        val next = when (val current = _timer.value) {
            SleepTimer.Off -> SleepTimer.Minutes(15)
            is SleepTimer.Minutes -> when (current.minutes) {
                15 -> SleepTimer.Minutes(30)
                30 -> SleepTimer.Minutes(45)
                45 -> SleepTimer.Minutes(60)
                60 -> SleepTimer.Minutes(90)
                else -> SleepTimer.EndOfEpisode
            }
            SleepTimer.EndOfEpisode -> SleepTimer.Off
        }
        set(next)
    }

    private fun set(timer: SleepTimer) {
        cancel()
        _timer.value = timer
        when (timer) {
            SleepTimer.Off, SleepTimer.EndOfEpisode -> _remainingMinutes.value = null
            is SleepTimer.Minutes -> {
                _remainingMinutes.value = timer.minutes
                job = scope.launch {
                    var remaining = timer.minutes * 60_000L
                    while (isActive && remaining > 0) {
                        val step = minOf(remaining, 5_000L)
                        delay(step)
                        remaining -= step
                        val minutesLeft = ((remaining + 59_999L) / 60_000L).toInt()
                        if (minutesLeft != _remainingMinutes.value) {
                            _remainingMinutes.value = minutesLeft
                        }
                    }
                    if (isActive) finish()
                }
            }
        }
    }

    /** Vence ahora (si estaba puesto): se desactiva y avisa con [onFinished]. */
    fun finish() {
        if (_timer.value == SleepTimer.Off) return
        reset()
        onFinished()
    }

    /** Lo desactiva sin avisar (al salir del reproductor). */
    fun reset() {
        cancel()
        _timer.value = SleepTimer.Off
        _remainingMinutes.value = null
    }

    /** Solo para la cuenta atrás (al destruir el ViewModel). */
    fun cancel() {
        job?.cancel()
        job = null
    }
}
