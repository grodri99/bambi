package com.shieldstream.tv.settings

import androidx.compose.ui.platform.LocalContext
import com.shieldstream.tv.ui.component.restartApp
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.SubtitlePreference
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.strings.keyNameResource
import com.shieldstream.tv.strings.titleResource
import com.shieldstream.tv.strings.R.string as SR
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.ui.R
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.tvClickable
import com.shieldstream.tv.ui.theme.DisplayFont

private val Background = Brush.verticalGradient(listOf(Color(0xFF151622), Color.Black))
private val Panel = Color(0xFF1A1C27)
private val Muted = Color.White.copy(alpha = 0.62f)
private val Green = Color(0xFF46D369)
private val Red = Color(0xFFFF5A5F)
private val Accent = Color(0xFFE50914)

@Composable
fun SetupScreen(
    onFinish: () -> Unit,
    onExit: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            .onBackKey(enabled = true) { if (!viewModel.back()) onExit() },
    ) {
        val s = state as? SetupUiState.Ready ?: return@Box
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 24.dp)) {
            SetupHeader(s.step)
            Spacer(Modifier.height(14.dp))
            when (s.step) {
                SetupStep.WELCOME -> WelcomeStep(
                    onStart = viewModel::next,
                    onLater = onExit,
                )
                SetupStep.PLAYBACK -> PlaybackPreferencesStep(
                    audio = s.audio,
                    subtitles = s.subtitles,
                    language = s.language,
                    onLanguage = viewModel::setLanguage,
                    onAudio = viewModel::setAudio,
                    onSubtitles = viewModel::setSubtitles,
                    onNext = viewModel::next,
                )
                SetupStep.TMDB -> KeyStep(
                    state = s,
                    service = ApiService.TMDB,
                    title = localizedStringResource(SR.setup_catalog),
                    intro = localizedStringResource(SR.setup_tmdb_intro),
                    steps = listOf(
                        localizedStringResource(SR.setup_tmdb_step_account),
                        localizedStringResource(SR.setup_tmdb_step_api),
                        localizedStringResource(SR.setup_tmdb_step_copy),
                    ),
                    required = true,
                    onSubmit = { viewModel.submitKey(ApiService.TMDB, it) },
                    onNext = viewModel::next,
                    onTyping = viewModel::clearError,
                )
                SetupStep.DEBRID -> KeyStep(
                    state = s,
                    service = s.debrid.apiService,
                    title = localizedStringResource(SR.setup_debrid_service),
                    intro = localizedStringResource(SR.setup_debrid_intro) +
                        if (s.debrid.viaTorrentio) localizedStringResource(SR.setup_debrid_torrentio) else "",
                    steps = debridSteps(s.debrid),
                    required = true,
                    onSubmit = { viewModel.submitKey(s.debrid.apiService, it) },
                    onNext = viewModel::next,
                    onTyping = viewModel::clearError,
                    selector = { modifier -> DebridSelectorCard(active = s.debrid, onChange = viewModel::setDebrid, modifier = modifier) },
                )
                SetupStep.SUBDL -> KeyStep(
                    state = s,
                    service = ApiService.SUBDL,
                    title = localizedStringResource(SR.setup_extra_subtitles),
                    intro = localizedStringResource(SR.setup_subdl_intro),
                    steps = listOf(
                        localizedStringResource(SR.setup_subdl_step_account),
                        localizedStringResource(SR.setup_subdl_step_api),
                        localizedStringResource(SR.setup_subdl_step_copy),
                    ),
                    required = false,
                    onSubmit = { viewModel.submitKey(ApiService.SUBDL, it) },
                    onNext = viewModel::next,
                    onTyping = viewModel::clearError,
                )
                SetupStep.DONE -> DoneStep(
                    state = s,
                    onFinish = { if (s.languageChanged) restartApp(context) else onFinish() },
                )
            }
        }
    }
}

