package com.shieldstream.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/** Un episodio de una serie que sigues, con su fecha de emisión. */
data class ReleaseEpisode(val show: CatalogItem, val season: Int, val number: Int, val title: String, val airDate: LocalDate) {
    val key get() = "${show.id}:$season:$number"
}

data class Releases(val recent: List<ReleaseEpisode>, val upcoming: List<ReleaseEpisode>)

/**
 * Centro de estrenos, como «Episodios» en la tele: de las series que sigues (empezadas o en Mi lista), los capítulos
 * de los últimos 30 días que aún no has visto y los que salen en los próximos 30.
 */
object ReleaseCenter {
    const val WINDOW_DAYS = 30L

    /** Series que sigue el perfil: con algo visto o en Mi lista. */
    fun followed(library: LibraryStore): List<CatalogItem> =
        (library.progress.map { it.item } + library.myList).filter { it.type == MediaType.TV.name && it.id.toIntOrNull() != null }
            .distinctBy { it.id }.map { it.toItem() }

    suspend fun load(library: LibraryStore, repository: DesktopRepository, today: LocalDate = LocalDate.now()): Releases {
        val episodes = followed(library).flatMap { show -> runCatching { repository.airingEpisodes(show) }.getOrDefault(emptyList()) }
        return split(episodes, library, today)
    }

    /** Los que siguen sin ver ahora mismo (lo visto desde que se cargaron ya no cuenta). */
    fun pending(episodes: List<ReleaseEpisode>, library: LibraryStore): List<ReleaseEpisode> =
        episodes.filter { library.progressFor(it.show, it.season, it.number)?.finished != true }

    fun split(episodes: List<ReleaseEpisode>, library: LibraryStore, today: LocalDate): Releases {
        val from = today.minusDays(WINDOW_DAYS)
        val to = today.plusDays(WINDOW_DAYS)
        val recent = episodes.filter { !it.airDate.isAfter(today) && !it.airDate.isBefore(from) }
            .filter { library.progressFor(it.show, it.season, it.number)?.finished != true }
            .sortedByDescending { it.airDate }
        val upcoming = episodes.filter { it.airDate.isAfter(today) && !it.airDate.isAfter(to) }.sortedBy { it.airDate }
        return Releases(recent, upcoming)
    }
}

/** Avisos ya dados (para no repetir la notificación de un mismo capítulo). */
class SeenReleases(private val file: File = File(ConfigStore.defaultFile().parentFile, "estrenos-avisados.json")) {
    private val json = Json { prettyPrint = true }

    @Synchronized
    fun unseen(keys: Collection<String>): List<String> {
        val seen = load()
        return keys.filter { it !in seen }
    }

    @Synchronized
    fun markSeen(keys: Collection<String>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(ListSerializer(String.serializer()), (load() + keys).toList().takeLast(2000)))
    }

    private fun load(): Set<String> = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), file.readText()).toSet()
    }.getOrDefault(emptySet())
}

/** Aviso de Windows (globo de la bandeja), el equivalente al canal de la tele. */
object Notifier {
    private var tray: java.awt.TrayIcon? = null

    fun notify(title: String, text: String) {
        runCatching {
            if (!java.awt.SystemTray.isSupported()) return
            val icon = tray ?: java.awt.TrayIcon(
                BundledImages.awtImage("bambi_logo.png") ?: java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                "Bambi",
            ).also {
                it.isImageAutoSize = true
                java.awt.SystemTray.getSystemTray().add(it)
                tray = it
            }
            icon.displayMessage(title, text, java.awt.TrayIcon.MessageType.INFO)
        }
    }
}

@Composable
fun ReleasesScreen(library: LibraryStore, repository: DesktopRepository, onOpen: (CatalogItem) -> Unit, trakt: TraktManager? = null) {
    val loaded by produceState<Releases?>(null, library) { value = ReleaseCenter.load(library, repository) }
    // Lo que se marca como visto (aquí o en la ficha) desaparece al momento y el número baja con ello.
    val releases = loaded?.let { it.copy(recent = ReleaseCenter.pending(it.recent, library)) }
    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 88.dp)) {
        Text("Estrenos", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Capítulos de las series que sigues: los de los últimos 30 días que no has visto y los que llegan.", color = BambiColors.Muted, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        val r = releases
        if (r == null) {
            CircularProgressIndicator(color = BambiColors.Accent)
            return
        }
        if (r.recent.isEmpty() && r.upcoming.isEmpty()) {
            Text("Nada nuevo. Las series que empieces o añadas a Mi lista aparecerán aquí.", color = BambiColors.Muted)
            return
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (r.recent.isNotEmpty()) item { Header("Nuevos para ti · ${r.recent.size}") }
            items(r.recent, key = { "r" + it.key }) { e ->
                EpisodeLine(e, new = true, onMarkWatched = {
                    setWatched(library, trakt, e.show, listOf(e.season to e.number), true)
                }) { onOpen(e.show) }
            }
            if (r.upcoming.isNotEmpty()) item { Header("Próximamente") }
            items(r.upcoming, key = { "u" + it.key }) { EpisodeLine(it, new = false) { onOpen(it.show) } }
        }
    }
}

@Composable
private fun Header(text: String) = Text(text, color = BambiColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))

@Composable
private fun EpisodeLine(e: ReleaseEpisode, new: Boolean, onMarkWatched: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(BambiColors.Surface, BambiShapes.Control).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (new) androidx.compose.foundation.layout.Box(Modifier.width(10.dp).height(10.dp).background(BambiColors.Accent, androidx.compose.foundation.shape.CircleShape))
        Column(Modifier.weight(1f)) {
            Text(e.show.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("T${e.season}:E${e.number} · ${e.title}", color = BambiColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatDate(e.airDate.toString()), color = BambiColors.Muted, fontSize = 13.sp)
        onMarkWatched?.let { mark -> androidx.compose.material3.TextButton(onClick = mark) { Text("Marcar visto", color = BambiColors.Muted) } }
        androidx.compose.material3.TextButton(onClick = onClick) { Text("Ver ficha", color = BambiColors.Accent) }
    }
}
