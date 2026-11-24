package com.shieldstream.tv.desktop

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.model.Quality

/** Calidades por las que se puede filtrar el panel (null = todas). */
private val QUALITY_FILTERS = listOf(null to "Todas", Quality.UHD_2160 to "4K", Quality.FHD_1080 to "1080p", Quality.HD_720 to "720p")

/**
 * Panel lateral de fuentes. Arriba, el resumen (cuántas, cuántas al instante y hasta qué calidad) y los filtros
 * (español, solo en caché, calidad); luego la recomendada, lista para darle; y cada buscador como una tarjeta
 * desplegable con sus fuentes, cada una con sus etiquetas (HDR, audio, tamaño, seeds, castellano).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourcesPanel(
    sources: Load<SourcesState>?,
    subtitle: String?,
    hint: String,
    trying: String?,
    broken: BrokenSources,
    spanishOnly: Boolean,
    onSpanishOnly: (Boolean) -> Unit,
    onPlay: (DesktopSource) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var cachedOnly by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf<Quality?>(null) }
    // Cada buscador es un desplegable; de primeras, todos cerrados (como en la tele).
    val open = remember(subtitle) { mutableStateMapOf<String, Boolean>() }
    val state = (sources as? Load.Ok)?.value
    val all = state?.sources.orEmpty().filter { !broken.isBroken(it.title) }
    val visible = all.filter { s ->
        (!spanishOnly || s.spanish) && (!cachedOnly || s.cached) && (quality == null || s.quality == quality)
    }
    Column(
        modifier.shadow(40.dp, BambiShapes.Panel).clip(BambiShapes.Panel)
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1A20), BambiColors.BackgroundRaised), endY = 420f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), BambiShapes.Panel)
            .padding(horizontal = 26.dp, vertical = 24.dp),
    ) {
        // Cabecera: título, de qué es y cerrar.
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Fuentes", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                subtitle?.let {
                    Text(it, color = BambiColors.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            }
            CloseButton(onClose)
        }
        if (state != null) {
            Spacer(Modifier.height(14.dp))
            Summary(all, state)
        }
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TogglePill("Solo en español", spanishOnly, accent = BambiColors.Accent) { onSpanishOnly(!spanishOnly) }
            TogglePill("Al instante", cachedOnly, icon = AppIcons.Bolt, accent = BambiColors.Cached) { cachedOnly = !cachedOnly }
            Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = 0.1f)).align(Alignment.CenterVertically))
            QualityFilter(quality, all) { quality = it }
        }
        trying?.let {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(BambiShapes.Control).background(BambiColors.Accent.copy(alpha = 0.14f))
                    .border(1.dp, BambiColors.Accent.copy(alpha = 0.35f), BambiShapes.Control).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(color = BambiColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text(it, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(16.dp))
        when (sources) {
            null -> Text(hint, color = BambiColors.Muted)
            Load.Loading -> Searching("Buscando fuentes…")
            is Load.Failed -> Text(sources.message, color = BambiColors.Text)
            is Load.Ok -> {
                val s = sources.value
                // Una sección por buscador («En castellano» primero); si solo hay uno, se abre solo.
                val sections = visible.groupBy { it.section }.toList().sortedByDescending { (name, _) -> name == "En castellano" }
                val single = sections.size == 1
                val best = visible.firstOrNull()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                    if (best != null) item(key = "best") { BestSource(best) { onPlay(best) } }
                    sections.forEach { (name, list) ->
                        item(key = "g-$name") {
                            val expanded = open[name] ?: single
                            SourceGroup(name.ifBlank { "Fuentes" }, list, expanded, onToggle = { open[name] = !expanded }) { source ->
                                // Clic derecho: descartar una fuente rota (se restaura en Ajustes).
                                androidx.compose.foundation.ContextMenuArea(items = {
                                    listOf(androidx.compose.foundation.ContextMenuItem("Descartar fuente (no funciona)") { broken.discard(source.title) })
                                }) { SourceRow(source) { onPlay(source) } }
                            }
                        }
                    }
                    if (s.pending.isNotEmpty()) item(key = "pending") { Searching("Buscando en " + s.pending.sorted().joinToString(", ") + "…") }
                    if (s.unavailable.isNotEmpty()) item(key = "unavailable") { Unavailable(s.unavailable) }
                    if (visible.isEmpty() && s.pending.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                when {
                                    all.isEmpty() -> "No hay fuentes para esto ahora mismo."
                                    spanishOnly -> "No hay fuentes en español con estos filtros."
                                    else -> "Ninguna fuente con estos filtros."
                                },
                                color = BambiColors.Muted,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (hovered) Color.White else BambiColors.Glass)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) { Icon(AppIcons.Close, contentDescription = "Cerrar", tint = if (hovered) Color.Black else Color.White, modifier = Modifier.size(20.dp)) }
}

/** «74 fuentes · 44 al instante · hasta 4K». */
@Composable
private fun Summary(all: List<DesktopSource>, state: SourcesState) {
    val cached = all.count { it.cached }
    val best = all.map { it.quality }.filter { it.rank > 0 }.maxByOrNull { it.rank }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Stat("${all.size}", if (all.size == 1) "fuente" else "fuentes", Color.White)
        if (cached > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Bolt, contentDescription = null, tint = BambiColors.Cached, modifier = Modifier.size(16.dp))
                Stat("$cached", "al instante", BambiColors.Cached)
            }
        }
        best?.let { Stat("hasta", it.label, if (it == Quality.UHD_2160) BambiColors.Gold else Color.White, valueFirst = false) }
        if (state.pending.isNotEmpty()) {
            CircularProgressIndicator(color = BambiColors.Muted, strokeWidth = 1.5.dp, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color, valueFirst: Boolean = true) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (valueFirst) {
            Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(label, color = BambiColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 1.dp))
        } else {
            Text(value, color = BambiColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 1.dp))
            Text(label, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TogglePill(label: String, on: Boolean, icon: ImageVector? = null, accent: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.clip(BambiShapes.Pill)
            .background(if (on) accent.copy(alpha = 0.18f) else if (hovered) BambiColors.SurfaceHigh else Color.Transparent)
            .border(1.dp, if (on) accent else Color.White.copy(alpha = 0.22f), BambiShapes.Pill)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (on) Icon(AppIcons.Check, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
        else if (icon != null) Icon(icon, contentDescription = null, tint = BambiColors.Muted, modifier = Modifier.size(15.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
    }
}

/** Todas · 4K · 1080p · 720p (solo las que hay), como un selector segmentado. */
@Composable
private fun QualityFilter(selected: Quality?, all: List<DesktopSource>, onSelect: (Quality?) -> Unit) {
    val present = all.map { it.quality }.toSet()
    Row(
        Modifier.clip(BambiShapes.Pill).background(Color.White.copy(alpha = 0.06f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        QUALITY_FILTERS.filter { (q, _) -> q == null || q in present }.forEach { (q, label) ->
            val on = q == selected
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Box(
                Modifier.clip(BambiShapes.Pill)
                    .background(if (on) Color.White else if (hovered) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) { onSelect(q) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    label,
                    color = if (on) Color.Black else if (q == Quality.UHD_2160) BambiColors.Gold else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** La que elegiría «Reproducir» con estos filtros, destacada y lista para darle. */
@Composable
private fun BestSource(source: DesktopSource, onPlay: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered = interaction.collectIsHoveredAsState().value || interaction.collectIsFocusedAsState().value
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(BambiColors.Accent.copy(alpha = if (hovered) 0.30f else 0.20f), Color(0xFF1C1C22))))
            .border(1.dp, BambiColors.Accent.copy(alpha = if (hovered) 0.9f else 0.5f), shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onPlay)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RECOMENDADA", color = BambiColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            Spacer(Modifier.width(8.dp))
            Text(source.section, color = BambiColors.Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            QualityBadge(source.quality, large = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(source.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Tags(source)
            }
            Row(
                Modifier.clip(BambiShapes.Control).background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Play, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reproducir", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Un buscador: cabecera con su color, cuántas hay, cuántas al instante y la mejor calidad; al abrirlo, sus fuentes. */
@Composable
private fun SourceGroup(
    title: String,
    list: List<DesktopSource>,
    expanded: Boolean,
    onToggle: () -> Unit,
    row: @Composable (DesktopSource) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered = interaction.collectIsHoveredAsState().value || interaction.collectIsFocusedAsState().value
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f)
    val cached = list.count { it.cached }
    val best = list.map { it.quality }.filter { it.rank > 0 }.maxByOrNull { it.rank }
    val color = providerColor(title)
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (expanded) Color(0xFF18181E) else BambiColors.Surface)
            .border(1.dp, if (expanded || hovered) Color.White.copy(alpha = 0.14f) else BambiColors.Stroke, shape)
            .animateContentSize(),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(if (hovered) Color.White.copy(alpha = 0.04f) else Color.Transparent)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.18f))
                    .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(title.take(1).uppercase(), color = color, fontSize = 16.sp, fontWeight = FontWeight.Black) }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${list.size} ${if (list.size == 1) "fuente" else "fuentes"}", color = BambiColors.Muted, fontSize = 12.sp)
            }
            if (cached > 0) {
                Row(
                    Modifier.clip(BambiShapes.Pill).background(BambiColors.Cached.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Bolt, contentDescription = null, tint = BambiColors.Cached, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("$cached", color = BambiColors.Cached, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            best?.let { QualityBadge(it) }
            Icon(AppIcons.ExpandMore, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation })
        }
        if (expanded) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(BambiColors.Stroke))
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                list.forEach { row(it) }
            }
        }
    }
}

@Composable
private fun SourceRow(source: DesktopSource, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered = interaction.collectIsHoveredAsState().value || interaction.collectIsFocusedAsState().value
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.07f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QualityBadge(source.quality)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(source.title, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
            Tags(source)
        }
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
            if (hovered) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Play, contentDescription = "Reproducir", tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            } else if (source.cached) {
                Icon(AppIcons.Bolt, contentDescription = "Al instante", tint = BambiColors.Cached, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Calidad como etiqueta: 4K en dorado (premium), el resto discreto. */
@Composable
private fun QualityBadge(quality: Quality, large: Boolean = false) {
    val uhd = quality == Quality.UHD_2160
    val shape = RoundedCornerShape(if (large) 9.dp else 7.dp)
    Box(
        Modifier.width(if (large) 64.dp else 52.dp).height(if (large) 40.dp else 28.dp).clip(shape)
            .background(
                if (uhd) Brush.linearGradient(listOf(Color(0xFF3A2E10), Color(0xFF241D0B)))
                else Brush.linearGradient(listOf(Color(0xFF2A2A32), Color(0xFF202027))),
            )
            .border(1.dp, if (uhd) BambiColors.Gold.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.1f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            quality.label.takeIf { it != "?" } ?: "—",
            color = if (uhd) BambiColors.Gold else Color.White,
            fontSize = if (large) 16.sp else 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/** Etiquetas de una fuente: imagen (Dolby Vision, HDR), sonido, castellano, tamaño y seeds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(source: DesktopSource) {
    val tags = sourceTags(source)
    if (tags.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        tags.forEach { (label, kind) ->
            val color = when (kind) {
                TagKind.VIDEO -> Color(0xFFE7C66B)
                TagKind.AUDIO -> Color(0xFF7FB8FF)
                TagKind.SPANISH -> Color(0xFFFF7A7A)
                TagKind.PLAIN -> Color.White.copy(alpha = 0.7f)
            }
            Text(
                label,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(5.dp))
                    .background(color.copy(alpha = if (kind == TagKind.PLAIN) 0.06f else 0.11f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

internal enum class TagKind { VIDEO, AUDIO, SPANISH, PLAIN }

/**
 * Etiquetas de una fuente, sin repetir: lo de imagen y sonido (lo mismo que enseña el reproductor al empezar), si es
 * en castellano, y lo que dice el buscador (tamaño, seeds, idiomas).
 */
internal fun sourceTags(source: DesktopSource): List<Pair<String, TagKind>> {
    val badges = sourceBadges(source).filter { it != source.quality.label }
    val video = setOf("Dolby Vision", "HDR10", "HDR10+", "HLG", "HDR")
    val out = ArrayList<Pair<String, TagKind>>()
    badges.forEach { out += it to if (it in video) TagKind.VIDEO else TagKind.AUDIO }
    if (source.spanish) out += "Castellano" to TagKind.SPANISH
    val seen = out.map { it.first.lowercase() }.toMutableSet()
    source.detail.split(" · ", "\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
        val key = token.lowercase()
        if (key in seen || key == "dolby vision" || key.startsWith("hdr")) return@forEach
        seen += key
        out += token to TagKind.PLAIN
    }
    return out
}

/** Un color fijo por buscador (Torrentio siempre igual), para reconocerlos de un vistazo. */
private fun providerColor(name: String): Color {
    val palette = listOf(
        Color(0xFF5AA9FF), Color(0xFF46D369), Color(0xFFB18CFF), Color(0xFFFF9F43),
        Color(0xFFFF6B8A), Color(0xFF2ED3C6), Color(0xFFF5C451),
    )
    if (name == "En castellano") return Color(0xFFFF5A5F)
    return palette[Math.floorMod(name.lowercase().hashCode(), palette.size)]
}

/** «Buscando en Comet…» con su ruedita. */
@Composable
private fun Searching(text: String) {
    Row(Modifier.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(color = BambiColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        Text(text, color = BambiColors.Muted, fontSize = 13.sp)
    }
}

/** Los buscadores que no han dado nada, en una línea discreta al final. */
@Composable
private fun Unavailable(unavailable: Map<String, String>) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        unavailable.forEach { (name, why) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
                Spacer(Modifier.width(8.dp))
                Text(name, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(why, color = BambiColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
