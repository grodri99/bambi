package com.shieldstream.tv.settings

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.strings.keyNameResource
import com.shieldstream.tv.strings.sectionResource
import com.shieldstream.tv.strings.titleResource
import com.shieldstream.tv.strings.usedForResource
import com.shieldstream.tv.strings.whereToGetResource
import com.shieldstream.tv.strings.labelResource
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.domain.IthilLibraryState
import com.shieldstream.tv.ui.component.restartApp
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.AudioCodec
import com.shieldstream.tv.model.DeviceCapabilities
import com.shieldstream.tv.model.DeviceOptimization
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.MemoryProfile
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.SourceExclusions
import com.shieldstream.tv.model.SubtitleAppearance
import com.shieldstream.tv.model.SubtitleColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import com.shieldstream.tv.settings.update.UpdateCard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.ui.component.ProvideMinimalScroll
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.tvClickable
import com.shieldstream.tv.ui.theme.DisplayFont

private val Background = Brush.verticalGradient(listOf(AppColors.BackgroundRaised, Color.Black))
private val CardColor = AppColors.Surface
private val CardFocused = AppColors.SurfaceFocused
private val CardShape = AppShapes.Control
private val Muted = Color.White.copy(alpha = 0.6f)
private val Green = AppColors.Success
private val Amber = AppColors.Warning
private val Red = AppColors.Error

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSetup: () -> Unit = {},
    onOpenPhoneConfig: () -> Unit = {},
    onOpenAddons: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state as? SettingsUiState.Ready
    val context = LocalContext.current
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val sourceExclusions by viewModel.sourceExclusions.collectAsStateWithLifecycle()
    val ithilState by viewModel.ithilState.collectAsStateWithLifecycle()
    val ithilUnlocked by viewModel.ithilUnlocked.collectAsStateWithLifecycle()
    val subtitleAppearance by viewModel.subtitleAppearance.collectAsStateWithLifecycle()
    val matchContentFrameRate by viewModel.matchContentFrameRate.collectAsStateWithLifecycle()
    val ithilUnlockedMessage = localizedStringResource(StringsR.string.settings_ithil_unlocked)
    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
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
                    onLanguage = { language -> viewModel.setLanguage(language) { restartApp(context) } },
                    providers = providers,
                    onToggleProvider = viewModel::toggleProvider,
                    sourceExclusions = sourceExclusions,
                    onToggleQuality = viewModel::toggleExcludedQuality,
                    onToggleHdrType = viewModel::toggleExcludedHdrType,
                    onToggleAudioCodec = viewModel::toggleExcludedAudioCodec,
                    ithilState = ithilState,
                    ithilUnlocked = ithilUnlocked,
                    onDownloadIthil = viewModel::downloadIthil,
                    onDeleteIthil = viewModel::deleteIthil,
                    onAboutClick = {
                        if (viewModel.onIthilUnlockTap()) {
                            Toast.makeText(
                                context,
                                ithilUnlockedMessage,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
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
    onLanguage: (AppLanguage) -> Unit,
    providers: Set<SourceProvider>,
    onToggleProvider: (SourceProvider) -> Unit,
    sourceExclusions: SourceExclusions,
    onToggleQuality: (Quality) -> Unit,
    onToggleHdrType: (HdrType) -> Unit,
    onToggleAudioCodec: (AudioCodec) -> Unit,
    ithilState: IthilLibraryState,
    ithilUnlocked: Boolean,
    onDownloadIthil: () -> Unit,
    onDeleteIthil: () -> Unit,
    onAboutClick: () -> Unit,
) {
    val requesters = remember { ApiService.entries.associateWith { FocusRequester() } }
    val debridRequester = remember { FocusRequester() }
    var lastEdited by rememberSaveable { mutableStateOf<ApiService?>(null) }
    var subtitleAppearanceExpanded by rememberSaveable { mutableStateOf(false) }
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
        .filter { it != SourceProvider.ITHIL || ithilUnlocked }
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
                        color = Muted,
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
                    if (state.debrid.viaTorrentio) {
                        localizedStringResource(StringsR.string.settings_torrentio_debrid_explanation, state.debrid.title)
                    } else {
                        localizedStringResource(StringsR.string.settings_real_debrid_connects_directly_from_the_shield)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
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
                    color = Muted,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
            item { SectionTitle(localizedStringResource(StringsR.string.settings_source_finders)) }
            items(providerSections.distinctBy { it.second }) { (provider, section) ->
                val enabled = providerSections.filter { it.second == section }.all { it.first in providers }
                ProviderCard(provider, enabled = enabled, onClick = { onToggleProvider(provider) })
            }
            if (ithilUnlocked) {
                item {
                    IthilLibraryCard(
                        state = ithilState,
                        onDownload = onDownloadIthil,
                        onDelete = onDeleteIthil,
                    )
                }
            }
            item { SectionTitle(localizedStringResource(StringsR.string.settings_source_exclusions)) }
            item {
                Text(
                    localizedStringResource(StringsR.string.settings_source_exclusions_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
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
                    color = Muted,
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
            item { SoftBackdropsCard() }
            item { HighResBackdropsCard() }
            item { SectionTitle("Idioma · Language") }
            item { LanguageCard(current = state.language, onApply = onLanguage) }
            item { SectionTitle(localizedStringResource(StringsR.string.settings_playback)) }
            item { MatchContentFrameRateCard(enabled = matchContentFrameRate, onChange = onMatchContentFrameRate) }
            item { ExperimentalSyncCard(enabled = state.experimentalSubtitleSync, onChange = onExperimentalSync) }
            item {
                SubtitleAppearanceHeaderCard(
                    appearance = subtitleAppearance,
                    expanded = subtitleAppearanceExpanded,
                    onClick = { subtitleAppearanceExpanded = !subtitleAppearanceExpanded },
                )
            }
            if (subtitleAppearanceExpanded) {
            item {
                SubtitleAppearanceCard(
                    title = localizedStringResource(StringsR.string.subtitle_size),
                    value = localizedStringResource(StringsR.string.subtitle_size_value, subtitleAppearance.sizePercent),
                    onShift = { delta ->
                        onSubtitleAppearance(
                            subtitleAppearance.copy(
                                sizePercent = (subtitleAppearance.sizePercent + delta * SubtitleAppearance.SIZE_STEP)
                                    .coerceIn(SubtitleAppearance.MIN_SIZE, SubtitleAppearance.MAX_SIZE),
                            ),
                        )
                    },
                )
            }
            item {
                SubtitleAppearanceCard(
                    title = localizedStringResource(StringsR.string.subtitle_position),
                    value = localizedStringResource(
                        StringsR.string.subtitle_position_value,
                        subtitleAppearance.verticalPositionPercent,
                    ),
                    onShift = { delta ->
                        onSubtitleAppearance(
                            subtitleAppearance.copy(
                                verticalPositionPercent = (
                                    subtitleAppearance.verticalPositionPercent + delta * SubtitleAppearance.POSITION_STEP
                                ).coerceIn(SubtitleAppearance.MIN_POSITION, SubtitleAppearance.MAX_POSITION),
                            ),
                        )
                    },
                )
            }
            item {
                SubtitleAppearanceCard(
                    title = localizedStringResource(StringsR.string.subtitle_text_color),
                    value = localizedStringResource(subtitleAppearance.textColor.labelResource),
                    swatch = subtitleAppearance.textColor,
                    onShift = { delta ->
                        onSubtitleAppearance(
                            subtitleAppearance.copy(
                                textColor = subtitleAppearance.textColor.shift(SubtitleAppearance.textColors, delta),
                            ),
                        )
                    },
                )
            }
            item {
                SubtitleAppearanceCard(
                    title = localizedStringResource(StringsR.string.subtitle_background_color),
                    value = localizedStringResource(subtitleAppearance.backgroundColor.labelResource),
                    swatch = subtitleAppearance.backgroundColor,
                    onShift = { delta ->
                        onSubtitleAppearance(
                            subtitleAppearance.copy(
                                backgroundColor = subtitleAppearance.backgroundColor.shift(
                                    SubtitleAppearance.backgroundColors,
                                    delta,
                                ),
                            ),
                        )
                    },
                )
            }
            item {
                SubtitleAppearanceCard(
                    title = localizedStringResource(StringsR.string.subtitle_outline_color),
                    value = localizedStringResource(subtitleAppearance.outlineColor.labelResource),
                    swatch = subtitleAppearance.outlineColor,
                    onShift = { delta ->
                        onSubtitleAppearance(
                            subtitleAppearance.copy(
                                outlineColor = subtitleAppearance.outlineColor.shift(SubtitleAppearance.outlineColors, delta),
                            ),
                        )
                    },
                )
            }
            item {
                SubtitleAppearanceCard(
                    title = localizedStringResource(StringsR.string.subtitle_embedded_style),
                    value = localizedStringResource(
                        if (subtitleAppearance.applyEmbeddedStyles) StringsR.string.subtitle_style_original
                        else StringsR.string.subtitle_style_custom
                    ),
                    onShift = {
                        onSubtitleAppearance(
                            subtitleAppearance.copy(applyEmbeddedStyles = !subtitleAppearance.applyEmbeddedStyles),
                        )
                    },
                )
            }
            }
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
            item { EpisodeNotificationsCard() }
            item {
                BufferCard(
                    seconds = state.bufferSeconds,
                    enabled = state.optimization == DeviceOptimization.CUSTOM,
                    onChange = onBuffer,
                )
            }
            item { SectionTitle(localizedStringResource(StringsR.string.settings_about)) }
            item { AboutCard(onClick = onAboutClick) }
            item { UpdateCard() }
            item { PhoneConfigCard(onClick = onOpenPhoneConfig) }
            item { SetupGuideCard(onClick = onOpenSetup) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = DisplayFont,
        fontSize = 26.sp,
        color = Color.White.copy(alpha = 0.85f),
        modifier = Modifier.padding(top = 18.dp, bottom = 2.dp, start = 4.dp),
    )
}

@Composable
private fun FocusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .background(if (focused) CardFocused else CardColor, CardShape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                shape = CardShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick ?: {})
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        content(focused)
    }
}

@Composable
private fun SubtitleAppearanceHeaderCard(
    appearance: SubtitleAppearance,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    FocusCard(onClick = onClick) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = localizedStringResource(StringsR.string.subtitle_appearance),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    text = localizedStringResource(StringsR.string.subtitle_size_value, appearance.sizePercent) +
                        " · " + localizedStringResource(appearance.textColor.labelResource),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.headlineSmall,
                color = if (focused) Color.White else Muted,
            )
        }
    }
}

@Composable
private fun SubtitleAppearanceCard(
    title: String,
    value: String,
    swatch: SubtitleColor? = null,
    onShift: (Int) -> Unit,
) {
    FocusCard(
        onClick = { onShift(1) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onShift(-1); true }
                Key.DirectionRight -> { onShift(1); true }
                else -> false
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            if (swatch != null) {
                Box(Modifier.size(20.dp).background(Color(swatch.argb), CircleShape))
                Spacer(Modifier.width(12.dp))
            }
            Arrow("◀", enabled = it)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Arrow("▶", enabled = it)
        }
    }
}

