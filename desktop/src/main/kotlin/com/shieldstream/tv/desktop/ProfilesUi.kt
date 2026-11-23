package com.shieldstream.tv.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.model.ProfileAvatars
import com.shieldstream.tv.model.ProfilePin
import com.shieldstream.tv.model.ProfileTheme
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Avatar del perfil: el icono elegido o, si no hay, la inicial sobre su color (igual que en la tele). */
@Composable
fun ProfileAvatar(profile: DesktopProfile, size: Dp, modifier: Modifier = Modifier, showLock: Boolean = false) {
    val (start, end) = ProfileAvatars.colors[ProfileAvatars.safe(profile.avatar)]
    val shape = RoundedCornerShape(size * 0.14f)
    Box(modifier.size(size)) {
        Box(
            Modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(Color(start), Color(end)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(profile.initial, color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.48f).sp)
            if (profile.image != null) RemoteImage(profile.image, Modifier.size(size))
        }
        if (showLock && profile.locked) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(size * 0.05f).size(size * 0.24f)
                    .background(Color.Black.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Lock, contentDescription = "Con PIN", tint = Color.White, modifier = Modifier.size(size * 0.14f)) }
        }
    }
}

/** Nombres de los acentos, como en Ajustes → Perfiles de la tele. */
fun ProfileTheme.label(): String = when (this) {
    ProfileTheme.BAMBI -> "Bambi"
    ProfileTheme.OCEAN -> "Océano"
    ProfileTheme.FOREST -> "Bosque"
    ProfileTheme.GOLD -> "Oro"
    ProfileTheme.VIOLET -> "Violeta"
    ProfileTheme.ROSE -> "Rosa"
}

/**
 * «¿Quién está viendo?», como al abrir Bambi en la tele. Un perfil con PIN lo pide antes de entrar y antes de
 * editarlo; desde aquí se añaden (hasta 5), se editan y se borran.
 */
@Composable
fun ProfilePickerScreen(store: ProfileStore, onPick: (DesktopProfile) -> Unit, startManaging: Boolean = false) {
    var editing by remember { mutableStateOf<DesktopProfile?>(null) }
    var manage by remember { mutableStateOf(startManaging) }
    // Perfil con PIN pendiente de confirmar y qué hacer después (entrar o editarlo).
    var unlocking by remember { mutableStateOf<Pair<DesktopProfile, Boolean>?>(null) }
    Box(Modifier.fillMaxSize().background(BambiColors.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BundledImages.get("bambi_logo.png")?.let {
                androidx.compose.foundation.Image(it, contentDescription = null, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(24.dp))
            }
            Text(if (manage) "Administrar perfiles" else "¿Quién está viendo?", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(40.dp))
            val first = remember { FocusRequester() }
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                store.profiles.forEachIndexed { index, profile ->
                    ProfileTile(
                        name = profile.name,
                        badge = if (profile.kids) "NIÑOS" else null,
                        editing = manage,
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                        onClick = {
                            when {
                                profile.locked -> unlocking = profile to manage
                                manage -> editing = profile
                                else -> onPick(profile)
                            }
                        },
                    ) { ProfileAvatar(profile, 150.dp, showLock = true) }
                }
                if (store.canAdd) {
                    ProfileTile("Añadir perfil", badge = null, editing = false, onClick = {
                        editing = DesktopProfile(name = "", avatar = store.profiles.size % ProfileAvatars.colors.size)
                    }) {
                        Box(Modifier.size(150.dp).background(BambiColors.Surface, RoundedCornerShape(21.dp)), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Add, contentDescription = null, tint = BambiColors.Muted, modifier = Modifier.size(64.dp))
                        }
                    }
                }
            }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            Spacer(Modifier.height(48.dp))
            OutlinedButton(onClick = { manage = !manage }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                Text(if (manage) "Listo" else "Administrar perfiles")
            }
        }
        editing?.let { profile ->
            ProfileEditor(
                profile = profile,
                isNew = store.byId(profile.id) == null,
                canDelete = store.byId(profile.id) != null && store.profiles.size > 1,
                onSave = { store.save(it); editing = null },
                onDelete = { store.delete(profile.id); editing = null },
                onCancel = { editing = null },
            )
        }
        unlocking?.let { (profile, edit) ->
            PinDialog(
                title = profile.name,
                subtitle = if (edit) "Introduce el PIN para editar este perfil." else "Introduce el PIN para entrar.",
                check = { pin -> profile.pinMatches(pin) },
                onSuccess = {
                    unlocking = null
                    if (edit) editing = profile else onPick(profile)
                },
                onCancel = { unlocking = null },
            )
        }
    }
}

