package com.shieldstream.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.domain.IthilLibraryInfo
import com.shieldstream.tv.domain.IthilLibraryState
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes

@Composable
fun IthilLibraryCard(
    state: IthilLibraryState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val muted = Color.White.copy(alpha = 0.6f)
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Surface, AppShapes.Control)
            .padding(20.dp),
    ) {
        Text(
            localizedStringResource(StringsR.string.settings_ithil_library),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        Spacer(Modifier.height(8.dp))
        when (state) {
            IthilLibraryState.NotInstalled -> {
                Text(localizedStringResource(StringsR.string.settings_ithil_not_installed), color = muted)
                Spacer(Modifier.height(12.dp))
                TvButton(localizedStringResource(StringsR.string.settings_ithil_download), onClick = onDownload)
            }
            is IthilLibraryState.Downloading -> Text(
                localizedStringResource(
                    StringsR.string.settings_ithil_downloading,
                    (state.progress * 100).toInt(),
                ),
                color = muted,
            )
            IthilLibraryState.Indexing -> Text(localizedStringResource(StringsR.string.settings_ithil_indexing), color = muted)
            is IthilLibraryState.Ready -> InstalledLibraryContent(
                info = state.info,
                updateText = localizedStringResource(StringsR.string.settings_ithil_update),
                onUpdate = onDownload,
                onDelete = onDelete,
            )
            is IthilLibraryState.UpToDate -> InstalledLibraryContent(
                info = state.info,
                updateText = localizedStringResource(StringsR.string.settings_ithil_update),
                onUpdate = onDownload,
                onDelete = onDelete,
                message = localizedStringResource(StringsR.string.settings_ithil_up_to_date),
            )
            is IthilLibraryState.Available -> InstalledLibraryContent(
                info = state.current,
                updateText = localizedStringResource(StringsR.string.settings_update),
                onUpdate = onDownload,
                onDelete = onDelete,
                message = localizedStringResource(StringsR.string.settings_ithil_update_available, state.update.version),
                messageColor = AppColors.Warning,
            )
            is IthilLibraryState.Error -> {
                Text(localizedStringResource(StringsR.string.settings_ithil_error, state.message), color = AppColors.Error)
                Spacer(Modifier.height(12.dp))
                TvButton(localizedStringResource(StringsR.string.settings_retry), onClick = onDownload)
            }
        }
    }
}

@Composable
private fun InstalledLibraryContent(
    info: IthilLibraryInfo,
    updateText: String,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    message: String? = null,
    messageColor: Color = AppColors.Success,
) {
    val muted = Color.White.copy(alpha = 0.6f)
    Text(
        localizedStringResource(
            StringsR.string.settings_ithil_ready,
            info.movieLinks,
            info.episodeLinks,
        ),
        color = muted,
    )
    if (message != null) {
        Spacer(Modifier.height(8.dp))
        Text(message, color = messageColor)
    }
    Spacer(Modifier.height(12.dp))
    Row {
        TvButton(updateText, onClick = onUpdate)
        Spacer(Modifier.width(12.dp))
        TvButton(localizedStringResource(StringsR.string.settings_ithil_delete), onClick = onDelete)
    }
}
