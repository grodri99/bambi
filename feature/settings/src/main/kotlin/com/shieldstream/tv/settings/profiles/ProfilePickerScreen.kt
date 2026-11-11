package com.shieldstream.tv.settings.profiles

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.model.ProfileManagement
import com.shieldstream.tv.ui.R
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.ProfileAvatar
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.tvClickable
import kotlinx.coroutines.delay

internal val ProfileBackground = Brush.verticalGradient(listOf(AppColors.BackgroundRaised, Color.Black))
internal val ProfileMuted = Color.White.copy(alpha = 0.55f)

@Composable
fun ProfilePickerScreen(
    onSelected: () -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state as? ProfilesUiState.Ready ?: run {
        Box(Modifier.fillMaxSize().background(AppColors.Background))
        return
    }
    var manage by remember { mutableStateOf(false) }
    var managementUnlocked by remember { mutableStateOf(false) }
    var managementAction by remember { mutableStateOf<ManagementAction?>(null) }
    var managementPinError by remember { mutableStateOf<String?>(null) }
    val managementPinProfile = ProfileManagement.pinProfile(ready.profiles)

    fun requestManagement(action: ManagementAction) {
        if (managementPinProfile == null || managementUnlocked) {
            managementUnlocked = true
            when (action) {
                ManagementAction.MANAGE -> manage = true
                ManagementAction.ADD -> onAdd()
            }
        } else {
            managementPinError = null
            managementAction = action
        }
    }
    var pinFor by remember { mutableStateOf<Pair<Profile, Boolean>?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val activeRequester = remember { FocusRequester() }
    LaunchedEffect(manage) {
        repeat(4) {
            runCatching { activeRequester.requestFocus() }
            delay(150)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ProfileBackground)
            .onBackKey(enabled = manage) { manage = false },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 32.dp),
        ) {
            Image(painterResource(R.drawable.bambi_logo), contentDescription = "Bambi", modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Image(painterResource(R.drawable.bambi_wordmark), contentDescription = null, modifier = Modifier.height(14.dp))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                if (manage) localizedStringResource(StringsR.string.settings_manage_profiles) else localizedStringResource(StringsR.string.settings_who_s_watching),
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(44.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.Top) {
                ready.profiles.forEach { profile ->
                    ProfileTile(
                        profile = profile,
                        manage = manage,
                        onClick = {
                            when {
                                profile.locked && !(manage && managementUnlocked) -> {
                                    pinError = null
                                    pinFor = profile to manage
                                }
                                manage -> onEdit(profile.id)
                                else -> {
                                    viewModel.select(profile.id)
                                    onSelected()
                                }
                            }
                        },
                        modifier = if (profile.id == ready.activeId) Modifier.focusRequester(activeRequester) else Modifier,
                    )
                }
                if (ready.canAdd) AddTile(onClick = { requestManagement(ManagementAction.ADD) })
            }
            Spacer(Modifier.height(56.dp))
            TvButton(
                text = if (manage) localizedStringResource(StringsR.string.settings_done) else localizedStringResource(StringsR.string.settings_manage_profiles),
                icon = if (manage) AppIcons.Check else AppIcons.Edit,
                onClick = {
                    if (manage) manage = false else requestManagement(ManagementAction.MANAGE)
                },
            )
        }
    }

    val wrongPinMessage = localizedStringResource(StringsR.string.settings_wrong_pin)
    managementAction?.let { action ->
        val profile = managementPinProfile ?: return@let
        PinDialog(
            title = localizedStringResource(StringsR.string.settings_management_pin),
            subtitle = localizedStringResource(StringsR.string.settings_management_pin_for, profile.name),
            error = managementPinError,
            onPin = { pin ->
                if (com.shieldstream.tv.model.ProfilePin.matches(profile, pin)) {
                    managementUnlocked = true
                    managementAction = null
                    managementPinError = null
                    when (action) {
                        ManagementAction.MANAGE -> manage = true
                        ManagementAction.ADD -> onAdd()
                    }
                } else {
                    managementPinError = wrongPinMessage
                }
            },
            onDismiss = {
                managementAction = null
                managementPinError = null
            },
        )
    }

    pinFor?.let { (profile, forEdit) ->
        PinDialog(
            title = localizedStringResource(StringsR.string.settings_profile_pin, profile.name),
            subtitle = localizedStringResource(StringsR.string.settings_enter_the_4_digit_pin),
            error = pinError,
            onPin = { pin ->
                if (com.shieldstream.tv.model.ProfilePin.matches(profile, pin)) {
                    pinFor = null
                    pinError = null
                    if (forEdit) {
                        onEdit(profile.id)
                    } else {
                        viewModel.select(profile.id)
                        onSelected()
                    }
                } else {
                    pinError = wrongPinMessage
                }
            },
            onDismiss = {
                pinFor = null
                pinError = null
            },
        )
    }
}

private enum class ManagementAction { MANAGE, ADD }

private val TileSize = 128.dp

@Composable
private fun ProfileTile(profile: Profile, manage: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "tileScale")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(TileSize + 20.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .border(4.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(TileSize * 0.16f))
                .padding(6.dp),
        ) {
            ProfileAvatar(initial = profile.initial, avatar = profile.avatar, size = TileSize)
            if (manage) {
                Box(
                    Modifier
                        .size(TileSize)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(TileSize * 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            profile.name,
            color = if (focused) Color.White else ProfileMuted,
            fontSize = 20.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (profile.traktUser != null || profile.kids || profile.locked) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (profile.traktUser != null) {
                    // Green reads as "linked/OK" here; Trakt's brand red looked like a warning.
                    Text("Trakt ✓", color = AppColors.Success.copy(alpha = if (focused) 1f else 0.75f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                if (profile.kids) {
                    Text(localizedStringResource(StringsR.string.settings_kids), color = Color(0xFF56CCF2).copy(alpha = if (focused) 1f else 0.75f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                if (profile.locked) {
                    Icon(AppIcons.Lock, contentDescription = localizedStringResource(StringsR.string.settings_pin_protected), tint = ProfileMuted, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "addScale")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(TileSize + 20.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .border(4.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(TileSize * 0.16f))
                .padding(6.dp),
        ) {
            Box(
                Modifier
                    .size(TileSize)
                    .background(Color.White.copy(alpha = if (focused) 0.18f else 0.08f), RoundedCornerShape(TileSize * 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            localizedStringResource(StringsR.string.settings_add_profile),
            color = if (focused) Color.White else ProfileMuted,
            fontSize = 20.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

internal val TraktRed = Color(0xFFED1C24)