@Composable
private fun debridSteps(debrid: DebridService): List<String> = when (debrid) {
    DebridService.REAL_DEBRID -> listOf(
        localizedStringResource(SR.setup_rd_step_account),
        localizedStringResource(SR.setup_rd_step_token),
        localizedStringResource(SR.setup_rd_step_copy),
    )
    DebridService.TORBOX -> listOf(
        localizedStringResource(SR.setup_torbox_step_account),
        localizedStringResource(SR.setup_torbox_step_settings),
        localizedStringResource(SR.setup_copy_key),
    )
    DebridService.PREMIUMIZE -> listOf(
        localizedStringResource(SR.setup_premiumize_step_account),
        localizedStringResource(SR.setup_premiumize_step_settings),
        localizedStringResource(SR.setup_copy_key),
    )
    DebridService.ALLDEBRID -> listOf(
        localizedStringResource(SR.setup_alldebrid_step_account),
        localizedStringResource(SR.setup_alldebrid_step_settings),
        localizedStringResource(SR.setup_copy_key),
    )
    DebridService.DEBRID_LINK -> listOf(
        localizedStringResource(SR.setup_debrid_link_step_account),
        localizedStringResource(SR.setup_debrid_link_step_settings),
        localizedStringResource(SR.setup_copy_key),
    )
    DebridService.OFFCLOUD -> listOf(
        localizedStringResource(SR.setup_offcloud_step_account),
        localizedStringResource(SR.setup_offcloud_step_settings),
        localizedStringResource(SR.setup_copy_key),
    )
}

// ------------------------------------------------------------------ cabecera

