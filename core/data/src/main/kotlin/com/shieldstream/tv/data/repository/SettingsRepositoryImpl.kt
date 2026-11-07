package com.shieldstream.tv.data.repository

import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.DeviceOptimization
import android.content.Context
import android.os.Build
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.CatalogProvider
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.enabledSourceProviders
import com.shieldstream.tv.model.DeviceProfile
import com.shieldstream.tv.model.SubtitleAppearance
import com.shieldstream.tv.model.SubtitlePreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val tokenStore: TokenStore,
    private val profiles: ProfileRepository,
) : SettingsRepository {

    private val prefs = context.getSharedPreferences(AppLanguage.PREFS, Context.MODE_PRIVATE)

    private val tokenFlow = MutableStateFlow(tokenStore.token)
    private val catalogProviderFlow = MutableStateFlow(
        runCatching { CatalogProvider.valueOf(prefs.getString(KEY_PROVIDER, null) ?: "") }
            .getOrDefault(CatalogProvider.COMBINED),
    )
    private val deviceOptimizationFlow = MutableStateFlow(
        runCatching { DeviceOptimization.valueOf(prefs.getString(KEY_DEVICE_OPTIMIZATION, null) ?: "") }
            .getOrDefault(DeviceOptimization.AUTOMATIC),
    )
    private val maxBufferFlow = MutableStateFlow(
        prefs.getInt(KEY_BUFFER, DeviceProfile.defaultBufferSeconds(Build.MODEL)),
    )
    private val seekThumbnailsFlow = MutableStateFlow(prefs.getBoolean(KEY_SEEK_THUMBNAILS, true))
    private val lightweightUiFlow = MutableStateFlow(
        prefs.getBoolean(KEY_LIGHTWEIGHT_UI, DeviceProfile.lightweightUiDefault(Build.MODEL)),
    )
    override val lightweightUi: Flow<Boolean> = lightweightUiFlow
    override val lightweightUiEnabled: Boolean get() = lightweightUiFlow.value

    override suspend fun setLightweightUi(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIGHTWEIGHT_UI, enabled).apply()
        lightweightUiFlow.value = enabled
    }

    private val softBackdropsFlow = MutableStateFlow(prefs.getBoolean(KEY_SOFT_BACKDROPS, true))
    override val softBackdrops: Flow<Boolean> = softBackdropsFlow
    override val softBackdropsEnabled: Boolean get() = softBackdropsFlow.value

    override suspend fun setSoftBackdrops(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOFT_BACKDROPS, enabled).apply()
        softBackdropsFlow.value = enabled
    }

    // Off by default: original images are several times larger to download and, decoded at 1080p,
    // take a third of the Nokia's 24 MB image memory cache each.
    private val highResBackdropsFlow = MutableStateFlow(prefs.getBoolean(KEY_HIGH_RES_BACKDROPS, false))
    override val highResBackdrops: Flow<Boolean> = highResBackdropsFlow
    override val highResBackdropsEnabled: Boolean get() = highResBackdropsFlow.value

    override suspend fun setHighResBackdrops(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_RES_BACKDROPS, enabled).apply()
        highResBackdropsFlow.value = enabled
    }
    override val spanishFirst: Flow<Boolean> = profiles.active
        .map { it.spanishFirst || it.spanishOnlySources }.distinctUntilChanged()

    override suspend fun setSpanishFirst(enabled: Boolean) {
        profiles.save(profiles.active.value.copy(spanishFirst = enabled))
    }

    override val spanishOnlySources: Flow<Boolean> = profiles.active
        .map { it.spanishOnlySources }.distinctUntilChanged()

    override suspend fun setSpanishOnlySources(enabled: Boolean) {
        profiles.save(profiles.active.value.copy(spanishOnlySources = enabled))
    }
    override val subtitlePreference: Flow<SubtitlePreference> = profiles.active
        .map { it.subtitlePreference }.distinctUntilChanged()

    override suspend fun setSubtitlePreference(preference: SubtitlePreference) {
        profiles.save(profiles.active.value.copy(subtitlePreference = preference))
    }

    override val subtitleAppearance: Flow<SubtitleAppearance> = profiles.active
        .map { it.subtitleAppearance }.distinctUntilChanged()

    override suspend fun setSubtitleAppearance(appearance: SubtitleAppearance) {
        profiles.save(profiles.active.value.copy(subtitleAppearance = appearance.normalized()))
    }

    override val experimentalSubtitleSync: Flow<Boolean> = profiles.active
        .map { it.experimentalSubtitleSync }.distinctUntilChanged()

    override suspend fun setExperimentalSubtitleSync(enabled: Boolean) {
        profiles.save(profiles.active.value.copy(experimentalSubtitleSync = enabled))
    }

    override val matchContentFrameRate: Flow<Boolean> = profiles.active
        .map { it.matchContentFrameRate }.distinctUntilChanged()

    override suspend fun setMatchContentFrameRate(enabled: Boolean) {
        profiles.save(profiles.active.value.copy(matchContentFrameRate = enabled))
    }

    private val autoNextFlow = MutableStateFlow(prefs.getBoolean(KEY_AUTO_NEXT, true))
    override val autoNextEpisode: Flow<Boolean> = autoNextFlow

    override suspend fun setAutoNextEpisode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_NEXT, enabled).apply()
        autoNextFlow.value = enabled
    }
    private val searchQwertyFlow = MutableStateFlow(prefs.getBoolean(KEY_SEARCH_QWERTY, false))
    override val searchQwerty: Flow<Boolean> = searchQwertyFlow

    override suspend fun setSearchQwerty(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEARCH_QWERTY, enabled).apply()
        searchQwertyFlow.value = enabled
    }
    override val hiddenCategories: Flow<Set<String>> = profiles.active
        .map { it.hiddenCategories }.distinctUntilChanged()

    override suspend fun setHiddenCategories(names: Set<String>) {
        profiles.save(profiles.active.value.copy(hiddenCategories = names))
    }

    private val homeRowItemLimitFlow = MutableStateFlow(
        prefs.getInt(KEY_HOME_ROW_ITEM_LIMIT, SettingsRepository.DEFAULT_HOME_ROW_ITEM_LIMIT)
            .coerceIn(SettingsRepository.MIN_HOME_ROW_ITEM_LIMIT, SettingsRepository.MAX_HOME_ROW_ITEM_LIMIT),
    )
    override val homeRowItemLimit: Flow<Int> = homeRowItemLimitFlow

    override suspend fun setHomeRowItemLimit(limit: Int) {
        val normalized = limit.coerceIn(
            SettingsRepository.MIN_HOME_ROW_ITEM_LIMIT,
            SettingsRepository.MAX_HOME_ROW_ITEM_LIMIT,
        )
        prefs.edit().putInt(KEY_HOME_ROW_ITEM_LIMIT, normalized).apply()
        homeRowItemLimitFlow.value = normalized
    }

    private val debridFlow = MutableStateFlow(
        runCatching { DebridService.valueOf(prefs.getString(KEY_DEBRID, null) ?: "") }
            .getOrDefault(DebridService.ALLDEBRID),
    )
    override val debridService: Flow<DebridService> = debridFlow

    private val providersFlow = MutableStateFlow(
        enabledSourceProviders(
            prefs.getStringSet(KEY_PROVIDERS_OFF, null)
                ?.mapNotNull { n -> SourceProvider.entries.firstOrNull { it.name == n } }?.toSet(),
        ),
    )
    override val sourceProviders: Flow<Set<SourceProvider>> = providersFlow

    override suspend fun setSourceProviders(providers: Set<SourceProvider>) {
        prefs.edit().putStringSet(KEY_PROVIDERS_OFF, (SourceProvider.entries.toSet() - providers).map { it.name }.toSet()).apply()
        providersFlow.value = providers
    }

    override suspend fun setDebridService(service: DebridService) {
        prefs.edit().putString(KEY_DEBRID, service.name).apply()
        debridFlow.value = service
    }

    override val language: AppLanguage get() = Lang.current

    override suspend fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(AppLanguage.PREF_KEY, language.name).commit()
    }

    override val token: Flow<String?> = tokenFlow
    override val catalogProvider: Flow<CatalogProvider> = catalogProviderFlow
    override val deviceOptimization: Flow<DeviceOptimization> = deviceOptimizationFlow
    override val maxBufferSeconds: Flow<Int> = maxBufferFlow
    override val seekThumbnails: Flow<Boolean> = seekThumbnailsFlow

    override suspend fun setDeviceOptimization(optimization: DeviceOptimization) {
        prefs.edit().putString(KEY_DEVICE_OPTIMIZATION, optimization.name).apply()
        deviceOptimizationFlow.value = optimization
    }

    override suspend fun setSeekThumbnails(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEEK_THUMBNAILS, enabled).apply()
        seekThumbnailsFlow.value = enabled
    }

    override suspend fun setToken(token: String?) {
        tokenStore.token = token
        tokenFlow.value = token
    }

    override suspend fun setCatalogProvider(provider: CatalogProvider) {
        prefs.edit().putString(KEY_PROVIDER, provider.name).apply()
        catalogProviderFlow.value = provider
    }

    override suspend fun setMaxBufferSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_BUFFER, seconds.coerceIn(MIN_BUFFER, MAX_BUFFER)).apply()
        maxBufferFlow.value = seconds.coerceIn(MIN_BUFFER, MAX_BUFFER)
    }

    private companion object {
        const val KEY_PROVIDER = "catalog_provider"
        const val KEY_DEVICE_OPTIMIZATION = "device_optimization"
        const val KEY_BUFFER = "max_buffer_seconds"
        const val KEY_SEEK_THUMBNAILS = "seek_thumbnails"
        const val KEY_LIGHTWEIGHT_UI = "lightweight_ui"
        const val KEY_SOFT_BACKDROPS = "soft_backdrops"
        const val KEY_HIGH_RES_BACKDROPS = "high_res_backdrops"
        const val KEY_SEARCH_QWERTY = "search_qwerty"
        const val KEY_AUTO_NEXT = "auto_next_episode"
        const val KEY_HOME_ROW_ITEM_LIMIT = "home_row_item_limit"
        const val KEY_DEBRID = "debrid_service"
        const val KEY_PROVIDERS_OFF = "source_providers_off"
        const val MIN_BUFFER = 20
        const val MAX_BUFFER = 100
    }
}