private fun SubtitleColor.shift(colors: List<SubtitleColor>, delta: Int): SubtitleColor {
    val index = colors.indexOf(this).coerceAtLeast(0)
    return colors[(index + delta + colors.size) % colors.size]
}

@Composable
internal fun ServiceCard(ui: ServiceUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusCard(modifier = modifier, onClick = onClick) { focused ->
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceLogo(ui.service)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            localizedStringResource(ui.service.titleResource),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when {
                                ui.service.isDebrid -> localizedStringResource(StringsR.string.settings_in_use)
                                ui.service.required -> localizedStringResource(StringsR.string.settings_required)
                                else -> localizedStringResource(StringsR.string.settings_optional)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Muted,
                        )
                    }
                    Text(localizedStringResource(ui.service.usedForResource), style = MaterialTheme.typography.bodyMedium, color = Muted)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        ui.maskedKey ?: localizedStringResource(StringsR.string.settings_no_key),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    if (ui.status == ServiceStatus.Missing) localizedStringResource(StringsR.string.settings_ok_to_add) else localizedStringResource(StringsR.string.settings_ok_to_change),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (focused) Color.White else Color.Transparent,
                )
            }
            // Status messages can be long: give them their own row so they cannot squeeze the provider details.
            Spacer(Modifier.height(12.dp))
            StatusPill(ui.status)
        }
    }
}

