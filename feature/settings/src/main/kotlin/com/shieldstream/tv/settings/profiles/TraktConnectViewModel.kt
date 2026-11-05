package com.shieldstream.tv.settings.profiles

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.TraktAuthPoll
import com.shieldstream.tv.domain.TraktRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TraktConnectUiState {
    data object Loading : TraktConnectUiState

    data class ShowCode(val userCode: String, val url: String, val secondsLeft: Int) : TraktConnectUiState

    data class Connected(val username: String) : TraktConnectUiState

    data object Expired : TraktConnectUiState

    data class Error(val message: String) : TraktConnectUiState
}

@HiltViewModel
class TraktConnectViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val trakt: TraktRepository,
    private val texts: Texts,
) : ViewModel() {

    private val profileId: String = checkNotNull(savedState.get<String>("id"))

    private val _uiState = MutableStateFlow<TraktConnectUiState>(TraktConnectUiState.Loading)
    val uiState: StateFlow<TraktConnectUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        start()
    }

    fun start() {
        job?.cancel()
        _uiState.value = TraktConnectUiState.Loading
        job = viewModelScope.launch {
            val code = trakt.requestDeviceCode().getOrElse {
                _uiState.value = TraktConnectUiState.Error(
                    it.message ?: texts.get(StringsR.string.settings_couldn_t_reach_trakt),
                )
                return@launch
            }
            val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
            var interval = code.intervalSeconds.coerceAtLeast(1) * 1000L
            var nextPoll = System.currentTimeMillis() + interval
            while (true) {
                val now = System.currentTimeMillis()
                if (now >= deadline) {
                    _uiState.value = TraktConnectUiState.Expired
                    return@launch
                }
                _uiState.value = TraktConnectUiState.ShowCode(code.userCode, code.verificationUrl, ((deadline - now) / 1000).toInt())
                if (now >= nextPoll) {
                    when (val result = trakt.pollDeviceToken(profileId, code)) {
                        is TraktAuthPoll.Connected -> {
                            _uiState.value = TraktConnectUiState.Connected(result.username)
                            return@launch
                        }
                        TraktAuthPoll.Pending -> Unit
                        TraktAuthPoll.SlowDown -> interval += 1000
                        TraktAuthPoll.Expired -> {
                            _uiState.value = TraktConnectUiState.Expired
                            return@launch
                        }
                        TraktAuthPoll.Denied -> {
                            _uiState.value = TraktConnectUiState.Error(
                                texts.get(StringsR.string.settings_you_declined_access_on_trakt),
                            )
                            return@launch
                        }
                        is TraktAuthPoll.Failed -> {
                            _uiState.value = TraktConnectUiState.Error(result.message)
                            return@launch
                        }
                    }
                    nextPoll = System.currentTimeMillis() + interval
                }
                delay(1000)
            }
        }
    }
}
