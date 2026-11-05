package com.shieldstream.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.tr
import com.shieldstream.tv.ui.component.PosterCard
import com.shieldstream.tv.ui.component.tvClickable
import kotlinx.coroutines.delay

@Composable
fun MyListScreen(
    onItemClick: (CatalogItem) -> Unit,
    viewModel: MyListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cardRequester = remember { FocusRequester() }
    var lastCardKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state is MyListUiState.Content) {
        if (state !is MyListUiState.Content || lastCardKey == null) return@LaunchedEffect
        withFrameNanos { }
        repeat(4) {
            if (runCatching { cardRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(60)
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 36.dp, top = 24.dp)) {
        val currentSort = when (val s = state) {
            is MyListUiState.Content -> s.sort
            is MyListUiState.Empty -> s.sort
            MyListUiState.Loading -> MyListSort.RECENT
        }
        val hidingCompleted = when (val s = state) {
            is MyListUiState.Content -> s.hideCompleted
            is MyListUiState.Empty -> s.hideCompleted
            MyListUiState.Loading -> false
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Mi lista", "My list"),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.width(24.dp))
            val current = when (val s = state) {
                is MyListUiState.Content -> s.filter
                is MyListUiState.Empty -> s.filter
                MyListUiState.Loading -> MyListFilter.ALL
            }
            listOf(
                MyListFilter.ALL to tr("Todo", "All"),
                MyListFilter.TV to tr("Series", "TV Shows"),
                MyListFilter.MOVIES to tr("Películas", "Movies"),
            ).forEach { (f, label) ->
                FilterChip(label = label, selected = f == current, onClick = { viewModel.setFilter(f) })
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            if (state is MyListUiState.Content) {
                Text(tr("Mantén OK para quitar", "Hold OK to remove"), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(
                MyListSort.NEW_FIRST to tr("Nuevos primero", "New first"),
                MyListSort.RECENT to tr("Recientes", "Recent"),
                MyListSort.TITLE to tr("A–Z", "A–Z"),
            ).forEach { (value, label) ->
                FilterChip(label = label, selected = value == currentSort, onClick = { viewModel.setSort(value) })
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            FilterChip(
                label = tr("Ocultar completadas", "Hide completed"),
                selected = hidingCompleted,
                onClick = viewModel::toggleHideCompleted,
            )
        }
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            MyListUiState.Loading -> Text(tr("Cargando…", "Loading…"), color = Color.White.copy(alpha = 0.6f))
            is MyListUiState.Empty -> Text(
                if (s.filter == MyListFilter.ALL) {
                    tr("Tu lista está vacía. Añade películas y series con el botón «Mi lista» de su ficha.", "Your list is empty. Add movies and shows with the “My list” button on their page.")
                } else if (s.hideCompleted) {
                    tr("No quedan series sin completar con estos filtros.", "No unfinished shows match these filters.")
                } else {
                    tr("No hay nada de este tipo en tu lista.", "Nothing of this kind in your list.")
                },
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.titleMedium,
            )
            is MyListUiState.Content -> LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(s.items, key = { it.key }) { item ->
                    PosterCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onFocused = { lastCardKey = item.key },
                        onLongClick = { viewModel.remove(item) },
                        newEpisodeCount = s.newEpisodeCounts[item.key] ?: 0,
                        width = 140.dp,
                        modifier = if (item.key == lastCardKey) Modifier.focusRequester(cardRequester) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .background(
                when {
                    focused -> Color.White
                    selected -> Color.White.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                RoundedCornerShape(50),
            )
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (focused) Color.Black else Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}
