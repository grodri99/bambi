package com.shieldstream.tv.domain

import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.DeviceOptimization
import com.shieldstream.tv.model.SubtitleAppearance
import com.shieldstream.tv.model.SubtitlePreference
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val token: Flow<String?>
    suspend fun setToken(token: String?)

    val catalogProvider: Flow<CatalogProvider>
    suspend fun setCatalogProvider(provider: CatalogProvider)

    val deviceOptimization: Flow<DeviceOptimization>
    suspend fun setDeviceOptimization(optimization: DeviceOptimization)

    val maxBufferSeconds: Flow<Int>
    suspend fun setMaxBufferSeconds(seconds: Int)

    val lightweightUi: Flow<Boolean>
    val lightweightUiEnabled: Boolean
    suspend fun setLightweightUi(enabled: Boolean)

    val seekThumbnails: Flow<Boolean>
    suspend fun setSeekThumbnails(enabled: Boolean)

    val language: AppLanguage
    suspend fun setLanguage(language: AppLanguage)

    val spanishFirst: Flow<Boolean>
    suspend fun setSpanishFirst(enabled: Boolean)

    val spanishOnlySources: Flow<Boolean>
    suspend fun setSpanishOnlySources(enabled: Boolean)

    val subtitlePreference: Flow<SubtitlePreference>
    suspend fun setSubtitlePreference(preference: SubtitlePreference)

    val subtitleAppearance: Flow<SubtitleAppearance>
    suspend fun setSubtitleAppearance(appearance: SubtitleAppearance)

    val experimentalSubtitleSync: Flow<Boolean>
    suspend fun setExperimentalSubtitleSync(enabled: Boolean)

    val matchContentFrameRate: Flow<Boolean>
    suspend fun setMatchContentFrameRate(enabled: Boolean)

    val autoNextEpisode: Flow<Boolean>
    suspend fun setAutoNextEpisode(enabled: Boolean)

    val searchQwerty: Flow<Boolean>
    suspend fun setSearchQwerty(enabled: Boolean)

    val hiddenCategories: Flow<Set<String>>
    suspend fun setHiddenCategories(names: Set<String>)

    val sourceProviders: Flow<Set<SourceProvider>>
    suspend fun setSourceProviders(providers: Set<SourceProvider>)

    /** Servicio debrid en uso (AllDebrid por defecto). */
    val debridService: Flow<DebridService>
    suspend fun setDebridService(service: DebridService)
}