@Composable
private fun SetupHeader(step: SetupStep) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Image(painterResource(R.drawable.bambi_logo), contentDescription = null, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(10.dp))
        Image(painterResource(R.drawable.bambi_wordmark), contentDescription = "Bambi", modifier = Modifier.height(13.dp))
        Spacer(Modifier.weight(1f))
        step.number?.let { n ->
            Text(localizedStringResource(SR.setup_step_counter, n), fontFamily = DisplayFont, fontSize = 22.sp, color = Muted)
            Spacer(Modifier.width(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..4).forEach { i ->
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(5.dp)
                            .background(if (i <= n) Accent else Color.White.copy(alpha = 0.18f), RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ bienvenida

@Composable
private fun ColumnScope.WelcomeStep(onStart: () -> Unit, onLater: () -> Unit) {
    val startRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { startRequester.requestFocus() }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        Image(painterResource(R.drawable.bambi_logo), contentDescription = null, modifier = Modifier.size(120.dp))
        Spacer(Modifier.height(16.dp))
        Text(localizedStringResource(SR.setup_welcome_title), fontFamily = DisplayFont, fontSize = 44.sp, color = Color.White)
        Text(
            localizedStringResource(SR.setup_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WelcomeTile("1", localizedStringResource(SR.setup_catalog), localizedStringResource(SR.setup_catalog_artwork))
            WelcomeTile("2", "Debrid", "Real-Debrid, TorBox…")
            WelcomeTile("3", localizedStringResource(SR.setup_extra_subtitles), localizedStringResource(SR.setup_optional_subdl))
            WelcomeTile("4", localizedStringResource(SR.setup_preferences), localizedStringResource(SR.setup_language_audio_subtitles))
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(text = localizedStringResource(SR.setup_start), onClick = onStart, modifier = Modifier.focusRequester(startRequester))
            TvButton(text = localizedStringResource(SR.setup_later), onClick = onLater)
        }
    }
}

@Composable
private fun RowScope.WelcomeTile(number: String, title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .weight(1f)
            .height(84.dp)
            .background(Panel, RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        StepNumber(number)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun StepNumber(text: String, color: Color = Accent, size: Dp = 30.dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size).background(color, CircleShape)) {
        Text(text, fontFamily = DisplayFont, fontSize = (size.value * 0.6f).sp, color = Color.White)
    }
}

@Composable
private fun ColumnScope.PlaybackPreferencesStep(
    audio: SetupAudioPreference,
    subtitles: SubtitlePreference,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onAudio: (SetupAudioPreference) -> Unit,
    onSubtitles: (SubtitlePreference) -> Unit,
    onNext: () -> Unit,
) {
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstRequester.requestFocus() }
    }
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        Text(localizedStringResource(SR.setup_audio_subtitles_title), fontFamily = DisplayFont, fontSize = 38.sp, color = Color.White)
        Text(
            localizedStringResource(SR.setup_preferences_saved),
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )
        Spacer(Modifier.height(18.dp))
        Text(localizedStringResource(SR.setup_interface_language), style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SetupChoice(
                title = "Español",
                description = "Español",
                selected = language == AppLanguage.ES,
                onClick = { onLanguage(AppLanguage.ES) },
                modifier = Modifier.weight(1f).focusRequester(firstRequester),
            )
            SetupChoice(
                title = "English",
                description = "English",
                selected = language == AppLanguage.EN,
                onClick = { onLanguage(AppLanguage.EN) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(localizedStringResource(SR.setup_preferred_audio), style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SetupChoice(
                title = localizedStringResource(SR.setup_original),
                description = localizedStringResource(SR.setup_no_forced_language),
                selected = audio == SetupAudioPreference.ORIGINAL,
                onClick = { onAudio(SetupAudioPreference.ORIGINAL) },
                modifier = Modifier.weight(1f),
            )
            SetupChoice(
                title = localizedStringResource(SR.setup_spanish),
                description = localizedStringResource(SR.setup_prefer_available),
                selected = audio == SetupAudioPreference.SPANISH,
                onClick = { onAudio(SetupAudioPreference.SPANISH) },
                modifier = Modifier.weight(1f),
            )
            SetupChoice(
                title = localizedStringResource(SR.setup_spanish_only),
                description = localizedStringResource(SR.setup_reject_other_audio),
                selected = audio == SetupAudioPreference.SPANISH_ONLY,
                onClick = { onAudio(SetupAudioPreference.SPANISH_ONLY) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(localizedStringResource(SR.setup_automatic_subtitles), style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SetupChoice(
                title = localizedStringResource(SR.setup_off),
                description = localizedStringResource(SR.setup_manual_selection),
                selected = subtitles == SubtitlePreference.OFF,
                onClick = { onSubtitles(SubtitlePreference.OFF) },
                modifier = Modifier.weight(1f),
            )
            SetupChoice(
                title = localizedStringResource(SR.setup_spanish),
                description = localizedStringResource(SR.setup_embedded_external),
                selected = subtitles == SubtitlePreference.SPANISH,
                onClick = { onSubtitles(SubtitlePreference.SPANISH) },
                modifier = Modifier.weight(1f),
            )
            SetupChoice(
                title = localizedStringResource(SR.setup_english),
                description = localizedStringResource(SR.setup_embedded_external),
                selected = subtitles == SubtitlePreference.ENGLISH,
                onClick = { onSubtitles(SubtitlePreference.ENGLISH) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            localizedStringResource(SR.setup_external_subtitles_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(20.dp))
        TvButton(text = localizedStringResource(SR.setup_next), onClick = onNext)
    }
}

@Composable
private fun SetupChoice(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .height(76.dp)
            .background(if (selected) Color.White.copy(alpha = 0.14f) else Panel, RoundedCornerShape(12.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else if (selected) Green else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = (if (selected) "✓ " else "") + title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
        Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun ColumnScope.KeyStep(
    state: SetupUiState.Ready,
    service: ApiService,
    title: String,
    intro: String,
    steps: List<String>,
    required: Boolean,
    onSubmit: (String) -> Unit,
    onNext: () -> Unit,
    onTyping: () -> Unit,
    selector: (@Composable (Modifier) -> Unit)? = null,
) {
    val has = state.has(service)
    var editing by rememberSaveable(service) { mutableStateOf(!has) }
    val detail = state.details[service]

    var input by rememberSaveable(service) { mutableStateOf("") }
    val fieldRequester = remember(service) { FocusRequester() }
    val primaryRequester = remember(service) { FocusRequester() }
    val skipRequester = remember(service) { FocusRequester() }
    val selectorRequester = remember { FocusRequester() }
    var focusRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.step, focusRequest) {
        withFrameNanos { }
        runCatching {
            when {
                editing && !required && !has -> skipRequester.requestFocus()
                editing -> fieldRequester.requestFocus()
                else -> primaryRequester.requestFocus()
            }
        }
    }
    LaunchedEffect(detail) {
        if (detail != null && editing) {
            editing = false
            focusRequest++
        }
    }

    Row(Modifier.fillMaxWidth().weight(1f)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(title.uppercase(), fontFamily = DisplayFont, fontSize = 38.sp, color = Color.White)
            Text(intro, style = MaterialTheme.typography.bodyMedium, color = Muted)
            Spacer(Modifier.height(10.dp))
            steps.forEachIndexed { i, text ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    StepNumber("${i + 1}", color = Color.White.copy(alpha = 0.14f), size = 24.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))
            selector?.let {
                it(Modifier.focusRequester(selectorRequester))
                Spacer(Modifier.height(10.dp))
            }
            if (editing) {
                KeyField(
                    value = input,
                    onValueChange = {
                        input = it
                        onTyping()
                    },
                    enabled = !state.checking,
                    placeholder = localizedStringResource(
                        SR.setup_key_placeholder,
                        localizedStringResource(service.keyNameResource),
                        localizedStringResource(service.titleResource),
                    ),
                    onDone = { onSubmit(input) },
                    modifier = Modifier
                        .focusRequester(fieldRequester)
                        .focusProperties { if (selector != null) up = selectorRequester },
                    downRequester = primaryRequester,
                )
                Spacer(Modifier.height(10.dp))
                StatusLine(state, service)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        text = if (state.checking) localizedStringResource(SR.setup_checking) else localizedStringResource(SR.setup_check_save),
                        onClick = { onSubmit(input) },
                        enabled = !state.checking,
                        modifier = Modifier.focusRequester(primaryRequester),
                    )
                    when {
                        has -> TvButton(text = localizedStringResource(SR.setup_keep_current), onClick = {
                            editing = false
                            focusRequest++
                        })
                        !required -> TvButton(text = localizedStringResource(SR.setup_skip), onClick = onNext, modifier = Modifier.focusRequester(skipRequester))
                    }
                }
            } else {
                ConfiguredRow(service = service, maskedKey = state.keys[service], detail = detail)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        text = localizedStringResource(SR.setup_next),
                        onClick = onNext,
                        modifier = Modifier
                            .focusRequester(primaryRequester)
                            .focusProperties { if (selector != null) up = selectorRequester },
                    )
                    TvButton(text = localizedStringResource(SR.setup_change_key), onClick = {
                        input = ""
                        editing = true
                        focusRequest++
                    })
                }
            }
        }
        Spacer(Modifier.width(36.dp))
        QrPanel(url = service.url)
    }
}

@Composable
private fun StatusLine(state: SetupUiState.Ready, service: ApiService) {
    val error = state.error
    val (text, color) = when {
        state.checking -> localizedStringResource(SR.setup_checking_with, localizedStringResource(service.titleResource)) to Muted
        error != null -> error to Red
        else -> localizedStringResource(SR.setup_key_validation_explanation, localizedStringResource(service.titleResource)) to Muted
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun ConfiguredRow(service: ApiService, maskedKey: String?, detail: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Green.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .border(1.dp, Green.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp).background(Green, CircleShape)) {
            Text("✓", color = Color.Black, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                localizedStringResource(SR.setup_service_configured, localizedStringResource(service.titleResource)),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Text(
                listOfNotNull(detail, maskedKey?.let { localizedStringResource(SR.setup_masked_key, it.takeLast(4)) }).joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
        }
    }
}

@Composable
private fun KeyField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String,
    onDone: () -> Unit,
    downRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    var pressedHere by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.replace("\n", "").trim()) },
        singleLine = true,
        enabled = enabled,
        textStyle = TextStyle(color = Color.White, fontSize = 20.sp, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            showKeyboardOnFocus = false,
        ),
        keyboardActions = KeyboardActions(onDone = {
            keyboard?.hide()
            onDone()
        }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        if (event.type == KeyEventType.KeyDown) pressedHere = true
                        if (event.type == KeyEventType.KeyUp && pressedHere) {
                            pressedHere = false
                            keyboard?.show()
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        if (event.type == KeyEventType.KeyDown) runCatching { downRequester.requestFocus() }
                        true
                    }
                    else -> false
                }
            },
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF101119), RoundedCornerShape(10.dp))
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = if (focused) Color.White else Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 18.dp, vertical = 15.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        if (focused) localizedStringResource(SR.setup_press_ok_type) else placeholder,
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.35f),
                    )
                }
                field()
            }
        },
    )
}

