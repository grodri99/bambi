package com.shieldstream.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.shieldstream.tv.domain.StremioAddon
import com.shieldstream.tv.domain.StremioAddonMetadata
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.component.ActionMenuDialog
import com.shieldstream.tv.ui.component.ActionMenuItem
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.LineIcons
import com.shieldstream.tv.ui.component.ProvideMinimalScroll
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.tvClickable
import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes

private val AddonMuted = Color.White.copy(alpha = 0.6f)

@Composable
fun AddonsScreen(
    onBack: () -> Unit,
    viewModel: AddonsViewModel = hiltViewModel(),
) {
    val addons by viewModel.addons.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val testStatus by viewModel.testStatus.collectAsStateWithLifecycle()
    val metadata by viewModel.metadata.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<StremioAddon?>(null) }
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(actionsFor, editor) {
        if (actionsFor != null || editor != null) return@LaunchedEffect
        withFrameNanos { }
        runCatching { firstRequester.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .onBackKey(enabled = editor == null) {
                if (actionsFor != null) actionsFor = null else onBack()
            },
    ) {
        ProvideMinimalScroll(margin = 56.dp) {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 120.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Text(
                        localizedStringResource(StringsR.string.settings_addons_title),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    Text(
                        localizedStringResource(StringsR.string.settings_addons_manage),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AddonMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (addons.isEmpty()) {
                    item {
                        Text(
                            localizedStringResource(StringsR.string.settings_addons_empty),
                            style = MaterialTheme.typography.titleMedium,
                            color = AddonMuted,
                        )
                    }
                }
                itemsIndexed(addons, key = { _, addon -> addon.id }) { index, addon ->
                    AddonCard(
                        addon = addon,
                        testStatus = testStatus[addon.id],
                        metadata = metadata[addon.id],
                        onClick = { actionsFor = addon },
                        modifier = if (index == 0) Modifier.focusRequester(firstRequester) else Modifier,
                    )
                }
                item {
                    AddonCard(
                        addon = null,
                        onClick = { viewModel.openEditor() },
                        modifier = if (addons.isEmpty()) Modifier.focusRequester(firstRequester) else Modifier,
                    )
                }
            }
        }
        actionsFor?.let { addon ->
            ActionMenuDialog(
                title = metadata[addon.id]?.name?.takeUnless { it.isBlank() } ?: addon.host,
                onDismiss = { actionsFor = null },
                scrimAlpha = 0.94f,
                actions = listOf(
                    ActionMenuItem(
                        text = localizedStringResource(StringsR.string.settings_test_addon),
                        icon = AppIcons.Bolt,
                        onClick = {
                            actionsFor = null
                            viewModel.test(addon.id)
                        },
                    ),
                    ActionMenuItem(
                        text = localizedStringResource(StringsR.string.settings_replace_manifest),
                        icon = AppIcons.Edit,
                        onClick = {
                            actionsFor = null
                            viewModel.openEditor(addon.id)
                        },
                    ),
                    ActionMenuItem(
                        text = localizedStringResource(StringsR.string.settings_remove_addon),
                        icon = LineIcons.Delete,
                        onClick = {
                            actionsFor = null
                            viewModel.remove(addon.id)
                        },
                    ),
                ),
            )
        }
        editor?.let { state ->
            AddonEditorDialog(
                editor = state,
                onSave = viewModel::save,
                onClose = viewModel::closeEditor,
            )
        }
    }
}

@Composable
private fun AddonCard(
    addon: StremioAddon?,
    testStatus: AddonTestStatus? = null,
    metadata: StremioAddonMetadata? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) AppColors.SurfaceFocused else AppColors.Surface, AppShapes.Control)
            .border(1.dp, if (focused) Color.White.copy(alpha = 0.7f) else Color.Transparent, AppShapes.Control)
            .tvClickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp).background(AppColors.SurfaceFocused, AppShapes.Control),
        ) {
            if (!metadata?.logoUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = metadata?.logoUrl.orEmpty(),
                    contentDescription = addon?.host,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(32.dp),
                    loading = { AddonFallbackIcon(addon) },
                    error = { AddonFallbackIcon(addon) },
                )
            } else {
                AddonFallbackIcon(addon)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = metadata?.name?.takeUnless { it.isBlank() } ?: addon?.host ?: localizedStringResource(StringsR.string.settings_add_addon),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Text(
                text = when {
                    addon == null -> localizedStringResource(StringsR.string.settings_add_manifest_help)
                    testStatus?.checking == true -> localizedStringResource(StringsR.string.settings_checking)
                    testStatus?.detail != null -> testStatus.detail
                    addon.isValid -> localizedStringResource(StringsR.string.settings_valid_manifest)
                    else -> localizedStringResource(StringsR.string.settings_invalid_manifest)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    testStatus?.error == true || (testStatus == null && addon?.isValid == false) -> AppColors.Error
                    testStatus?.detail != null -> AppColors.Success
                    else -> AddonMuted
                },
            )
        }
        if (addon != null) {
            Text(
                text = localizedStringResource(StringsR.string.settings_ok_to_change),
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) Color.White else Color.Transparent,
            )
        }
    }
}

