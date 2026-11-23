package com.shieldstream.tv.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Fuentes rotas que el usuario ha descartado (por nombre de la release, como en la tele): no vuelven a salir en
 * ninguna ficha hasta que se restauran en Ajustes.
 */
class BrokenSources(private val file: File = File(ConfigStore.defaultFile().parentFile, "broken-sources.json")) {
    private val json = Json { prettyPrint = true }
    var titles by mutableStateOf(load())
        private set

    fun isBroken(title: String) = title in titles

    fun discard(title: String) = write(titles + title)

    fun restore(title: String) = write(titles - title)

    private fun write(value: Set<String>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(ListSerializer(String.serializer()), value.sorted()))
        titles = value
    }

    private fun load(): Set<String> = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), file.readText()).toSet()
    }.getOrDefault(emptySet())

    companion object {
        val default by lazy { BrokenSources() }
    }
}

/** Ajustes → Fuentes descartadas: para devolverlas a las fichas. */
@Composable
fun BrokenSourcesSection(broken: BrokenSources) {
    Section("Fuentes descartadas", "Las que marcaste como rotas (clic derecho en una fuente). Restáuralas si vuelven a funcionar.")
    if (broken.titles.isEmpty()) {
        Text("Ninguna.", color = BambiColors.Muted, fontSize = 14.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        broken.titles.sorted().forEach { title ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = BambiColors.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton(onClick = { broken.restore(title) }) { Text("Restaurar", color = BambiColors.Accent) }
            }
        }
    }
}

/** Resultado de buscar una fuente que se pueda reproducir. */
sealed interface Pick {
    data class Ready(val source: DesktopSource, val rest: List<DesktopSource>) : Pick
    data class Failed(val message: String) : Pick
}

/**
 * «Reproducir» como en la tele: prueba las fuentes en orden hasta que una dé vídeo (salta las que no están en caché,
 * las bloqueadas o las que no son vídeo) y para si el servicio pide calma o la clave falla. [rest] son las que quedan,
 * por si el reproductor tampoco puede con la elegida.
 */
suspend fun pickPlayable(
    candidates: List<DesktopSource>,
    service: String,
    resolver: LinkResolver = LinkResolver(),
    maxTries: Int = MAX_TRIES,
    onTrying: (DesktopSource) -> Unit = {},
): Pick = withContext(Dispatchers.IO) {
    var last = "No hay fuentes que se puedan reproducir ahora mismo."
    candidates.take(maxTries).forEachIndexed { index, source ->
        onTrying(source)
        when (val check = resolver.resolve(source.url, service)) {
            is LinkCheck.Ready -> return@withContext Pick.Ready(source.copy(url = check.url), candidates.drop(index + 1))
            is LinkCheck.Problem -> {
                last = check.message
                if (check.stop) return@withContext Pick.Failed(check.message)
            }
        }
    }
    Pick.Failed(last)
}

private const val MAX_TRIES = 8
