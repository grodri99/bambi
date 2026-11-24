package com.shieldstream.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType

/**
 * Ficha de una persona del reparto (clic en su foto en una ficha, como en la tele): foto, a qué se dedica, cuándo y
 * dónde nació, la biografía y todo en lo que ha salido, lo más conocido primero, con el filtro Todo · Películas · Series.
 */
@Composable
fun PersonScreen(repository: DesktopRepository, cast: CastMember, onOpen: (CatalogItem) -> Unit) {
    val id = cast.id
    val person by produceState<Load<PersonDetails?>>(Load.Loading, id) {
        value = if (id == null) Load.Ok(null) else load { repository.person(id) }
    }
    var filter by remember(id) { mutableStateOf(Catalog.ALL) }
    var fullBio by remember(id) { mutableStateOf(false) }
    val p = (person as? Load.Ok)?.value
    val shown = p?.credits.orEmpty().filter {
        when (filter) {
            Catalog.ALL -> true
            Catalog.MOVIES -> it.type == MediaType.MOVIE
            Catalog.SHOWS -> it.type == MediaType.TV
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(158.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
        contentPadding = PaddingValues(top = 104.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(168.dp).clip(CircleShape).background(BambiColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                    Text(cast.name.take(1), color = BambiColors.Muted, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    RemoteImage(p?.photoUrl ?: cast.photoUrl, Modifier.fillMaxSize())
                }
                Column(Modifier.widthIn(max = 860.dp)) {
                    Text(p?.name ?: cast.name, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black)
                    val facts = listOfNotNull(
                        p?.knownFor,
                        p?.birthday?.let { "Nacimiento: " + formatDate(it) },
                        p?.placeOfBirth,
                    )
                    if (facts.isNotEmpty()) {
                        Text(facts.joinToString("  ·  "), color = BambiColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    cast.role?.let { Text("En esta ficha: $it", color = BambiColors.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
                    when (val state = person) {
                        Load.Loading -> Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = BambiColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Text("Cargando su filmografía…", color = BambiColors.Muted)
                        }
                        is Load.Failed -> Text(state.message, color = BambiColors.Text, modifier = Modifier.padding(top = 18.dp))
                        is Load.Ok -> p?.biography?.takeIf { it.isNotBlank() }?.let { bio ->
                            Text(
                                bio,
                                color = BambiColors.Text.copy(alpha = 0.9f),
                                fontSize = 15.sp,
                                lineHeight = 23.sp,
                                maxLines = if (fullBio) Int.MAX_VALUE else 5,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 14.dp),
                            )
                            if (bio.length > 420) {
                                Text(
                                    if (fullBio) "Ver menos" else "Leer más",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(top = 6.dp).clickable { fullBio = !fullBio },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (p != null && p.credits.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Filmografía", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(8.dp))
                    for ((value, label) in listOf(Catalog.ALL to "Todo", Catalog.MOVIES to "Películas", Catalog.SHOWS to "Series")) {
                        FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
                    }
                }
            }
            items(shown, key = { it.key }) { item -> PosterCard(item, onClick = { onOpen(item) }) }
        } else if (person is Load.Ok) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("TMDB no tiene su filmografía.", color = BambiColors.Muted, modifier = Modifier.padding(top = 12.dp))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.fillMaxWidth().height(1.dp)) }
    }
}
