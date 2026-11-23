package com.shieldstream.tv.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.domain.DebridService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ajustes como los de una app de escritorio cuidada: a la izquierda tu perfil y las secciones (cada una con su
 * icono); a la derecha la sección con su cabecera y grupos de filas (icono, título, explicación y el control a la
 * derecha), como los ajustes de Windows 11 o de macOS. Cada cambio se guarda al momento.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: ConfigStore,
    repository: DesktopRepository,
    onDone: () -> Unit,
    profile: DesktopProfile? = null,
    trakt: TraktManager? = null,
    requests: RequestStore? = null,
    profiles: ProfileStore? = null,
    update: Updater.Update? = null,
) {
    var draft by remember { mutableStateOf(config.current) }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var keys by remember { mutableStateOf<KeysCheck?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var checkJob by remember { mutableStateOf<Job?>(null) }
    fun checkKeys(cfg: DesktopConfig) {
        checkJob?.cancel()
        checking = true
        checkJob = scope.launch {
            keys = runCatching { withContext(Dispatchers.IO) { repository.checkKeys(cfg) } }.getOrNull()
            checking = false
        }
    }
    fun update(next: DesktopConfig) {
        if (next.tmdbKey != draft.tmdbKey) HomeCache.clear()
        draft = next
        saveError = runCatching { config.save(next) }.exceptionOrNull()
            ?.let { "No se han podido guardar los ajustes (${it.message}). Revisa ${ConfigStore.defaultFile()}." }
    }
    LaunchedEffect(draft.tmdbKey, draft.debridKey, draft.debrid) {
        if (draft.tmdbKey.isBlank() && draft.debridKey.isBlank()) { keys = null; return@LaunchedEffect }
        delay(if (keys == null && !checking) 0 else 900)
        checkKeys(draft)
    }
    var mpvFound by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(draft.mpvPath) {
        while (true) {
            mpvFound = withContext(Dispatchers.IO) { MpvPlayer.find(draft.mpvPath) }
            delay(3_000)
        }
    }
    var tab by remember { mutableStateOf(SettingsTourHook.tab ?: SettingsTab.ACCOUNT) }
    val tabs = SettingsTab.entries.filter { it != SettingsTab.FAMILY || (requests != null && profile?.kids != true) }

    Row(Modifier.fillMaxSize().padding(top = 84.dp)) {
        // ---------------------------------------------------------------- barra lateral
        Column(
            Modifier.width(300.dp).fillMaxHeight().padding(start = 36.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Ajustes", color = BambiColors.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp, bottom = 16.dp))
            profile?.let { p ->
                Row(
                    Modifier.fillMaxWidth().clip(BambiShapes.Panel).background(BambiColors.Surface)
                        .border(1.dp, BambiColors.Stroke, BambiShapes.Panel).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProfileAvatar(p, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (p.kids) "Perfil infantil" else "Tu perfil", color = BambiColors.Muted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            tabs.forEach { t -> SettingsNavItem(t, selected = tab == t) { tab = t } }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                if (saveError == null) {
                    Icon(AppIcons.Check, contentDescription = null, tint = BambiColors.Cached, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    saveError ?: "Los cambios se guardan solos",
                    color = if (saveError != null) BambiColors.Accent else BambiColors.Muted,
                    fontSize = 12.sp,
                )
            }
            Text("Bambi ${Updater.VERSION} para Windows", color = BambiColors.Muted.copy(alpha = 0.45f), fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
        }

        // ---------------------------------------------------------------- contenido
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 48.dp, top = 16.dp, bottom = 56.dp)
                .wrapContentWidth(Alignment.Start)
                .widthIn(max = 880.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            PageHeader(tab)
            when (tab) {
                SettingsTab.ACCOUNT -> {
                    SettingsGroup("Catálogo") {
                        KeyRow(
                            icon = AppIcons.Movie,
                            tint = Color(0xFF01B4E4),
                            title = "TMDB",
                            hint = "Carátulas, fichas y el catálogo. Gratis en themoviedb.org → Ajustes → API.",
                            value = draft.tmdbKey,
                            status = keys?.tmdb?.takeIf { draft.tmdbKey.isNotBlank() },
                            checking = checking,
                            link = TMDB_KEY_PAGE,
                        ) { update(draft.copy(tmdbKey = it.trim())) }
                    }
                    SettingsGroup("Servicio debrid", footer = "Las fuentes llegan ya resueltas con tu servicio. Si cambias de servicio, cambia también la clave.") {
                        SettingRow(AppIcons.Bolt, BambiColors.Cached, "Servicio", "El que usas para reproducir.")
                        Pills(
                            DebridService.entries.map { it.title to it },
                            selected = draft.debridService,
                            modifier = Modifier.padding(start = 70.dp, end = 20.dp, bottom = 16.dp),
                        ) { update(draft.copy(debrid = it.name)) }
                        RowDivider()
                        KeyRow(
                            icon = AppIcons.Key,
                            tint = BambiColors.Gold,
                            title = "Clave de ${draft.debridService.title}",
                            hint = "La API key de tu cuenta.",
                            value = draft.debridKey,
                            status = keys?.debrid?.takeIf { draft.debridKey.isNotBlank() },
                            checking = checking,
                            link = keyPage(draft.debridService),
                        ) { update(draft.copy(debridKey = it.trim())) }
                        if (draft.debridService == DebridService.ALLDEBRID) {
                            RowDivider()
                            Box(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                                AllDebridConnect { key -> update(draft.copy(debridKey = key)) }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        GhostButton("Comprobar las claves", enabled = !checking) { checkKeys(draft) }
                        if (checking) CircularProgressIndicator(Modifier.size(18.dp), color = BambiColors.Accent, strokeWidth = 2.dp)
                        if (keys?.tmdb?.ok == true) GhostButton("Ir al Inicio", primary = true) { onDone() }
                    }
                }
                SettingsTab.PLAYBACK -> {
                    SettingsGroup("Al ver") {
                        SwitchRow(AppIcons.SkipNext, Color(0xFF4FA3FF), "Siguiente episodio automático", "Tarjeta en los créditos y el siguiente capítulo al acabar.", draft.autoNext) { update(draft.copy(autoNext = it)) }
                        RowDivider()
                        SwitchRow(AppIcons.Tv, Color(0xFFB18CFF), "Ajustar a la frecuencia de la pantalla", "Movimiento más suave en películas a 24 fps.", draft.matchRefresh) { update(draft.copy(matchRefresh = it)) }
                        RowDivider()
                        SwitchRow(AppIcons.List, Color(0xFFFF9F43), "Reparto en las fichas", "Actores y personajes debajo de la sinopsis.", draft.showCast) { update(draft.copy(showCast = it)) }
                    }
                    SettingsGroup("Subtítulos", footer = "En el reproductor, botón CC (o la tecla C): «En internet» busca más y «Editar subtítulos» cambia color, fondo, letra, posición y sincronía en vivo.") {
                        SwitchRow(
                            AppIcons.Subtitles,
                            Color(0xFF46D369),
                            "Poner subtítulos en español automáticamente",
                            "Si el audio no está en español y el vídeo no trae subtítulos en español, Bambi los busca en internet (OpenSubtitles y Podnapisi) y los pone.",
                            draft.autoSubtitles,
                        ) { update(draft.copy(autoSubtitles = it)) }
                        RowDivider()
                        SettingRow(AppIcons.Edit, Color(0xFF4FA3FF), "Tamaño de los subtítulos", null) {
                            Pills(
                                listOf("Pequeño" to 80, "Normal" to 100, "Grande" to 125, "Enorme" to 150),
                                selected = draft.subtitleStyle.size,
                            ) { update(draft.copy(subtitleStyle = draft.subtitleStyle.copy(size = it))) }
                        }
                    }
                    SettingsGroup("Idioma del contenido", footer = "Títulos, sinopsis y audio preferido. Los menús siguen en español.") {
                        SettingRow(AppIcons.Translate, Color(0xFFFF6FAE), "Idioma", null) {
                            Pills(
                                com.shieldstream.tv.model.AppLanguage.entries.map { it.label to it },
                                selected = com.shieldstream.tv.model.AppLanguage.entries.firstOrNull { it.name == draft.language }
                                    ?: com.shieldstream.tv.model.AppLanguage.default(),
                            ) { lang ->
                                update(draft.copy(language = lang.name))
                                com.shieldstream.tv.model.Lang.current = lang
                                HomeCache.clear()
                            }
                        }
                    }
                    SettingsGroup("Reproductor") {
                        val found = mpvFound
                        SettingRow(
                            AppIcons.Play,
                            if (found != null) BambiColors.Cached else BambiColors.Accent,
                            "mpv",
                            found ?: "No se encuentra mpv: vuelve a descomprimir Bambi o pon la ruta de mpv.exe.",
                        )
                        RowDivider()
                        Box(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            BambiTextField(draft.mpvPath, "Ruta de mpv (opcional; Bambi ya lleva el suyo)") { update(draft.copy(mpvPath = it)) }
                        }
                    }
                }
                SettingsTab.SOURCES -> {
                    SettingsGroup("Al elegir fuente") {
                        SwitchRow(AppIcons.Star, BambiColors.Gold, "Castellano primero", "Las fuentes en castellano van delante y «Reproducir» las prueba antes.", draft.spanishFirst) { update(draft.copy(spanishFirst = it)) }
                        RowDivider()
                        SwitchRow(AppIcons.Lock, Color(0xFF4FA3FF), "No cambiar de fuente si la elegida falla", "Al elegir una a mano, solo se prueba esa.", draft.keepChosenSource) { update(draft.copy(keepChosenSource = it)) }
                        RowDivider()
                        SwitchRow(
                            AppIcons.Movie,
                            Color(0xFFFF9F43),
                            "Ocultar lo que aún no se puede ver en España",
                            "Quita del Inicio las películas que aún no han salido en digital en España (en cines casi nunca hay castellano). La búsqueda las sigue enseñando.",
                            draft.hideUnreleased,
                        ) {
                            update(draft.copy(hideUnreleased = it))
                            HomeCache.clear()
                        }
                    }
                    SettingsGroup(null) { Box(Modifier.padding(20.dp)) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { ExclusionsSection(draft) { update(it) } } } }
                    SettingsGroup(null) { Box(Modifier.padding(20.dp)) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { BrokenSourcesSection(BrokenSources.default) } } }
                }
                SettingsTab.ADDONS -> SettingsCard(null, null) { AddonsSection(draft.addons, repository) { update(draft.copy(addons = it)) } }
                SettingsTab.TRAKT -> SettingsCard(null, null) { TraktSection(draft, onChange = { update(it) }, profile = profile, trakt = trakt) }
                SettingsTab.HOME -> if (profile != null && profiles != null) SettingsCard(null, null) { CategoriesSection(profile, profiles) }
                SettingsTab.FAMILY -> if (requests != null && profiles != null) SettingsCard(null, null) { RequestsSection(requests, profiles) }
                SettingsTab.ABOUT -> {
                    SettingsCard(null, null) {
                        UpdatesSection(update) { kotlin.system.exitProcess(0) }
                    }
                    Text("Tus ajustes están en ${ConfigStore.defaultFile().parentFile}. Las claves van cifradas con tu usuario de Windows.", color = BambiColors.Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

enum class SettingsTab(val label: String, val hint: String) {
    ACCOUNT("Cuenta y claves", "Las claves con las que Bambi busca el catálogo y reproduce. Se guardan cifradas con tu usuario de Windows."),
    PLAYBACK("Reproducción", "Cómo se ve: siguiente episodio, subtítulos, idioma y el reproductor."),
    SOURCES("Fuentes", "Qué fuentes salen, en qué orden y cuáles no quieres ver nunca."),
    ADDONS("Addons", "Más fuentes con tus addons de Stremio."),
    TRAKT("Trakt", "Sincroniza lo que ves con la tele y con otros PC."),
    HOME("Inicio", "Las categorías que salen en el Inicio de este perfil."),
    FAMILY("Peticiones", "Lo que han pedido los perfiles infantiles."),
    ABOUT("Bambi", "Instalación, actualizaciones y dónde se guarda todo."),
    ;

    val icon: ImageVector
        get() = when (this) {
            ACCOUNT -> AppIcons.Key
            PLAYBACK -> AppIcons.Play
            SOURCES -> AppIcons.List
            ADDONS -> AppIcons.Extension
            TRAKT -> AppIcons.Check
            HOME -> AppIcons.Home
            FAMILY -> AppIcons.Mail
            ABOUT -> AppIcons.Info
        }

    val tint: Color
        get() = when (this) {
            ACCOUNT -> Color(0xFFF5C451)
            PLAYBACK -> Color(0xFF4FA3FF)
            SOURCES -> Color(0xFF46D369)
            ADDONS -> Color(0xFFB18CFF)
            TRAKT -> Color(0xFFED4C5C)
            HOME -> Color(0xFFFF9F43)
            FAMILY -> Color(0xFFFF6FAE)
            ABOUT -> Color(0xFFB8BCC8)
        }
}

// -------------------------------------------------------------------------------------------------------------------
// Piezas
// -------------------------------------------------------------------------------------------------------------------

/** Cuadradito de color con el icono, como en los ajustes de macOS. */
@Composable
private fun IconTile(icon: ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f)) }
}

@Composable
private fun SettingsNavItem(tab: SettingsTab, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered = interaction.collectIsHoveredAsState().value || interaction.collectIsFocusedAsState().value
    val bg by animateColorAsState(if (selected) BambiColors.SurfaceHigh else if (hovered) BambiColors.Surface else Color.Transparent)
    Row(
        Modifier.fillMaxWidth().clip(BambiShapes.Control).background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(tab.icon, tab.tint, 30.dp)
        Text(
            tab.label,
            color = if (selected) Color.White else BambiColors.Text.copy(alpha = 0.78f),
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun PageHeader(tab: SettingsTab) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        IconTile(tab.icon, tab.tint, 52.dp)
        Column {
            Text(tab.label, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(tab.hint, color = BambiColors.Muted, fontSize = 14.sp)
        }
    }
}

/** Grupo de filas con su título encima y una nota debajo. */
@Composable
fun SettingsGroup(title: String?, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            Text(it.uppercase(), color = BambiColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 4.dp))
        }
        Column(
            Modifier.fillMaxWidth().clip(BambiShapes.Panel).background(BambiColors.Surface)
                .border(1.dp, BambiColors.Stroke, BambiShapes.Panel),
            content = content,
        )
        footer?.let { Text(it, color = BambiColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp)) }
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 70.dp).height(1.dp).background(BambiColors.Stroke))
}

/** Una fila: icono, título, explicación y, a la derecha, su control. */
@Composable
private fun SettingRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth()
            .background(if (onClick != null && hovered) Color.White.copy(alpha = 0.03f) else Color.Transparent)
            .then(if (onClick != null) Modifier.hoverable(interaction).clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, color = BambiColors.Muted, fontSize = 13.sp, lineHeight = 18.sp) }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, tint: Color, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon, tint, title, subtitle, onClick = { onChange(!checked) }) { BambiSwitch(checked, onChange) }
}

@Composable
fun BambiSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = BambiColors.Accent,
            checkedBorderColor = BambiColors.Accent,
            uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
            uncheckedTrackColor = BambiColors.SurfaceHigh,
            uncheckedBorderColor = Color.White.copy(alpha = 0.18f),
        ),
    )
}