@Composable
private fun AddonFallbackIcon(addon: StremioAddon?) {
    androidx.compose.material3.Icon(
        if (addon == null) AppIcons.Add else AppIcons.List,
        contentDescription = null,
        tint = Color.White,
    )
}

@Composable
private fun AddonEditorDialog(
    editor: AddonEditor,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
) {
    var input by rememberSaveable(editor.id) { mutableStateOf("") }
    val fieldRequester = remember { FocusRequester() }
    val saveRequester = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    LaunchedEffect(editor.id) {
        withFrameNanos { }
        runCatching { fieldRequester.requestFocus() }
    }
    LaunchedEffect(editor.busy, editor.error) {
        if (!editor.busy && editor.error != null) {
            withFrameNanos { }
            runCatching { saveRequester.requestFocus() }
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .onBackKey(enabled = true, onBack = onClose),
    ) {
        Column(
            Modifier
                .width(720.dp)
                .background(AppColors.Surface, AppShapes.Panel)
                .border(1.dp, Color.White.copy(alpha = 0.1f), AppShapes.Panel)
                .padding(36.dp),
        ) {
            Text(
                text = localizedStringResource(
                    if (editor.id == null) StringsR.string.settings_add_manifest_title
                    else StringsR.string.settings_replace_manifest_title,
                ),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Text(
                text = localizedStringResource(
                    if (editor.id == null) StringsR.string.settings_add_manifest_help
                    else StringsR.string.settings_replace_manifest_help,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = AddonMuted,
            )
            Spacer(Modifier.height(24.dp))
            BasicTextField(
                value = input,
                onValueChange = { input = it.replace("\n", "") },
                singleLine = true,
                enabled = !editor.busy,
                textStyle = TextStyle(color = Color.White, fontSize = 20.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSave(input) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldRequester)
                    .onFocusChanged { fieldFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.DirectionDown && !editor.busy) {
                            if (event.type == KeyEventType.KeyDown) runCatching { saveRequester.requestFocus() }
                            true
                        } else false
                    },
                decorationBox = { field ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(AppColors.Field, AppShapes.Control)
                            .border(
                                width = if (fieldFocused) 2.dp else 1.dp,
                                color = if (fieldFocused) Color.White else Color.White.copy(alpha = 0.2f),
                                shape = AppShapes.Control,
                            )
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                    ) {
                        if (input.isEmpty()) {
                            Text("https://addon.example/manifest.json", fontSize = 18.sp, color = Color.White.copy(alpha = 0.35f))
                        }
                        field()
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    editor.busy -> localizedStringResource(StringsR.string.settings_checking)
                    editor.error != null -> editor.error
                    else -> localizedStringResource(StringsR.string.settings_add_manifest_help)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (editor.error != null) AppColors.Error else AddonMuted,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = localizedStringResource(StringsR.string.settings_check_and_save),
                    onClick = { onSave(input) },
                    enabled = !editor.busy,
                    modifier = Modifier.focusRequester(saveRequester),
                )
                TvButton(
                    text = localizedStringResource(StringsR.string.settings_cancel),
                    onClick = onClose,
                    enabled = !editor.busy,
                )
            }
        }
    }
}
