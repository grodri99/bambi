package com.shieldstream.tv.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.KeyCheck
import com.shieldstream.tv.domain.StremioAddon
import com.shieldstream.tv.domain.StremioAddonMetadata
import com.shieldstream.tv.domain.StremioAddons
import com.shieldstream.tv.domain.mapConcurrent
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddonEditor(
    val id: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

data class AddonTestStatus(
    val checking: Boolean = false,
    val detail: String? = null,
    val error: Boolean = false,
)

@HiltViewModel
class AddonsViewModel @Inject constructor(
    private val apiKeys: ApiKeysRepository,
    private val texts: Texts,
) : ViewModel() {

    val addons: StateFlow<List<StremioAddon>> = apiKeys.keys
        .map { StremioAddons.parse(it[ApiService.AIOSTREAMS].orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _metadata = MutableStateFlow<Map<String, StremioAddonMetadata>>(emptyMap())
    val metadata: StateFlow<Map<String, StremioAddonMetadata>> = _metadata.asStateFlow()

    private val _editor = MutableStateFlow<AddonEditor?>(null)
    val editor: StateFlow<AddonEditor?> = _editor.asStateFlow()
    private val _testStatus = MutableStateFlow<Map<String, AddonTestStatus>>(emptyMap())
    val testStatus: StateFlow<Map<String, AddonTestStatus>> = _testStatus.asStateFlow()

    fun openEditor(id: String? = null) {
        _editor.value = AddonEditor(id = id)
    }

    fun closeEditor() {
        if (_editor.value?.busy != true) _editor.value = null
    }

    fun save(value: String) {
        val edit = _editor.value ?: return
        if (edit.busy) return
        val manifestUrl = value.trim()
        val candidate = StremioAddons.parse(manifestUrl).singleOrNull()
        if (candidate == null || !candidate.isValid) {
            _editor.value = edit.copy(error = texts.get(StringsR.string.settings_phone_config_one_manifest))
            return
        }
        val current = currentAddons()
        if (current.any { it.id == candidate.id && it.id != edit.id }) {
            _editor.value = edit.copy(error = texts.get(StringsR.string.settings_phone_config_addon_exists))
            return
        }
        _editor.value = edit.copy(busy = true, error = null)
        viewModelScope.launch {
            when (val result = apiKeys.check(ApiService.AIOSTREAMS, candidate.manifestUrl)) {
                is KeyCheck.Valid -> {
                    val updated = currentAddons().toMutableList()
                    val index = edit.id?.let { id -> updated.indexOfFirst { it.id == id } } ?: -1
                    if (edit.id != null && index < 0) {
                        _editor.value = edit.copy(error = texts.get(StringsR.string.settings_phone_config_invalid_request))
                    } else {
                        if (index < 0) updated += candidate else updated[index] = candidate
                        apiKeys.set(ApiService.AIOSTREAMS, StremioAddons.serialize(updated))
                        _editor.value = null
                    }
                }
                is KeyCheck.Invalid -> _editor.value = edit.copy(busy = false, error = result.message)
            }
        }
    }

    fun test(id: String) {
        val addon = currentAddons().firstOrNull { it.id == id } ?: return
        if (_testStatus.value[id]?.checking == true) return
        _testStatus.value += id to AddonTestStatus(checking = true)
        viewModelScope.launch {
            when (val result = apiKeys.check(ApiService.AIOSTREAMS, addon.manifestUrl)) {
                is KeyCheck.Valid -> {
                    _testStatus.value += id to AddonTestStatus(
                        detail = result.detail ?: texts.get(StringsR.string.settings_connected),
                    )
                }
                is KeyCheck.Invalid -> {
                    _testStatus.value += id to AddonTestStatus(detail = result.message, error = true)
                }
            }
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            val updated = currentAddons().filterNot { it.id == id }
            apiKeys.set(ApiService.AIOSTREAMS, StremioAddons.serialize(updated))
        }
    }

    init {
        viewModelScope.launch {
            addons.collect { current ->
                current.filter { it.id !in _metadata.value }.mapConcurrent { addon ->
                    runCatching { apiKeys.addonMetadata(addon.manifestUrl) }.getOrNull()?.let { metadata ->
                        _metadata.value += addon.id to metadata
                    }
                }
            }
        }
    }

    private fun currentAddons(): List<StremioAddon> =
        StremioAddons.parse(apiKeys.keys.value[ApiService.AIOSTREAMS].orEmpty())
}
