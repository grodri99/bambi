package com.shieldstream.tv.intro

/** Petición de intro al volver a la app. [reload]: hay que recargar el Inicio y sus carátulas. */
data class ResumeIntro(val reload: Boolean, val at: Long)
