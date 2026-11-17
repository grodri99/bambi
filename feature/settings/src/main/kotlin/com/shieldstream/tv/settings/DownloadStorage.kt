package com.shieldstream.tv.settings

import androidx.lifecycle.ViewModel
import com.shieldstream.tv.domain.DownloadsRepository
import com.shieldstream.tv.model.StorageOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Ajustes → carpeta de descargas.

@HiltViewModel
class DownloadStorageViewModel @Inject constructor(
    private val downloads: DownloadsRepository,
) : ViewModel() {
    private val _options = MutableStateFlow(downloads.storageOptions())
    val options: StateFlow<List<StorageOption>> = _options.asStateFlow()
    val selected: StateFlow<String?> = downloads.storagePath

    /** Vuelve a mirar qué discos hay (por si se acaba de enchufar o quitar un USB). */
    fun refresh() {
        _options.value = downloads.storageOptions()
    }

    fun shift(delta: Int) {
        refresh()
        val options = _options.value
        if (options.isEmpty()) return
        val index = options.indexOfFirst { it.path == selected.value }.coerceAtLeast(0)
        downloads.selectStorage(options[((index + delta) % options.size + options.size) % options.size].path)
    }
}

/** Ajustes → Carpeta de descargas: memoria interna o un USB/tarjeta, con el espacio libre de cada uno. */
@Composable
internal fun DownloadStorageCard(viewModel: DownloadStorageViewModel = hiltViewModel()) {
    val options by viewModel.options.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val current = options.firstOrNull { it.path == selected } ?: options.firstOrNull()
    FocusCard(
        onClick = { viewModel.shift(1) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.shift(-1); true }
                Key.DirectionRight -> { viewModel.shift(1); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.downloads_storage),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.downloads_storage_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    localizedStringResource(StringsR.string.downloads_storage_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsMuted.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.width(24.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (options.size > 1) "‹  ${current?.label.orEmpty()}  ›" else current?.label.orEmpty(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                current?.let {
                    Text(
                        localizedStringResource(StringsR.string.downloads_storage_free, storageGb(it.freeBytes), storageGb(it.totalBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private fun storageGb(bytes: Long): String = String.format(Locale.getDefault(), "%.1f GB", bytes / 1_073_741_824.0)
