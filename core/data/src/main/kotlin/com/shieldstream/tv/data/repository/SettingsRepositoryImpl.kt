package com.shieldstream.tv.data.repository

import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.AppLanguage
import android.content.Context
import android.os.Build
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.CatalogProvider
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.enabledSourceProviders
import com.shieldstream.tv.model.DeviceProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val tokenStore: TokenStore,
) : SettingsRepository {

    private val prefs = context.getSharedPreferences(AppLanguage.PREFS, Context.MODE_PRIVATE)

    private val tokenFlow = MutableStateFlow(tokenStore.token)
    private val catalogProviderFlow = MutableStateFlow(
        runCatching { CatalogProvider.valueOf(prefs.getString(KEY_PROVIDER, null) ?: "") }
            .getOrDefault(CatalogProvider.COMBINED),
    )
    private val maxBufferFlow = MutableStateFlow(
        prefs.getInt(
            KEY_BUFFER,
            DeviceProfile.defaultBufferSeconds(Build.MODEL),
        ),
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
    private val spanishFirstFlow = MutableStateFlow(prefs.getBoolean(KEY_SPANISH_FIRST, !Lang.isEnglish))
    override val spanishFirst: Flow<Boolean> = spanishFirstFlow

    override suspend fun setSpanishFirst(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPANISH_FIRST, enabled).apply()
        spanishFirstFlow.value = enabled
    }
    private val spanishOnlyFlow = MutableStateFlow(prefs.getBoolean(KEY_SPANISH_ONLY, false))
    override val spanishOnlySources: Flow<Boolean> = spanishOnlyFlow

    override suspend fun setSpanishOnlySources(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPANISH_ONLY, enabled).apply()
        spanishOnlyFlow.value = enabled
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
    private val hiddenCategoriesFlow = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN_CATEGORIES, emptySet()).orEmpty().toSet())
    override val hiddenCategories: Flow<Set<String>> = hiddenCategoriesFlow

    override suspend fun setHiddenCategories(names: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN_CATEGORIES, names).apply()
        hiddenCategoriesFlow.value = names
    }
    private val debridFlow = MutableStateFlow(
        runCatching { DebridService.valueOf(prefs.getString(KEY_DEBRID, null) ?: "") }
            .getOrDefault(DebridService.REAL_DEBRID),
    )
    override val debridService: Flow<DebridService> = debridFlow

    // Se guardan los DESACTIVADOS: así un buscador nuevo (actualización) sale activado sin tocar nada.
    // Comet es la excepción: requiere enviar el token de Real-Debrid a un tercero y debe ser opt-in.
    // Si el usuario ya lo activó expresamente, su elección guardada se respeta.
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

    /** commit() (no apply): la app se reinicia justo después y el valor tiene que estar ya en disco. */
    override suspend fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(AppLanguage.PREF_KEY, language.name).commit()
    }

    override val token: Flow<String?> = tokenFlow
    override val catalogProvider: Flow<CatalogProvider> = catalogProviderFlow
    override val maxBufferSeconds: Flow<Int> = maxBufferFlow
    override val seekThumbnails: Flow<Boolean> = seekThumbnailsFlow

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
        const val KEY_BUFFER = "max_buffer_seconds"
        const val KEY_SEEK_THUMBNAILS = "seek_thumbnails"
        const val KEY_LIGHTWEIGHT_UI = "lightweight_ui"
        const val KEY_SPANISH_FIRST = "spanish_first"
        const val KEY_HIDDEN_CATEGORIES = "hidden_categories"
        const val KEY_SEARCH_QWERTY = "search_qwerty"
        const val KEY_AUTO_NEXT = "auto_next_episode"
        const val KEY_SPANISH_ONLY = "spanish_only_sources"
        const val KEY_DEBRID = "debrid_service"
        const val KEY_PROVIDERS_OFF = "source_providers_off"
        const val MIN_BUFFER = 20
        const val MAX_BUFFER = 100
    }
}
