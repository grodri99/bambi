package com.shieldstream.tv.home

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.model.CatalogItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUiState {
    data object Loading : SearchUiState
    data class Suggestions(val items: List<CatalogItem>) : SearchUiState
    data class Results(val query: String, val items: List<CatalogItem>) : SearchUiState
    data class Empty(val query: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val settings: SettingsRepository,
    private val texts: Texts,
) : ViewModel() {

    val qwerty: StateFlow<Boolean> = settings.searchQwerty.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun toggleKeyboardLayout() {
        viewModelScope.launch { settings.setSearchQwerty(!qwerty.value) }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Loading)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var suggestions: List<CatalogItem> = emptyList()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            suggestions = runCatching { catalogRepository.trending() }.getOrDefault(emptyList())
            if (_query.value.isBlank()) _uiState.value = SearchUiState.Suggestions(suggestions)
        }
    }

    fun type(char: Char) = setQuery(_query.value + char)

    fun backspace() = setQuery(_query.value.dropLast(1))

    fun clear() = setQuery("")

    fun setQuery(value: String) {
        _query.value = value
        searchJob?.cancel()
        val q = value.trim()
        if (q.length < 2) {
            _uiState.value = SearchUiState.Suggestions(suggestions)
            return
        }
        searchJob = viewModelScope.launch {
            delay(450)
            _uiState.value = SearchUiState.Loading
            runCatching { catalogRepository.search(q) }
                .onSuccess { items ->
                    _uiState.value = if (items.isEmpty()) SearchUiState.Empty(q) else SearchUiState.Results(q, items)
                }
                .onFailure { e -> _uiState.value = SearchUiState.Error(e.message ?: texts.get(StringsR.string.home_search_error)) }
        }
    }
}
