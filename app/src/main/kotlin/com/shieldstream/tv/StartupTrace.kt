package com.shieldstream.tv

import android.os.SystemClock
import android.util.Log

/** Marcas de tiempo del arranque en frío (logcat, etiqueta BambiStartup), para ver dónde se va el tiempo. */
object StartupTrace {
    private val origin = SystemClock.uptimeMillis()
    private var done = false

    fun mark(step: String) {
        if (done) return
        Log.i("BambiStartup", "$step +${SystemClock.uptimeMillis() - origin} ms")
    }

    fun finish(step: String) {
        mark(step)
        done = true
    }
}
