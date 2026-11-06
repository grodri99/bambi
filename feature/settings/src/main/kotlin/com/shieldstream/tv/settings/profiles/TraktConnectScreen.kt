package com.shieldstream.tv.settings.profiles

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.settings.QrCode
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.TvButton
import kotlinx.coroutines.delay

@Composable
fun TraktConnectScreen(onDone: () -> Unit, viewModel: TraktConnectViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val buttonRequester = remember { FocusRequester() }
    LaunchedEffect(state::class) {
        delay(60)
        runCatching { buttonRequester.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(ProfileBackground)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
        ) {
            Column(Modifier.width(500.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).background(TraktRed, AppShapes.Pill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("t", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(18.dp))
                    Text(localizedStringResource(StringsR.string.settings_connect_trakt), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(32.dp))
                when (val s = state) {
                    TraktConnectUiState.Loading -> {
                        Text(localizedStringResource(StringsR.string.settings_getting_a_code_from_trakt), color = ProfileMuted, fontSize = 22.sp)
                        Spacer(Modifier.height(24.dp))
                        TvButton(localizedStringResource(StringsR.string.settings_cancel), onClick = onDone, modifier = Modifier.focusRequester(buttonRequester))
                    }
                    is TraktConnectUiState.ShowCode -> {
                        Step("1", localizedStringResource(StringsR.string.settings_on_your_phone_or_computer_go_to))
                        Text(
                            s.url.removePrefix("https://").removePrefix("www."),
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 46.dp, top = 4.dp),
                        )
                        Spacer(Modifier.height(24.dp))
                        Step("2", localizedStringResource(StringsR.string.settings_sign_in_and_enter_this_code))
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 46.dp),
                        ) {
                            s.userCode.forEach { c ->
                                Box(
                                    Modifier
                                        .size(width = 46.dp, height = 60.dp)
                                        .background(AppColors.Surface, AppShapes.Control)
                                        .border(1.dp, Color.White.copy(alpha = 0.18f), AppShapes.Control),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        c.toString(),
                                        color = Color.White,
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            localizedStringResource(StringsR.string.settings_waiting_the_code_expires_in) +
                                "%d:%02d".format(s.secondsLeft / 60, s.secondsLeft % 60),
                            color = ProfileMuted,
                            fontSize = 18.sp,
                        )
                        Spacer(Modifier.height(24.dp))
                        TvButton(localizedStringResource(StringsR.string.settings_cancel), onClick = onDone, modifier = Modifier.focusRequester(buttonRequester))
                    }
                    is TraktConnectUiState.Connected -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).background(AppColors.Success, AppShapes.Pill),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(AppIcons.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                localizedStringResource(StringsR.string.settings_trakt_connected_as, s.username),
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            localizedStringResource(StringsR.string.settings_trakt_sync_explanation),
                            color = ProfileMuted,
                            fontSize = 19.sp,
                        )
                        Spacer(Modifier.height(28.dp))
                        TvButton(localizedStringResource(StringsR.string.settings_done), onClick = onDone, modifier = Modifier.focusRequester(buttonRequester))
                    }
                    TraktConnectUiState.Expired -> {
                        Text(localizedStringResource(StringsR.string.settings_the_code_has_expired), color = Color.White, fontSize = 24.sp)
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TvButton(localizedStringResource(StringsR.string.settings_get_a_new_code), onClick = viewModel::start, modifier = Modifier.focusRequester(buttonRequester))
                            TvButton(localizedStringResource(StringsR.string.settings_cancel), onClick = onDone)
                        }
                    }
                    is TraktConnectUiState.Error -> {
                        Text(s.message, color = AppColors.Error, fontSize = 22.sp)
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TvButton(localizedStringResource(StringsR.string.settings_retry), onClick = viewModel::start, modifier = Modifier.focusRequester(buttonRequester))
                            TvButton(localizedStringResource(StringsR.string.settings_cancel), onClick = onDone)
                        }
                    }
                }
            }
            val code = state as? TraktConnectUiState.ShowCode
            if (code != null) {
                Spacer(Modifier.width(44.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(AppColors.Surface, AppShapes.Panel)
                        .padding(22.dp),
                ) {
                    Box(
                        Modifier.size(190.dp).background(Color.White, AppShapes.Control).padding(12.dp),
                    ) {
                        QrCode(code.url, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(localizedStringResource(StringsR.string.settings_or_scan_it_with_your_phone), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).background(Color.White.copy(alpha = 0.12f), AppShapes.Pill),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = ProfileMuted, fontSize = 21.sp)
    }
}
