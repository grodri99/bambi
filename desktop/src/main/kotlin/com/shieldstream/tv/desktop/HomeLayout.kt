package com.shieldstream.tv.desktop

import kotlinx.serialization.Serializable

/**
 * Cómo quiere cada perfil su Inicio (sugerencia del foro): qué va en la portada de arriba, qué filas salen y en qué
 * orden. Las filas se guardan por su identificador; las que no conoce (una lista nueva, una fila nueva de Bambi) se
 * añaden al final, a la vista.
 */
@Serializable
data class HomeLayout(
    /** Portada: [AUTO] (lo primero del catálogo), [TOP10], [MYLIST] o [NONE] (sin portada: el Inicio empieza en las filas). */
    val billboard: String = AUTO,
    /** Orden elegido (vacío: el de siempre). */
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
) {
    /** Las filas que salen, en su orden, de entre las que hay ahora mismo ([available], en el orden de siempre). */
    fun arrange(available: List<String>): List<String> = all(available).filter { it !in hidden }

    /** Todas (también las ocultas), en el orden elegido: para Ajustes. */
    fun all(available: List<String>): List<String> {
        val known = available.toSet()
        return order.filter { it in known } + available.filter { it !in order }
    }

    fun toggle(id: String): HomeLayout = copy(hidden = if (id in hidden) hidden - id else hidden + id)

    /** Sube ([delta] = -1) o baja (+1) una fila. El orden queda guardado entero desde ese momento. */
    fun move(id: String, delta: Int, available: List<String>): HomeLayout {
        val list = all(available).toMutableList()
        val from = list.indexOf(id)
        if (from < 0) return this
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return this
        list.add(to, list.removeAt(from))
        return copy(order = list)
    }

    companion object {
        const val AUTO = "auto"
        const val TOP10 = "top10"
        const val MYLIST = "mylist"
        const val NONE = "none"

        const val CONTINUE = "continue"
        const val MY_LIST_ROW = "mylist"
        const val PLAY_SOMETHING = "play"
        const val TOP10_ROW = "top10"
        const val TRAKT = "trakt"
        const val CATEGORIES = "categories"

        fun listRow(id: String) = "list:$id"
        fun catalogRow(title: String) = "row:$title"

        /** Las filas del catálogo de TMDB en el Inicio (las mismas que pinta el repositorio, en su orden). */
        val CATALOG_ROWS_ALL = listOf(
            "Películas en tendencia", "Series en tendencia", "Populares en Bambi", "Series populares",
            "Películas mejor valoradas", "Series mejor valoradas", "Acción", "Dramas",
        )

        /** Las filas que puede tener el Inicio, en el orden de siempre. */
        fun available(catalogTitles: List<String>, lists: List<CustomList>): List<String> =
            listOf(CONTINUE, MY_LIST_ROW) + lists.map { listRow(it.id) } + listOf(PLAY_SOMETHING, TOP10_ROW, TRAKT) +
                catalogTitles.map(::catalogRow) + CATEGORIES

        /** Cómo se llama cada fila en Ajustes. */
        fun label(id: String, lists: List<CustomList>): String = when {
            id == CONTINUE -> "Seguir viendo"
            id == MY_LIST_ROW -> "Mi lista"
            id == PLAY_SOMETHING -> "«¿No sabes qué ver?» (Reproduce algo · Sorpréndeme)"
            id == TOP10_ROW -> "Top 10 de hoy"
            id == TRAKT -> "Recomendadas para ti (Trakt)"
            id == CATEGORIES -> "Categorías (las de abajo)"
            id.startsWith("list:") -> lists.firstOrNull { "list:" + it.id == id }?.let { if (it.isTrakt) "Lista de Trakt: ${it.name}" else "Lista: ${it.name}" } ?: "Lista"
            id.startsWith("row:") -> id.removePrefix("row:")
            else -> id
        }
    }
}
