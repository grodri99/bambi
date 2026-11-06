package com.shieldstream.tv.settings.profiles

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shieldstream.tv.model.ProfilePin
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.tvClickable
import kotlinx.coroutines.delay

@Composable
fun PinDialog(
    title: String,
    subtitle: String,
    error: String?,
    onPin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(3) {
            runCatching { firstKey.requestFocus() }
            delay(100)
        }
    }
    LaunchedEffect(error) { if (error != null) pin = "" }
    fun type(d: Char) {
        if (pin.length >= ProfilePin.LENGTH) return
        pin += d
        if (pin.length == ProfilePin.LENGTH) {
            val done = pin
            pin = ""
            onPin(done)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(AppColors.Surface, AppShapes.Panel)
                .border(1.dp, Color.White.copy(alpha = 0.08f), AppShapes.Panel)
                .padding(horizontal = 40.dp, vertical = 28.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val digit = when (event.key) {
                        Key.Zero, Key.NumPad0 -> '0'
                        Key.One, Key.NumPad1 -> '1'
                        Key.Two, Key.NumPad2 -> '2'
                        Key.Three, Key.NumPad3 -> '3'
                        Key.Four, Key.NumPad4 -> '4'
                        Key.Five, Key.NumPad5 -> '5'
                        Key.Six, Key.NumPad6 -> '6'
                        Key.Seven, Key.NumPad7 -> '7'
                        Key.Eight, Key.NumPad8 -> '8'
                        Key.Nine, Key.NumPad9 -> '9'
                        else -> null
                    }
                    if (digit != null) type(digit)
                    digit != null
                },
        ) {
            Icon(AppIcons.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = ProfileMuted, fontSize = 15.sp)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(ProfilePin.LENGTH) { i ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .background(if (i < pin.length) Color.White else Color.Transparent, CircleShape)
                            .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(error ?: " ", color = AppColors.Error, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            val rows = listOf("123", "456", "789")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { r, keys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        keys.forEachIndexed { c, d ->
                            PinKey(d.toString(), Modifier.then(if (r == 0 && c == 0) Modifier.focusRequester(firstKey) else Modifier)) { type(d) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinKey("✕") { onDismiss() }
                    PinKey("0") { type('0') }
                    PinKey("⌫") { pin = pin.dropLast(1) }
                }
            }
        }
    }
}

@Composable
private fun PinKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 64.dp, height = 46.dp)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.08f), AppShapes.Control)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick),
    ) {
        Text(label, color = if (focused) Color.Black else Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}
