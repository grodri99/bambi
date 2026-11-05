package com.shieldstream.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
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
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.ui.component.TopBar
import com.shieldstream.tv.ui.component.TopTab
import com.shieldstream.tv.ui.component.onBackKey

/**
 * Contenedor principal: menú superior (🔍 · Inicio · Series · Películas · Mi lista · ⚙) + sección activa.
 * Las categorías (Dramas, Anime…) se abren encima; Atrás vuelve. Atrás desde el menú sale de la app.
 */
@Composable
fun MainScreen(
    onOpenItem: (CatalogItem) -> Unit,
    onOpenNewEpisode: (CatalogItem, Episode) -> Unit,
    onOpenSettings: () -> Unit,
    /** Perfil activo para el avatar del menú (null = sin avatar). */
    profileInitial: String? = null,
    profileAvatar: Int = 0,
    onSwitchProfile: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(TopTab.HOME) }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    // Tras cambiar de pestaña, el foco se queda en el menú (como Netflix) en vez de saltar a la portada.
    var focusMenuAfterSwitch by remember { mutableStateOf(false) }
    val topBarRequester = remember { FocusRequester() }
    val filter = when (tab) {
        TopTab.TV -> HomeFilter.TV
        TopTab.MOVIES -> HomeFilter.MOVIES
        else -> HomeFilter.ALL
    }
    val focusTopBar: () -> Unit = { runCatching { topBarRequester.requestFocus() } }
    val topBar: @Composable (Modifier) -> Unit = { modifier ->
        TopBar(
            selected = tab,
            onSelect = { selected ->
                if (selected != tab) {
                    tab = selected
                    focusMenuAfterSwitch = selected != TopTab.SEARCH && selected != TopTab.MY_LIST
                }
            },
            onSettings = onOpenSettings,
            selectedRequester = topBarRequester,
            modifier = modifier,
            profileInitial = profileInitial,
            profileAvatar = profileAvatar,
            onProfile = onSwitchProfile,
        )
    }

    // Guarda el estado de cada pestaña (foco, filas, portada) aunque se tape con una categoría: al volver
    // con Atrás todo sigue donde estaba.
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
            // Buscar y Mi lista: menú arriba fijo y la pantalla debajo. Atrás (fuera del menú) → menú.
            tab == TopTab.SEARCH || tab == TopTab.MY_LIST -> {
                var topBarFocused by remember { mutableStateOf(false) }
                Column(Modifier.fillMaxSize().onBackKey(enabled = !topBarFocused) { focusTopBar() }) {
                    topBar(Modifier.onFocusChanged { topBarFocused = it.hasFocus })
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        if (tab == TopTab.SEARCH) {
                            SearchScreen(onItemClick = onOpenItem)
                        } else {
                            saveable.SaveableStateProvider("tab_${tab.name}") { MyListScreen(onItemClick = onOpenItem) }
                        }
                    }
                }
            }
            else -> saveable.SaveableStateProvider("tab_${tab.name}") {
                HomeScreen(
                    filter = filter,
                    onItemClick = onOpenItem,
                    onNewEpisodeClick = onOpenNewEpisode,
                    onOpenCategory = { category = it },
                    topBar = topBar,
                    topBarRequester = topBarRequester,
                    focusTopBar = focusTopBar,
                    preferTopBarFocus = focusMenuAfterSwitch,
                    onTopBarFocusConsumed = { focusMenuAfterSwitch = false },
                )
            }
        }
    }
}