@Composable
private fun ServiceLogo(service: ApiService) {
    val (initials, color) = when (service) {
        ApiService.REAL_DEBRID -> "RD" to Color(0xFF6DB33F)
        ApiService.TORBOX -> "TB" to Color(0xFF7C5CFF)
        ApiService.PREMIUMIZE -> "PM" to Color(0xFFE5533D)
        ApiService.ALLDEBRID -> "AD" to Color(0xFFE9A21B)
        ApiService.DEBRID_LINK -> "DL" to Color(0xFF2F7DE1)
        ApiService.OFFCLOUD -> "OC" to Color(0xFF1FA58E)
        ApiService.TMDB -> "TM" to Color(0xFF01B4E4)
        ApiService.SUBDL -> "SD" to Color(0xFFF08A24)
        ApiService.AIOSTREAMS -> "+" to Color(0xFF8E5CF7)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(52.dp).background(color, CircleShape),
    ) {
        Text(initials, fontFamily = DisplayFont, fontSize = 24.sp, color = Color.White)
    }
}

@Composable
private fun StatusPill(status: ServiceStatus) {
    val (color, text) = when (status) {
        ServiceStatus.Missing -> Amber to localizedStringResource(StringsR.string.settings_not_set_up)
        ServiceStatus.Checking -> Muted to localizedStringResource(StringsR.string.settings_checking)
        is ServiceStatus.Ok -> Green to (status.detail ?: localizedStringResource(StringsR.string.settings_connected))
        is ServiceStatus.Bad -> Red to status.message
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), AppShapes.Pill)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.weight(1f, fill = false).padding(end = 2.dp),
        )
    }
}

