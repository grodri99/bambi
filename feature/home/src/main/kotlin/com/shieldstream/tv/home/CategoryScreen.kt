package com.shieldstream.tv.home

import com.shieldstream.tv.model.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.ui.component.onBackKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun CategoryScreen(
    category: Category,
    filter: HomeFilter,
    onItemClick: (CatalogItem) -> Unit,
    onBack: () -> Unit,
    viewModel: CategoryViewModel = hiltViewModel(key = "category_${category.name}_${filter.name}"),
) {
    LaunchedEffect(category, filter) { viewModel.load(category, filter) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Color.Black).onBackKey(enabled = true) { onBack() }) {
        when (val s = state) {
            HomeUiState.Loading -> HomeSkeleton()
            is HomeUiState.Error -> HomeError(s.message, onRetry = { viewModel.load(category, filter, force = true) })
            is HomeUiState.Content -> RowsBrowser(
                rows = s.rows,
                onItemClick = onItemClick,
                onRemoveContinue = {},
                onExitTop = null,
                onBack = onBack,
                header = category.title,
            )
        }
    }
}

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var loaded: Pair<Category, HomeFilter>? = null

    fun load(category: Category, filter: HomeFilter, force: Boolean = false) {
        if (!force && loaded == (category to filter) && _uiState.value is HomeUiState.Content) return
        loaded = category to filter
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            runCatching { catalogRepository.getCategoryRows(category, filter) }
                .onSuccess { rows ->
                    _uiState.value = if (rows.isEmpty()) {
                        HomeUiState.Error(tr("No hay títulos en «${category.title}» ahora mismo", "No titles in “${category.title}” right now"))
                    } else {
                        HomeUiState.Content(rows)
                    }
                }
                .onFailure { _uiState.value = HomeUiState.Error(it.message ?: tr("Error cargando «${category.title}»", "Error loading “${category.title}”")) }
        }
    }
}
