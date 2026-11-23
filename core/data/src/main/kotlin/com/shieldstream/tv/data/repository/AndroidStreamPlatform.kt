package com.shieldstream.tv.data.repository

import android.util.Log
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import com.shieldstream.tv.strings.sectionResource
import com.shieldstream.tv.strings.titleResource
import kotlinx.coroutines.flow.first

/** La búsqueda de fuentes (:core:streams) con lo de Android: textos de R.string, TokenStore, ajustes y logcat. */
internal class AndroidStreamTexts(private val texts: Texts) : StreamTexts {
    override fun providerTitle(provider: SourceProvider) = texts.get(provider.titleResource)
    override fun providerSection(provider: SourceProvider) = texts.get(provider.sectionResource)
    override fun imdbMissing(title: String) = texts.get(StringsR.string.data_imdb_id_missing, title)
    override fun pickEpisode() = texts.get(StringsR.string.data_pick_an_episode)
    override fun notResponding() = texts.get(StringsR.string.data_not_responding_right_now)
    override fun noSources() = texts.get(StringsR.string.data_no_sources_for_this)
    override fun providerUnavailable(names: String) = texts.get(StringsR.string.data_provider_unavailable, names)
    override fun debridKeyMissing(service: String) = texts.get(StringsR.string.data_debrid_key_missing, service)
    override fun stremioAddon() = texts.get(StringsR.string.data_the_stremio_addon)
    override fun subscriptionExpired(service: String) = texts.get(StringsR.string.data_subscription_expired, service)
    override fun invalidServiceKey(service: String) = texts.get(StringsR.string.data_invalid_service_key, service)
    override fun serviceBlocked(service: String) = texts.get(StringsR.string.data_service_blocked, service)
    override fun serviceUnavailableTorrentio(service: String) = texts.get(StringsR.string.data_service_unavailable_torrentio, service)
}

internal class TokenStoreSecrets(private val store: TokenStore) : StreamSecrets {
    override fun secret(service: ApiService): String? = store.getSecret(TokenStore.keyNameFor(service))
}

internal class RepositoryStreamSettings(private val settings: SettingsRepository) : StreamSettings {
    override suspend fun sourceProviders(): Set<SourceProvider> = settings.sourceProviders.first()
    override suspend fun debridService(): DebridService = settings.debridService.first()
    override suspend fun spanishFirst(): Boolean = settings.spanishFirst.first()
}

internal fun installAndroidStreamLog() {
    StreamLog.sink = { warning, tag, message -> if (warning) Log.w(tag, message) else Log.i(tag, message) }
}
