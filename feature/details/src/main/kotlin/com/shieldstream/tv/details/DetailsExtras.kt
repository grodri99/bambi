package com.shieldstream.tv.details

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Person
import com.shieldstream.tv.ui.component.PosterCard
import com.shieldstream.tv.ui.component.ProvidePivotScroll
import com.shieldstream.tv.ui.component.tvClickable

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = modifier.padding(start = 56.dp, bottom = 4.dp),
    )
}

/**
 * Reparto y equipo: tarjetas verticales con la foto a sangre, el nombre sobre un degradado y el personaje debajo.
 * Dirección/creación lleva una etiqueta para distinguirla del reparto.
 */
@Composable
internal fun PeopleRow(
    people: List<Person>,
    enabled: Boolean,
    onPerson: (Person) -> Unit,
    crewIds: Set<Int> = emptySet(),
    crewLabel: String = "",
) {
    ProvidePivotScroll(leading = 56.dp) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(people, key = { it.id }) { person ->
                PersonCard(
                    person = person,
                    enabled = enabled,
                    badge = crewLabel.takeIf { person.id in crewIds },
                    onClick = { onPerson(person) },
                )
            }
        }
    }
}

@Composable
internal fun PersonCard(person: Person, enabled: Boolean, onClick: () -> Unit, badge: String? = null) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(PersonCardWidth)
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                val s = if (focused) 1.06f else 1f
                scaleX = s
                scaleY = s
            }
            .tvClickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(PersonCardWidth)
                .height(PersonPhotoHeight)
                // El borde va como fondo de un recuadro algo mayor (en la Shield el recorte de capas escaladas falla).
                .background(if (focused) Color.White else Color.White.copy(alpha = 0.10f), PersonCardShape)
                .padding(if (focused) 3.dp else 1.dp)
                .clip(PersonCardShape)
                .background(PersonPlaceholder),
        ) {
            if (person.photoUrl != null) {
                AsyncImage(
                    model = person.photoUrl,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Text(
                    text = initials(person.name),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
            )
            if (badge != null) {
                Text(
                    text = badge.uppercase(),
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(
                text = person.name,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = person.role.orEmpty(),
            color = Color.White.copy(alpha = if (focused) 0.85f else 0.55f),
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** Fila de carátulas (saga, filmografía). */
@Composable
internal fun PosterStrip(
    items: List<CatalogItem>,
    onItem: (CatalogItem) -> Unit,
    highlightId: String? = null,
    modifier: Modifier = Modifier,
) {
    ProvidePivotScroll(leading = 56.dp) {
        LazyRow(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items, key = { it.key }) { item ->
                Column {
                    PosterCard(item = item, onClick = { onItem(item) })
                    if (item.id == highlightId) {
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .width(36.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                    }
                }
            }
        }
    }
}

internal fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

private val PersonCardWidth = 118.dp
private val PersonPhotoHeight = 160.dp
private val PersonCardShape = RoundedCornerShape(10.dp)
private val PersonPlaceholder = Color(0xFF2A2D35)
