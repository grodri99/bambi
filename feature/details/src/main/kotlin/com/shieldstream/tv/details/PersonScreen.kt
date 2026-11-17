package com.shieldstream.tv.details

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.PersonDetails
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.component.ProvideMinimalScroll
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PersonUiState {
    data object Loading : PersonUiState
    data object Error : PersonUiState
    data class Ready(val details: PersonDetails) : PersonUiState
}

@HiltViewModel
class PersonViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val id: Int = savedState.get<String>("id")?.toIntOrNull() ?: 0
    private val _state = MutableStateFlow<PersonUiState>(PersonUiState.Loading)
    val state: StateFlow<PersonUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = runCatching { catalog.getPerson(id) }
                .map { PersonUiState.Ready(it) }
                .getOrDefault(PersonUiState.Error)
        }
    }
}

/** Ficha de actor/director: foto, biografía y sus títulos (películas y series) más conocidos. */
@Composable
fun PersonScreen(
    onOpenItem: (CatalogItem) -> Unit,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            PersonUiState.Loading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            PersonUiState.Error -> Text(
                localizedStringResource(StringsR.string.person_error),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            is PersonUiState.Ready -> PersonContent(s.details, onOpenItem)
        }
    }
}

@Composable
private fun PersonContent(details: PersonDetails, onOpenItem: (CatalogItem) -> Unit) {
    val backdrop = details.knownFor.firstNotNullOfOrNull { it.backdropUrl }
    if (backdrop != null) {
        AsyncImage(
            model = backdrop,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.35f,
            modifier = Modifier.fillMaxSize(),
        )
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.55f to Color.Black.copy(alpha = 0.9f), 1f to Color.Black),
        ),
    )
    val firstRow = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstRow.requestFocus() }
    }
    ProvideMinimalScroll(margin = 72.dp) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 56.dp, bottom = 56.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 56.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(150.dp)
                        .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                ) {
                    val photo = details.person.photoUrl
                    if (photo != null) {
                        AsyncImage(photo, details.person.name, contentScale = ContentScale.Crop, modifier = Modifier.size(150.dp))
                    } else {
                        Text(initials(details.person.name), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(32.dp))
                Column(Modifier.widthIn(max = 900.dp)) {
                    Text(
                        details.person.name,
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(
                        details.department?.let { departmentLabel(it) },
                        details.birthday?.take(4),
                        details.placeOfBirth,
                    ).joinToString("  ·  ")
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(meta, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (details.biography.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            details.biography,
                            color = Color.White.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            val movies = details.knownFor.filter { it.type == MediaType.MOVIE }
            val shows = details.knownFor.filter { it.type == MediaType.TV }
            if (movies.isNotEmpty()) {
                SectionTitle(localizedStringResource(StringsR.string.person_movies))
                PosterStrip(movies, onOpenItem, modifier = Modifier.focusRequester(firstRow))
                Spacer(Modifier.height(16.dp))
            }
            if (shows.isNotEmpty()) {
                SectionTitle(localizedStringResource(StringsR.string.person_shows))
                PosterStrip(shows, onOpenItem, modifier = if (movies.isEmpty()) Modifier.focusRequester(firstRow) else Modifier)
            }
            if (movies.isEmpty() && shows.isEmpty()) {
                Text(
                    localizedStringResource(StringsR.string.person_no_titles),
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 56.dp),
                )
            }
        }
    }
}

@Composable
private fun departmentLabel(department: String): String = when (department) {
    "Acting" -> localizedStringResource(StringsR.string.person_dept_acting)
    "Directing" -> localizedStringResource(StringsR.string.person_dept_directing)
    "Writing" -> localizedStringResource(StringsR.string.person_dept_writing)
    "Production" -> localizedStringResource(StringsR.string.person_dept_production)
    else -> department
}
