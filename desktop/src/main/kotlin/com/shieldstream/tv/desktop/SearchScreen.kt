package com.shieldstream.tv.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.model.CatalogItem
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    repository: DesktopRepository,
    onOpen: (CatalogItem) -> Unit,
    kids: Boolean = false,
    /** Perfil infantil: pedir a un adulto lo que no es para niños. */
    onRequest: ((CatalogItem) -> Unit)? = null,
    isRequested: (CatalogItem) -> Boolean = { false },
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<Load<List<CatalogItem>>>(Load.Ok(emptyList())) }
    var outside by remember { mutableStateOf(emptyList<CatalogItem>()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = Load.Ok(emptyList())
            return@LaunchedEffect
        }
        delay(400)
        results = Load.Loading
        results = load { repository.search(query, kids) }
        outside = if (kids && onRequest != null) runCatching { repository.searchOutsideKids(query) }.getOrDefault(emptyList()) else emptyList()
    }
    // Sin escribir nada: lo más visto hoy, como en la tele.
    val today by androidx.compose.runtime.produceState(emptyList<CatalogItem>(), kids) {
        value = runCatching { if (kids) repository.homeRows(Catalog.ALL, true).firstOrNull()?.items.orEmpty() else repository.top10(Catalog.ALL) }.getOrDefault(emptyList())
    }
    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 88.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Películas, series…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        // Teclado en pantalla, como en la tele (para manejar Bambi con el ratón o un mando desde el sofá).
        OnScreenKeyboard(onKey = { query += it }, onDelete = { query = query.dropLast(1) }, onClear = { query = "" })
        Spacer(Modifier.height(18.dp))
        when (val r = results) {
            Load.Loading -> Text("Buscando…", color = BambiColors.Muted)
            is Load.Failed -> Text(r.message, color = BambiColors.Text)
            is Load.Ok -> if (r.value.isEmpty() && outside.isEmpty() && query.isNotBlank()) {
                Text("Sin resultados", color = BambiColors.Muted)
            } else {
                val shown = if (query.isBlank()) today else r.value
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (query.isBlank() && shown.isNotEmpty()) {
                        item(key = "today", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text("Lo más visto hoy", color = BambiColors.Text, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                    items(shown, key = { it.key }) { PosterCard(it, onClick = { onOpen(it) }) }
                    if (outside.isNotEmpty() && onRequest != null) {
                        item(key = "outside", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text("Fuera de tu perfil: pídeselo a un adulto", color = BambiColors.Text, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                        }
                        items(outside, key = { "out-" + it.key }) { item ->
                            Column {
                                PosterCard(item, onClick = { if (!isRequested(item)) onRequest(item) })
                                Text(
                                    if (isRequested(item)) "Pedido" else "Pedir",
                                    color = if (isRequested(item)) BambiColors.Muted else BambiColors.Accent,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Teclado ABC o QWERTY con Ñ, espacio, borrar y vaciar. */
@Composable
fun OnScreenKeyboard(onKey: (String) -> Unit, onDelete: () -> Unit, onClear: () -> Unit) {
    var qwerty by remember { mutableStateOf(false) }
    val rows = if (qwerty) listOf("1234567890", "qwertyuiop", "asdfghjklñ", "zxcvbnm")
    else listOf("abcdefghijklm", "nñopqrstuvwxyz", "1234567890")
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { c -> KeyCap(c.uppercase()) { onKey(c.toString()) } }
            }
        }
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyCap(if (qwerty) "ABC" else "QWERTY", wide = true) { qwerty = !qwerty }
            KeyCap("Espacio", wide = true) { onKey(" ") }
            KeyCap("Borrar", wide = true) { onDelete() }
            KeyCap("Vaciar", wide = true) { onClear() }
        }
    }
}

@Composable
private fun KeyCap(label: String, wide: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val active = interaction.collectIsHoveredAsState().value || interaction.collectIsFocusedAsState().value
    androidx.compose.foundation.layout.Box(
        Modifier.height(40.dp).let { if (wide) it.width(110.dp) else it.width(40.dp) }
            .background(if (active) Color.White else BambiColors.SurfaceHigh, BambiShapes.Badge)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { Text(label, color = if (active) Color.Black else Color.White, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
}
