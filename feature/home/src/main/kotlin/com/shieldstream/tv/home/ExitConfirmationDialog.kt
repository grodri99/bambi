package com.shieldstream.tv.home

import androidx.compose.runtime.Composable
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.component.ActionMenuDialog
import com.shieldstream.tv.ui.component.ActionMenuItem
import com.shieldstream.tv.ui.component.LineIcons

@Composable
fun ExitConfirmationDialog(
    onDismiss: () -> Unit,
    onExit: () -> Unit,
) = ActionMenuDialog(
    title = localizedStringResource(StringsR.string.home_exit_title),
    onDismiss = onDismiss,
    scrimAlpha = 0.94f,
    actions = listOf(
        ActionMenuItem(localizedStringResource(StringsR.string.home_cancel), LineIcons.Close, onDismiss),
        ActionMenuItem(localizedStringResource(StringsR.string.home_exit_confirm), LineIcons.Exit, onExit),
    ),
)
