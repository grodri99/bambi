package com.shieldstream.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ventanas encima de todo (escribir el nombre de una lista, buscar listas de Trakt). Se pintan al final de la app, por
 * encima de la barra de arriba; Escape o un clic fuera las cierran.
 */
object Overlays {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)

    fun show(block: @Composable () -> Unit) { content = block }
    fun close() { content = null }

    /** Pedir un texto (nombre de una lista…). */
    fun prompt(title: String, hint: String, initial: String = "", confirm: String = "Guardar", onConfirm: (String) -> Unit) =
        show { PromptCard(title, hint, initial, confirm, onConfirm) }
}

@Composable
fun OverlayHost() {
    val block = Overlays.content ?: return
    DisposableEffect(block) {
        val close: () -> Boolean = { Overlays.close(); true }
        BackPress.handler = close
        onDispose { if (BackPress.handler === close) BackPress.handler = null }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { Overlays.close() },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}) { block() }
    }
}

@Composable
private fun OverlayCard(width: androidx.compose.ui.unit.Dp = 520.dp, content: @Composable () -> Unit) {
    Column(
        Modifier.width(width).background(BambiColors.BackgroundRaised, BambiShapes.Panel)
            .border(1.dp, BambiColors.Stroke, BambiShapes.Panel).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
private fun PromptCard(title: String, hint: String, initial: String, confirm: String, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(50); runCatching { focus.requestFocus() } }
    fun done() {
        if (text.isBlank()) return
        Overlays.close()
        onConfirm(text.trim())
    }
    OverlayCard {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(hint, color = BambiColors.Muted, fontSize = 14.sp)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(60) },
            singleLine = true,
            colors = darkFieldColors(),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { done(); true } else false
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            GhostButton("Cancelar") { Overlays.close() }
            GhostButton(confirm, primary = true, enabled = text.isNotBlank()) { done() }
        }
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.White.copy(alpha = 0.8f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
    cursorColor = BambiColors.Accent,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Color.White,
    unfocusedLabelColor = BambiColors.Muted,
)

/**
 * «Listas» en la ficha: Mi lista y las listas propias del perfil, con su marca si el título ya está, y «Nueva lista…».
 */
@Composable
fun ListsButton(library: LibraryStore, item: CatalogItem) {
    var open by remember { mutableStateOf(false) }
    val own = library.lists.filterNot { it.isTrakt }
    val inSome = own.count { it.contains(item) }
    Box {
        PillButton(if (inSome > 0) "En $inSome ${if (inSome == 1) "lista" else "listas"}" else "Listas", AppIcons.List) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(BambiColors.Surface).width(300.dp)) {
            val inMy = library.inMyList(item)
            MenuOption("Mi lista", if (inMy) AppIcons.Check else AppIcons.Add) { library.toggleMyList(item) }
            own.forEach { list ->
                MenuOption(list.name, if (list.contains(item)) AppIcons.Check else AppIcons.Add) { library.toggleInList(list.id, item) }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            MenuOption("Nueva lista…", AppIcons.Edit) {
                open = false
                Overlays.prompt("Nueva lista", "Ponle un nombre. «${item.title}» entra ya en ella.", confirm = "Crear") { name ->
                    library.createList(name, first = item)
                }
            }
        }
    }
}

/**
 * Mi lista y las demás listas del perfil, como pestañas: las propias (renombrar, borrar) y las de Trakt (poner al día,
 * abrir en Trakt, quitar). «Nueva lista» y «Añadir lista de Trakt» al final.
 */
@Composable
fun MyListScreen(library: LibraryStore, onOpen: (CatalogItem) -> Unit, trakt: TraktManager? = null, initialFilter: Catalog = Catalog.ALL) {
    var filter by remember { mutableStateOf(initialFilter) }
    var selected by remember { mutableStateOf<String?>(null) }
    val list = selected?.let { library.list(it) }
    // Si la lista elegida se borra, se vuelve a Mi lista.
    LaunchedEffect(selected, list == null) { if (selected != null && list == null) selected = null }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 88.dp)) {
        // Pestañas: Mi lista, las listas y los botones para crear o añadir.
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ListTab("Mi lista", library.myList.size, selected == null) { selected = null }
            library.lists.forEach { l -> ListTab(l.name, l.items.size, selected == l.id, trakt = l.isTrakt) { selected = l.id } }
            ListTab("+ Nueva lista", null, false, ghost = true) {
                Overlays.prompt("Nueva lista", "Ponle un nombre. Luego, desde cualquier ficha: «Listas».", confirm = "Crear") { name ->
                    selected = library.createList(name).id
                }
            }
            ListTab("+ Lista de Trakt", null, false, ghost = true) {
                Overlays.show { TraktListPicker(trakt, library) { selected = it } }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(list?.name ?: "Mi lista", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            list?.traktUser?.let { Text("de $it en Trakt", color = BambiColors.Muted, fontSize = 15.sp) }
            Spacer(Modifier.width(12.dp))
            // Como en la tele: Todo · Series · Películas.
            for ((value, label) in listOf(Catalog.ALL to "Todo", Catalog.SHOWS to "Series", Catalog.MOVIES to "Películas")) {
                FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
            }
            Spacer(Modifier.weight(1f))
            if (list != null && !list.isTrakt) {
                GhostButton("Renombrar") {
                    Overlays.prompt("Renombrar lista", "Nuevo nombre para «${list.name}».", initial = list.name) { library.renameList(list.id, it) }
                }
                GhostButton("Borrar lista") {
                    Overlays.show { ConfirmCard("¿Borrar «${list.name}»?", "Los títulos siguen en Bambi; solo se borra la lista.", "Borrar") { library.deleteList(list.id) } }
                }
            }
            if (list != null && list.isTrakt) {
                if (refreshing) CircularProgressIndicator(color = BambiColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                GhostButton("Actualizar", enabled = !refreshing && trakt != null) {
                    val path = list.traktPath ?: return@GhostButton
                    refreshing = true
                    problem = null
                    scope.launch {
                        runCatching { trakt!!.listItems(path) }
                            .onSuccess { library.saveTraktList(path, list.name, list.traktUser, it) }
                            .onFailure { problem = it.message }
                        refreshing = false
                    }
                }
                GhostButton("Ver en Trakt") { openInBrowser("https://trakt.tv" + list.traktPath) }
                GhostButton("Quitar") { library.deleteList(list.id) }
            }
        }
        problem?.let { Text(it, color = BambiColors.Accent, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(16.dp))
        val all = list?.items ?: library.myList
        val shown = all.filter {
            when (filter) {
                Catalog.ALL -> true
                Catalog.SHOWS -> it.type == MediaType.TV.name
                Catalog.MOVIES -> it.type == MediaType.MOVIE.name
            }
        }
        if (shown.isEmpty()) {
            Text(
                when {
                    all.isNotEmpty() -> "Nada en este filtro."
                    list == null -> "Aún no has añadido nada. En cualquier ficha, pulsa «Mi lista»."
                    list.isTrakt -> "Esta lista de Trakt está vacía (o no se pudo leer: pulsa «Actualizar»)."
                    else -> "Lista vacía. En cualquier ficha, pulsa «Listas» y elígela."
                },
                color = BambiColors.Muted,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(158.dp),
                contentPadding = PaddingValues(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items(shown, key = { it.type + it.id }) { saved ->
                    val item = saved.toItem()
                    // Clic derecho: quitar de esta lista (las de Trakt no se tocan desde aquí).
                    androidx.compose.foundation.ContextMenuArea(items = {
                        when {
                            list == null -> listOf(androidx.compose.foundation.ContextMenuItem("Quitar de Mi lista") { library.toggleMyList(item) })
                            !list.isTrakt -> listOf(androidx.compose.foundation.ContextMenuItem("Quitar de «${list.name}»") { library.toggleInList(list.id, item) })
                            else -> emptyList()
                        }
                    }) { PosterCard(item, onClick = { onOpen(item) }) }
                }
            }
        }
    }
}

@Composable
private fun ListTab(label: String, count: Int?, selected: Boolean, trakt: Boolean = false, ghost: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.background(
            when {
                selected -> Color.White
                hovered -> BambiColors.SurfaceHigh
                ghost -> Color.Transparent
                else -> BambiColors.Glass
            },
            BambiShapes.Pill,
        ).border(1.dp, if (selected) Color.White else Color.White.copy(alpha = if (ghost) 0.35f else 0.14f), BambiShapes.Pill)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (trakt) Text("Trakt", color = if (selected) Color(0xFFB0121F) else Color(0xFFED4C5C), fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text(label, color = if (selected) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        count?.let { Text("$it", color = if (selected) Color.Black.copy(alpha = 0.55f) else BambiColors.Muted, fontSize = 13.sp) }
    }
}

@Composable
private fun ConfirmCard(title: String, text: String, confirm: String, onConfirm: () -> Unit) {
    OverlayCard {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text, color = BambiColors.Muted, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            GhostButton("Cancelar") { Overlays.close() }
            GhostButton(confirm, primary = true) { Overlays.close(); onConfirm() }
        }
    }
}

/**
 * Añadir una lista de Trakt de otra persona: buscar por nombre (vacío: las más populares) o pegar su enlace. Se lee
 * entera (hasta 100 títulos) y se guarda en el perfil; sale en Mi lista y, si se quiere, como fila del Inicio.
 */
@Composable
private fun TraktListPicker(trakt: TraktManager?, library: LibraryStore, onAdded: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<Load<List<TraktListInfo>>?>(null) }
    var adding by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    fun add(info: TraktListInfo) {
        val t = trakt ?: return
        adding = info.path
        problem = null
        scope.launch {
            runCatching { t.listItems(info.path) }
                .onSuccess { items ->
                    val saved = library.saveTraktList(info.path, info.name, info.user, items)
                    Overlays.close()
                    onAdded(saved.id)
                }
                .onFailure { problem = it.message ?: "No se pudo leer la lista." }
            adding = null
        }
    }
    fun search() {
        val t = trakt ?: return
        problem = null
        val q = query.trim()
        // Un enlace se abre directamente.
        if (TraktLists.pathFromUrl(q) != null) {
            results = Load.Loading
            scope.launch {
                runCatching { t.listFromUrl(q) }
                    .onSuccess { info -> results = Load.Ok(listOf(info)); add(info) }
                    .onFailure { results = Load.Failed(it.message ?: "No se pudo abrir el enlace.") }
            }
            return
        }
        results = Load.Loading
        scope.launch { results = load { t.searchLists(q) } }
    }
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { focus.requestFocus() }
        if (trakt?.isConfigured == true) search()
    }
    OverlayCard(width = 640.dp) {
        Text("Añadir una lista de Trakt", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (trakt?.isConfigured != true) {
            Text("Hace falta el Client ID de Trakt: Ajustes → Trakt (no hace falta conectar la cuenta).", color = BambiColors.Text, fontSize = 14.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { GhostButton("Cerrar") { Overlays.close() } }
            return@OverlayCard
        }
        Text("Busca por nombre (por ejemplo «Marvel» o «terror») o pega el enlace de la lista. Sin escribir nada salen las más populares.", color = BambiColors.Muted, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Nombre o https://trakt.tv/users/…/lists/…", color = BambiColors.Muted) },
                colors = darkFieldColors(),
                modifier = Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { search(); true } else false
                },
            )
            GhostButton("Buscar", primary = true) { search() }
        }
        problem?.let { Text(it, color = BambiColors.Accent, fontSize = 14.sp) }
        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp)) {
            when (val r = results) {
                null -> {}
                Load.Loading -> CircularProgressIndicator(color = BambiColors.Accent, modifier = Modifier.align(Alignment.Center))
                is Load.Failed -> Text(r.message, color = BambiColors.Text)
                is Load.Ok -> if (r.value.isEmpty()) {
                    Text("No hay listas con ese nombre.", color = BambiColors.Muted)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(r.value, key = { it.path }) { info ->
                            val already = library.lists.any { it.traktPath == info.path }
                            TraktListRow(info, busy = adding == info.path, already = already) { if (!already && adding == null) add(info) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { GhostButton("Cerrar") { Overlays.close() } }
    }
}

@Composable
private fun TraktListRow(info: TraktListInfo, busy: Boolean, already: Boolean, onAdd: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().background(if (hovered) BambiColors.SurfaceHigh else BambiColors.Surface, BambiShapes.Control)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onAdd)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(info.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(info.user?.let { "de $it" }, "${info.count} títulos", info.likes.takeIf { it > 0 }?.let { "$it me gusta" }).joinToString("  ·  "),
                color = BambiColors.Muted,
                fontSize = 12.sp,
            )
            if (info.description.isNotBlank()) Text(info.description, color = BambiColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            busy -> CircularProgressIndicator(color = BambiColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            already -> Text("Añadida", color = BambiColors.Cached, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            else -> Text("Añadir", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
