package com.shieldstream.tv.settings

import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.tr
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.KeyCheck
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.media.PlaybackPolicyProvider
import com.shieldstream.tv.model.DeviceCapabilities
import com.shieldstream.tv.model.DeviceOptimization
import com.shieldstream.tv.model.MemoryProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado de la clave de un servicio. */
sealed interface ServiceStatus {
    data object Missing : ServiceStatus
    data object Checking : ServiceStatus
    data class Ok(val detail: String?) : ServiceStatus
    data class Bad(val message: String) : ServiceStatus
}

data class ServiceUi(
    val service: ApiService,
    /** Últimos 4 caracteres a la vista, el resto con puntos. null = sin clave. */
    val maskedKey: String?,
    val status: ServiceStatus,
)

/** Ventana para escribir una clave nueva. */
data class KeyEditor(
    val service: ApiService,
    val hasKey: Boolean,
    val busy: Boolean = false,
    val error: String? = null,
)

private data class PlaybackSettings(
    val bufferSeconds: Int,
    val seekThumbnails: Boolean,
    val debrid: DebridService,
    val optimization: DeviceOptimization,
)

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Ready(
        val services: List<ServiceUi>,
        val bufferSeconds: Int,
        /** ◀▶ en el reproductor: miniaturas (true) o saltos de 10 s (false). */
        val seekThumbnails: Boolean,
        /** Servicio debrid en uso. */
        val debrid: DebridService,
        val optimization: DeviceOptimization,
        val capabilities: DeviceCapabilities,
        val memoryProfile: MemoryProfile,
        val editor: KeyEditor?,
        /** Idioma con el que arrancó la app (cambiarlo la reinicia). */
        val language: AppLanguage = Lang.current,
    ) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeys: ApiKeysRepository,
    private val settings: SettingsRepository,
    private val playbackPolicies: PlaybackPolicyProvider,
) : ViewModel() {

    private val statuses = MutableStateFlow<Map<ApiService, ServiceStatus>>(emptyMap())
    private val editor = MutableStateFlow<KeyEditor?>(null)

    private val playback = combine(
        settings.maxBufferSeconds,
        settings.seekThumbnails,
        settings.debridService,
        settings.deviceOptimization,
    ) { buffer, thumbnails, debrid, optimization ->
        PlaybackSettings(buffer, thumbnails, debrid, optimization)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        apiKeys.keys,
        statuses,
        playback,
        editor,
    ) { keys, status, playback, edit ->
        val policy = playbackPolicies.resolve(
            playback.optimization,
            playback.bufferSeconds,
            playback.seekThumbnails,
        )
        SettingsUiState.Ready(
            services = ApiService.entries.map { service ->
                val key = keys[service]
                ServiceUi(
                    service = service,
                    maskedKey = key?.let(::mask),
                    status = if (key.isNullOrBlank()) ServiceStatus.Missing else status[service] ?: ServiceStatus.Checking,
                )
            },
            bufferSeconds = policy.maxBufferSeconds,
            seekThumbnails = policy.seekThumbnails,
            debrid = playback.debrid,
            optimization = playback.optimization,
            capabilities = playbackPolicies.capabilities,
            memoryProfile = policy.memoryProfile,
            editor = edit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    init {
        // Al entrar se prueban todas las claves guardadas (en paralelo) para enseñar su estado real.
        apiKeys.keys.value.forEach { (service, key) ->
            if (!key.isNullOrBlank()) {
                viewModelScope.launch {
                    val result = apiKeys.check(service, key).toStatus()
                    statuses.update { it + (service to result) }
                }
            }
        }
    }

    /** Guarda el idioma y avisa para reiniciar la app (lo hace la pantalla, que tiene el Context). */
    fun setLanguage(language: AppLanguage, onSaved: () -> Unit) {
        if (language == settings.language) return
        viewModelScope.launch {
            settings.setLanguage(language)
            onSaved()
        }
    }

    /** Interfaz ligera para TV boxes de baja potencia. */
    val lightweightUi: StateFlow<Boolean> =
        settings.lightweightUi.stateIn(viewModelScope, SharingStarted.Eagerly, settings.lightweightUiEnabled)

    fun setLightweightUi(enabled: Boolean) {
        viewModelScope.launch { settings.setLightweightUi(enabled) }
    }

    /** Buscadores de fuentes activados (se pinta aparte del resto del estado). */
    val providers: StateFlow<Set<SourceProvider>> =
        settings.sourceProviders.stateIn(viewModelScope, SharingStarted.Eagerly, SourceProvider.entries.toSet())

    /** Activa o desactiva un buscador; siempre queda al menos uno. */
    fun toggleProvider(p: SourceProvider) {
        viewModelScope.launch {
            val current = settings.sourceProviders.first()
            // Toda la sección a la vez (los buscadores propios van juntos bajo "The Pirate Bay").
            val group = SourceProvider.entries.filter { it.section == p.section }.toSet()
            val next = if (group.all { it in current }) current - group else current + group
            if (next.isNotEmpty()) settings.setSourceProviders(next)
        }
    }

    fun openEditor(service: ApiService) {
        editor.value = KeyEditor(service, hasKey = !apiKeys.keys.value[service].isNullOrBlank())
    }

    fun closeEditor() {
        if (editor.value?.busy != true) editor.value = null
    }

    /** Solo se guarda si el servicio la acepta: así nunca se queda una clave rota guardada. */
    fun saveKey(input: String) {
        val current = editor.value ?: return
        val key = input.trim()
        if (key.isEmpty()) {
            editor.value = current.copy(error = tr("Escribe la clave primero", "Type the key first"))
            return
        }
        editor.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            when (val result = apiKeys.check(current.service, key)) {
                is KeyCheck.Valid -> {
                    apiKeys.set(current.service, key)
                    statuses.update { it + (current.service to ServiceStatus.Ok(result.detail)) }
                    editor.value = null
                }
                is KeyCheck.Invalid -> editor.value = current.copy(busy = false, error = result.message)
            }
        }
    }

    /** Solo para servicios opcionales (SubDL). */
    fun removeKey() {
        val current = editor.value ?: return
        if (current.service.required) return
        viewModelScope.launch {
            apiKeys.set(current.service, null)
            statuses.update { it - current.service }
            editor.value = null
        }
    }

    fun setDebrid(service: DebridService) {
        viewModelScope.launch { settings.setDebridService(service) }
    }

    fun setDeviceOptimization(optimization: DeviceOptimization) {
        viewModelScope.launch { settings.setDeviceOptimization(optimization) }
    }

    /** Series: siguiente episodio automático. */
    val autoNextEpisode: StateFlow<Boolean> = settings.autoNextEpisode
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAutoNextEpisode(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoNextEpisode(enabled) }
    }

    fun setSeekThumbnails(enabled: Boolean) {
        viewModelScope.launch {
            if (settings.deviceOptimization.first() == DeviceOptimization.AUTOMATIC) return@launch
            settings.setSeekThumbnails(enabled)
        }
    }

    fun changeBuffer(deltaSeconds: Int) {
        viewModelScope.launch {
            if (settings.deviceOptimization.first() == DeviceOptimization.AUTOMATIC) return@launch
            val now = settings.maxBufferSeconds.first()
            settings.setMaxBufferSeconds(now + deltaSeconds)
        }
    }

    private fun KeyCheck.toStatus(): ServiceStatus = when (this) {
        is KeyCheck.Valid -> ServiceStatus.Ok(detail)
        is KeyCheck.Invalid -> ServiceStatus.Bad(message)
    }

    private fun mask(key: String): String = if (key.length <= 4) "••••" else "••••••••  " + key.takeLast(4)
}
