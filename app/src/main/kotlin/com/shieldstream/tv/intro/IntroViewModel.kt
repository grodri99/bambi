package com.shieldstream.tv.intro

import androidx.lifecycle.ViewModel
import com.shieldstream.tv.home.HomePreloader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Puente entre la intro / la carga del perfil y la precarga del Inicio. */
@HiltViewModel
class IntroViewModel @Inject constructor(private val preloader: HomePreloader) : ViewModel() {
    fun start() = preloader.start()
    fun isReady(): Boolean = preloader.isReady()
    suspend fun awaitReady(timeoutMs: Long): Boolean = preloader.awaitReady(timeoutMs)
}
