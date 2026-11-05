package com.shieldstream.tv.settings.profiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.model.ProfilePin
import com.shieldstream.tv.model.SubtitlePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProfileEditUiState {
    data object NotFound : ProfileEditUiState

    data class Editing(
        val profile: Profile,
        val isNew: Boolean,
        val canDelete: Boolean,
        val traktAvailable: Boolean,
        val confirmDelete: Boolean = false,
        val confirmDisconnect: Boolean = false,
        val confirmRemovePin: Boolean = false,
        val busy: Boolean = false,
    ) : ProfileEditUiState
}

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val profiles: ProfileRepository,
    private val trakt: TraktRepository,
) : ViewModel() {

    private val requestedId: String = savedState.get<String>("id") ?: NEW

    @Volatile private var profileId: String? = requestedId.takeIf { it != NEW }

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<ProfileEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profiles.profiles.collect { list ->
                val id = profileId ?: return@collect
                val stored = list.firstOrNull { it.id == id } ?: return@collect
                edit { it.copy(profile = it.profile.copy(traktUser = stored.traktUser)) }
            }
        }
    }

    private fun initialState(): ProfileEditUiState {
        val list = profiles.profiles.value
        val profile = if (requestedId == NEW) profiles.draft() else list.firstOrNull { it.id == requestedId }
        profile ?: return ProfileEditUiState.NotFound
        return ProfileEditUiState.Editing(
            profile = profile,
            isNew = requestedId == NEW,
            canDelete = requestedId != NEW && list.size > 1,
            traktAvailable = trakt.isConfigured,
        )
    }

    fun setName(name: String) = edit { it.copy(profile = it.profile.copy(name = name.take(20)), confirmDelete = false) }

    fun setAvatar(index: Int) = edit { it.copy(profile = it.profile.copy(avatar = index), confirmDelete = false) }

    fun setKids(kids: Boolean) = edit {
        it.copy(
            profile = it.profile.copy(
                kids = kids,
                spanishFirst = if (kids) true else it.profile.spanishFirst,
                spanishOnlySources = if (kids) true else it.profile.spanishOnlySources,
            ),
            confirmDelete = false,
        )
    }

    fun setSpanishFirst(enabled: Boolean) = edit { it.copy(profile = it.profile.copy(spanishFirst = enabled)) }

    fun setSpanishOnly(enabled: Boolean) = edit { it.copy(profile = it.profile.copy(spanishOnlySources = enabled)) }

    fun setSubtitlePreference(preference: SubtitlePreference) = edit {
        it.copy(profile = it.profile.copy(subtitlePreference = preference))
    }

    fun toggleCategory(category: Category) {
        val state = _uiState.value as? ProfileEditUiState.Editing ?: return
        val hidden = state.profile.hiddenCategories
        if (category.name !in hidden && Category.entries.count { it.name !in hidden && it != category } == 0) return
        val next = if (category.name in hidden) hidden - category.name else hidden + category.name
        edit { it.copy(profile = it.profile.copy(hiddenCategories = next), confirmDelete = false) }
    }

    fun showAllCategories() = edit {
        it.copy(profile = it.profile.copy(hiddenCategories = emptySet()), confirmDelete = false)
    }

    fun setPin(pin: String) = edit { it.copy(profile = it.profile.copy(pinHash = ProfilePin.hash(it.profile.id, pin)), confirmRemovePin = false) }

    fun removePin() {
        val state = _uiState.value as? ProfileEditUiState.Editing ?: return
        if (!state.confirmRemovePin) {
            edit { it.copy(confirmRemovePin = true) }
            return
        }
        edit { it.copy(profile = it.profile.copy(pinHash = null), confirmRemovePin = false) }
    }

    fun save(): String? {
        val state = _uiState.value as? ProfileEditUiState.Editing ?: return null
        val stored = profiles.profiles.value.firstOrNull { it.id == state.profile.id }
        val saved = profiles.save(state.profile.copy(traktUser = stored?.traktUser))
        profileId = saved.id
        return saved.id
    }

    fun delete(): Boolean {
        val state = _uiState.value as? ProfileEditUiState.Editing ?: return false
        if (!state.canDelete) return false
        if (!state.confirmDelete) {
            edit { it.copy(confirmDelete = true) }
            return false
        }
        profiles.delete(state.profile.id)
        return true
    }

    fun disconnectTrakt() {
        val state = _uiState.value as? ProfileEditUiState.Editing ?: return
        if (!state.confirmDisconnect) {
            edit { it.copy(confirmDisconnect = true) }
            return
        }
        edit { it.copy(busy = true, confirmDisconnect = false) }
        viewModelScope.launch {
            trakt.disconnect(state.profile.id)
            edit { it.copy(busy = false, profile = it.profile.copy(traktUser = null)) }
        }
    }

    fun cancelConfirmations() = edit { it.copy(confirmDelete = false, confirmDisconnect = false, confirmRemovePin = false) }

    private fun edit(transform: (ProfileEditUiState.Editing) -> ProfileEditUiState.Editing) {
        _uiState.update { s -> if (s is ProfileEditUiState.Editing) transform(s) else s }
    }

    companion object {
        const val NEW = "new"
    }
}
