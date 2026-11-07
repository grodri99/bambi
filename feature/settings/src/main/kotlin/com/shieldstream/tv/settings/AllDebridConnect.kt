package com.shieldstream.tv.settings

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.AllDebridAuthPoll
import com.shieldstream.tv.domain.AllDebridAuthRepository
import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.component.TvButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AllDebridConnectUiState {
    data object Loading : AllDebridConnectUiState
    data class ShowPin(val pin: String, val url: String, val secondsLeft: Int) : AllDebridConnectUiState
    data object Connected : AllDebridConnectUiState
    data object Expired : AllDebridConnectUiState
    data class Error(val message: String) : AllDebridConnectUiState
}

@HiltViewModel
class AllDebridConnectViewModel @Inject constructor(
    private val auth: AllDebridAuthRepository,
    private val apiKeys: ApiKeysRepository,
    private val texts: Texts,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AllDebridConnectUiState>(AllDebridConnectUiState.Loading)
    val uiState: StateFlow<AllDebridConnectUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    fun start(onConnected: () -> Unit) {
        job?.cancel()
        _uiState.value = AllDebridConnectUiState.Loading
        job = viewModelScope.launch {
            val pin = auth.requestPin().getOrElse {
                _uiState.value = AllDebridConnectUiState.Error(
                    texts.get(StringsR.string.settings_couldn_t_connect_to_alldebrid),
                )
                return@launch
            }
            val deadline = System.currentTimeMillis() + pin.expiresInSeconds * 1000L
            var nextPoll = 0L
            while (true) {
                val now = System.currentTimeMillis()
                if (now >= deadline) {
                    _uiState.value = AllDebridConnectUiState.Expired
                    return@launch
                }
                _uiState.value = AllDebridConnectUiState.ShowPin(
                    pin = pin.pin,
                    url = pin.verificationUrl,
                    secondsLeft = ((deadline - now) / 1000).toInt(),
                )
                if (now >= nextPoll) {
                    when (val result = auth.pollPin(pin)) {
                        AllDebridAuthPoll.Pending -> Unit
                        is AllDebridAuthPoll.Connected -> {
                            try {
                                apiKeys.set(ApiService.ALLDEBRID, result.apiKey)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                _uiState.value = AllDebridConnectUiState.Error(
                                    texts.get(StringsR.string.settings_couldn_t_connect_to_alldebrid),
                                )
                                return@launch
                            }
                            _uiState.value = AllDebridConnectUiState.Connected
                            onConnected()
                            return@launch
                        }
                        AllDebridAuthPoll.Expired -> {
                            _uiState.value = AllDebridConnectUiState.Expired
                            return@launch
                        }
                        is AllDebridAuthPoll.Failed -> {
                            _uiState.value = AllDebridConnectUiState.Error(result.message)
                            return@launch
                        }
                    }
                    nextPoll = System.currentTimeMillis() + POLL_INTERVAL_MS
                }
                delay(1000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}

@Composable
fun AllDebridQrPanel(
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AllDebridConnectViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnConnected by rememberUpdatedState(onConnected)
    LaunchedEffect(viewModel) {
        viewModel.start { currentOnConnected() }
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.stop() }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(250.dp)
            .background(AppColors.Surface, AppShapes.Panel)
            .border(1.dp, Color.White.copy(alpha = 0.08f), AppShapes.Panel)
            .padding(20.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(190.dp).background(Color.White, AppShapes.Control).padding(12.dp),
        ) {
            when (val s = state) {
                is AllDebridConnectUiState.ShowPin -> QrCode(s.url, Modifier.fillMaxSize())
                AllDebridConnectUiState.Loading -> PanelMessage(localizedStringResource(StringsR.string.settings_getting_alldebrid_pin))
                AllDebridConnectUiState.Connected -> PanelMessage("✓")
                AllDebridConnectUiState.Expired -> PanelMessage(localizedStringResource(StringsR.string.settings_the_code_has_expired))
                is AllDebridConnectUiState.Error -> PanelMessage(s.message)
            }
        }
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            AllDebridConnectUiState.Loading -> Unit
            is AllDebridConnectUiState.ShowPin -> {
                Text(
                    localizedStringResource(StringsR.string.settings_scan_to_connect_alldebrid),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    s.pin,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_waiting_the_code_expires_in) +
                        "%d:%02d".format(s.secondsLeft / 60, s.secondsLeft % 60),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f),
                    textAlign = TextAlign.Center,
                )
            }
            AllDebridConnectUiState.Connected -> PanelStatus(localizedStringResource(StringsR.string.settings_connected), AppColors.Success)
            AllDebridConnectUiState.Expired -> {
                PanelStatus(localizedStringResource(StringsR.string.settings_the_code_has_expired), Color.White)
                Spacer(Modifier.height(10.dp))
                TvButton(localizedStringResource(StringsR.string.settings_get_a_new_code), onClick = { viewModel.start { currentOnConnected() } })
            }
            is AllDebridConnectUiState.Error -> {
                PanelStatus(s.message, AppColors.Error)
                Spacer(Modifier.height(10.dp))
                TvButton(localizedStringResource(StringsR.string.settings_retry), onClick = { viewModel.start { currentOnConnected() } })
            }
        }
    }
}

@Composable
private fun PanelMessage(text: String) {
    Text(text, color = AppColors.Surface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
}

@Composable
private fun PanelStatus(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.Center)
}
