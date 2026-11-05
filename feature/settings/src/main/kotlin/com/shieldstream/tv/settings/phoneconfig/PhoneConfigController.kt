package com.shieldstream.tv.settings.phoneconfig

import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.KeyCheck
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.SubtitlePreference
import com.shieldstream.tv.settings.SetupAudioPreference
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import com.shieldstream.tv.strings.keyNameResource
import com.shieldstream.tv.strings.sectionResource
import com.shieldstream.tv.strings.titleResource
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

enum class PhoneConfigMode { EDIT, SETUP }

@Serializable
data class PhoneOptionDto(val id: String, val title: String)

@Serializable
data class PhoneKeyDto(
    val service: String,
    val title: String,
    val keyLabel: String,
    val helpUrl: String,
    val configured: Boolean,
    val last4: String? = null,
    val required: Boolean,
)

@Serializable
data class PhoneProviderGroupDto(
    val title: String,
    val providers: List<String>,
    val enabled: Boolean,
)

@Serializable
data class PhonePreferencesDto(
    val lightweightUi: Boolean,
    val experimentalSubtitleSync: Boolean,
    val autoNextEpisode: Boolean,
)

@Serializable
data class PhoneSetupDto(
    val audio: String,
    val subtitles: String,
    val complete: Boolean,
)

@Serializable
data class PhoneConfigDto(
    val language: String,
    val debrid: String,
    val debridOptions: List<PhoneOptionDto>,
    val keys: List<PhoneKeyDto>,
    val providerGroups: List<PhoneProviderGroupDto>,
    val preferences: PhonePreferencesDto,
    val setup: PhoneSetupDto? = null,
)

@Serializable
data class PhoneKeyUpdateDto(
    val service: String,
    val value: String? = null,
    val remove: Boolean = false,
)

@Serializable
data class PhoneConfigUpdateDto(
    val debrid: String,
    val providers: List<String>,
    val lightweightUi: Boolean,
    val experimentalSubtitleSync: Boolean,
    val autoNextEpisode: Boolean,
    val keys: List<PhoneKeyUpdateDto> = emptyList(),
    val language: String? = null,
    val audio: String? = null,
    val subtitles: String? = null,
)

sealed interface PhoneConfigUpdateResult {
    data class Success(val config: PhoneConfigDto) : PhoneConfigUpdateResult
    data class ValidationFailed(val errors: Map<String, String>) : PhoneConfigUpdateResult
}

