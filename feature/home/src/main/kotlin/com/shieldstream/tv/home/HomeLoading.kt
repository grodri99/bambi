package com.shieldstream.tv.home

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.ui.component.PosterWidth
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.theme.LocalLightweightUi
import kotlinx.coroutines.delay

// Inicio → pantallas de carga y de error.

internal val SkeletonColor = Color(0xFF1C1E2A)

@Composable
internal fun delayedSkeletonAlpha(): Float {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(250)
        visible = true
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(300), label = "skeleton")
    return alpha
}

@Composable
internal fun skeletonPulse(): Float {
    if (LocalLightweightUi.current) return 0.8f
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Reverse),
        label = "skeletonPulseAlpha",
    )
    return pulse
}

@Composable
internal fun HomeSkeleton() {
    val alpha = delayedSkeletonAlpha() * skeletonPulse()
    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .graphicsLayer { this.alpha = alpha }
            .padding(start = ContentStart, top = 140.dp),
    ) {
        repeat(2) {
            Box(
                Modifier
                    .width(180.dp)
                    .height(18.dp)
                    .background(SkeletonColor, AppShapes.Badge),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(7) {
                    Box(
                        Modifier
                            .width(PosterWidth)
                            .aspectRatio(2f / 3f)
                            .background(SkeletonColor, AppShapes.Control),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun HomeError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier.fillMaxSize()) {
    val retryRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { retryRequester.requestFocus() }
    }
    Column(
        modifier.background(AppColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        TvButton(text = localizedStringResource(StringsR.string.home_retry), onClick = onRetry, modifier = Modifier.focusRequester(retryRequester))
    }
}