@Composable
private fun PhoneConfigCard(onClick: () -> Unit) {
    FocusCard(onClick = onClick) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_configure_from_phone),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_phone_config_card_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Text(
                localizedStringResource(StringsR.string.settings_ok_to_open),
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) Color.White else Color.Transparent,
            )
        }
    }
}

@Composable
private fun SetupGuideCard(onClick: () -> Unit) {
    FocusCard(onClick = onClick) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_setup_guide),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_repeat_the_first_run_setup_step_by),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Text(
                localizedStringResource(StringsR.string.settings_ok_to_open),
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) Color.White else Color.Transparent,
            )
        }
    }
}

/** Servicio debrid en uso: ◀ ▶ (u OK) recorren Real-Debrid, TorBox, Premiumize, AllDebrid, Debrid-Link y Offcloud. */
@Composable
internal fun DebridSelectorCard(active: DebridService, onChange: (DebridService) -> Unit, modifier: Modifier = Modifier) {
    val all = DebridService.entries
    fun shift(delta: Int) = onChange(all[(all.indexOf(active) + delta + all.size) % all.size])
    FocusCard(
        onClick = { shift(1) },
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { shift(-1); true }
                Key.DirectionRight -> { shift(1); true }
                else -> false
            }
        },
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_service_in_use),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    if (active.viaTorrentio) localizedStringResource(StringsR.string.settings_sources_and_links_through_torrentio) else localizedStringResource(StringsR.string.settings_direct_connection_to_real_debrid),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Arrow("◀", enabled = focused)
                    Text(
                        active.title.uppercase(),
                        fontFamily = DisplayFont,
                        fontSize = 30.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                    Arrow("▶", enabled = focused)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    all.forEach { service ->
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(
                                    if (service == active) Color.White else Color.White.copy(alpha = 0.25f),
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRowLimitCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val limit by viewModel.homeRowItemLimit.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.changeHomeRowItemLimit(SettingsRepository.HOME_ROW_ITEM_LIMIT_STEP) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> {
                    if (limit > SettingsRepository.MIN_HOME_ROW_ITEM_LIMIT) {
                        viewModel.changeHomeRowItemLimit(-SettingsRepository.HOME_ROW_ITEM_LIMIT_STEP)
                    }
                    true
                }
                Key.DirectionRight -> {
                    if (limit < SettingsRepository.MAX_HOME_ROW_ITEM_LIMIT) {
                        viewModel.changeHomeRowItemLimit(SettingsRepository.HOME_ROW_ITEM_LIMIT_STEP)
                    }
                    true
                }
                else -> false
            }
        },
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_items_per_home_row),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_items_per_home_row_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Arrow("◀", enabled = focused && limit > SettingsRepository.MIN_HOME_ROW_ITEM_LIMIT)
                Text(
                    "$limit",
                    fontFamily = DisplayFont,
                    fontSize = 34.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                Arrow("▶", enabled = focused && limit < SettingsRepository.MAX_HOME_ROW_ITEM_LIMIT)
            }
        }
    }
}

