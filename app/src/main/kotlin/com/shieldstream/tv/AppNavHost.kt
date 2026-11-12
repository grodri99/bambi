package com.shieldstream.tv

import androidx.compose.runtime.Composable
import com.shieldstream.tv.intro.ResumeIntro
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shieldstream.tv.details.DetailsScreen
import com.shieldstream.tv.domain.EpisodeTarget
import com.shieldstream.tv.domain.SelectedContentHolder
import com.shieldstream.tv.home.MainScreen
import com.shieldstream.tv.intro.IntroScreen
import com.shieldstream.tv.intro.IntroViewModel
import com.shieldstream.tv.intro.ProfileLoadingScreen
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.player.PlayerScreen
import com.shieldstream.tv.settings.AddonsScreen
import com.shieldstream.tv.settings.SettingsScreen
import com.shieldstream.tv.settings.SetupGateViewModel
import com.shieldstream.tv.settings.SetupScreen
import com.shieldstream.tv.settings.profiles.ProfileEditScreen
import com.shieldstream.tv.settings.profiles.ProfileEditViewModel
import com.shieldstream.tv.settings.profiles.ProfilePickerScreen
import com.shieldstream.tv.settings.profiles.ProfilesViewModel
import com.shieldstream.tv.settings.profiles.TraktConnectScreen
import com.shieldstream.tv.settings.phoneconfig.PhoneConfigMode
import com.shieldstream.tv.settings.phoneconfig.PhoneConfigScreen
import com.shieldstream.tv.settings.update.UpdatePrompt
import com.shieldstream.tv.ui.component.restartApp
import com.shieldstream.tv.watchnext.WatchNextNavigationViewModel
import com.shieldstream.tv.watchnext.WatchNextRequest
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppNavHost(
    holder: SelectedContentHolder,
    onExit: () -> Unit,
    watchNextRequest: StateFlow<WatchNextRequest?>,
    onWatchNextHandled: (WatchNextRequest) -> Unit,
    resumeIntro: StateFlow<ResumeIntro?>,
    onResumeIntroDone: (ResumeIntro) -> Unit,
) {
    val navController = rememberNavController()
    val gate: SetupGateViewModel = hiltViewModel()
    val watchNextNavigation: WatchNextNavigationViewModel = hiltViewModel()
    // Al abrir en frío, la intro de Bambi (mientras se precarga el Inicio) y después "¿Quién está viendo?".
    val start = remember { if (gate.needsSetup) "setup" else "intro" }
    val intro: IntroViewModel = hiltViewModel()
    val request by watchNextRequest.collectAsState()
    // Si se abre desde "Seguir viendo" de Android TV, la intro es más corta.
    val quickIntro = remember { watchNextRequest.value != null }
    val currentEntry by navController.currentBackStackEntryFlow.collectAsState(initial = null)

    LaunchedEffect(request?.requestedAtNs) {
        if (request != null && navController.currentDestination?.route in WATCH_NEXT_INTERRUPT_ROUTES) {
            navController.popBackStack("main", inclusive = false)
        }
    }

    LaunchedEffect(request, currentEntry?.destination?.route) {
        val pending = request ?: return@LaunchedEffect
        if (currentEntry?.destination?.route != "main") return@LaunchedEffect

        val resolved = watchNextNavigation.resolve(pending)
        if (resolved != null) {
            holder.selectItem(resolved.item, resolved.target, playImmediately = true)
            onWatchNextHandled(pending)
            navController.navigate("details") { launchSingleTop = true }
        } else {
            onWatchNextHandled(pending)
        }
    }
    val toMainFresh = {
        navController.navigate("main") { popUpTo(navController.graph.id) { inclusive = true } }
    }

    Box(Modifier.fillMaxSize()) {
    NavHost(navController = navController, startDestination = start) {
        composable("setup") {
            val toProfiles = {
                navController.navigate("profiles") { popUpTo("setup") { inclusive = true } }
            }
            SetupScreen(
                onFinish = toProfiles,
                onExit = toProfiles,
                onOpenPhone = { navController.navigate("phone_setup") },
            )
        }
        composable("intro") {
            IntroScreen(
                onStartPreload = intro::start,
                awaitReady = intro::awaitReady,
                onFinished = { navController.navigate("profiles") { popUpTo("intro") { inclusive = true } } },
                quick = quickIntro,
            )
        }
        composable("profile_loading") {
            val profiles: ProfilesViewModel = hiltViewModel()
            val active by profiles.active.collectAsState()
            ProfileLoadingScreen(
                initial = active.initial,
                avatar = active.avatar,
                awaitReady = intro::awaitReady,
                onReady = toMainFresh,
            )
        }
        composable("profiles") { entry ->
            ProfilePickerScreen(
                onSelected = {
                    if (entry.isVisible()) {
                        // Si el Inicio de este perfil ya está preparado se entra directo; si no, avatar + anillo.
                        if (intro.isReady()) toMainFresh() else navController.navigate("profile_loading")
                    }
                },
                onEdit = { id -> if (entry.isVisible()) navController.navigate("profile_edit/$id") },
                onAdd = { if (entry.isVisible()) navController.navigate("profile_edit/${ProfileEditViewModel.NEW}") },
            )
            UpdatePrompt()
        }
        composable("profile_edit/{id}") { entry ->
            ProfileEditScreen(
                onDone = { if (entry.isVisible()) navController.popBackStack() },
                onConnectTrakt = { id -> if (entry.isVisible()) navController.navigate("trakt_connect/$id") },
            )
        }
        composable("trakt_connect/{id}") { entry ->
            TraktConnectScreen(onDone = { if (entry.isVisible()) navController.popBackStack() })
        }
        composable("setup_again") {
            SetupScreen(
                onFinish = { navController.popBackStack() },
                onExit = { navController.popBackStack() },
                onOpenPhone = { navController.navigate("phone_setup_again") },
            )
        }
        composable("main") { entry ->
            val profiles: ProfilesViewModel = hiltViewModel()
            val active by profiles.active.collectAsState()
            MainScreen(
                onExit = onExit,
                profileInitial = active.initial,
                profileAvatar = active.avatar,
                onSwitchProfile = { if (entry.isVisible()) navController.navigate("profiles") },
                onOpenItem = { item ->
                    if (entry.isVisible()) {
                        holder.selectItem(item)
                        navController.navigate("details")
                    }
                },
                onContinue = { item, info ->
                    if (entry.isVisible()) {
                        val target = info.season?.let { season ->
                            info.episode?.let { episode -> EpisodeTarget(season, episode) }
                        }
                        holder.selectItem(item, target)
                        navController.navigate("details")
                    }
                },
                onOpenNewEpisode = { item, episode ->
                    if (entry.isVisible()) {
                        holder.selectItem(item, EpisodeTarget(episode.season, episode.number))
                        navController.navigate("details")
                    }
                },
                onUpNext = { item, episode ->
                    if (entry.isVisible()) {
                        holder.selectItem(
                            item,
                            EpisodeTarget(episode.season, episode.number),
                        )
                        navController.navigate("details")
                    }
                },
                onOpenSettings = { navController.navigate("settings") },
            )
            UpdatePrompt()
        }
        composable("details") { entry ->
            DetailsScreen(
                onBack = { if (entry.isVisible()) navController.popBackStack() },
                onPlay = { if (entry.isVisible()) navController.navigate("player") },
            )
        }
        composable("player") {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSetup = { navController.navigate("setup_again") },
                onOpenPhoneConfig = { navController.navigate("phone_config") },
                onOpenAddons = { navController.navigate("addons") },
            )
        }
        composable("addons") { entry ->
            AddonsScreen(onBack = { if (entry.isVisible()) navController.popBackStack() })
        }
        composable("phone_config") { entry ->
            PhoneConfigScreen(onDone = { if (entry.isVisible()) navController.popBackStack() })
        }
        composable("phone_setup") { entry ->
            val context = LocalContext.current
            PhoneConfigScreen(
                mode = PhoneConfigMode.SETUP,
                onDone = { if (entry.isVisible()) navController.popBackStack() },
                onFinishSetup = { language ->
                    if (language != Lang.current) {
                        restartApp(context)
                    } else {
                        navController.navigate("profiles") { popUpTo("setup") { inclusive = true } }
                    }
                },
            )
        }
        composable("phone_setup_again") { entry ->
            val context = LocalContext.current
            PhoneConfigScreen(
                mode = PhoneConfigMode.SETUP,
                onDone = { if (entry.isVisible()) navController.popBackStack() },
                onFinishSetup = { language ->
                    if (language != Lang.current) {
                        restartApp(context)
                    } else {
                        navController.popBackStack("settings", inclusive = false)
                    }
                },
            )
        }
    }
    ResumeIntroOverlay(resumeIntro, currentEntry?.destination?.route, intro, onResumeIntroDone)
    }
}

@Composable
private fun ResumeIntroOverlay(
    resumeIntro: StateFlow<ResumeIntro?>,
    route: String?,
    intro: IntroViewModel,
    onDone: (ResumeIntro) -> Unit,
) {
    val pending by resumeIntro.collectAsState()
    val request = pending ?: return
    // No tapa el reproductor ni la configuración inicial, ni repite la intro del arranque.
    if (route == null || route in NO_RESUME_INTRO_ROUTES) {
        LaunchedEffect(request) { onDone(request) }
        return
    }
    key(request.at) {
        IntroScreen(
            onStartPreload = { if (request.reload) intro.refresh() else intro.start() },
            awaitReady = intro::awaitReady,
            onFinished = { onDone(request) },
        )
    }
}

private val NO_RESUME_INTRO_ROUTES = setOf("intro", "player", "setup", "phone_setup", "profile_loading")

private val WATCH_NEXT_INTERRUPT_ROUTES = setOf("details", "player", "settings")

private fun NavBackStackEntry.isVisible() = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
