package com.shieldstream.tv.settings.profiles

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.runtime.collectAsState
import com.shieldstream.tv.ui.component.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.component.ProfileAvatar
import com.shieldstream.tv.ui.component.ProfileIconFiles
import com.shieldstream.tv.ui.component.ProvidePivotScroll
import com.shieldstream.tv.ui.component.throttleVerticalRepeat
import com.shieldstream.tv.ui.component.tvClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Un icono de perfil: nombre del personaje y su imagen. */
data class ProfileIcon(val name: String, val url: String)

/** Los iconos de una serie o categoría. */
data class ProfileIconGroup(val title: String, val icons: List<ProfileIcon>)

/**
 * Lista de iconos de perfil: primero los propios de Bambi (dentro de la app) y después los de Netflix
 * (assets/profile_icons.json, por serie, con la URL de cada imagen: esos no van en la app, se cargan al verlos).
 */
object ProfileIcons {
    /** Iconos propios de Bambi: van dentro de la app (assets/profile_icons_bambi), no se descargan. */
    private const val BAMBI_DIR = "profile_icons_bambi"
    const val ASSET_PREFIX = "file:///android_asset/"

    private val bambiNames = mapOf(
        "01_sentado" to "Sentado",
        "02_asomandose" to "Asomándose",
        "03_sonriendo" to "Sonriendo",
        "04_agazapado" to "Agazapado",
        "05_palomitas" to "Palomitas",
        "06_jugando" to "Jugando",
        "07_dormido" to "Dormido",
    )

    /** true si es una foto subida desde el móvil (no un icono de la app ni de Netflix). */
    fun isUploadedPhoto(image: String?): Boolean = image != null && image.startsWith("file:") && !image.startsWith(ASSET_PREFIX)

    private fun bambi(context: Context): ProfileIconGroup? = runCatching {
        val files = context.assets.list(BAMBI_DIR).orEmpty().filter { it.endsWith(".webp") }.sorted()
        ProfileIconGroup(
            title = "Bambi",
            icons = files.map { file ->
                val key = file.removeSuffix(".webp")
                ProfileIcon(bambiNames[key] ?: key, "$ASSET_PREFIX$BAMBI_DIR/$file")
            },
        )
    }.getOrNull()?.takeIf { it.icons.isNotEmpty() }

    fun load(context: Context): List<ProfileIconGroup> = listOfNotNull(bambi(context)) + loadNetflix(context)

    private fun loadNetflix(context: Context): List<ProfileIconGroup> = runCatching {
        val raw = context.assets.open("profile_icons.json").bufferedReader().use { it.readText() }
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val group = array.optJSONObject(i) ?: return@mapNotNull null
            val icons = group.optJSONArray("icons") ?: return@mapNotNull null
            ProfileIconGroup(
                title = group.optString("show"),
                icons = (0 until icons.length()).mapNotNull { j ->
                    icons.optJSONObject(j)?.let { ProfileIcon(it.optString("name"), it.optString("url")) }
                }.filter { it.url.startsWith("https://") },
            )
        }.filter { it.icons.isNotEmpty() }
    }.getOrDefault(emptyList())
}

/**
 * Selector de icono a pantalla completa, como en Netflix: una fila por serie. La primera opción deja el perfil sin
 * icono (la inicial sobre su color). Atrás cierra sin cambiar nada.
 */
