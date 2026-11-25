package com.shieldstream.tv.settings

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.strings.sectionResource
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.ui.component.restartApp
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.AudioCodec
import com.shieldstream.tv.model.DeviceOptimization
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.SourceExclusions
import com.shieldstream.tv.model.SubtitleAppearance
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import com.shieldstream.tv.settings.update.UpdateCard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.ui.component.ProvideMinimalScroll
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.theme.DisplayFont

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSetup: () -> Unit = {},
    onOpenPhoneConfig: () -> Unit = {},
    onOpenAddons: () -> Unit = {},
    onOpenRequests: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state as? SettingsUiState.Ready
    val context = LocalContext.current
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val sourceExclusions by viewModel.sourceExclusions.collectAsStateWithLifecycle()
    val brokenSourceCount by viewModel.brokenSourceCount.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val pendingRequestCount by viewModel.pendingRequestCount.collectAsStateWithLifecycle()
    val subtitleAppearance by viewModel.subtitleAppearance.collectAsStateWithLifecycle()
    val matchContentFrameRate by viewModel.matchContentFrameRate.collectAsStateWithLifecycle()
    Box(
        Modifier
            .fillMaxSize()
            .background(SettingsBackground)
            .onBackKey(enabled = ready?.editor == null) { onBack() },
    ) {
        when (val s = state) {
            SettingsUiState.Loading -> Unit
            is SettingsUiState.Ready -> {
                SettingsList(
                    state = s,
                    onEditKey = { service ->
                        if (service == ApiService.AIOSTREAMS) onOpenAddons() else viewModel.openEditor(service)
                    },
                    onBuffer = viewModel::changeBuffer,
                    onSeekMode = viewModel::setSeekThumbnails,
                    onDebrid = viewModel::setDebrid,
                    onDeviceOptimization = viewModel::setDeviceOptimization,
                    onExperimentalSync = viewModel::setExperimentalSubtitleSync,
                    matchContentFrameRate = matchContentFrameRate,
                    onMatchContentFrameRate = viewModel::setMatchContentFrameRate,
                    subtitleAppearance = subtitleAppearance,
                    onSubtitleAppearance = viewModel::setSubtitleAppearance,
                    onOpenSetup = onOpenSetup,
                    onOpenPhoneConfig = onOpenPhoneConfig,
                    onOpenRequests = onOpenRequests,
                    showRequests = isAdmin,
                    pendingRequestCount = pendingRequestCount,
                    onLanguage = { language -> viewModel.setLanguage(language) { restartApp(context) } },
                    providers = providers,
                    onToggleProvider = viewModel::toggleProvider,
                    sourceExclusions = sourceExclusions,
                    brokenSourceCount = brokenSourceCount,
                    onRestoreBrokenSources = viewModel::restoreBrokenSources,
                    onToggleQuality = viewModel::toggleExcludedQuality,
                    onToggleHdrType = viewModel::toggleExcludedHdrType,
                    onToggleAudioCodec = viewModel::toggleExcludedAudioCodec,
                )
                s.editor?.let { editor ->
                    KeyEditorDialog(
                        editor = editor,
                        onSave = viewModel::saveKey,
                        onRemove = viewModel::removeKey,
                        onClose = viewModel::closeEditor,
                        onAllDebridConnected = viewModel::completeAllDebridConnect,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsList(
    state: SettingsUiState.Ready,
    onEditKey: (ApiService) -> Unit,
    onBuffer: (Int) -> Unit,
    onSeekMode: (Boolean) -> Unit,
    onDebrid: (DebridService) -> Unit,
    onDeviceOptimization: (DeviceOptimization) -> Unit,
    onExperimentalSync: (Boolean) -> Unit,
    matchContentFrameRate: Boolean,
    onMatchContentFrameRate: (Boolean) -> Unit,
    subtitleAppearance: SubtitleAppearance,
    onSubtitleAppearance: (SubtitleAppearance) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenPhoneConfig: () -> Unit,
    onOpenRequests: () -> Unit,
    showRequests: Boolean,
    pendingRequestCount: Int,
    onLanguage: (AppLanguage) -> Unit,
    providers: Set<SourceProvider>,
    onToggleProvider: (SourceProvider) -> Unit,
    sourceExclusions: SourceExclusions,
    brokenSourceCount: Int,
    onRestoreBrokenSources: () -> Unit,
    onToggleQuality: (Quality) -> Unit,
    onToggleHdrType: (HdrType) -> Unit,
    onToggleAudioCodec: (AudioCodec) -> Unit,
) {
    val requesters = remember { ApiService.entries.associateWith { FocusRequester() } }
    val debridRequester = remember { FocusRequester() }
    var lastEdited by rememberSaveable { mutableStateOf<ApiService?>(null) }
    var qualityExclusionsExpanded by rememberSaveable { mutableStateOf(false) }
    var videoExclusionsExpanded by rememberSaveable { mutableStateOf(false) }
    var audioExclusionsExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.editor == null) {
        if (state.editor == null) {
            withFrameNanos { }
            runCatching { (lastEdited?.let { requesters.getValue(it) } ?: debridRequester).requestFocus() }
        }
    }
    val debridUi = state.services.first { it.service == state.debrid.apiService }
    val otherServices = state.services.filter { !it.service.isDebrid }
    val providerSections = SourceProvider.entries
        .map { it to localizedStringResource(it.sectionResource) }
    ProvideMinimalScroll(margin = 56.dp) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 120.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(localizedStringResource(StringsR.string.settings_settings), fontFamily = DisplayFont, fontSize = 52.sp, color = Color.White)
                    Text(
                        localizedStringResource(StringsR.string.settings_service_keys_playback_and_app_version),
                        style = MaterialTheme.typography.bodyLarge,
                        color = SettingsMuted,
                    )
                }
            }
            item { SectionTitle(localizedStringResource(StringsR.string.settings_debrid_service)) }
            item {
                DebridSelectorCard(
                    active = state.debrid,
                    onChange = onDebrid,
                    modifier = Modifier.focusRequester(debridRequester),
                )
            }
            item(key = "debrid_key") {
                ServiceCard(
                    ui = debridUi,
                    onClick = {
                        lastEdited = debridUi.service
                        onEditKey(debridUi.service)
                    },
                    modifier = Modifier.focusRequester(requesters.getValue(debridUi.service)),
                )
            }
            item {
                Text(
                    if (state.debrid.viaTorrentio && state.debrid.supportsDirectMagnetResolution) {
                        localizedStringResource(StringsR.string.settings_native_debrid_explanation, state.debrid.title)
                    } else if (state.debrid.viaTorrentio) {
                        localizedStringResource(StringsR.string.settings_torrentio_debrid_explanation, state.debrid.title)
                    } else {
                        localizedStringResource(StringsR.string.settings_real_debrid_connects_directly_from_the_shield)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
            item { SectionTitle(localizedStringResource(StringsR.string.settings_other_services)) }
            items(otherServices, key = { it.service.name }) { ui ->
                ServiceCard(
                    ui = ui,
                    onClick = {
                        lastEdited = ui.service
                        onEditKey(ui.service)
                    },
                    modifier = Modifier.focusRequester(requesters.getValue(ui.service)),
                )
            }
            item {
                Text(
                    localizedStringResource(StringsR.string.settings_torrentio_sources_opensubtitles_and_podnapisi_subtitles_don),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
            item { SectionTitle(localizedStringResource(StringsR.string.settings_source_finders)) }
            items(providerSections.distinctBy { it.second }) { (provider, section) ->
                val enabled = providerSections.filter { it.second == section }.all { it.first in providers }
                ProviderCard(provider, enabled = enabled, onClick = { onToggleProvider(provider) })
            }
            if (brokenSourceCount > 0) {
                item {
                    BrokenSourcesCard(count = brokenSourceCount, onRestore = onRestoreBrokenSources)
                }
            }
            item { SectionTitle(localizedStringResource(StringsR.string.settings_source_exclusions)) }
            item {
                Text(
                    localizedStringResource(StringsR.string.settings_source_exclusions_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
            item(key = "excluded-quality-header") {
                SourceExclusionGroupCard(
                    title = localizedStringResource(StringsR.string.settings_quality),
                    summary = acceptedSummary(
                        acceptedLabels = Quality.entries.filterNot { it in sourceExclusions.qualities }.map { qualityLabel(it) },
                        acceptsAll = sourceExclusions.qualities.isEmpty(),
                    ),
                    expanded = qualityExclusionsExpanded,
                    onClick = { qualityExclusionsExpanded = !qualityExclusionsExpanded },
                )
            }
            if (qualityExclusionsExpanded) {
                items(Quality.entries, key = { "excluded-quality:${it.name}" }) { quality ->
                    SourceExclusionCard(
                        label = qualityLabel(quality),
                        excluded = quality in sourceExclusions.qualities,
                        onClick = { onToggleQuality(quality) },
                    )
                }
            }
            item(key = "excluded-hdr-header") {
                SourceExclusionGroupCard(
                    title = localizedStringResource(StringsR.string.settings_video_features),
                    summary = acceptedSummary(
                        acceptedLabels = HdrType.entries.filterNot { it in sourceExclusions.hdrTypes }.map { it.label },
                        acceptsAll = sourceExclusions.hdrTypes.isEmpty(),
                    ),
                    expanded = videoExclusionsExpanded,
                    onClick = { videoExclusionsExpanded = !videoExclusionsExpanded },
                )
            }
            if (videoExclusionsExpanded) {
                items(HdrType.entries, key = { "excluded-hdr:${it.name}" }) { hdrType ->
                    SourceExclusionCard(
                        label = hdrType.label,
                        excluded = hdrType in sourceExclusions.hdrTypes,
                        onClick = { onToggleHdrType(hdrType) },
                    )
                }
            }
            item(key = "excluded-audio-header") {
                SourceExclusionGroupCard(
                    title = localizedStringResource(StringsR.string.settings_audio_codecs),
                    summary = acceptedSummary(
                        acceptedLabels = AudioCodec.entries.filterNot { it in sourceExclusions.audioCodecs }.map { it.label },
                        acceptsAll = sourceExclusions.audioCodecs.isEmpty(),
                    ),
                    expanded = audioExclusionsExpanded,
                    onClick = { audioExclusionsExpanded = !audioExclusionsExpanded },
                )
            }
            item {
                Text(
                    localizedStringResource(StringsR.string.settings_audio_codec_exclusions_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (audioExclusionsExpanded) {
                items(AudioCodec.entries, key = { "excluded-audio:${it.name}" }) { codec ->
                    SourceExclusionCard(
                        label = codec.label,
                        excluded = codec in sourceExclusions.audioCodecs,
                        onClick = { onToggleAudioCodec(codec) },
                    )
                }
            }
            item { SectionTitle(localizedStringResource(StringsR.string.settings_home)) }
            item { HomeRowLimitCard() }
            item { LightweightModeCard() }
            item { IntroAlwaysCard() }
            item { SoftBackdropsCard() }
            item { HighResBackdropsCard() }
            item { BillboardTrailersCard() }
            item { SectionTitle("Idioma · Language") }
            item { LanguageCard(current = state.language, onApply = onLanguage) }
            item { SectionTitle(localizedStringResource(StringsR.string.settings_playback)) }
            item { PlaybackPriorityCard() }
            item { PreferredQualityCard() }
            item { SwitchPickedSourceCard() }
            item { MatchContentFrameRateCard(enabled = matchContentFrameRate, onChange = onMatchContentFrameRate) }
            item { ExperimentalSyncCard(enabled = state.experimentalSubtitleSync, onChange = onExperimentalSync) }
            item {
                DeviceOptimizationCard(
                    optimization = state.optimization,
                    capabilities = state.capabilities,
                    memoryProfile = state.memoryProfile,
                    onChange = onDeviceOptimization,
                )
            }
            item {
                SeekModeCard(
                    thumbnails = state.seekThumbnails,
                    enabled = state.optimization == DeviceOptimization.CUSTOM,
                    onChange = onSeekMode,
                )
            }
            item { AutoNextCard() }
            item { AutoSkipIntroCard() }
            item { ShowCastCard() }
            item { TracksPanelStyleCard() }
            item { DownloadStorageCard() }
            item { EpisodeNotificationsCard() }
            item {
                BufferCard(
                    seconds = state.bufferSeconds,
                    enabled = state.optimization == DeviceOptimization.CUSTOM,
                    onChange = onBuffer,
                )
            }
            item { SectionTitle(localizedStringResource(StringsR.string.settings_about)) }
            item { AboutCard() }
            item { UpdateCard() }
            if (showRequests) {
                item { FamilyRequestsCard(count = pendingRequestCount, onClick = onOpenRequests) }
            }
            item { PhoneConfigCard(onClick = onOpenPhoneConfig) }
            item { SetupGuideCard(onClick = onOpenSetup) }
        }
    }
}
