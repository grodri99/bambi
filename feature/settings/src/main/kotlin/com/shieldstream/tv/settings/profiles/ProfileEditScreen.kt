package com.shieldstream.tv.settings.profiles

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.ProfileAvatars
import com.shieldstream.tv.model.SubtitlePreference
import com.shieldstream.tv.model.tr
import com.shieldstream.tv.settings.HomeCategoriesDialog
import com.shieldstream.tv.ui.component.ProfileAvatar
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.tvClickable

/**
 * Añadir / editar perfil: avatar grande a la izquierda; nombre, color y Trakt a la derecha; Guardar, Eliminar y
 * Cancelar abajo. Atrás = Cancelar.
 */
@Composable
fun ProfileEditScreen(
    onDone: () -> Unit,
    onConnectTrakt: (profileId: String) -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editing = state as? ProfileEditUiState.Editing
    if (editing == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val nameRequester = remember { FocusRequester() }
    // ▼ desde las opciones (infantil / PIN) → Guardar: por cercanía iba a Cancelar y se perdía lo editado.
    val saveRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { nameRequester.requestFocus() }
        // Dar el foco a un campo de texto abre el teclado de la tele aunque se pida que no: se cierra y se abre
        // solo con OK (si no, el OK siguiente pulsaba la "q" del teclado).
        repeat(3) {
            delay(120)
            keyboard?.hide()
        }
    }
    val profile = editing.profile
    // Poner PIN: se escribe dos veces (como en Netflix); si no coinciden, se vuelve a empezar.
    var creatingPin by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var choosingCategories by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(ProfileBackground)) {
        // La Shield mide 960 dp de ancho: todo con márgenes, nada de anchos fijos grandes.
        Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 72.dp)) {
            Text(
                if (editing.isNew) tr("Añadir perfil", "Add profile") else tr("Editar perfil", "Edit profile"),
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(18.dp))
            Row {
                ProfileAvatar(initial = profile.initial, avatar = profile.avatar, size = 150.dp)
                Spacer(Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    NameField(
                        value = profile.name,
                        onValueChange = viewModel::setName,
                        modifier = Modifier.focusRequester(nameRequester),
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(tr("Color", "Color"), color = ProfileMuted, fontSize = 16.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProfileAvatars.colors.indices.forEach { index ->
                            ColorChoice(
                                initial = profile.initial,
                                avatar = index,
                                selected = index == ProfileAvatars.safe(profile.avatar),
                                onClick = { viewModel.setAvatar(index) },
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    TraktCard(
                        state = editing,
                        onConnect = { viewModel.save()?.let(onConnectTrakt) },
                        onDisconnect = viewModel::disconnectTrakt,
                        onFocusLost = viewModel::cancelConfirmations,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(tr("Audio de este perfil", "Audio for this profile"), color = ProfileMuted, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OptionChip(
                            text = if (profile.spanishOnlySources) tr("Solo en español: Sí", "Spanish only: On") else tr("Solo en español: No", "Spanish only: Off"),
                            highlighted = profile.spanishOnlySources,
                            onClick = { viewModel.setSpanishOnly(!profile.spanishOnlySources) },
                        )
                        OptionChip(
                            text = if (profile.spanishFirst || profile.spanishOnlySources) tr("Español primero: Sí", "Spanish first: On") else tr("Español primero: No", "Spanish first: Off"),
                            highlighted = profile.spanishFirst || profile.spanishOnlySources,
                            onClick = { if (!profile.spanishOnlySources) viewModel.setSpanishFirst(!profile.spanishFirst) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(tr("Subtítulos por defecto", "Default subtitles"), color = ProfileMuted, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SubtitlePreference.entries.forEach { preference ->
                            OptionChip(
                                text = when (preference) {
                                    SubtitlePreference.OFF -> tr("Desactivados", "Off")
                                    SubtitlePreference.SPANISH -> tr("Español", "Spanish")
                                    SubtitlePreference.ENGLISH -> tr("Inglés", "English")
                                },
                                highlighted = profile.subtitlePreference == preference,
                                onClick = { viewModel.setSubtitlePreference(preference) },
                            )
                        }
                    }
                    Text(
                        tr(
                            "Si el archivo no trae el idioma elegido, Bambi busca y selecciona subtítulos externos.",
                            "If the file has no subtitles in the chosen language, Bambi finds and selects external subtitles.",
                        ),
                        color = ProfileMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OptionChip(
                            text = if (profile.kids) tr("Perfil infantil: Sí", "Kids profile: On") else tr("Perfil infantil: No", "Kids profile: Off"),
                            highlighted = profile.kids,
                            onClick = { viewModel.setKids(!profile.kids) },
                            modifier = Modifier.focusProperties { down = saveRequester },
                        )
                        OptionChip(
                            text = when {
                                !profile.locked -> tr("Poner PIN", "Set PIN")
                                editing.confirmRemovePin -> tr("Pulsa otra vez para quitar el PIN", "Press again to remove the PIN")
                                else -> tr("PIN puesto · OK para quitarlo", "PIN set · OK to remove")
                            },
                            highlighted = profile.locked,
                            onClick = {
                                if (profile.locked) {
                                    viewModel.removePin()
                                } else {
                                    firstPin = null
                                    pinError = null
                                    creatingPin = true
                                }
                            },
                            onFocusLost = viewModel::cancelConfirmations,
                            modifier = Modifier.focusProperties { down = saveRequester },
                        )
                        OptionChip(
                            text = tr(
                                "Categorías: ${Category.entries.count { it.name !in profile.hiddenCategories }}/${Category.entries.size}",
                                "Categories: ${Category.entries.count { it.name !in profile.hiddenCategories }}/${Category.entries.size}",
                            ),
                            highlighted = profile.hiddenCategories.isNotEmpty(),
                            onClick = { choosingCategories = true },
                            modifier = Modifier.focusProperties { down = saveRequester },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton(
                    text = tr("Guardar", "Save"),
                    onClick = {
                        viewModel.save()
                        onDone()
                    },
                    modifier = Modifier.focusRequester(saveRequester),
                )
                if (editing.canDelete) {
                    TvButton(
                        text = if (editing.confirmDelete) {
                            tr("Pulsa otra vez para eliminar", "Press again to delete")
                        } else {
                            tr("Eliminar perfil", "Delete profile")
                        },
                        onClick = { if (viewModel.delete()) onDone() },
                        modifier = Modifier.onFocusChanged { if (!it.isFocused) viewModel.cancelConfirmations() },
                    )
                }
                TvButton(text = tr("Cancelar", "Cancel"), onClick = onDone)
            }
        }
    }

    if (creatingPin) {
        PinDialog(
            title = if (firstPin == null) tr("Nuevo PIN", "New PIN") else tr("Repite el PIN", "Repeat the PIN"),
            subtitle = tr("4 cifras para entrar en ${profile.name.ifBlank { "este perfil" }}", "4 digits to open ${profile.name.ifBlank { "this profile" }}"),
            error = pinError,
            onPin = { pin ->
                val first = firstPin
                when {
                    first == null -> {
                        firstPin = pin
                        pinError = null
                    }
                    first == pin -> {
                        viewModel.setPin(pin)
                        creatingPin = false
                        firstPin = null
                    }
                    else -> {
                        firstPin = null
                        pinError = tr("No coinciden: escribe el PIN nuevo otra vez", "They don't match: enter the new PIN again")
                    }
                }
            },
            onDismiss = {
                creatingPin = false
                firstPin = null
                pinError = null
            },
        )
    }

    if (choosingCategories) {
        HomeCategoriesDialog(
            hidden = profile.hiddenCategories,
            onToggle = viewModel::toggleCategory,
            onShowAll = viewModel::showAllCategories,
            onClose = { choosingCategories = false },
        )
    }
}

/** Nombre: OK abre el teclado de la tele (no al recibir el foco); ▼ baja a los colores. */
@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var pressedHere by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.replace("\n", "")) },
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
            showKeyboardOnFocus = false,
        ),
        keyboardActions = KeyboardActions(onDone = {
            keyboard?.hide()
            focusManager.moveFocus(FocusDirection.Down)
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
                        if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(FocusDirection.Down)
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        if (focused) tr("Pulsa OK para escribir el nombre", "Press OK to type the name") else tr("Nombre", "Name"),
                        fontSize = 22.sp,
                        color = Color.White.copy(alpha = 0.35f),
                    )
                }
                field()
            }
        },
    )
}

@Composable
private fun ColorChoice(initial: String, avatar: Int, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .border(
                width = 3.dp,
                color = when {
                    focused -> Color.White
                    selected -> Color.White.copy(alpha = 0.55f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(10.dp),
            )
            .padding(4.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick),
    ) {
        ProfileAvatar(initial = initial, avatar = avatar, size = 38.dp)
    }
}

/** Tarjeta de Trakt: conectar / desconectar (con confirmación) o por qué no está disponible. */
@Composable
private fun TraktCard(
    state: ProfileEditUiState.Editing,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onFocusLost: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val user = state.profile.traktUser
    val (title, subtitle) = when {
        !state.traktAvailable -> tr("Trakt no disponible", "Trakt not available") to
            tr("Esta versión de Bambi no tiene traktClientId.", "This build of Bambi has no traktClientId.")
        user != null && state.confirmDisconnect -> tr("¿Desconectar Trakt?", "Disconnect Trakt?") to
            tr("Pulsa OK otra vez para desconectar @$user", "Press OK again to disconnect @$user")
        user != null -> tr("Trakt: conectado como @$user", "Trakt: connected as @$user") to
            tr("\"Seguir viendo\" y lo que has visto se sincronizan entre tus Shields.", "\"Continue watching\" and your history sync across your Shields.")
        else -> tr("Conectar con Trakt", "Connect Trakt") to
            tr("Sincroniza \"Seguir viendo\" y lo que has visto entre tus Shields.", "Sync \"Continue watching\" and your history across your Shields.")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Color(0xFF2A2D3D) else Color(0xFF1A1C27), RoundedCornerShape(14.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) onFocusLost()
            }
            .tvClickable(enabled = state.traktAvailable && !state.busy) {
                if (user != null) onDisconnect() else onConnect()
            }
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Box(
            Modifier.size(44.dp).background(TraktRed, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text("t", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(18.dp))
        Column {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = if (state.confirmDisconnect) Color(0xFFF5B83D) else ProfileMuted, fontSize = 15.sp)
        }
    }
}

/** Opción pequeña del perfil (infantil, PIN): píldora que se ilumina con el foco. */
@Composable
private fun OptionChip(
    text: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    onFocusLost: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text,
        color = if (focused) Color.Black else Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(
                when {
                    focused -> Color.White
                    highlighted -> Color.White.copy(alpha = 0.2f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                RoundedCornerShape(50),
            )
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) onFocusLost()
            }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