/** Selector en pastillas (la elegida, blanca), como los filtros de Netflix. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> Pills(options: List<Pair<String, T>>, selected: T, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (label, value) ->
            val on = value == selected
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Box(
                Modifier.clip(BambiShapes.Pill)
                    .background(if (on) Color.White else if (hovered) BambiColors.SurfaceHigh else Color.Transparent)
                    .border(1.dp, if (on) Color.White else Color.White.copy(alpha = 0.22f), BambiShapes.Pill)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(label, color = if (on) Color.Black else Color.White, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium) }
        }
    }
}

@Composable
private fun BambiTextField(value: String, label: String, password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = BambiShapes.Control,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White.copy(alpha = 0.8f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
            focusedLabelColor = Color.White,
            unfocusedLabelColor = BambiColors.Muted,
            cursorColor = BambiColors.Accent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Una clave: guardada se ve tapada («•••• a3f9») con su estado (correcta / no vale) y «Cambiar»; al cambiarla o si
 * está vacía, el campo para pegarla.
 */
@Composable
private fun KeyRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    hint: String,
    value: String,
    status: KeyStatus?,
    checking: Boolean,
    link: String,
    onChange: (String) -> Unit,
) {
    var editing by remember(title) { mutableStateOf(false) }
    val showField = editing || value.isBlank()
    Column {
        SettingRow(icon, tint, title, if (status != null && !status.ok) status.message else hint) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    value.isBlank() -> StatusPill("Falta", ok = false)
                    checking && status == null -> CircularProgressIndicator(Modifier.size(16.dp), color = BambiColors.Muted, strokeWidth = 2.dp)
                    status != null -> StatusPill(if (status.ok) "Correcta" else "No vale", ok = status.ok)
                }
                if (!showField) {
                    Text("•••• " + value.takeLast(4), color = BambiColors.Muted, fontSize = 14.sp)
                    GhostButton("Cambiar") { editing = true }
                }
            }
        }
        if (showField) {
            Row(
                Modifier.fillMaxWidth().padding(start = 70.dp, end = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.weight(1f)) { BambiTextField(value, "Pega aquí la clave", password = true, onChange = onChange) }
                if (editing && value.isNotBlank()) GhostButton("Listo") { editing = false }
            }
        }
        Row(Modifier.padding(start = 62.dp, bottom = 8.dp)) { LinkButton("Dónde sacar la clave", link) }
    }
}

