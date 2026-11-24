package com.shieldstream.tv.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.model.CatalogItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Shows : Screen
    data object Movies : Screen
    data object MyList : Screen
    data object Search : Screen
    data object Settings : Screen

    /** Centro de estrenos (en la tele, «Episodios»). */
    data object Releases : Screen

    /** Guía del primer arranque (sin clave de TMDB). */
    data object Setup : Screen
    data class Details(val item: CatalogItem, val autoPlay: Boolean = false, val season: Int? = null, val episode: Int? = null) : Screen
    data class Player(val request: PlayRequest) : Screen

    /** Una persona del reparto y su filmografía. */
    data class Person(val cast: CastMember) : Screen
}

/** Lo que se cierra con Escape o «Atrás» antes de cambiar de pantalla (el panel de fuentes de una ficha). */
object BackPress {
    @Volatile
    var handler: (() -> Boolean)? = null
}

/** Pila de pantallas: Escape o «Atrás» vuelven a la anterior. */
class Navigator(start: Screen) {
    val stack = mutableStateListOf(start)

    /** Posición de cada lista (Inicio, Series…) y el último póster abierto, para dejarlo todo como estaba al volver. */
    val listStates = HashMap<String, androidx.compose.foundation.lazy.LazyListState>()
    val focus = FocusRestore()

    fun listState(key: String) = listStates.getOrPut(key) { androidx.compose.foundation.lazy.LazyListState() }

    /**
     * «Reproducir» al abrir una ficha vale una sola vez por ficha abierta. Al volver del vídeo (Escape) la ficha se
     * vuelve a pintar y, como seguía marcada para reproducir, a veces arrancaba otra vez sola el mismo episodio.
     */
    private val autoPlayed = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Screen, Boolean>())

    fun takeAutoPlay(screen: Screen.Details): Boolean = screen.autoPlay && autoPlayed.add(screen)
    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1
    fun open(screen: Screen) {
        // La misma pantalla dos veces seguidas dejaba un reproductor «vacío» debajo al salir.
        if (stack.lastOrNull() != screen) stack.add(screen)
    }
    fun section(screen: Screen) { stack.clear(); stack.add(screen) }
    fun back() {
        if (BackPress.handler?.invoke() == true) return
        if (canGoBack) stack.removeAt(stack.lastIndex)
    }
}

/** Mensaje breve abajo (fuente que no se pudo abrir, ajustes guardados…). */
class Notice {
    var text by mutableStateOf<String?>(null)
}

/** Versión nueva encontrada en GitHub (se instala desde Ajustes). */
class UpdateState {
    var available by mutableStateOf<Updater.Update?>(null)
}

/** Perfil elegido y su biblioteca; null = «¿Quién está viendo?». */
class Session(initial: DesktopProfile? = null) {
    private var current by mutableStateOf(initial)

    /** Lo mismo que [profile], para quien lo sigue fuera de la interfaz (Trakt). */
    val flow = kotlinx.coroutines.flow.MutableStateFlow(initial)

    var profile: DesktopProfile?
        get() = current
        set(value) {
            current = value
            flow.value = value
        }
    /** «¿Quién está viendo?» abierto desde el menú del avatar, directamente en «Administrar perfiles». */
    var managing by mutableStateOf(false)
}