@Composable
private fun QrPanel(url: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(250.dp)
            .background(Panel, RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(20.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(190.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp),
        ) {
            QrCode(url, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(12.dp))
        Text(
            localizedStringResource(SR.setup_scan_qr),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            url.removePrefix("https://").removePrefix("www."),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun QrCode(url: String, modifier: Modifier = Modifier) {
    val matrix: BitMatrix? = remember(url) {
        runCatching {
            QRCodeWriter().encode(
                url,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
            )
        }.getOrNull()
    }
    Canvas(modifier) {
        val m = matrix ?: return@Canvas
        val cell = size.minDimension / m.width
        for (y in 0 until m.height) {
            for (x in 0 until m.width) {
                if (m[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
            }
        }
    }
}

// ------------------------------------------------------------------ final

@Composable
private fun ColumnScope.DoneStep(state: SetupUiState.Ready, onFinish: () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp).background(Green, CircleShape)) {
            Text("✓", color = Color.Black, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text(localizedStringResource(SR.setup_all_set), fontFamily = DisplayFont, fontSize = 48.sp, color = Color.White)
        Text(
            localizedStringResource(SR.setup_done_body),
            style = MaterialTheme.typography.bodyLarge,
            color = Muted,
        )
        Spacer(Modifier.height(22.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.width(560.dp),
        ) {
            PreferenceSummaryRow(localizedStringResource(SR.setup_language), state.language.label)
            PreferenceSummaryRow(localizedStringResource(SR.setup_audio), audioLabel(state.audio))
            PreferenceSummaryRow(localizedStringResource(SR.setup_subtitles), subtitleLabel(state.subtitles))
            SummaryRow(localizedStringResource(SR.setup_catalog_tmdb), state.has(ApiService.TMDB), required = true)
            SummaryRow("Debrid · ${state.debrid.title}", state.has(state.debrid.apiService), required = true)
            SummaryRow(localizedStringResource(SR.setup_extra_subtitles_subdl), state.has(ApiService.SUBDL), required = false)
        }
        Spacer(Modifier.height(26.dp))
        TvButton(text = localizedStringResource(SR.setup_start_watching), onClick = onFinish, modifier = Modifier.focusRequester(requester))
    }
}

@Composable
private fun audioLabel(audio: SetupAudioPreference): String = when (audio) {
    SetupAudioPreference.ORIGINAL -> localizedStringResource(SR.setup_original)
    SetupAudioPreference.SPANISH -> localizedStringResource(SR.setup_spanish_first)
    SetupAudioPreference.SPANISH_ONLY -> localizedStringResource(SR.setup_spanish_only)
}

@Composable
private fun subtitleLabel(preference: SubtitlePreference): String = when (preference) {
    SubtitlePreference.OFF -> localizedStringResource(SR.setup_off)
    SubtitlePreference.SPANISH -> localizedStringResource(SR.setup_spanish)
    SubtitlePreference.ENGLISH -> localizedStringResource(SR.setup_english)
}

@Composable
private fun PreferenceSummaryRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Green)
    }
}

@Composable
private fun SummaryRow(label: String, ok: Boolean, required: Boolean) {
    val (mark, color, note) = when {
        ok -> Triple("✓", Green, localizedStringResource(SR.setup_set_up))
        required -> Triple("!", Red, localizedStringResource(SR.setup_missing_required))
        else -> Triple("–", Muted, localizedStringResource(SR.setup_not_set_optional))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(mark, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.width(24.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
        Text(note, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
