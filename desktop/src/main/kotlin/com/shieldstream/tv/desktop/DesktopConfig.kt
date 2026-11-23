package com.shieldstream.tv.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shieldstream.tv.domain.DebridService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Ajustes de la app de escritorio: claves y servicios, como en Ajustes de la Shield. */
@Serializable
data class DesktopConfig(
    val tmdbKey: String = "",
    val debrid: String = DebridService.REAL_DEBRID.name,
    val debridKey: String = "",
    val mpvPath: String = "mpv",
    val spanishFirst: Boolean = true,
    /** Addons de Stremio (URL del manifest), como en Ajustes → Addons de la tele. */
    val addons: String = "",
    /** Aplicación de Trakt propia (trakt.tv/oauth/applications): Client ID y Client Secret. */
    val traktClientId: String = "",
    val traktClientSecret: String = "",
    /** Reparto y equipo en las fichas (en la tele, Ajustes → «Reparto y equipo»). */
    val showCast: Boolean = true,
    /** «No cambiar de fuente si la elegida falla» (como en la tele): al elegir una a mano, solo esa. */
    val keepChosenSource: Boolean = false,
    /**
     * Ocultar del Inicio las películas que aún no han salido en digital en España (en cines o recién estrenadas:
     * casi nunca tienen castellano todavía).
     */
    val hideUnreleased: Boolean = false,
    /** Si el audio no es español y no hay subtítulos en español, buscarlos en internet y ponerlos. */
    val autoSubtitles: Boolean = false,
    /** (Antiguo: el tamaño va ahora en [subtitleStyle].) */
    val subtitleScale: Float = 1f,
    /** Cómo se ven los subtítulos (editor del reproductor, como en la tele). */
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    /** Siguiente episodio solo en los créditos (desactivable, como en la tele). */
    val autoNext: Boolean = true,
    /** Sincronizar el vídeo con la frecuencia de la pantalla (mpv display-resample). */
    val matchRefresh: Boolean = false,
    /** Idioma del contenido (TMDB y audio preferido): ES o EN; vacío = el de Windows. */
    val language: String = "",
    /** Fuentes que no se quieren ver nunca: calidades, HDR y audio (nombres de Quality, HdrType y AudioCodec). */
    val excludedQualities: Set<String> = emptySet(),
    val excludedHdr: Set<String> = emptySet(),
    val excludedAudio: Set<String> = emptySet(),
) {
    val exclusions: com.shieldstream.tv.model.SourceExclusions
        get() = com.shieldstream.tv.model.SourceExclusions(
            qualities = com.shieldstream.tv.model.Quality.entries.filter { it.name in excludedQualities }.toSet(),
            hdrTypes = com.shieldstream.tv.model.HdrType.entries.filter { it.name in excludedHdr }.toSet(),
            audioCodecs = com.shieldstream.tv.model.AudioCodec.entries.filter { it.name in excludedAudio }.toSet(),
        )

    val debridService: DebridService get() = DebridService.entries.firstOrNull { it.name == debrid } ?: DebridService.REAL_DEBRID
}


private fun DesktopConfig.secretValues(): Map<String, String> = mapOf(
    "tmdbKey" to tmdbKey,
    "debridKey" to debridKey,
    "addons" to addons,
    "traktClientId" to traktClientId,
    "traktClientSecret" to traktClientSecret,
)

private fun DesktopConfig.withoutSecrets() = copy(tmdbKey = "", debridKey = "", addons = "", traktClientId = "", traktClientSecret = "")

private fun DesktopConfig.withSecrets(secrets: SecretStore) = copy(
    tmdbKey = secrets.get("config.tmdbKey").orEmpty(),
    debridKey = secrets.get("config.debridKey").orEmpty(),
    addons = secrets.get("config.addons").orEmpty(),
    traktClientId = secrets.get("config.traktClientId").orEmpty(),
    traktClientSecret = secrets.get("config.traktClientSecret").orEmpty(),
)

/**
 * Ajustes en %APPDATA%\Bambi\config.json (en Linux, ~/.config/bambi). Las claves no van ahí: se guardan cifradas con
 * DPAPI de Windows en secrets.dat (como el Keystore de la tele), y si un config.json antiguo las tenía en claro, se
 * pasan al almacén cifrado y se borran del JSON.
 */
class ConfigStore(
    private val file: File = defaultFile(),
    private val secrets: SecretStore = SecretStore(File(file.parentFile, "secrets.dat")),
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    var current by mutableStateOf(load())
        private set

    fun save(config: DesktopConfig) {
        storeSecrets(config)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(DesktopConfig.serializer(), config.withoutSecrets()))
        current = config
    }

    private fun storeSecrets(config: DesktopConfig) {
        config.secretValues().forEach { (name, value) ->
            val key = "config.$name"
            val wanted = value.takeIf { it.isNotBlank() }
            if (secrets.get(key) != wanted) secrets.set(key, wanted)
        }
    }

    private fun load(): DesktopConfig {
        val raw = runCatching { json.decodeFromString(DesktopConfig.serializer(), file.readText()) }.getOrNull() ?: return DesktopConfig().withSecrets(secrets)
        // Migración: claves en claro de versiones anteriores → al almacén cifrado.
        if (raw.secretValues().values.any { it.isNotBlank() }) {
            storeSecrets(raw.withSecrets(secrets).let { stored ->
                stored.copy(
                    tmdbKey = raw.tmdbKey.ifBlank { stored.tmdbKey },
                    debridKey = raw.debridKey.ifBlank { stored.debridKey },
                    addons = raw.addons.ifBlank { stored.addons },
                    traktClientId = raw.traktClientId.ifBlank { stored.traktClientId },
                    traktClientSecret = raw.traktClientSecret.ifBlank { stored.traktClientSecret },
                )
            })
            runCatching { file.writeText(json.encodeToString(DesktopConfig.serializer(), raw.withoutSecrets())) }
        }
        return raw.withSecrets(secrets)
    }

    companion object {
        fun defaultFile(): File {
            val dir = System.getenv("APPDATA")?.let { File(it, "Bambi") }
                ?: File(System.getProperty("user.home"), ".config/bambi")
            return File(dir, "config.json")
        }
    }
}

/**
 * Estilo de los subtítulos, como el editor de la tele: tamaño y posición (en %), letra, color, fondo y borde (el
 * número de cada opción, en el orden del editor) y si los .ass mantienen su estilo original.
 */
@Serializable
data class SubtitleStyle(
    val size: Int = 100,
    val position: Int = 6,
    val font: Int = 0,
    val color: Int = 0,
    val background: Int = 0,
    val edge: Int = 0,
    val original: Boolean = true,
) {
    /** Como se lo pasa Bambi al script del reproductor: «100:6:0:0:0:0:1». */
    fun encode(): String = listOf(size, position, font, color, background, edge, if (original) 1 else 0).joinToString(":")

    companion object {
        fun decode(text: String): SubtitleStyle? {
            val v = text.split(':').map { it.trim().toIntOrNull() ?: return null }
            if (v.size < 7) return null
            return SubtitleStyle(
                size = v[0].coerceIn(50, 200),
                position = v[1].coerceIn(0, 40),
                font = v[2].coerceIn(0, 4),
                color = v[3].coerceIn(0, 4),
                background = v[4].coerceIn(0, 2),
                edge = v[5].coerceIn(0, 2),
                original = v[6] == 1,
            )
        }
    }
}