@Composable
fun BambiApp(
    repository: DesktopRepository,
    config: ConfigStore,
    profiles: ProfileStore,
    navigator: Navigator,
    session: Session,
    notice: Notice = remember { Notice() },
    /** Trakt (null en las capturas). */
    trakt: TraktManager? = null,
    updates: UpdateState = remember { UpdateState() },
    requests: RequestStore = remember { RequestStore(java.io.File(ConfigStore.defaultFile().parentFile, "requests.json")) },
    /** Pantalla completa mientras se reproduce. */
    onFullscreen: (Boolean) -> Unit = {},
) {
    BambiTheme {
        val focusManager = LocalFocusManager.current
        Box(
            Modifier.fillMaxSize().background(BambiColors.Background).onKeyEvent { event ->
                // Las flechas mueven el foco entre pósteres, botones y pestañas, como el mando en la tele
                // (Intro/Espacio pulsan; Escape vuelve atrás).
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionDown -> FocusDirection.Down
                    else -> return@onKeyEvent false
                }
                focusManager.moveFocus(direction) || focusManager.moveFocus(FocusDirection.Next)
            },
        ) {
            // El perfil se lee siempre del almacén: si se edita (tema, infantil…) se aplica al momento y si se borra
            // se vuelve a «¿Quién está viendo?».
            val profile = session.profile?.let { profiles.byId(it.id) }
            val theme = profile?.profileTheme ?: com.shieldstream.tv.model.ProfileTheme.BAMBI
            androidx.compose.runtime.SideEffect { BambiColors.theme = theme }
            var profileMenu by remember { mutableStateOf(false) }
            var unlocking by remember { mutableStateOf<DesktopProfile?>(null) }
            if (profile == null) {
                ProfilePickerScreen(
                    profiles,
                    onPick = { session.profile = it; session.managing = false; navigator.section(Screen.Home) },
                    startManaging = session.managing,
                )
            } else {
                val library = remember(profile.id) { profiles.library(profile) }
                val retryScope = androidx.compose.runtime.rememberCoroutineScope()
                // Al entrar en un perfil, Trakt trae lo visto en la tele o en otro PC (y pone al día sus listas añadidas).
                LaunchedEffect(profile.id) {
                    trakt?.syncNow(force = true)
                    trakt?.refreshTraktLists(library)
                }
                // Estrenos de las series que sigue (contador arriba y aviso de Windows) y actualizaciones, cada 6 horas.
                // Se guardan los capítulos y el número se cuenta al pintar: al marcar algo como visto (aquí, en la ficha
                // o desde Trakt) baja al momento, no a las 6 horas.
                var recentEpisodes by remember(profile.id) { mutableStateOf<List<ReleaseEpisode>>(emptyList()) }
                val newEpisodes = ReleaseCenter.pending(recentEpisodes, library).size
                LaunchedEffect(profile.id) {
                    while (true) {
                        runCatching {
                            val recent = ReleaseCenter.load(library, repository).recent
                            recentEpisodes = recent
                            val seen = SeenReleases()
                            val fresh = recent.filter { it.key in seen.unseen(recent.map { r -> r.key }) }
                            fresh.take(3).forEach { Notifier.notify("Nuevo capítulo de ${it.show.title}", "T${it.season}:E${it.number} · ${it.title}") }
                            seen.markSeen(recent.map { it.key })
                        }
                        if (updates.available == null) {
                            updates.available = Updater.check()
                            updates.available?.let { notice.text = "Hay una versión nueva de Bambi (${it.version}): Ajustes → Actualizaciones." }
                        }
                        delay(Updater.CHECK_EVERY_MS)
                    }
                }
                val openDetails: (CatalogItem) -> Unit = { navigator.open(Screen.Details(it)) }
                Box(Modifier.fillMaxSize()) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalFocusRestore provides navigator.focus) {
                    when (val screen = navigator.current) {
                        Screen.Home -> HomeScreen(repository, Catalog.ALL, library, openDetails, listState = navigator.listState("home-ALL-${profile.id}"), kids = profile.kids, trakt = trakt, profileId = profile.id, hiddenCategories = profile.hiddenCategories, onPlay = { navigator.open(Screen.Details(it, autoPlay = true)) }, onResume = { navigator.open(Screen.Details(it.item.toItem(), autoPlay = true, season = it.season, episode = it.episode)) }, layout = profile.homeLayout, onSettings = { navigator.section(Screen.Settings) })
                        Screen.Shows -> HomeScreen(repository, Catalog.SHOWS, library, openDetails, listState = navigator.listState("home-SHOWS-${profile.id}"), kids = profile.kids, trakt = trakt, profileId = profile.id, hiddenCategories = profile.hiddenCategories, onPlay = { navigator.open(Screen.Details(it, autoPlay = true)) }, onResume = { navigator.open(Screen.Details(it.item.toItem(), autoPlay = true, season = it.season, episode = it.episode)) }, layout = profile.homeLayout, onSettings = { navigator.section(Screen.Settings) })
                        Screen.Movies -> HomeScreen(repository, Catalog.MOVIES, library, openDetails, listState = navigator.listState("home-MOVIES-${profile.id}"), kids = profile.kids, trakt = trakt, profileId = profile.id, hiddenCategories = profile.hiddenCategories, onPlay = { navigator.open(Screen.Details(it, autoPlay = true)) }, onResume = { navigator.open(Screen.Details(it.item.toItem(), autoPlay = true, season = it.season, episode = it.episode)) }, layout = profile.homeLayout, onSettings = { navigator.section(Screen.Settings) })
                        Screen.MyList -> MyListScreen(library, openDetails, trakt = trakt, initialFilter = Catalog.ALL)
                        Screen.Search -> SearchScreen(
                            repository,
                            openDetails,
                            kids = profile.kids,
                            onRequest = { requests.request(profile, it); notice.text = "Pedido. Un adulto lo verá en Ajustes → Peticiones." },
                            isRequested = { requests.isRequested(profile.id, it) },
                        )
                        Screen.Releases -> ReleasesScreen(library, repository, openDetails, trakt)
                        Screen.Setup -> SetupScreen(config, repository, onDone = { navigator.section(Screen.Home) })
                        Screen.Settings -> SettingsScreen(config, repository, onDone = { navigator.section(Screen.Home) }, profile = profile, trakt = trakt, requests = requests, profiles = profiles, update = updates.available)
                        is Screen.Details -> DetailsScreen(
                            repository = repository,
                            item = screen.item,
                            library = library,
                            onPlay = { request -> navigator.open(Screen.Player(request)) },
                            trakt = trakt,
                            autoPlayOnOpen = remember(screen) { navigator.takeAutoPlay(screen) },
                            showCast = config.current.showCast,
                            onOpen = openDetails,
                            onNotice = { notice.text = it },
                            service = config.current.debridService.title,
                            keepChosenSource = config.current.keepChosenSource,
                            startSeason = screen.season,
                            startEpisode = screen.episode,
                            autoNext = config.current.autoNext,
                            matchRefresh = config.current.matchRefresh,
                            onPerson = { navigator.open(Screen.Person(it)) },
                            spanishOnly = config.current.spanishOnly,
                            onSpanishOnly = { on -> runCatching { config.save(config.current.copy(spanishOnly = on)) } },
                        )
                        is Screen.Person -> PersonScreen(repository, screen.cast, openDetails)
                        is Screen.Player -> {
                            PlayerPrefs.autoSubtitles = config.current.autoSubtitles
                            PlayerPrefs.subtitleScale = config.current.subtitleScale
                            PlayerPrefs.subtitleStyle = config.current.subtitleStyle
                            PlayerPrefs.onStyleChanged = { style -> runCatching { config.save(config.current.copy(subtitleStyle = style)) } }
                            LaunchedEffect(screen) {
                                val r = screen.request
                                if (r.trailer) return@LaunchedEffect
                                trakt?.playbackStarted(r, library.progressFor(r.item, r.episode?.season, r.episode?.number)?.durationSec)
                            }
                            PlayerScreen(
                                request = screen.request,
                                mpvPath = config.current.mpvPath,
                                onError = { message ->
                                    notice.text = message
                                    onFullscreen(false)
                                    if (navigator.current == screen) navigator.back()
                                },
                                onExit = { result ->
                                    // Tráiler: no cuenta como visto ni va a Trakt. Si YouTube no lo dejó ver, al navegador.
                                    if (screen.request.trailer) {
                                        if (result.failed && (result.positionSec ?: 0.0) < 1.0) {
                                            notice.text = "No se pudo abrir el tráiler aquí; se abre en el navegador."
                                            openInBrowser(screen.request.source.url)
                                        }
                                        onFullscreen(false)
                                        if (navigator.current == screen) navigator.back()
                                        return@PlayerScreen
                                    }
                                    // Como en la tele: si no arranca, se prueba la siguiente fuente.
                                    if (screen.request.alternatives.isNotEmpty() && (result.report.slate || (result.failed && (result.positionSec ?: 0.0) < 1.0))) {
                                        retryScope.launch {
                                            val pick = pickPlayable(screen.request.alternatives, screen.request.service)
                                            // Si mientras se buscaba otra fuente se salió del vídeo, no se abre nada.
                                            if (navigator.current != screen) return@launch
                                            when (pick) {
                                                is Pick.Ready -> {
                                                    notice.text = if (result.report.slate) "Esa fuente era un aviso del addon; probando otra…" else "No arrancó; probando otra fuente…"
                                                    if (navigator.current == screen) navigator.back()
                                                    navigator.open(Screen.Player(screen.request.copy(source = pick.source, alternatives = pick.rest)))
                                                }
                                                is Pick.Failed -> {
                                                    notice.text = pick.message
                                                    onFullscreen(false)
                                                    if (navigator.current == screen) navigator.back()
                                                }
                                            }
                                        }
                                        return@PlayerScreen
                                    }
                                    val r = screen.request
                                    // Corte a mitad (red, servidor): se vuelve a abrir la misma fuente en el mismo punto, una vez.
                                    if (result.failed && !r.retried && (result.positionSec ?: 0.0) >= 1.0) {
                                        notice.text = "Se cortó; retomando en el mismo punto…"
                                        if (navigator.current == screen) navigator.back()
                                        navigator.open(Screen.Player(r.copy(startSec = result.positionSec ?: r.startSec, retried = true)))
                                        return@PlayerScreen
                                    }
                                    // Si se pasó al siguiente desde los créditos, este cuenta como visto.
                                    val done = if (result.report.next) result.copy(finished = true) else result
                                    recordPlayback(library, r, done, notice)
                                    trakt?.playbackEnded(r, done)
                                    library.saveTracks(r.item, TrackPreference(result.report.audioLang, result.report.subLang))
                                    // Siguiente episodio: desde la tarjeta de créditos (N o la cuenta atrás) o al acabar, si
                                    // está activado y el temporizador no dice «al terminar el episodio».
                                    val chain = r.episode != null && !result.report.sleep && !result.report.sleepAtEnd &&
                                        (result.report.next || (result.finished && r.autoNext))
                                    if (chain) {
                                        onFullscreen(false)
                                        if (navigator.current == screen) navigator.back()
                                        if (navigator.current is Screen.Details) navigator.back()
                                        navigator.open(Screen.Details(r.item, autoPlay = true, season = r.episode!!.season))
                                        return@PlayerScreen
                                    }
                                    onFullscreen(false)
                                    if (navigator.current == screen) navigator.back()
                                },
                            )
                        }
                    }
                    }
                    if (navigator.current !is Screen.Player && navigator.current != Screen.Setup) {
                        TopBar(navigator, profile, newEpisodes, onProfile = { profileMenu = true }) {
                            ProfileSwitchMenu(
                                expanded = profileMenu,
                                store = profiles,
                                current = profile,
                                onSwitch = { other ->
                                    if (other.locked) unlocking = other
                                    else { session.profile = other; navigator.section(Screen.Home) }
                                },
                                onManage = { session.managing = true; session.profile = null },
                                onDismiss = { profileMenu = false },
                            )
                        }
                    }
                    unlocking?.let { other ->
                        PinDialog(
                            title = other.name,
                            subtitle = "Introduce el PIN para entrar.",
                            check = { other.pinMatches(it) },
                            onSuccess = { unlocking = null; session.profile = other; navigator.section(Screen.Home) },
                            onCancel = { unlocking = null },
                        )
                    }
                }
            }
            // Ventanas encima de todo (nombre de una lista, listas de Trakt…).
            OverlayHost()
            notice.text?.let { text ->
                LaunchedEffect(text) {
                    delay(if (text.contains("mpv")) 15_000 else 5_000)
                    notice.text = null
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(28.dp)
                        .background(BambiColors.SurfaceHigh, BambiShapes.Control)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) { Text(text, color = BambiColors.Text) }
            }
        }
    }
}