/** Reduce image decoding, background changes and focus animations on low-power TV boxes. */
@Composable
private fun LightweightModeCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.lightweightUi.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.setLightweightUi(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.setLightweightUi(true); true }
                Key.DirectionRight -> { viewModel.setLightweightUi(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_lightweight_ui),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_smaller_backdrops_no_automatic_rotation_or_focus),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), AppShapes.Pill)
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_on), selected = enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_off_9847b4), selected = !enabled)
            }
        }
    }
}

/** Blur the backdrop behind Home rows so the cards stand out. */
@Composable
private fun SoftBackdropsCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.softBackdrops.collectAsStateWithLifecycle()
    OnOffCard(
        title = localizedStringResource(StringsR.string.settings_soft_backdrops),
        summary = localizedStringResource(StringsR.string.settings_soft_backdrops_summary),
        enabled = enabled,
        onChange = viewModel::setSoftBackdrops,
    )
}

/** Load the featured carousel and sharp Home backdrops at TMDB's original size. */
@Composable
private fun HighResBackdropsCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.highResBackdrops.collectAsStateWithLifecycle()
    OnOffCard(
        title = localizedStringResource(StringsR.string.settings_high_res_backdrops),
        summary = localizedStringResource(StringsR.string.settings_high_res_backdrops_summary),
        enabled = enabled,
        onChange = viewModel::setHighResBackdrops,
    )
}

/** On/Off setting row: OK toggles, Left selects On, Right selects Off. */
@Composable
private fun OnOffCard(title: String, summary: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    FocusCard(
        onClick = { onChange(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onChange(true); true }
                Key.DirectionRight -> { onChange(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), AppShapes.Pill)
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_on), selected = enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_off_9847b4), selected = !enabled)
            }
        }
    }
}

@Composable
private fun AutoNextCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.autoNextEpisode.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.setAutoNextEpisode(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.setAutoNextEpisode(true); true }
                Key.DirectionRight -> { viewModel.setAutoNextEpisode(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_autoplay_next_episode),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_shows_during_the_credits_next_episode_appears),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), AppShapes.Pill)
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_on), selected = enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_off_9847b4), selected = !enabled)
            }
        }
    }
}

@Composable
private fun EpisodeNotificationsCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.episodeNotifications.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setEpisodeNotifications(true)
    }
    fun enable() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setEpisodeNotifications(true)
        }
    }
    FocusCard(
        onClick = { if (enabled) viewModel.setEpisodeNotifications(false) else enable() },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { enable(); true }
                Key.DirectionRight -> { viewModel.setEpisodeNotifications(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_new_episode_notifications),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_followed_shows_only_checks_for_releases_every),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(Modifier.background(Color.White.copy(alpha = 0.08f), AppShapes.Pill).padding(4.dp)) {
                SegmentOption(localizedStringResource(StringsR.string.settings_on), selected = enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_off_9847b4), selected = !enabled)
            }
        }
    }
}

@Composable
private fun MatchContentFrameRateCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    FocusCard(
        onClick = { onChange(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onChange(true); true }
                Key.DirectionRight -> { onChange(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_match_content_frame_rate),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_match_content_frame_rate_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), AppShapes.Pill)
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_on), selected = enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_off_9847b4), selected = !enabled)
            }
        }
    }
}

@Composable
private fun ExperimentalSyncCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    FocusCard(
        onClick = { onChange(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onChange(true); true }
                Key.DirectionRight -> { onChange(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_experimental_subtitle_sync),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_sync_demux_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), AppShapes.Pill)
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_on), selected = enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_off_9847b4), selected = !enabled)
            }
        }
    }
}

