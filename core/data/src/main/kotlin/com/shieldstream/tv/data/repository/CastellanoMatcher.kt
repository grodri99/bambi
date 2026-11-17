package com.shieldstream.tv.data.repository

import com.shieldstream.tv.model.SpanishAudio
import java.text.Normalizer

/**
 * Buscador propio de Bambi para contenido en castellano. Las webs españolas publican con el título en español
 * y a su manera ("Juego de Tronos - Temporada 1 [HDTV][Cap.101][Castellano]"), así que las búsquedas normales
 * (título en inglés + S01E01) no las encuentran. Aquí se preparan búsquedas con los títulos en español y se
 * decide qué resultados valen: tienen que decir que traen castellano, ser de este título y, en series,
 * cubrir el episodio (S01E01, 1x01, "Cap.101", "Cap.101_110" o la temporada completa).
 */
internal class CastellanoMatcher(
    titles: List<String>,
    private val season: Int?,
    private val episode: Int?,
    private val year: Int?,
) {
    // Solo títulos en alfabeto latino: con el original en japonés o coreano no se encuentra nada en webs españolas.
    private val titles = titles.map(::deaccent).filter { t -> t.any { it.isLetter() } && t.all { it.code < 0x250 } }.distinct()

    /** Búsquedas que se lanzan en los buscadores (sin tildes: los índices no las llevan). */
    val queries: List<String> = buildList {
        this@CastellanoMatcher.titles.take(MAX_TITLES).forEach { t ->
            if (season != null) add("$t temporada $season")
            add("$t castellano")
            if (season == null && year != null) add("$t $year")
        }
    }.distinct()

    private val titleRxs = this.titles.map { t ->
        Regex(
            "^(?:\\[[^\\]]*\\]\\s*)*" + t.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
                .joinToString("[\\W_]+") { Regex.escape(it) } +
                "(?:$|[\\W_]+(?:\\(?(?:19|20)\\d{2}|s\\d|season|temporada|temp\\b|\\d{1,2}\\s*[ªºa°]\\s*temporada|" +
                "complet|\\d{3,4}p|\\d+x\\d|cap|castellano|spanish|espa[ñn]ol|\\[|\\())",
            RegexOption.IGNORE_CASE,
        )
    }
    private val episodeRx = if (season != null && episode != null) {
        Regex("(?i)s0*${season}\\W?e0*$episode(?!\\d)|(?<!\\d)0*${season}x0*$episode(?!\\d)")
    } else {
        null
    }
    private val packRx = season?.let {
        Regex("(?i)(\\bS0*$it\\b(?!\\s*E\\d)|season\\s*0*$it\\b|temporada[\\s._-]*0*$it(?!\\d)|temp\\.?\\s*0*$it(?!\\d)|(?<!\\d)0*$it\\s*[ªºa°]\\s*temporada|serie\\s+completa)")
    }

    fun accepts(name: String): Boolean {
        if (SpanishAudio.score(name) < SpanishAudio.CASTELLANO_MIN) return false
        val plain = deaccent(name)
        if (titleRxs.none { it.containsMatchIn(plain) }) return false
        if (season == null || episode == null) return year == null || plain.contains(year.toString())
        // Con capítulo indicado ("Cap.103", "1x03") manda el capítulo; si no, vale el pack de la temporada.
        capRange(plain)?.let { return capNumber() in it }
        if (ANY_EPISODE.containsMatchIn(plain)) return episodeRx!!.containsMatchIn(plain)
        return packRx!!.containsMatchIn(plain)
    }

    /** "Cap.101" → 101..101 · "Cap.101_110" → 101..110 (temporada + episodio con dos cifras). */
    private fun capRange(name: String): IntRange? {
        val m = CAP.find(name) ?: return null
        val first = m.groupValues[1].toInt()
        val last = m.groupValues[2].toIntOrNull() ?: first
        return first..maxOf(first, last)
    }

    private fun capNumber(): Int = season!! * 100 + episode!!

    private companion object {
        const val MAX_TITLES = 3
        val CAP = Regex("cap(?:itulo)?s?[\\s._-]*(\\d{3,4})(?:\\s*[_-]\\s*(\\d{3,4}))?", RegexOption.IGNORE_CASE)
        val ANY_EPISODE = Regex("(?i)s\\d{1,2}\\W?e\\d{1,3}|(?<!\\d)\\d{1,2}x\\d{1,3}(?!\\d)")

        fun deaccent(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()
    }
}
