package com.shieldstream.tv.settings.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.RequestRepository
import com.shieldstream.tv.domain.TitleRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RequestsViewModel @Inject constructor(
    private val requests: RequestRepository,
    profiles: ProfileRepository,
) : ViewModel() {

    val pending: StateFlow<List<TitleRequest>> = requests.requests

    /** Las aprueba cualquier perfil adulto; los infantiles no pueden aprobarse a sí mismos. */
    val isAdmin: StateFlow<Boolean> = profiles.active.map { !it.kids }
        .stateIn(viewModelScope, SharingStarted.Eagerly, !profiles.active.value.kids)

    fun approve(id: String) = requests.approve(id)

    fun dismiss(id: String) = requests.dismiss(id)
}