@Composable
internal fun ProfileIconPicker(
    profileId: String,
    initial: String,
    avatar: Int,
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    var uploading by remember { mutableStateOf(false) }
    val groups by produceState<List<ProfileIconGroup>?>(null) {
        value = withContext(Dispatchers.IO) { ProfileIcons.load(context.applicationContext) }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    // Como el Inicio: arriba/abajo mantenido va a ritmo fijo y la fila enfocada se queda a la misma altura (el scroll
    // la sigue sin acumular saltos ni frenar al final).
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0C10))
            .throttleVerticalRepeat(),
    ) {
        Column(Modifier.fillMaxSize()) {
        // El título va fijo arriba; debajo, la lista con la fila enfocada siempre a la misma altura.
        Text(
            localizedStringResource(StringsR.string.settings_choose_icon),
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 72.dp, top = 32.dp, bottom = 8.dp),
        )
        ProvidePivotScroll(leading = 16.dp) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 360.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 72.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            IconTile(
                                selected = current == null,
                                onClick = { onPick(null) },
                                modifier = Modifier.focusRequester(first),
                            ) {
                                ProfileAvatar(initial = initial, avatar = avatar, size = IconSize)
                            }
                            TileLabel(localizedStringResource(StringsR.string.settings_icon_none))
                        }
                        Column {
                            IconTile(
                                selected = ProfileIcons.isUploadedPhoto(current),
                                onClick = { uploading = true },
                            ) {
                                Box(
                                    Modifier
                                        .size(IconSize)
                                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(IconSize * 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (ProfileIcons.isUploadedPhoto(current)) {
                                        AsyncImage(
                                            model = current,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(IconSize).clip(RoundedCornerShape(IconSize * 0.14f)),
                                        )
                                    } else {
                                        Icon(AppIcons.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                                    }
                                }
                            }
                            TileLabel(localizedStringResource(StringsR.string.settings_upload_photo))
                        }
                    }
                }
            }
            val loaded = groups
            if (loaded == null) {
                item {
                    Box(Modifier.padding(start = 72.dp, top = 24.dp)) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            } else {
                items(loaded, key = { it.title }) { group ->
                    Column {
                        Text(
                            group.title,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 72.dp, top = 14.dp, bottom = 4.dp),
                        )
                        ProvidePivotScroll(leading = 72.dp) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 66.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(group.icons, key = { it.url }) { icon ->
                                    IconTile(selected = icon.url == current, onClick = { onPick(icon.url) }) {
                                        AsyncImage(
                                            model = remember(icon.url) { ProfileIconFiles.model(context, icon.url) },
                                            contentDescription = icon.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(IconSize)
                                                .clip(RoundedCornerShape(IconSize * 0.14f))
                                                .background(Color.White.copy(alpha = 0.06f)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        }
        if (uploading) {
            PhotoUploadDialog(
                profileId = profileId,
                onUploaded = { uri ->
                    uploading = false
                    onPick(uri)
                },
                onDismiss = { uploading = false },
            )
        }
    }
}

@Composable
private fun TileLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 4.dp, start = 6.dp),
    )
}

/**
 * QR para subir una foto desde el móvil (misma wifi): mientras está abierto hay un servidor de un solo uso en la Shield;
 * en cuanto llega la foto, se pone en el perfil y se cierra.
 */
@Composable
private fun PhotoUploadDialog(profileId: String, onUploaded: (String) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val pageTexts = mapOf(
        "{{TITLE}}" to localizedStringResource(StringsR.string.photo_page_title).html(),
        "{{HINT}}" to localizedStringResource(StringsR.string.photo_page_hint).html(),
        "{{PICK}}" to localizedStringResource(StringsR.string.photo_page_pick).html(),
        "{{SENDING}}" to localizedStringResource(StringsR.string.photo_page_sending).js(),
        "{{DONE}}" to localizedStringResource(StringsR.string.photo_page_done).js(),
        "{{ERROR}}" to localizedStringResource(StringsR.string.photo_page_error).js(),
        "{{LANG}}" to com.shieldstream.tv.model.Lang.current.code,
    )
    val server = remember {
        val template = context.assets.open("profile_photo.html").bufferedReader().use { it.readText() }
        val page = pageTexts.entries.fold(template) { acc, (key, value) -> acc.replace(key, value) }
        ProfilePhotoServer(context.applicationContext, profileId, page)
    }
    var connection by remember { mutableStateOf<ProfilePhotoServer.Connection?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(server) {
        connection = withContext(Dispatchers.IO) { runCatching { server.start() }.getOrNull() }
        failed = connection == null
    }
    androidx.compose.runtime.DisposableEffect(server) { onDispose { server.stop() } }
    val uploaded by server.uploaded.collectAsState()
    LaunchedEffect(uploaded) { uploaded?.let(onUploaded) }
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { cancel.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(380.dp)) {
                Text(
                    localizedStringResource(StringsR.string.settings_upload_photo_title),
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (failed) localizedStringResource(StringsR.string.settings_upload_photo_no_network)
                    else localizedStringResource(StringsR.string.settings_upload_photo_steps),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                )
                connection?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it.displayUrl, color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp)
                }
                Spacer(Modifier.height(24.dp))
                com.shieldstream.tv.ui.component.TvButton(
                    localizedStringResource(StringsR.string.settings_upload_photo_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(cancel),
                )
            }
            Spacer(Modifier.width(48.dp))
            Box(
                Modifier
                    .size(300.dp)
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                val c = connection
                if (c != null) {
                    com.shieldstream.tv.settings.QrCode(c.url, Modifier.fillMaxSize())
                } else if (!failed) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}

private fun String.html(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun String.js(): String = replace("\\", "\\\\").replace("'", "\\'").replace("<", "\\u003c")

@Composable
private fun IconTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .graphicsLayer {
                val s = if (focused) 1.08f else 1f
                scaleX = s
                scaleY = s
            }
            .border(
                width = 3.dp,
                color = when {
                    focused -> Color.White
                    selected -> Color.White.copy(alpha = 0.5f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(IconSize * 0.18f),
            )
            .padding(6.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick),
    ) {
        content()
    }
}

private val IconSize = 104.dp