@Composable
private fun DeviceOptimizationCard(
    optimization: DeviceOptimization,
    capabilities: DeviceCapabilities,
    memoryProfile: MemoryProfile,
    onChange: (DeviceOptimization) -> Unit,
) {
    FocusCard(
        onClick = {
            onChange(if (optimization == DeviceOptimization.AUTOMATIC) DeviceOptimization.CUSTOM else DeviceOptimization.AUTOMATIC)
        },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onChange(DeviceOptimization.AUTOMATIC); true }
                Key.DirectionRight -> { onChange(DeviceOptimization.CUSTOM); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_device_optimization),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    if (optimization == DeviceOptimization.AUTOMATIC) {
                        localizedStringResource(StringsR.string.settings_bambi_adapts_memory_buffering_thumbnails_and_sources)
                    } else {
                        localizedStringResource(StringsR.string.settings_uses_your_manual_settings_and_shield_compatible)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                val codecs = capabilities.hardwareVideoCodecs
                    .sortedBy { it.ordinal }
                    .joinToString(" · ") { it.label }
                    .ifBlank { localizedStringResource(StringsR.string.settings_none_detected) }
                val hdr = capabilities.supportedHdr
                    .sortedBy { it.rank }
                    .joinToString(" · ") { it.label }
                    .ifBlank { localizedStringResource(StringsR.string.settings_no_hdr_detected) }
                val memory = when {
                    optimization == DeviceOptimization.AUTOMATIC && memoryProfile == MemoryProfile.CONSTRAINED ->
                        " · " + localizedStringResource(StringsR.string.settings_constrained_memory)
                    else -> ""
                }
                Text(
                    "${capabilities.model} · ${capabilities.heapLimitBytes / (1024 * 1024)} MB$memory\n$codecs\n$hdr",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), AppShapes.Pill)
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_automatic), selected = optimization == DeviceOptimization.AUTOMATIC)
                SegmentOption(localizedStringResource(StringsR.string.settings_custom), selected = optimization == DeviceOptimization.CUSTOM)
            }
        }
    }
}

@Composable
private fun SeekModeCard(thumbnails: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    FocusCard(
        onClick = { if (enabled) onChange(!thumbnails) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onChange(true); true }
                Key.DirectionRight -> { onChange(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_seeking_in_the_player),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    if (!enabled) {
                        localizedStringResource(StringsR.string.settings_chosen_automatically_for_this_device)
                    } else if (thumbnails) {
                        localizedStringResource(StringsR.string.settings_pause_and_show_thumbnails_of_the_chosen)
                    } else {
                        localizedStringResource(StringsR.string.settings_skip_forward_or_back_10_s_instantly)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), AppShapes.Pill)
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_thumbnails), selected = thumbnails)
                SegmentOption(localizedStringResource(StringsR.string.settings_10_s_skips), selected = !thumbnails)
            }
        }
    }
}

@Composable
private fun LanguageCard(current: AppLanguage, onApply: (AppLanguage) -> Unit) {
    var pending by remember(current) { mutableStateOf(current) }
    FocusCard(
        onClick = { if (pending != current) onApply(pending) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { pending = AppLanguage.ES; true }
                Key.DirectionRight -> { pending = AppLanguage.EN; true }
                else -> false
            }
        },
    ) { focused ->
        LaunchedEffect(focused) { if (!focused) pending = current }
        val changing = pending != current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_app_language),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    if (changing) {
                        if (pending == AppLanguage.EN) "OK to switch to English: the app will restart." else "OK para cambiar a español: la app se reiniciará."
                    } else {
                        localizedStringResource(StringsR.string.settings_menus_titles_and_synopses_and_preferred_audio)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (changing) Amber else Muted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), AppShapes.Pill)
                    .padding(4.dp),
            ) {
                AppLanguage.entries.forEach { SegmentOption(it.label, selected = it == pending) }
            }
        }
    }
}

@Composable
private fun qualityLabel(quality: Quality): String = if (quality == Quality.UNKNOWN) {
    localizedStringResource(StringsR.string.settings_unknown_quality)
} else {
    quality.label
}

@Composable
private fun acceptedSummary(acceptedLabels: List<String>, acceptsAll: Boolean): String = when {
    acceptedLabels.isEmpty() -> localizedStringResource(StringsR.string.settings_accepts_none)
    acceptsAll -> localizedStringResource(StringsR.string.settings_accepts_all)
    else -> localizedStringResource(StringsR.string.settings_accepts_values, acceptedLabels.joinToString(" · "))
}

