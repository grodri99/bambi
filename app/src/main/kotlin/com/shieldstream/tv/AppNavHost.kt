package com.shieldstream.tv

import androidx.compose.runtime.Composable
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
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.player.PlayerScreen
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

@Composable
fun AppNavHost(holder: SelectedContentHolder, onExit: () -> Unit) {
    val navController = rememberNavController()
    val gate: SetupGateViewModel = hiltViewModel()
    val start = remember { if (gate.needsSetup) "setup" else "profiles" }
    val toMainFresh = {
        navController.navigate("main") { popUpTo(navController.graph.id) { inclusive = true } }
    }

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
        composable("profiles") { entry ->
            ProfilePickerScreen(
                onSelected = { if (entry.isVisible()) toMainFresh() },
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
            )
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
}

private fun NavBackStackEntry.isVisible() = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
