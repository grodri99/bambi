package com.shieldstream.tv.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.ContinueInfo
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HomeFilter
import androidx.hilt.navigation.compose.hiltViewModel
import com.shieldstream.tv.ui.component.TopBar
import com.shieldstream.tv.ui.component.TopTab
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
    var tab by rememberSaveable { mutableStateOf(TopTab.HOME) }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var focusMenuAfterSwitch by remember { mutableStateOf(false) }
    var topBarFocused by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    val topBarRequester = remember { FocusRequester() }
    val filter = when (tab) {
        TopTab.TV -> HomeFilter.TV
        TopTab.MOVIES -> HomeFilter.MOVIES
        else -> HomeFilter.ALL
    }
    val episodeCenter: EpisodeCenterViewModel = hiltViewModel()
    val episodeState by episodeCenter.uiState.collectAsState()
    LaunchedEffect(tab) { if (tab == TopTab.EPISODES) episodeCenter.activate() }
    val focusTopBar: () -> Unit = { runCatching { topBarRequester.requestFocus() } }
    val topBar: @Composable (Modifier) -> Unit = { modifier ->
        TopBar(
            selected = tab,
            onSelect = { selected ->
                if (selected != tab) {
                    tab = selected
                    focusMenuAfterSwitch = selected != TopTab.SEARCH && selected != TopTab.MY_LIST && selected != TopTab.EPISODES
                }
            },
            onSettings = onOpenSettings,
            selectedRequester = topBarRequester,
            newEpisodeCount = (episodeState as? EpisodeCenterUiState.Content)?.newEpisodeCount ?: 0,
            modifier = modifier.onFocusChanged { topBarFocused = it.hasFocus },
            profileInitial = profileInitial,
            profileAvatar = profileAvatar,
            onProfile = onSwitchProfile,
        )
    }

    BackHandler(enabled = showExitConfirmation || shouldRequestExit(category != null, topBarFocused)) {
        showExitConfirmation = !showExitConfirmation
    }

    val saveable = rememberSaveableStateHolder()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val openCategory = category
        when {
            openCategory != null -> CategoryScreen(
                category = openCategory,
                filter = filter,
                onItemClick = onOpenItem,
                onBack = { category = null },
            )
            tab == TopTab.SEARCH || tab == TopTab.MY_LIST || tab == TopTab.EPISODES -> {
                Column(Modifier.fillMaxSize().onBackKey(enabled = !topBarFocused) { focusTopBar() }) {
                    topBar(Modifier)
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        when (tab) {
                            TopTab.SEARCH -> SearchScreen(onItemClick = onOpenItem)
                            TopTab.EPISODES -> EpisodeCenterScreen(onOpenEpisode = onOpenNewEpisode, viewModel = episodeCenter)
                            else -> saveable.SaveableStateProvider("tab_${tab.name}") { MyListScreen(onItemClick = onOpenItem) }
                        }
                    }
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
                    topBar = topBar,
                    topBarRequester = topBarRequester,
                    focusTopBar = focusTopBar,
                    preferTopBarFocus = focusMenuAfterSwitch,
                    onTopBarFocusConsumed = { focusMenuAfterSwitch = false },
                )
            }
        }
        if (showExitConfirmation) {
            ExitConfirmationDialog(
                onDismiss = { showExitConfirmation = false },
                onExit = onExit,
            )
        }
    }
}

internal fun shouldRequestExit(categoryOpen: Boolean, topBarFocused: Boolean): Boolean = !categoryOpen && topBarFocused
