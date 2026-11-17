package com.shieldstream.tv.intro

import androidx.lifecycle.ViewModel
import com.shieldstream.tv.home.HomePreloader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Puente entre la intro / la carga del perfil y la precarga del Inicio. */
@HiltViewModel
class IntroViewModel @Inject constructor(
    private val preloader: HomePreloader,
    private val profileIcons: com.shieldstream.tv.profileicons.ProfileIconWarmer,
) : ViewModel() {
    fun start() {
        preloader.start()
        // Los iconos de los perfiles, para que "¿Quién está viendo?" los tenga ya (solo descarga, sin CPU).
        profileIcons.warmProfiles()
    }

    /** Tras la intro: el resto de iconos del selector, sin prisa (solo los que falten). */
    fun warmAllProfileIcons() = profileIcons.warmAll()
    fun refresh() = preloader.refresh()
    fun isReady(): Boolean = preloader.isReady()
    fun setUiBusy(busy: Boolean) = preloader.setUiBusy(busy)
    suspend fun awaitReady(timeoutMs: Long): Boolean = preloader.awaitReady(timeoutMs)
}
