package com.shieldstream.tv.settings.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.IthilDownloadScheduler
import com.shieldstream.tv.domain.IthilLibraryState
import com.shieldstream.tv.domain.IthilUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class IthilUpdateViewModel @Inject constructor(
    private val updates: IthilUpdateRepository,
    private val scheduler: IthilDownloadScheduler,
) : ViewModel() {

    private val dismissedSha = MutableStateFlow<String?>(null)

    val prompt: StateFlow<IthilLibraryState.Available?> = combine(updates.state, dismissedSha) { state, dismissed ->
        (state as? IthilLibraryState.Available)?.takeIf { it.update.sha != dismissed }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun update() {
        val available = prompt.value ?: return
        dismissedSha.value = available.update.sha
        scheduler.downloadNow()
    }

    fun later() {
        val available = prompt.value ?: return
        dismissedSha.value = available.update.sha
    }
}
