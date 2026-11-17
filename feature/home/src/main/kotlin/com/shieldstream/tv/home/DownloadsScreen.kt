package com.shieldstream.tv.home

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.shieldstream.tv.domain.DownloadsRepository
import com.shieldstream.tv.domain.SelectedContentHolder
import com.shieldstream.tv.model.Download
import com.shieldstream.tv.model.DownloadStatus
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.PlaybackMedia
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.component.ActionMenuDialog
import com.shieldstream.tv.ui.component.ActionMenuItem
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.LineIcons
import com.shieldstream.tv.ui.component.requestFocusWithRetry
import com.shieldstream.tv.ui.component.tvClickable
import com.shieldstream.tv.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadsRepository,
    private val holder: SelectedContentHolder,
    private val texts: Texts,
) : ViewModel() {
    val downloads: StateFlow<List<Download>> = repository.downloads

    /**
     * Reproduce el fichero descargado directamente (no hay nada que resolver: es un fichero local), con la misma
     * clave de progreso que desde la ficha para que "Seguir viendo" y Trakt sigan funcionando.
     */
    fun play(download: Download): Boolean {
        val path = repository.localFileFor(download.id) ?: return false
        val source = StreamSource(
            id = "LOCAL:${download.id}",
            title = texts.get(StringsR.string.downloads_local_source),
            infoHash = "",
            fileIdx = null,
            magnet = "",
            quality = Quality.UNKNOWN,
            hdrType = HdrType.SDR,
            sizeBytes = File(path).length(),
            seeders = null,
            isCached = true,
            directUrl = Uri.fromFile(File(path)).toString(),
            provider = texts.get(StringsR.string.downloads_title),
        )
        holder.playQueue(
            sources = listOf(source),
            label = listOfNotNull(download.title, download.episodeLabel).joinToString(" · "),
            progressKey = download.id,
            media = PlaybackMedia(
                type = download.type,
                imdbId = download.imdbId,
                title = download.title,
                originalTitle = download.originalTitle,
                year = download.year,
                season = download.season,
                episode = download.episode,
            ),
        )
        return true
    }

    fun pause(id: String) = repository.pause(id)
    fun resume(id: String) = repository.resume(id)
    fun delete(id: String) = repository.delete(id)
}

/** Pantalla completa de Descargas (se abre desde el indicador de arriba a la derecha). */
@Composable
fun DownloadsRoute(onPlay: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(top = 36.dp),
    ) {
        Text(
            localizedStringResource(StringsR.string.downloads_title),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 56.dp, bottom = 8.dp),
        )
        Box(Modifier.weight(1f)) { DownloadsScreen(onPlay = onPlay) }
    }
}

@Composable
fun DownloadsScreen(
    onPlay: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf<Download?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(downloads.isNotEmpty()) { if (downloads.isNotEmpty()) first.requestFocusWithRetry() }

    if (downloads.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                localizedStringResource(StringsR.string.downloads_empty),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.widthIn(max = 520.dp),
            )
        }
    } else {
        val sorted = remember(downloads) { downloads.sortedWith(compareBy<Download> { !it.isActive }.thenByDescending { it.createdAt }) }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(sorted, key = { it.id }) { d ->
                DownloadRow(
                    download = d,
                    onClick = {
                        if (d.isDone) {
                            if (viewModel.play(d)) onPlay()
                        } else {
                            menu = d
                        }
                    },
                    onLongClick = { menu = d },
                    modifier = if (d.id == sorted.first().id) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }

    menu?.let { d ->
        val actions = buildList {
            add(ActionMenuItem(localizedStringResource(StringsR.string.downloads_cancel), LineIcons.Close) { menu = null })
            if (d.isDone) {
                add(ActionMenuItem(localizedStringResource(StringsR.string.downloads_play), AppIcons.Play) {
                    menu = null
                    if (viewModel.play(d)) onPlay()
                })
            }
            if (d.isActive) {
                add(ActionMenuItem(localizedStringResource(StringsR.string.downloads_pause), AppIcons.Pause) {
                    viewModel.pause(d.id)
                    menu = null
                })
            }
            if (d.status == DownloadStatus.PAUSED || d.status == DownloadStatus.FAILED) {
                add(ActionMenuItem(localizedStringResource(StringsR.string.downloads_resume), LineIcons.Download) {
                    viewModel.resume(d.id)
                    menu = null
                })
            }
            add(ActionMenuItem(localizedStringResource(StringsR.string.downloads_delete), LineIcons.Delete) {
                viewModel.delete(d.id)
                menu = null
            })
        }
        ActionMenuDialog(
            title = listOfNotNull(d.title, d.episodeLabel).joinToString(" · "),
            actions = actions,
            onDismiss = { menu = null },
            consumeOpeningKeyRelease = true,
        )
    }
}

@Composable
private fun DownloadRow(download: Download, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) Color.Black else Color.White
    val sub = if (focused) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.6f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
            .tvClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
    ) {
        AsyncImage(
            model = download.backdropUrl ?: download.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(160.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                download.title,
                color = fg,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            download.episodeLabel?.let {
                Text(it, color = sub, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            val status = when (download.status) {
                DownloadStatus.DONE -> localizedStringResource(StringsR.string.downloads_downloaded)
                DownloadStatus.RUNNING -> localizedStringResource(StringsR.string.downloads_downloading, (download.progress * 100).toInt())
                DownloadStatus.QUEUED -> localizedStringResource(StringsR.string.downloads_queued)
                DownloadStatus.PAUSED -> localizedStringResource(StringsR.string.downloads_paused)
                DownloadStatus.FAILED -> localizedStringResource(StringsR.string.downloads_failed) + (download.error?.let { " · $it" } ?: "")
            }
            val size = if (download.isDone) {
                gb(download.totalBytes.coerceAtLeast(download.downloadedBytes))
            } else if (download.totalBytes > 0) {
                localizedStringResource(StringsR.string.downloads_size, gb(download.downloadedBytes), gb(download.totalBytes))
            } else {
                ""
            }
            Text(
                listOf(status, size).filter { it.isNotBlank() }.joinToString("  ·  "),
                color = if (download.status == DownloadStatus.FAILED && !focused) Color(0xFFFF8A80) else sub,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!download.isDone) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (focused) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(download.progress)
                            .height(4.dp)
                            .background(if (focused) Color.Black else AppColors.Accent),
                    )
                }
            }
        }
        if (download.isDone) {
            Spacer(Modifier.width(12.dp))
            androidx.compose.material3.Icon(AppIcons.Play, contentDescription = null, tint = fg, modifier = Modifier.size(28.dp))
        }
    }
}

private fun gb(bytes: Long): String = String.format(Locale.getDefault(), "%.1f GB", bytes / 1_073_741_824.0)
