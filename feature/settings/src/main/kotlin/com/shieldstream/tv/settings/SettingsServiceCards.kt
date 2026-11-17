package com.shieldstream.tv.settings

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.strings.keyNameResource
import com.shieldstream.tv.strings.titleResource
import com.shieldstream.tv.strings.usedForResource
import com.shieldstream.tv.strings.whereToGetResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.theme.DisplayFont

// Ajustes → cuentas y servicios: tarjetas de cada servicio, debrid activo, configuración desde el móvil y editor de claves.

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
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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
                            color = SettingsMuted,
                        )
                    }
                    Text(localizedStringResource(ui.service.usedForResource), style = MaterialTheme.typography.bodyMedium, color = SettingsMuted)
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
        ServiceStatus.Missing -> SettingsAmber to localizedStringResource(StringsR.string.settings_not_set_up)
        ServiceStatus.Checking -> SettingsMuted to localizedStringResource(StringsR.string.settings_checking)
        is ServiceStatus.Ok -> SettingsGreen to (status.detail ?: localizedStringResource(StringsR.string.settings_connected))
        is ServiceStatus.Bad -> SettingsRed to status.message
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
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
internal fun PhoneConfigCard(onClick: () -> Unit) {
    FocusCard(onClick = onClick) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_configure_from_phone),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_phone_config_card_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
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
internal fun SetupGuideCard(onClick: () -> Unit) {
    FocusCard(onClick = onClick) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_setup_guide),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_repeat_the_first_run_setup_step_by),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
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
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    if (active.viaTorrentio) localizedStringResource(StringsR.string.settings_sources_and_links_through_torrentio) else localizedStringResource(StringsR.string.settings_direct_connection_to_real_debrid),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
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
internal fun KeyEditorDialog(
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
                .background(Color(0xFF1B1D29), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                .padding(36.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceLogo(editor.service)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        localizedStringResource(editor.service.titleResource),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
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
                        color = SettingsMuted,
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
                            .background(Color(0xFF101119), RoundedCornerShape(10.dp))
                            .border(
                                width = if (fieldFocused) 2.dp else 1.dp,
                                color = if (fieldFocused) Color.White else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp),
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
                    editor.busy -> SettingsMuted
                    editor.error != null -> SettingsRed
                    else -> SettingsMuted
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