/** Barra de arriba como en la tele: logo, pestañas en el centro, y perfil y Ajustes a la derecha. */
@Composable
private fun TopBar(navigator: Navigator, profile: DesktopProfile, newEpisodes: Int, onProfile: () -> Unit, menu: @Composable () -> Unit) {
    val current = navigator.stack.firstOrNull()
    // Como en Netflix: transparente sobre la cabecera y sólida en cuanto bajas, para que los pósteres no se vean por
    // detrás de las pestañas.
    val list = when (navigator.current) {
        Screen.Home -> navigator.listStates["home-ALL-${profile.id}"]
        Screen.Shows -> navigator.listStates["home-SHOWS-${profile.id}"]
        Screen.Movies -> navigator.listStates["home-MOVIES-${profile.id}"]
        else -> null
    }
    val scrolled = list != null && (list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 120)
    val solid by animateFloatAsState(if (scrolled) 1f else 0f, androidx.compose.animation.core.tween(260))
    Box(
        Modifier.fillMaxWidth().height(84.dp)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.88f), Color.Black.copy(alpha = 0.45f), Color.Transparent)))
            .background(BambiColors.Background.copy(alpha = 0.97f * solid)),
    ) {
        if (solid > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(BambiColors.Stroke.copy(alpha = BambiColors.Stroke.alpha * solid)))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { navigator.section(Screen.Home) },
        ) {
            BundledImages.get("bambi_logo.png")?.let { Image(it, contentDescription = "Bambi", modifier = Modifier.size(48.dp)) }
            Spacer(Modifier.width(12.dp))
            BundledImages.get("bambi_wordmark.png")?.let { Image(it, contentDescription = null, modifier = Modifier.height(30.dp)) }
                ?: Text("BAMBI", color = BambiColors.Accent, fontWeight = FontWeight.Black, fontSize = 24.sp)
        }
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopBarItem(icon = AppIcons.Search, label = null, selected = current == Screen.Search) { navigator.section(Screen.Search) }
            TopBarItem(label = "Inicio", selected = current == Screen.Home) { navigator.section(Screen.Home) }
            TopBarItem(label = "Series", selected = current == Screen.Shows) { navigator.section(Screen.Shows) }
            TopBarItem(label = "Películas", selected = current == Screen.Movies) { navigator.section(Screen.Movies) }
            TopBarItem(label = "Mi lista", selected = current == Screen.MyList) { navigator.section(Screen.MyList) }
            TopBarItem(label = if (newEpisodes > 0) "Estrenos · $newEpisodes" else "Estrenos", selected = current == Screen.Releases) { navigator.section(Screen.Releases) }
        }
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigator.canGoBack) TopBarItem(label = "← Atrás", selected = false) { navigator.back() }
            Box {
                ProfileButton(profile, onProfile)
                menu()
            }
            TopBarItem(icon = AppIcons.Settings, label = null, selected = current == Screen.Settings) { navigator.section(Screen.Settings) }
        }
    }
}

