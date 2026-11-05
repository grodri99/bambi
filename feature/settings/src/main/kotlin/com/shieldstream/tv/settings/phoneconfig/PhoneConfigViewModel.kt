package com.shieldstream.tv.settings.phoneconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PhoneConfigUiState {
    data object Loading : PhoneConfigUiState
    data class Ready(val connection: PhoneConfigConnection) : PhoneConfigUiState
    data object Expired : PhoneConfigUiState
    data object Error : PhoneConfigUiState
}

@HiltViewModel
class PhoneConfigViewModel @Inject constructor(
    private val server: PhoneConfigServer,
    private val texts: Texts,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PhoneConfigUiState>(PhoneConfigUiState.Loading)
    val uiState: StateFlow<PhoneConfigUiState> = _uiState.asStateFlow()
    val lastConfig: StateFlow<PhoneConfigDto?> = server.lastConfig
    val lastSavedConfig: StateFlow<PhoneConfigDto?> = server.lastSavedConfig
    private var startJob: Job? = null
    private var expiryJob: Job? = null

    fun start(mode: PhoneConfigMode = PhoneConfigMode.EDIT) {
        if (startJob?.isActive == true || _uiState.value is PhoneConfigUiState.Ready) return
        expiryJob?.cancel()
        _uiState.value = PhoneConfigUiState.Loading
        startJob = viewModelScope.launch {
            val connection = runCatching { withContext(Dispatchers.IO) { server.start(mode = mode) } }.getOrElse {
                _uiState.value = PhoneConfigUiState.Error
                return@launch
            }
            _uiState.value = PhoneConfigUiState.Ready(connection)
            expiryJob = viewModelScope.launch {
                delay((connection.expiresAt - System.currentTimeMillis()).coerceAtLeast(0))
                server.stop()
                _uiState.value = PhoneConfigUiState.Expired
            }
        }
    }

    fun stop() {
        startJob?.cancel()
        expiryJob?.cancel()
        server.stop()
        startJob = null
        expiryJob = null
    }

    fun errorMessage(): String = texts.get(StringsR.string.settings_phone_config_start_failed)

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
