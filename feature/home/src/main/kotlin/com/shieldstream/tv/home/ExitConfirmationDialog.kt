package com.shieldstream.tv.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.component.ActionDockCard
import com.shieldstream.tv.ui.component.BottomActionDialog
import com.shieldstream.tv.ui.component.LineIcons

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
    BottomActionDialog(
        title = localizedStringResource(StringsR.string.home_exit_title),
        onDismiss = onDismiss,
        scrimAlpha = 0.94f,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionDockCard(
                text = localizedStringResource(StringsR.string.home_cancel),
                icon = LineIcons.Close,
                onClick = onDismiss,
                modifier = Modifier.focusRequester(cancelRequester),
            )
            ActionDockCard(
                text = localizedStringResource(StringsR.string.home_exit_confirm),
                icon = LineIcons.Exit,
                onClick = onExit,
            )
        }
    }
}
