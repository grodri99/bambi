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
            is IthilLibraryState.Ready -> {
                Text(
                    localizedStringResource(
                        StringsR.string.settings_ithil_ready,
                        state.info.movieLinks,
                        state.info.episodeLinks,
                    ),
                    color = muted,
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    TvButton(localizedStringResource(StringsR.string.settings_ithil_update), onClick = onDownload)
                    Spacer(Modifier.width(12.dp))
                    TvButton(localizedStringResource(StringsR.string.settings_ithil_delete), onClick = onDelete)
                }
            }
            is IthilLibraryState.Error -> {
                Text(localizedStringResource(StringsR.string.settings_ithil_error, state.message), color = AppColors.Error)
                Spacer(Modifier.height(12.dp))
                TvButton(localizedStringResource(StringsR.string.settings_retry), onClick = onDownload)
            }
        }
    }
}