@Composable
private fun StatusPill(text: String, ok: Boolean) {
    val color = if (ok) BambiColors.Cached else BambiColors.Accent
    Row(
        Modifier.clip(BambiShapes.Pill).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Botón discreto (o blanco, si es el principal), con el mismo aire que los de la ficha. */
@Composable
fun GhostButton(label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.clip(BambiShapes.Pill)
            .background(
                when {
                    primary -> if (hovered) Color.White.copy(alpha = 0.85f) else Color.White
                    hovered && enabled -> BambiColors.SurfaceHigh
                    else -> BambiColors.Glass.copy(alpha = 0.06f)
                },
            )
            .border(1.dp, if (primary) Color.Transparent else Color.White.copy(alpha = 0.2f), BambiShapes.Pill)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (primary) Color.Black else Color.White.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Tarjeta de ajustes para las secciones largas (addons, Trakt…), con el mismo estilo que los grupos. */
@Composable
fun SettingsCard(title: String?, hint: String?, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(BambiShapes.Panel).background(BambiColors.Surface)
            .border(1.dp, BambiColors.Stroke, BambiShapes.Panel)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (title != null) {
            Column {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                hint?.let { Text(it, color = BambiColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        content()
    }
}

/** Interruptor con título y explicación (para las secciones de otros ficheros). */
@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            subtitle?.let { Text(it, color = BambiColors.Muted, fontSize = 13.sp) }
        }
        BambiSwitch(checked, onChange)
    }
}

/** Botón que abre una web en el navegador del PC. */
@Composable
fun LinkButton(label: String, url: String) {
    androidx.compose.material3.TextButton(onClick = { openInBrowser(url) }) { Text("$label ↗", color = BambiColors.Text.copy(alpha = 0.7f), fontSize = 13.sp) }
}

@Composable
internal fun Section(title: String, hint: String) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(hint, color = BambiColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
internal fun StatusLine(status: KeyStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.size(9.dp).background(if (status.ok) BambiColors.Cached else BambiColors.Accent, CircleShape))
        Text(status.message, color = BambiColors.Text, fontSize = 14.sp)
    }
}

/** Categorías del Inicio de este perfil (como en la tele, cada perfil elige las suyas). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CategoriesSection(profile: DesktopProfile, profiles: ProfileStore) {
    Section("Categorías del Inicio", "Las que se ven debajo de las filas principales en «${profile.name}».")
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        com.shieldstream.tv.model.Category.entries
            .filter { !profile.kids || it in com.shieldstream.tv.model.Category.KIDS_SAFE }
            .forEach { c ->
                val visible = c.name !in profile.hiddenCategories
                FilterChip(
                    selected = visible,
                    onClick = {
                        val hidden = if (visible) profile.hiddenCategories + c.name else profile.hiddenCategories - c.name
                        profiles.save(profile.copy(hiddenCategories = hidden))
                    },
                    label = { Text(CategorySpecs.title(c)) },
                )
            }
    }
}

/** Fuentes que no quieres ver nunca, como en la tele: por calidad, HDR o audio. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ExclusionsSection(draft: DesktopConfig, onChange: (DesktopConfig) -> Unit) {
    Section("Excluir fuentes", "Marca lo que no quieras ver en la lista de fuentes ni en «Reproducir».")
    @Composable
    fun <T : Enum<T>> chips(title: String, values: List<T>, label: (T) -> String, selected: Set<String>, onToggle: (Set<String>) -> Unit) {
        Text(title, color = BambiColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { v ->
                val on = v.name in selected
                FilterChip(selected = on, onClick = { onToggle(if (on) selected - v.name else selected + v.name) }, label = { Text(label(v)) })
            }
        }
    }
    chips("CALIDAD", com.shieldstream.tv.model.Quality.entries, { it.label }, draft.excludedQualities) { onChange(draft.copy(excludedQualities = it)) }
    chips("HDR", com.shieldstream.tv.model.HdrType.entries.filter { it != com.shieldstream.tv.model.HdrType.SDR }, { it.label }, draft.excludedHdr) { onChange(draft.copy(excludedHdr = it)) }
    chips("AUDIO", com.shieldstream.tv.model.AudioCodec.entries, { it.label }, draft.excludedAudio) { onChange(draft.copy(excludedAudio = it)) }
}

/** Solo para la prueba con datos reales: abrir Ajustes en una sección. */
object SettingsTourHook {
    @Volatile
    var tab: SettingsTab? = null
}
