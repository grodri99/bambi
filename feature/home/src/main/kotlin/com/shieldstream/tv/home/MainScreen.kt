package com.shieldstream.tv.home

import com.shieldstream.tv.ui.theme.AppColors
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.ContinueInfo
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HomeFilter
import androidx.hilt.navigation.compose.hiltViewModel
import com.shieldstream.tv.ui.component.NavRail
import com.shieldstream.tv.ui.component.NavTab
import com.shieldstream.tv.ui.component.RailContentStart
import com.shieldstream.tv.ui.component.onBackKey

@Composable
fun MainScreen(
    onExit: () -> Unit,
    onOpenItem: (CatalogItem) -> Unit,
    onContinue: (CatalogItem, ContinueInfo) -> Unit,
    onOpenNewEpisode: (CatalogItem, Episode) -> Unit,
    onUpNext: (CatalogItem, Episode) -> Unit,
    onOpenSettings: () -> Unit,
    profileInitial: String? = null,
    profileAvatar: Int = 0,
    onSwitchProfile: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(NavTab.HOME) }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var focusMenuAfterSwitch by remember { mutableStateOf(false) }
    var railFocused by remember { mutableStateOf(false) }
    // While Home is still loading nothing else is focusable, so Android would hand the startup
    // focus to the rail and expand it over the skeleton. Keep it unfocusable until Home is usable.
    var homeInteractive by rememberSaveable { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    val railRequester = remember { FocusRequester() }
    val contentRequester = remember { FocusRequester() }
    val filter = when (tab) {
        NavTab.TV -> HomeFilter.TV
        NavTab.MOVIES -> HomeFilter.MOVIES
        else -> HomeFilter.ALL
    }
    val episodeCenter: EpisodeCenterViewModel = hiltViewModel()
    val episodeState by episodeCenter.uiState.collectAsState()
    LaunchedEffect(tab) { if (tab == NavTab.EPISODES) episodeCenter.activate() }
    val focusRail: () -> Unit = { runCatching { railRequester.requestFocus() } }

    val railFocusable = homeInteractive || tab == NavTab.SEARCH || tab == NavTab.MY_LIST || tab == NavTab.EPISODES
    BackHandler(enabled = showExitConfirmation || shouldRequestExit(category != null, railFocused || !railFocusable)) {
        showExitConfirmation = !showExitConfirmation
    }

    val saveable = rememberSaveableStateHolder()
    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        val openCategory = category
        if (openCategory != null) {
            CategoryScreen(
                category = openCategory,
                filter = filter,
                onItemClick = onOpenItem,
                onBack = { category = null },
            )
        } else {
            // Content is full-bleed so artwork can run behind the collapsed rail; browse screens
            // inset their own content by RailContentStart.
            // Right from the rail requests this group, which hands focus to its top-left child.
            Box(
                Modifier
                    .fillMaxSize()
                    .onBackKey(enabled = true) { focusRail() }
                    .focusRequester(contentRequester)
                    .focusGroup(),
            ) {
                when (tab) {
                    NavTab.SEARCH, NavTab.MY_LIST, NavTab.EPISODES -> Box(
                        Modifier.fillMaxSize().padding(start = TabContentInset),
                    ) {
                        when (tab) {
                            NavTab.SEARCH -> SearchScreen(onItemClick = onOpenItem)
                            NavTab.EPISODES -> EpisodeCenterScreen(onOpenEpisode = onOpenNewEpisode, viewModel = episodeCenter)
                            else -> saveable.SaveableStateProvider("tab_${tab.name}") { MyListScreen(onItemClick = onOpenItem) }
                        }
                    }
                    else -> saveable.SaveableStateProvider("tab_${tab.name}") {
                        HomeScreen(
                            filter = filter,
                            onItemClick = onOpenItem,
                            onContinueClick = onContinue,
                            onNewEpisodeClick = onOpenNewEpisode,
                            onUpNextClick = onUpNext,
                            onOpenCategory = { category = it },
                            onContentReady = episodeCenter::activate,
                            onInteractive = { homeInteractive = true },
                            railRequester = railRequester,
                            focusRail = focusRail,
                            preferRailFocus = focusMenuAfterSwitch,
                            onRailFocusConsumed = { focusMenuAfterSwitch = false },
                        )
                    }
                }
            }
            NavRail(
                selected = tab,
                onSelect = { selected ->
                    if (selected != tab) {
                        tab = selected
                        focusMenuAfterSwitch = selected != NavTab.SEARCH && selected != NavTab.MY_LIST && selected != NavTab.EPISODES
                    }
                },
                onSettings = onOpenSettings,
                selectedRequester = railRequester,
                contentRequester = contentRequester,
                focusable = railFocusable,
                newEpisodeCount = (episodeState as? EpisodeCenterUiState.Content)?.newEpisodeCount ?: 0,
                profileInitial = profileInitial,
                profileAvatar = profileAvatar,
                onProfile = onSwitchProfile,
                onFocusChange = { railFocused = it },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        if (showExitConfirmation) {
            ExitConfirmationDialog(
                onDismiss = { showExitConfirmation = false },
                onExit = onExit,
            )
        }
    }
}

// Search, My List and Episodes already apply a ~48 dp start padding of their own.
private val TabContentInset = RailContentStart - 48.dp

internal fun shouldRequestExit(categoryOpen: Boolean, railFocused: Boolean): Boolean = !categoryOpen && railFocused
