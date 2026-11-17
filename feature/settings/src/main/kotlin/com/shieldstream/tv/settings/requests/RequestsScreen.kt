package com.shieldstream.tv.settings.requests

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.shieldstream.tv.domain.TitleRequest
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.theme.AppShapes
import com.shieldstream.tv.ui.theme.AppSpacing

@Composable
fun RequestsScreen(
    onBack: () -> Unit,
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val requests by viewModel.pending.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E10))
            .onBackKey(enabled = true) { onBack() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.ScreenEdge, vertical = 32.dp),
        ) {
            Text(
                localizedStringResource(StringsR.string.settings_family_requests),
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                localizedStringResource(StringsR.string.settings_family_requests_hint),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(20.dp))
            when {
                !isAdmin -> Text(
                    localizedStringResource(StringsR.string.settings_family_requests_admin_only),
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                requests.isEmpty() -> Text(
                    localizedStringResource(StringsR.string.settings_family_requests_empty),
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(requests, key = { it.id }) { request ->
                        RequestCard(
                            request = request,
                            onApprove = { viewModel.approve(request.id) },
                            onDismiss = { viewModel.dismiss(request.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(request: TitleRequest, onApprove: () -> Unit, onDismiss: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Control)
            .background(if (focused) Color(0xFF2A2D3D) else Color(0xFF1A1C27))
            .border(
                1.dp,
                if (focused) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                AppShapes.Control,
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Box(
            Modifier
                .size(width = 46.dp, height = 68.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF232327)),
        ) {
            request.posterUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                request.title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                localizedStringResource(StringsR.string.settings_family_requested_by, request.profileName),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.width(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(
                text = localizedStringResource(StringsR.string.settings_dismiss),
                onClick = onDismiss,
            )
            TvButton(
                text = localizedStringResource(StringsR.string.settings_approve),
                onClick = onApprove,
            )
        }
    }
}
