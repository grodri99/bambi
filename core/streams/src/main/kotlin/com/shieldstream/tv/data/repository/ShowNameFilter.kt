package com.shieldstream.tv.data.repository

import java.text.Normalizer

/**
 * Las webs españolas (MejorTorrent, Wolfmax4k, DonTorrent…) se buscan por título y los addons mezclan series parecidas:
 * para "Dragon Ball" salen también Dragon Ball Z, GT y Super; para "Juego de tronos", "El juego de la pirámide";
 * para Spider-Man (1994), el "Marvel's Spider-Man" de 2017; para Friends, "Smiling Friends"… Se reproducía
 * OTRA serie. Aquí se saca el nombre de la serie de cada release (lo que va antes de "- Temporada", "[", "S01",
 * "1x01"…) y se compara con los nombres de TMDB (español, inglés, original).
 *
 * Prudente: si no se puede sacar un nombre claro (p. ej. series que se llaman como un año, "1923"), se deja pasar.
 * Probado contra lo que devolvía Peerflix el 27-oct-2026 para Dragon Ball, Friends, Dexter, Rick y Morty, Juego de
 * tronos, Los Simpson, La casa de papel, Lupin, Stranger Things y Spider-Man.
 */
object ShowNameFilter {

    /** Artículos que se ignoran al principio ("Los Simpsons" = "The Simpsons" = "Simpsons"). */
    private val ARTICLES = setOf("the", "a", "an", "el", "la", "los", "las", "un", "una", "les", "le", "il", "lo", "gli")

    /** Dónde acaba el nombre de la serie en una release. */
    private val SEPARATOR = Regex(
        // "01. The One…" (archivo de un pack que empieza por el número de episodio): sin nombre de serie.
        "^\\d{1,4}(?=[\\s._-])|" +
            "\\s+-\\s+|\\(|\\[|(?<![a-z0-9])(?:s\\d{1,2}(?:e\\d{0,3})?|t\\d{1,2}e\\d{1,3}|e\\d{2,4}|\\d{1,2}x\\d{1,3}(?:x\\d{1,3})*|" +
            "epis[oó]d(?:e|io)|ep|temporada|season|temp|" +
            "cap(?:itulo)?|complet[ao]|parte|primera|segunda|tercera|cuarta|quinta|(?:19|20)\\d{2}|\\d{3,4}p|hdtv|web|webrip|" +
            "bluray|bdrip|dvdrip|microhd)(?![a-z0-9])",
        RegexOption.IGNORE_CASE,
    )
    /** "La Casa De Papel AKA Money Heist": vale cualquiera de los dos nombres. */
    private val AKA = Regex("(?i)(?<![a-z])a\\.?k\\.?a\\.?(?![a-z])")
    private val LEADING_TAGS = Regex("^(\\[[^\\]]*\\]\\s*)+")
    private val YEAR_IN_PARENS = Regex("\\(\\d{4}\\)")

    /**
     * true = la release es de este título (o no se puede saber). [titles]: sus nombres en TMDB.
     * [movie]: en películas el nombre puede quedar cortado en un número que es parte del título
     * ("Blade Runner 2049" → "Blade Runner"), así que también vale si el título empieza por lo que se ha sacado.
     */
    fun matches(release: String, titles: List<String>, movie: Boolean = false): Boolean {
        val keys = titleKeys(titles)
        return showPart(release).split(AKA).any { part ->
            val releaseKey = key(words(part))
            // Sin nombre claro: muy corto, o solo números (archivos de un pack: "101 - The One Where….mkv").
            releaseKey.length < 3 || releaseKey.none { it.isLetter() } || releaseKey in keys ||
                (movie && keys.any { it.startsWith(releaseKey) })
        }
    }

    internal fun showPart(release: String): String {
        val s = release.trim().replace(LEADING_TAGS, "")
        val m = SEPARATOR.find(s) ?: return s
        return s.substring(0, m.range.first)
    }

    /** "Spider-Man: La serie animada" vale como tal y también como "Spider-Man". */
    private fun titleKeys(titles: List<String>): Set<String> = buildSet {
        titles.forEach { t ->
            val base = t.replace(YEAR_IN_PARENS, "")
            add(key(words(base)))
            if (':' in base) add(key(words(base.substringBefore(':'))))
        }
    }

    private fun words(s: String): List<String> =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }

    private fun key(words: List<String>): String {
        var i = 0
        while (i < words.size - 1 && words[i] in ARTICLES) i++
        return words.drop(i).joinToString("")
    }
}
