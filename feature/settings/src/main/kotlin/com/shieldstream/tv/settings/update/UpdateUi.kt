package com.shieldstream.tv.settings.update

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.domain.AppUpdate
import com.shieldstream.tv.domain.UpdateState
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.tvClickable
import kotlinx.coroutines.delay

private val Panel = Color(0xFF1A1C27)
private val Muted = Color.White.copy(alpha = 0.6f)
private val Red = Color(0xFFE50914)

@Composable
fun UpdatePrompt(viewModel: UpdateViewModel = hiltViewModel()) {
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }
    val visible = prompt as? UpdatePromptUiState.Visible ?: return
    val context = LocalContext.current
    Dialog(
        onDismissRequest = viewModel::later,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .width(620.dp)
                .background(Panel, RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(32.dp),
        ) {
            when (val s = visible.state) {
                is UpdateState.Available -> UpdateBody(
                    title = localizedStringResource(StringsR.string.update_available_version, s.update.version),
                    subtitle = localizedStringResource(StringsR.string.update_current_version, s.current),
                    update = s.update,
                ) {
                    Buttons(primary = localizedStringResource(StringsR.string.settings_update), onPrimary = viewModel::update, secondary = localizedStringResource(StringsR.string.settings_not_now), onSecondary = viewModel::later)
                }
                is UpdateState.Downloading -> UpdateBody(
                    title = localizedStringResource(StringsR.string.update_downloading_version, s.update.version),
                    subtitle = "${(s.progress * 100).toInt()} %",
                    update = null,
                ) {
                    ProgressBar(s.progress)
                    Spacer(Modifier.height(20.dp))
                    Buttons(primary = localizedStringResource(StringsR.string.settings_keep_downloading_in_background), onPrimary = viewModel::later)
                }
                is UpdateState.Installing -> UpdateBody(
                    title = localizedStringResource(StringsR.string.settings_ready_to_install),
                    subtitle = localizedStringResource(StringsR.string.update_installing_version, s.update.version),
                    update = null,
                ) {
                    Buttons(primary = localizedStringResource(StringsR.string.settings_open_the_installer_again), onPrimary = viewModel::update, secondary = localizedStringResource(StringsR.string.settings_close), onSecondary = viewModel::later)
                }
                is UpdateState.NeedsPermission -> UpdateBody(
                    title = localizedStringResource(StringsR.string.settings_one_permission_needed_first_time_only),
                    subtitle = localizedStringResource(StringsR.string.settings_android_asks_to_allow_franklix_to_install),
                    update = null,
                ) {
                    Buttons(
                        primary = localizedStringResource(StringsR.string.settings_open_settings),
                        onPrimary = { viewModel.openInstallPermission(context) },
                        secondary = localizedStringResource(StringsR.string.settings_done_update),
                        onSecondary = viewModel::update,
                    )
                }
                is UpdateState.Failed -> UpdateBody(
                    title = localizedStringResource(StringsR.string.settings_couldn_t_update),
                    subtitle = s.message,
                    update = null,
                ) {
                    Buttons(primary = localizedStringResource(StringsR.string.settings_retry), onPrimary = viewModel::update, secondary = localizedStringResource(StringsR.string.settings_not_now), onSecondary = viewModel::later)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun UpdateBody(title: String, subtitle: String, update: AppUpdate?, actions: @Composable () -> Unit) {
    Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = Muted, fontSize = 17.sp)
    val notes = update?.notes?.let(::cleanNotes).orEmpty()
    if (notes.isNotBlank()) {
        Spacer(Modifier.height(18.dp))
        Text(localizedStringResource(StringsR.string.settings_what_s_new), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(notes, color = Muted, fontSize = 16.sp, maxLines = 9, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(26.dp))
    actions()
}

private fun cleanNotes(raw: String): String = raw
    .substringBefore("### Instalar")
    .lines()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") }
    .joinToString("\n") { it.replace("**", "").replace("`", "") }

@Composable
private fun Buttons(primary: String, onPrimary: () -> Unit, secondary: String? = null, onSecondary: () -> Unit = {}) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(primary) {
        repeat(3) {
            runCatching { requester.requestFocus() }
            delay(100)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        TvButton(text = primary, onClick = onPrimary, modifier = Modifier.focusRequester(requester))
        if (secondary != null) TvButton(text = secondary, onClick = onSecondary)
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(8.dp)
                .background(Red, RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
fun UpdateCard(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val (title, subtitle) = when (val s = state) {
        UpdateState.Unsupported -> localizedStringResource(StringsR.string.settings_updates) to
            localizedStringResource(StringsR.string.settings_the_testing_app_doesn_t_self_update)
        UpdateState.Idle -> localizedStringResource(StringsR.string.settings_check_for_updates) to
            localizedStringResource(StringsR.string.settings_franklix_checks_for_new_versions_when_it)
        UpdateState.Checking -> localizedStringResource(StringsR.string.settings_checking_for_updates) to ""
        is UpdateState.UpToDate -> localizedStringResource(StringsR.string.settings_you_re_up_to_date) to
            localizedStringResource(StringsR.string.update_latest_version, s.current)
        is UpdateState.Available -> localizedStringResource(StringsR.string.update_new_version, s.update.version) to
            localizedStringResource(StringsR.string.settings_ok_to_download_and_install_it)
        is UpdateState.Downloading -> localizedStringResource(StringsR.string.update_downloading_version, s.update.version) to "${(s.progress * 100).toInt()} %"
        is UpdateState.NeedsPermission -> localizedStringResource(StringsR.string.settings_install_permission_needed) to
            localizedStringResource(StringsR.string.settings_ok_opens_the_list_turn_franklix_on)
        is UpdateState.Installing -> localizedStringResource(StringsR.string.settings_ready_to_install) to
            localizedStringResource(StringsR.string.settings_press_install_in_android_s_window)
        is UpdateState.Failed -> localizedStringResource(StringsR.string.settings_couldn_t_update) to s.message
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Color(0xFF2A2D3D) else Panel, RoundedCornerShape(14.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .tvClickable {
                when (val s = state) {
                    is UpdateState.Available -> viewModel.update()
                    is UpdateState.NeedsPermission -> if (!viewModel.openInstallPermission(context)) viewModel.update()
                    is UpdateState.Failed -> if (s.update != null) viewModel.update() else viewModel.checkNow()
                    UpdateState.Idle, is UpdateState.UpToDate -> viewModel.checkNow()
                    else -> Unit
                }
            }
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotEmpty()) Text(subtitle, color = Muted, fontSize = 15.sp)
        }
        if (state !is UpdateState.Unsupported) {
            Spacer(Modifier.width(16.dp))
            Text("OK ›", color = Muted, fontSize = 16.sp)
        }
    }
}