@Composable
private fun ProfileTile(
    name: String,
    badge: String?,
    editing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    avatar: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val active = interaction.collectIsHoveredAsState().value || interaction.collectIsFocusedAsState().value
    val scale by animateFloatAsState(if (active) 1.06f else 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.hoverable(interaction).clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier.scale(scale)
                .border(3.dp, if (active) Color.White else Color.Transparent, RoundedCornerShape(24.dp))
                .padding(4.dp),
        ) {
            avatar()
            if (editing) {
                Box(Modifier.align(Alignment.Center).size(48.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(name, color = if (active) Color.White else BambiColors.Muted, fontSize = 18.sp)
        if (badge != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                badge,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(BambiColors.SurfaceHigh, BambiShapes.Badge).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * Teclado de PIN de 4 cifras: con el ratón, con los números del teclado o del teclado numérico. Borrar quita la
 * última cifra y Escape cancela. Al completar las 4 cifras se comprueba solo.
 */
@Composable
fun PinDialog(
    title: String,
    subtitle: String,
    check: (String) -> Boolean,
    onSuccess: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    fun type(digit: Char) {
        if (pin.length >= ProfilePin.LENGTH) return
        wrong = false
        pin += digit
    }
    LaunchedEffect(pin) {
        if (pin.length < ProfilePin.LENGTH) return@LaunchedEffect
        delay(150)
        val typed = pin
        pin = ""
        if (check(typed)) onSuccess(typed) else wrong = true
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Overlay(onDismiss = onCancel) {
        Column(
            Modifier.width(420.dp).background(BambiColors.Surface, BambiShapes.Panel).padding(32.dp)
                .focusRequester(focus).focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val char = event.utf16CodePoint.toChar()
                    when {
                        char.isDigit() -> { type(char); true }
                        event.key == Key.Backspace -> { pin = pin.dropLast(1); true }
                        event.key == Key.Escape -> { onCancel(); true }
                        else -> false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(AppIcons.Lock, contentDescription = null, tint = BambiColors.Accent, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(if (wrong) "PIN incorrecto. Prueba otra vez." else subtitle, color = if (wrong) BambiColors.Accent else BambiColors.Muted, fontSize = 14.sp)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(ProfilePin.LENGTH) { i ->
                    Box(
                        Modifier.size(18.dp).clip(CircleShape)
                            .background(if (i < pin.length) Color.White else Color.Transparent)
                            .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    row.forEach { key ->
                        if (key.isEmpty()) Spacer(Modifier.size(72.dp, 56.dp))
                        else PinKey(key) { if (key == "⌫") pin = pin.dropLast(1) else type(key[0]) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onCancel) { Text("Cancelar", color = BambiColors.Muted) }
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val active = interaction.collectIsHoveredAsState().value
    Box(
        Modifier.size(72.dp, 56.dp)
            .background(if (active) Color.White else BambiColors.SurfaceHigh, BambiShapes.Control)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (active) Color.Black else Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
}

/** Fondo oscuro que tapa la pantalla; un clic fuera cierra (como Atrás en la tele). */
@Composable
private fun Overlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}) { content() }
    }
}

/** Grupos de iconos: primero los gatitos de Bambi (van en la app) y después los de Netflix (se cargan de sus servidores). */
object ProfileIcons {
    data class Group(val title: String, val icons: List<String>)

    val groups: List<Group> by lazy {
        val bambi = Group(
            "Bambi",
            listOf("01_sentado", "02_asomandose", "03_sonriendo", "04_agazapado", "05_palomitas", "06_jugando", "07_dormido")
                .map { "${BundledImages.ANDROID_ASSET}profile_icons_bambi/$it.webp" },
        )
        val netflix = runCatching {
            Json.parseToJsonElement(BundledImages.text("profile_icons.json").orEmpty()).jsonArray.mapNotNull { g ->
                val o = g.jsonObject
                val title = o["show"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val icons = o["icons"]?.jsonArray?.mapNotNull { it.jsonObject["url"]?.jsonPrimitive?.content }
                    ?.filter { it.startsWith("https://") }.orEmpty().distinct()
                Group(title, icons).takeIf { icons.isNotEmpty() }
            }
        }.getOrDefault(emptyList())
        (listOf(bambi) + netflix).distinctBy { it.title }
    }
}

/** Editor de perfil: nombre, color, acento, perfil infantil, PIN e icono; borrar pide confirmación. */
@Composable
internal fun ProfileEditor(
    profile: DesktopProfile,
    isNew: Boolean,
    canDelete: Boolean,
    onSave: (DesktopProfile) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var draft by remember(profile.id) { mutableStateOf(profile) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Poner o cambiar el PIN: se escribe dos veces; el primero se guarda aquí hasta confirmarlo.
    var pinStep by remember { mutableStateOf<String?>(null) }
    var choosingPin by remember { mutableStateOf(false) }
    Overlay(onDismiss = onCancel) {
        Column(Modifier.width(1040.dp).height(740.dp).background(BambiColors.Surface, BambiShapes.Panel).padding(28.dp)) {
            Text(if (isNew) "Nuevo perfil" else "Editar perfil", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.width(380.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProfileAvatar(draft, 88.dp, showLock = true)
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = { draft = draft.copy(name = it.take(20)) },
                            label = { Text("Nombre") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    EditorLabel("Color del avatar")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileAvatars.colors.forEachIndexed { index, (start, end) ->
                            Box(
                                Modifier.size(28.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(start), Color(end))))
                                    .border(2.dp, if (draft.avatar == index && draft.image == null) Color.White else Color.Transparent, CircleShape)
                                    .clickable { draft = draft.copy(avatar = index, image = null) },
                            )
                        }
                    }
                    EditorLabel("Tema de la app")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileTheme.entries.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { theme -> ThemeChip(theme, selected = draft.profileTheme == theme) { draft = draft.copy(theme = theme.name) } }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(checked = draft.kids, onCheckedChange = { draft = draft.copy(kids = it) })
                        Column {
                            Text("Perfil infantil", color = Color.White, fontSize = 15.sp)
                            Text("Inicio, portada y búsqueda solo con títulos para niños.", color = BambiColors.Muted, fontSize = 12.sp)
                        }
                    }
                    EditorLabel("PIN de 4 cifras")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { choosingPin = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                            Text(if (draft.locked) "Cambiar PIN" else "Poner PIN")
                        }
                        if (draft.locked) {
                            TextButton(onClick = { draft = draft.withPin(null) }) { Text("Quitar PIN", color = BambiColors.Muted) }
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    EditorLabel("Elige un icono")
                    LazyColumn(Modifier.weight(1f).padding(top = 4.dp)) {
                        items(ProfileIcons.groups, key = { it.title }) { group ->
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text(group.title, color = BambiColors.Muted, fontSize = 14.sp)
                                Spacer(Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                                    items(group.icons, key = { it }) { url ->
                                        val selected = draft.image == url
                                        RemoteImage(
                                            url,
                                            Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).background(BambiColors.Placeholder)
                                                .border(3.dp, if (selected) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                                                .clickable { draft = draft.copy(image = url) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canDelete) TextButton(onClick = { confirmDelete = true }) { Text("Borrar perfil", color = BambiColors.Accent) }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onCancel, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancelar") }
                Button(onClick = { onSave(draft.copy(name = draft.name.trim().ifBlank { "Perfil" })) }) { Text("Guardar") }
            }
        }
    }
    if (choosingPin) {
        val first = pinStep
        PinDialog(
            title = if (first == null) "Nuevo PIN" else "Repite el PIN",
            subtitle = if (first == null) "Elige 4 cifras para «${draft.name.ifBlank { "este perfil" }}»." else "Escríbelo otra vez para confirmarlo.",
            check = { pin -> first == null || pin == first },
            onSuccess = { pin ->
                if (first == null) {
                    pinStep = pin
                } else {
                    draft = draft.withPin(pin)
                    pinStep = null
                    choosingPin = false
                }
            },
            onCancel = { pinStep = null; choosingPin = false },
        )
    }
    if (confirmDelete) {
        Overlay(onDismiss = { confirmDelete = false }) {
            Column(Modifier.width(480.dp).background(BambiColors.Surface, BambiShapes.Panel).padding(28.dp)) {
                Text("¿Borrar a ${profile.name}?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("Se borrarán también su lista y su progreso. No se puede deshacer.", color = BambiColors.Muted, fontSize = 15.sp)
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    OutlinedButton(onClick = { confirmDelete = false }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancelar") }
                    Button(onClick = { confirmDelete = false; onDelete() }) { Text("Borrar") }
                }
            }
        }
    }
}

@Composable
private fun EditorLabel(text: String) {
    Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ThemeChip(theme: ProfileTheme, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.width(118.dp)
            .background(if (selected) BambiColors.SurfaceHigh else Color.Transparent, BambiShapes.Pill)
            .border(1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.25f), BambiShapes.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(14.dp).background(Color(theme.accent), CircleShape))
        Text(theme.label(), color = Color.White, fontSize = 14.sp)
    }
}

/**
 * Menú del avatar de arriba a la derecha: cambiar a otro perfil (con su PIN si lo tiene), administrar perfiles o
 * volver a «¿Quién está viendo?».
 */
@Composable
fun ProfileSwitchMenu(
    expanded: Boolean,
    store: ProfileStore,
    current: DesktopProfile,
    onSwitch: (DesktopProfile) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(BambiColors.Surface).width(260.dp),
    ) {
        store.profiles.filter { it.id != current.id }.forEach { profile ->
            DropdownMenuItem(
                text = { Text(profile.name + if (profile.kids) "  · Niños" else "", color = Color.White) },
                leadingIcon = { ProfileAvatar(profile, 32.dp, showLock = true) },
                onClick = { onDismiss(); onSwitch(profile) },
            )
        }
        if (store.profiles.size > 1) HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        DropdownMenuItem(
            text = { Text("Administrar perfiles", color = Color.White) },
            leadingIcon = { Icon(AppIcons.Edit, contentDescription = null, tint = BambiColors.Muted) },
            onClick = { onDismiss(); onManage() },
        )
    }
}
