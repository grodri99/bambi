package com.shieldstream.tv.settings.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface ProfilesUiState {
    data object Loading : ProfilesUiState

    data class Ready(
        val profiles: List<Profile>,
        val activeId: String,
        val canAdd: Boolean,
    ) : ProfilesUiState
}

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val trakt: TraktRepository,
) : ViewModel() {

    val uiState: StateFlow<ProfilesUiState> =
        combine(profiles.profiles, profiles.active) { list, active ->
            ProfilesUiState.Ready(list, active.id, canAdd = list.size < Profile.MAX)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfilesUiState.Loading)

    val active: StateFlow<Profile> = profiles.active

    fun select(id: String) {
        profiles.select(id)
        trakt.syncActive(force = true)
    }
}