class PhoneConfigController @Inject constructor(
    private val apiKeys: ApiKeysRepository,
    private val settings: SettingsRepository,
    private val texts: Texts,
) {
    private var selectedSetupLanguage: AppLanguage? = null

    suspend fun snapshot(mode: PhoneConfigMode = PhoneConfigMode.EDIT): PhoneConfigDto {
        val keys = apiKeys.keys.value
        val debrid = settings.debridService.first()
        val enabledProviders = settings.sourceProviders.first()
        val setup = if (mode == PhoneConfigMode.SETUP) {
            PhoneSetupDto(
                audio = when {
                    settings.spanishOnlySources.first() -> SetupAudioPreference.SPANISH_ONLY
                    settings.spanishFirst.first() -> SetupAudioPreference.SPANISH
                    else -> SetupAudioPreference.ORIGINAL
                }.name,
                subtitles = settings.subtitlePreference.first().name,
                complete = setupComplete(keys, debrid),
            )
        } else {
            null
        }
        return PhoneConfigDto(
            language = (if (mode == PhoneConfigMode.SETUP) selectedSetupLanguage ?: settings.language else settings.language).name.lowercase(),
            debrid = debrid.name,
            debridOptions = DebridService.entries.map { PhoneOptionDto(it.name, it.title) },
            keys = ApiService.entries
                .filter { mode == PhoneConfigMode.EDIT || it != ApiService.AIOSTREAMS }
                .map { service ->
                    val key = keys[service]
                    PhoneKeyDto(
                        service = service.name,
                        title = texts.get(service.titleResource),
                        keyLabel = texts.get(service.keyNameResource),
                        helpUrl = service.url,
                        configured = !key.isNullOrBlank(),
                        last4 = key?.takeLast(4),
                        required = service.required || (mode == PhoneConfigMode.SETUP && service == debrid.apiService),
                    )
                },
            providerGroups = SourceProvider.entries
                .groupBy { texts.get(it.sectionResource) }
                .map { (title, group) ->
                    PhoneProviderGroupDto(
                        title = title,
                        providers = group.map { it.name },
                        enabled = group.all { it in enabledProviders },
                    )
                },
            preferences = PhonePreferencesDto(
                lightweightUi = settings.lightweightUi.first(),
                experimentalSubtitleSync = settings.experimentalSubtitleSync.first(),
                autoNextEpisode = settings.autoNextEpisode.first(),
            ),
            setup = setup,
        )
    }

    suspend fun update(
        request: PhoneConfigUpdateDto,
        mode: PhoneConfigMode = PhoneConfigMode.EDIT,
    ): PhoneConfigUpdateResult {
        val debrid = request.debrid.toEnum<DebridService>()
        val providers = request.providers.map { it.toEnum<SourceProvider>() }.toSet()
        require(providers.isNotEmpty()) { texts.get(StringsR.string.settings_phone_config_source_required) }
        require(request.keys.map { it.service }.distinct().size == request.keys.size) {
            texts.get(StringsR.string.settings_phone_config_invalid_request)
        }
        val setupChanges = if (mode == PhoneConfigMode.SETUP) {
            Triple(
                request.language?.toEnum<AppLanguage>(),
                request.audio?.toEnum<SetupAudioPreference>(),
                request.subtitles?.toEnum<SubtitlePreference>(),
            )
        } else {
            require(request.language == null && request.audio == null && request.subtitles == null) {
                texts.get(StringsR.string.settings_phone_config_invalid_request)
            }
            null
        }

        val changes = request.keys.map { change ->
            val service = change.service.toEnum<ApiService>()
            Triple(service, change.value?.trim(), change.remove)
        }
        val errors = linkedMapOf<String, String>()
        for ((service, value, remove) in changes) {
            val required = service.required || (mode == PhoneConfigMode.SETUP && service == debrid.apiService)
            when {
                remove && required -> errors[service.name] = texts.get(StringsR.string.settings_phone_config_required_key)
                remove && value != null -> errors[service.name] = texts.get(StringsR.string.settings_phone_config_invalid_request)
                !remove && value != null && value.isEmpty() -> errors[service.name] = texts.get(StringsR.string.settings_phone_config_key_required)
            }
        }
        if (errors.isNotEmpty()) return PhoneConfigUpdateResult.ValidationFailed(errors)

        for ((service, value, remove) in changes) {
            if (remove || value == null) continue
            when (val result = apiKeys.check(service, value)) {
                is KeyCheck.Valid -> Unit
                is KeyCheck.Invalid -> errors[service.name] = result.message
            }
        }
        if (errors.isNotEmpty()) return PhoneConfigUpdateResult.ValidationFailed(errors)

        for ((service, value, remove) in changes) {
            when {
                remove -> apiKeys.set(service, null)
                value != null -> apiKeys.set(service, value)
            }
        }
        settings.setDebridService(debrid)
        settings.setSourceProviders(providers)
        settings.setLightweightUi(request.lightweightUi)
        settings.setExperimentalSubtitleSync(request.experimentalSubtitleSync)
        settings.setAutoNextEpisode(request.autoNextEpisode)
        if (setupChanges != null) {
            val (language, audio, subtitles) = setupChanges
            if (language != null) {
                settings.setLanguage(language)
                selectedSetupLanguage = language
            }
            if (audio != null) {
                settings.setSpanishOnlySources(audio == SetupAudioPreference.SPANISH_ONLY)
                settings.setSpanishFirst(audio != SetupAudioPreference.ORIGINAL)
            }
            if (subtitles != null) settings.setSubtitlePreference(subtitles)
        }
        return PhoneConfigUpdateResult.Success(snapshot(mode))
    }

    private fun setupComplete(keys: Map<ApiService, String?>, debrid: DebridService): Boolean =
        !keys[ApiService.TMDB].isNullOrBlank() && !keys[debrid.apiService].isNullOrBlank()

    private inline fun <reified T : Enum<T>> String.toEnum(): T =
        enumValues<T>().firstOrNull { it.name == this }
            ?: throw IllegalArgumentException(texts.get(StringsR.string.settings_phone_config_invalid_request))
}
