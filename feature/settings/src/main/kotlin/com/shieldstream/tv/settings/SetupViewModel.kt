package com.shieldstream.tv.settings

import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.SubtitlePreference
import com.shieldstream.tv.strings.Texts
import com.shieldstream.tv.strings.R.string as StringResources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.KeyCheck
import com.shieldstream.tv.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupStep(val number: Int?) {
    WELCOME(null),
    TMDB(1),
    DEBRID(2),
    SUBDL(3),
    PLAYBACK(4),
    DONE(null),
}

enum class SetupAudioPreference { ORIGINAL, SPANISH, SPANISH_ONLY }

private data class SetupPlaybackPreferences(
    val language: AppLanguage,
    val spanishFirst: Boolean,
    val spanishOnly: Boolean,
    val subtitles: SubtitlePreference,
    val experimentalSubtitleSync: Boolean,
)

sealed interface SetupUiState {
    data object Loading : SetupUiState
    data class Ready(
        val step: SetupStep,
        val debrid: DebridService,
        val keys: Map<ApiService, String?>,
        val details: Map<ApiService, String>,
        val checking: Boolean,
        val error: String?,
        val audio: SetupAudioPreference,
        val subtitles: SubtitlePreference,
        val experimentalSubtitleSync: Boolean = true,
        val language: AppLanguage,
    ) : SetupUiState {
        val languageChanged: Boolean get() = language != com.shieldstream.tv.model.Lang.current
        fun has(service: ApiService) = !keys[service].isNullOrBlank()
    }
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val apiKeys: ApiKeysRepository,
    private val settings: SettingsRepository,
    private val texts: Texts,
) : ViewModel() {

    private val step = MutableStateFlow(SetupStep.WELCOME)
    private val selectedLanguage = MutableStateFlow(settings.language)
    private val details = MutableStateFlow<Map<ApiService, String>>(emptyMap())
    private val checking = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val status = combine(details, checking, error) { d, c, e -> Triple(d, c, e) }
    private val playback = combine(
        selectedLanguage,
        settings.spanishFirst,
        settings.spanishOnlySources,
        settings.subtitlePreference,
        settings.experimentalSubtitleSync,
        ::SetupPlaybackPreferences,
    )

    val uiState: StateFlow<SetupUiState> = combine(
        step,
        settings.debridService,
        apiKeys.keys,
        status,
        playback,
    ) { s, debrid, keys, (d, c, e), preferences ->
        SetupUiState.Ready(
            step = s,
            debrid = debrid,
            keys = keys,
            details = d,
            checking = c,
            error = e,
            audio = when {
                preferences.spanishOnly -> SetupAudioPreference.SPANISH_ONLY
                preferences.spanishFirst -> SetupAudioPreference.SPANISH
                else -> SetupAudioPreference.ORIGINAL
            },
            subtitles = preferences.subtitles,
            experimentalSubtitleSync = preferences.experimentalSubtitleSync,
            language = preferences.language,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState.Loading)

    fun next() {
        val current = step.value
        step.value = SetupStep.entries.getOrElse(current.ordinal + 1) { SetupStep.DONE }
        error.value = null
    }

    fun back(): Boolean {
        if (checking.value) return true
        val current = step.value
        if (current == SetupStep.WELCOME) return false
        step.value = SetupStep.entries[current.ordinal - 1]
        error.value = null
        return true
    }

    fun setLanguage(language: AppLanguage) {
        selectedLanguage.value = language
        viewModelScope.launch { settings.setLanguage(language) }
    }

    fun setDebrid(service: DebridService) {
        error.value = null
        viewModelScope.launch { settings.setDebridService(service) }
    }

    fun setAudio(preference: SetupAudioPreference) {
        viewModelScope.launch {
            settings.setSpanishOnlySources(preference == SetupAudioPreference.SPANISH_ONLY)
            settings.setSpanishFirst(preference != SetupAudioPreference.ORIGINAL)
        }
    }

    fun setSubtitles(preference: SubtitlePreference) {
        viewModelScope.launch { settings.setSubtitlePreference(preference) }
    }

    fun setExperimentalSubtitleSync(enabled: Boolean) {
        viewModelScope.launch { settings.setExperimentalSubtitleSync(enabled) }
    }

    fun submitKey(service: ApiService, input: String) {
        val key = input.trim()
        if (key.isEmpty()) {
            error.value = texts.get(StringResources.setup_key_required)
            return
        }
        if (checking.value) return
        checking.value = true
        error.value = null
        viewModelScope.launch {
            when (val result = apiKeys.check(service, key)) {
                is KeyCheck.Valid -> {
                    apiKeys.set(service, key)
                    details.update { it + (service to (result.detail ?: texts.get(StringResources.setup_connected))) }
                }
                is KeyCheck.Invalid -> error.value = result.message
            }
            checking.value = false
        }
    }

    fun completeAllDebridConnect() {
        error.value = null
        details.update { it + (ApiService.ALLDEBRID to texts.get(StringResources.setup_connected)) }
    }

    fun clearError() {
        error.value = null
    }
}

@HiltViewModel
class SetupGateViewModel @Inject constructor(apiKeys: ApiKeysRepository) : ViewModel() {
    val needsSetup: Boolean = apiKeys.keys.value.let { keys ->
        keys[ApiService.TMDB].isNullOrBlank() ||
            ApiService.entries.filter { it.isDebrid }.all { keys[it].isNullOrBlank() }
    }
}
