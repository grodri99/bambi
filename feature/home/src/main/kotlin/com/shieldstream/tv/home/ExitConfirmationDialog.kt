package com.shieldstream.tv.home

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.onBackKey

@Composable
fun ExitConfirmationDialog(
    onDismiss: () -> Unit,
    onExit: () -> Unit,
) {
    val cancelRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { cancelRequester.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .onBackKey(enabled = true, onBack = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .background(AppColors.Surface, AppShapes.Control)
                .padding(24.dp),
        ) {
            Text(
                text = localizedStringResource(StringsR.string.home_exit_title),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TvButton(
                    text = localizedStringResource(StringsR.string.home_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).focusRequester(cancelRequester),
                )
                TvButton(
                    text = localizedStringResource(StringsR.string.home_exit_confirm),
                    onClick = onExit,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