@Composable
private fun ProfileButton(profile: DesktopProfile, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered = interaction.collectIsHoveredAsState().value || interaction.collectIsFocusedAsState().value
    val scale by animateFloatAsState(if (hovered) 1.12f else 1f)
    Box(
        Modifier.scale(scale)
            .border(2.dp, if (hovered) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(3.dp)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) { ProfileAvatar(profile, 32.dp) }
}

/**
 * Pestaña de la barra: texto que se ilumina al pasar por encima y una raya del color del perfil debajo de la activa
 * (iconos: círculo de cristal al pasar por encima).
 */
@Composable
private fun TopBarItem(icon: ImageVector? = null, label: String?, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered = interaction.collectIsHoveredAsState().value || interaction.collectIsFocusedAsState().value
    val underline by animateFloatAsState(if (selected) 1f else if (hovered) 0.45f else 0f, androidx.compose.animation.core.tween(200))
    val foreground = when {
        selected || hovered -> Color.White
        else -> Color.White.copy(alpha = 0.66f)
    }
    if (label == null && icon != null) {
        Box(
            Modifier.size(40.dp)
                .background(if (hovered || selected) BambiColors.Glass else Color.Transparent, androidx.compose.foundation.shape.CircleShape)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(22.dp)) }
        return
    }
    Column(
        Modifier.hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label.orEmpty(), color = foreground, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.height(3.dp).width(28.dp * underline)
                .background(if (selected) BambiColors.Accent else Color.White.copy(alpha = 0.5f), BambiShapes.Pill),
        )
    }
}