@Composable
private fun SourceExclusionGroupCard(title: String, summary: String, expanded: Boolean, onClick: () -> Unit) {
    FocusCard(onClick = onClick) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.headlineSmall,
                color = if (focused) Color.White else Muted,
            )
        }
    }
}

@Composable
private fun SourceExclusionCard(label: String, excluded: Boolean, onClick: () -> Unit) {
    FocusCard(onClick = onClick) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(24.dp))
            SegmentOption(
                localizedStringResource(
                    if (excluded) StringsR.string.settings_excluded else StringsR.string.settings_shown,
                ),
                selected = excluded,
            )
        }
    }
}

@Composable
private fun ProviderCard(provider: SourceProvider, enabled: Boolean, onClick: () -> Unit) {
    FocusCard(onClick = onClick) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(localizedStringResource(provider.sectionResource), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                Text(providerNote(provider), style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            Spacer(Modifier.width(24.dp))
            SegmentOption(if (enabled) localizedStringResource(StringsR.string.settings_on_5f22c1) else localizedStringResource(StringsR.string.settings_off_25e189), selected = enabled)
        }
    }
}

@Composable
private fun providerNote(p: SourceProvider): String = when (p) {
    SourceProvider.TORRENTIO -> localizedStringResource(StringsR.string.settings_the_main_finder_like_in_stremio)
    SourceProvider.PEERFLIX -> localizedStringResource(StringsR.string.settings_spanish_torrents_from_spanish_sites_dontorrent_mejortorrent)
    SourceProvider.KNABEN -> localizedStringResource(StringsR.string.settings_bambi_s_own_finders_together_the_pirate)
    SourceProvider.THEPIRATEBAY -> localizedStringResource(StringsR.string.settings_bambi_s_own_finders_together_the_pirate)
    SourceProvider.NYAA -> localizedStringResource(StringsR.string.settings_own_anime_finder_cocoscrapers_real_debrid_only)
    SourceProvider.COMET -> localizedStringResource(StringsR.string.settings_comet_with_your_real_debrid_token_sent)
    SourceProvider.AIOSTREAMS -> localizedStringResource(StringsR.string.settings_any_stremio_addon_aiostreams_comet_mediafusion_paste)
    SourceProvider.ITHIL -> localizedStringResource(StringsR.string.settings_ithil_indexed_sources_through_alldebrid)
}

@Composable
private fun SegmentOption(text: String, selected: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = if (selected) Color.Black else Color.White.copy(alpha = 0.7f),
        modifier = Modifier
            .background(if (selected) Color.White else Color.Transparent, AppShapes.Pill)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun BufferCard(seconds: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    FocusCard(
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> {
                    if (seconds > MIN_BUFFER) onChange(-10)
                    true
                }
                Key.DirectionRight -> {
                    if (seconds < MAX_BUFFER) onChange(10)
                    true
                }
                else -> false
            }
        },
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_player_buffer),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    when {
                        !enabled -> localizedStringResource(StringsR.string.settings_chosen_automatically_from_the_available_memory)
                        seconds <= 40 -> localizedStringResource(StringsR.string.settings_lightweight_5_s_back_buffer_up_to)
                        else -> localizedStringResource(StringsR.string.settings_seconds_of_video_buffered_ahead_use_to)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Arrow("◀", enabled = enabled && focused && seconds > MIN_BUFFER)
                    Text(
                        "$seconds s",
                        fontFamily = DisplayFont,
                        fontSize = 34.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                    Arrow("▶", enabled = enabled && focused && seconds < MAX_BUFFER)
                }
                Spacer(Modifier.height(6.dp))
                if (enabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (MIN_BUFFER..MAX_BUFFER step 10).forEach { step ->
                            Box(
                                Modifier
                                    .width(22.dp)
                                    .height(4.dp)
                                    .background(
                                        if (step <= seconds) Color.White else Color.White.copy(alpha = 0.18f),
                                        AppShapes.Pill,
                                    ),
                            )
                        }
                    }
                } else {
                    Text(localizedStringResource(StringsR.string.settings_automatic_8e7638), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun Arrow(symbol: String, enabled: Boolean) {
    Text(symbol, fontSize = 18.sp, color = if (enabled) Color.White else Color.White.copy(alpha = 0.2f))
}

@Composable
private fun AboutCard(onClick: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    val testing = context.packageName.endsWith(".debug")
    FocusCard(onClick = onClick) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (testing) localizedStringResource(StringsR.string.settings_bambi_testing) else "Bambi",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_catalog_by_tmdb_sources_from_torrentio_playback),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Text(localizedStringResource(StringsR.string.settings_version, version), style = MaterialTheme.typography.titleMedium, color = Muted)
        }
    }
}

