package com.shieldstream.tv.settings.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.UpdateRepository
import com.shieldstream.tv.domain.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface UpdatePromptUiState {
    data object Hidden : UpdatePromptUiState
    data class Visible(val state: UpdateState) : UpdatePromptUiState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: UpdateRepository,
) : ViewModel() {

    val state: StateFlow<UpdateState> = updates.state

    val prompt: StateFlow<UpdatePromptUiState> = combine(updates.state, updates.promptDismissed) { s, dismissed ->
        val relevant = when (s) {
            is UpdateState.Available, is UpdateState.Downloading,
            is UpdateState.NeedsPermission, is UpdateState.Installing -> true
            is UpdateState.Failed -> s.update != null
            else -> false
        }
        if (relevant && !dismissed) UpdatePromptUiState.Visible(s) else UpdatePromptUiState.Hidden
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UpdatePromptUiState.Hidden)

    fun checkNow() = updates.check(manual = true)

    fun update() = updates.downloadAndInstall()

    fun later() = updates.dismissPrompt()

    fun onResumed() {
        if (updates.state.value is UpdateState.NeedsPermission) updates.downloadAndInstall()
    }

    fun openInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        return runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }
}
