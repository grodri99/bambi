package com.shieldstream.tv.data.repository

import android.content.Context
import com.shieldstream.tv.domain.IthilAccessRepository
import com.shieldstream.tv.domain.IthilLibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IthilAccessRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    library: IthilLibraryRepository,
) : IthilAccessRepository {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutableUnlocked: MutableStateFlow<Boolean>
    override val unlocked: StateFlow<Boolean> get() = mutableUnlocked
    override val isUnlocked: Boolean get() = mutableUnlocked.value

    init {
        // En Bambi Ithil viene activado de serie (en Franklix se desbloqueaba con un gesto oculto en Ajustes).
        // Solo funciona si la app se compila con su configuración (ithilMetadataUrl/ithilIndexKey/ithilArchivePrefix)
        // y hay clave de AllDebrid.
        val stored = prefs.getBoolean(KEY_UNLOCKED, true)
        val initial = stored || library.isReady()
        if (initial && !stored) prefs.edit().putBoolean(KEY_UNLOCKED, true).apply()
        mutableUnlocked = MutableStateFlow(initial)
    }

    override fun unlock() {
        if (mutableUnlocked.value) return
        prefs.edit().putBoolean(KEY_UNLOCKED, true).apply()
        mutableUnlocked.value = true
    }

    private companion object {
        const val PREFS = "ithil_access"
        const val KEY_UNLOCKED = "unlocked"
    }
}