@Composable
private fun KeyEditorDialog(
    editor: KeyEditor,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    onAllDebridConnected: () -> Unit,
) {
    var input by rememberSaveable(editor.service) { mutableStateOf("") }
    val fieldRequester = remember { FocusRequester() }
    val saveRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var fieldFocused by remember { mutableStateOf(false) }
    LaunchedEffect(editor.service) {
        withFrameNanos { }
        runCatching { fieldRequester.requestFocus() }
    }
    LaunchedEffect(editor.busy, editor.error) {
        if (!editor.busy && editor.error != null) {
            withFrameNanos { }
            runCatching { saveRequester.requestFocus() }
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .onBackKey(enabled = true) { onClose() },
    ) {
        Column(
            Modifier
                .width(if (editor.service == ApiService.ALLDEBRID) 1040.dp else 720.dp)
                .background(AppColors.Surface, AppShapes.Panel)
                .border(1.dp, Color.White.copy(alpha = 0.1f), AppShapes.Panel)
                .padding(36.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceLogo(editor.service)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        localizedStringResource(editor.service.titleResource),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    Text(
                        if (editor.service == ApiService.ALLDEBRID) {
                            localizedStringResource(StringsR.string.settings_alldebrid_qr_or_manual)
                        } else {
                            localizedStringResource(
                                StringsR.string.settings_get_key_at,
                                localizedStringResource(editor.service.keyNameResource),
                                localizedStringResource(editor.service.whereToGetResource),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
            BasicTextField(
                value = input,
                onValueChange = { input = it.replace("\n", "") },
                singleLine = true,
                enabled = !editor.busy,
                textStyle = TextStyle(color = Color.White, fontSize = 20.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    onSave(input)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldRequester)
                    .onFocusChanged { fieldFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter -> {
                                if (event.type == KeyEventType.KeyUp) keyboard?.show()
                                true
                            }
                            Key.DirectionDown -> {
                                if (event.type == KeyEventType.KeyDown) runCatching { saveRequester.requestFocus() }
                                true
                            }
                            else -> false
                        }
                    },
                decorationBox = { field ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(AppColors.Field, AppShapes.Control)
                            .border(
                                width = if (fieldFocused) 2.dp else 1.dp,
                                color = if (fieldFocused) Color.White else Color.White.copy(alpha = 0.2f),
                                shape = AppShapes.Control,
                            )
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                    ) {
                        if (input.isEmpty()) {
                            Text(
                                if (fieldFocused) localizedStringResource(StringsR.string.settings_press_ok_to_type_the_new_key) else localizedStringResource(StringsR.string.settings_new_key),
                                fontSize = 18.sp,
                                color = Color.White.copy(alpha = 0.35f),
                            )
                        }
                        field()
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    editor.busy -> localizedStringResource(StringsR.string.settings_checking_with_service, localizedStringResource(editor.service.titleResource))
                    editor.error != null -> editor.error
                    else -> localizedStringResource(StringsR.string.settings_key_checked_before_saving, localizedStringResource(editor.service.titleResource))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    editor.busy -> Muted
                    editor.error != null -> Red
                    else -> Muted
                },
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = localizedStringResource(StringsR.string.settings_check_and_save),
                    onClick = { onSave(input) },
                    enabled = !editor.busy,
                    modifier = Modifier.focusRequester(saveRequester),
                )
                if (!editor.service.required && editor.hasKey) {
                    TvButton(text = localizedStringResource(StringsR.string.settings_remove_key), onClick = onRemove, enabled = !editor.busy)
                }
                TvButton(text = localizedStringResource(StringsR.string.settings_cancel), onClick = onClose, enabled = !editor.busy)
            }
                }
                if (editor.service == ApiService.ALLDEBRID) {
                    Spacer(Modifier.width(24.dp))
                    AllDebridQrPanel(onConnected = onAllDebridConnected)
                }
            }
        }
    }
}

private const val MIN_BUFFER = 20
private const val MAX_BUFFER = 100
