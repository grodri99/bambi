package com.shieldstream.tv.settings.phoneconfig

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.settings.QrCode
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.R
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.theme.DisplayFont

private val PhoneBackground = Brush.verticalGradient(listOf(Color(0xFF151622), Color.Black))
private val PhoneMuted = Color.White.copy(alpha = 0.62f)
private val PhonePanel = Color(0xFF1A1C27)

@Composable
fun PhoneConfigScreen(
    onDone: () -> Unit,
    mode: PhoneConfigMode = PhoneConfigMode.EDIT,
    onFinishSetup: (AppLanguage) -> Unit = {},
    viewModel: PhoneConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lastConfig by viewModel.lastConfig.collectAsStateWithLifecycle()
    val lastSavedConfig by viewModel.lastSavedConfig.collectAsStateWithLifecycle()
    val setupComplete = mode == PhoneConfigMode.SETUP && lastConfig?.setup?.complete == true
    val configurationSaved = mode == PhoneConfigMode.EDIT && lastSavedConfig != null
    val buttonRequester = remember { FocusRequester() }
    LaunchedEffect(mode) { viewModel.start(mode) }
    DisposableEffect(mode) { onDispose { viewModel.stop() } }
    LaunchedEffect(state::class, setupComplete, configurationSaved) {
        withFrameNanos { }
        runCatching { buttonRequester.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(PhoneBackground)
            .onBackKey(enabled = true) { onDone() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 56.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.bambi_logo),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        localizedStringResource(
                            if (mode == PhoneConfigMode.SETUP) StringsR.string.settings_phone_setup_title
                            else StringsR.string.settings_configure_from_phone,
                        ),
                        fontFamily = DisplayFont,
                        fontSize = 44.sp,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(20.dp))
                when (val s = state) {
                    PhoneConfigUiState.Loading -> Text(
                        localizedStringResource(StringsR.string.settings_phone_config_starting),
                        style = MaterialTheme.typography.titleLarge,
                        color = PhoneMuted,
                    )
                    is PhoneConfigUiState.Ready -> {
                        Text(
                            localizedStringResource(StringsR.string.settings_phone_config_scan),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            localizedStringResource(
                                when {
                                    setupComplete -> StringsR.string.settings_phone_setup_ready
                                    configurationSaved -> StringsR.string.settings_phone_config_saved
                                    else -> StringsR.string.settings_phone_config_keep_open
                                },
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (setupComplete || configurationSaved) Color(0xFF46D369) else PhoneMuted,
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(s.connection.displayUrl, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            localizedStringResource(StringsR.string.settings_phone_config_privacy),
                            style = MaterialTheme.typography.bodyLarge,
                            color = PhoneMuted,
                        )
                    }
                    PhoneConfigUiState.Expired -> Text(
                        localizedStringResource(StringsR.string.settings_phone_config_expired),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    PhoneConfigUiState.Error -> Text(
                        viewModel.errorMessage(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFFF5A5F),
                    )
                }
                Spacer(Modifier.height(30.dp))
                Row {
                    if (state is PhoneConfigUiState.Expired || state is PhoneConfigUiState.Error) {
                        TvButton(
                            localizedStringResource(StringsR.string.settings_retry),
                            onClick = { viewModel.start(mode) },
                            modifier = Modifier.focusRequester(buttonRequester),
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    if (setupComplete) {
                        TvButton(
                            localizedStringResource(StringsR.string.settings_finish_setup),
                            onClick = {
                                val language = lastConfig?.language?.uppercase()?.let(AppLanguage::fromName) ?: Lang.current
                                onFinishSetup(language)
                            },
                            modifier = if (state is PhoneConfigUiState.Expired || state is PhoneConfigUiState.Error) Modifier else Modifier.focusRequester(buttonRequester),
                        )
                    } else {
                        TvButton(
                            localizedStringResource(StringsR.string.settings_done),
                            onClick = onDone,
                            modifier = if (state is PhoneConfigUiState.Expired || state is PhoneConfigUiState.Error) Modifier else Modifier.focusRequester(buttonRequester),
                        )
                    }
                }
            }
            if (state is PhoneConfigUiState.Ready) {
                val connection = (state as PhoneConfigUiState.Ready).connection
                Spacer(Modifier.width(40.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(328.dp)
                        .background(PhonePanel, RoundedCornerShape(20.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                        .padding(24.dp),
                ) {
                    Box(Modifier.size(280.dp).background(Color.White, RoundedCornerShape(14.dp)).padding(16.dp)) {
                        QrCode(connection.url, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        localizedStringResource(StringsR.string.settings_phone_config_local_only),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
