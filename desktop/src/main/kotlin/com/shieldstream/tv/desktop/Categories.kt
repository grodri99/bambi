package com.shieldstream.tv.desktop

import com.shieldstream.tv.model.Category

/**
 * Las categorías del Inicio de la tele (core/model Category) con la misma consulta a TMDB que HomeExtrasSource:
 * género, palabra clave, país o idioma, por separado para películas y series (null = no aplica).
 */
object CategorySpecs {
    data class Query(val genres: String? = null, val keyword: String? = null, val country: String? = null, val language: String? = null)
    data class Spec(val movie: Query?, val tv: Query?)

    fun of(category: Category): Spec = when (category) {
        Category.ACCLAIMED -> Spec(Query(), Query())
        Category.CLASSICS -> Spec(Query(), null)
        Category.DRAMAS -> Spec(Query("18"), Query("18"))
        Category.THRILLERS -> Spec(Query("53"), Query("9648"))
        Category.CRIME -> Spec(Query("80"), Query("80"))
        Category.ACTION -> Spec(Query("28"), Query("10759"))
        Category.BOOKS -> Spec(Query(keyword = "based on novel or book"), Query(keyword = "based on novel or book"))
        Category.COMEDY -> Spec(Query("35"), Query("35"))
        Category.HALLOWEEN -> Spec(Query(keyword = "halloween"), Query(keyword = "halloween"))
        Category.REALITY -> Spec(null, Query("10764"))
        Category.FANTASY -> Spec(Query("14"), Query("10765"))
        Category.SCIFI -> Spec(Query("878"), Query("10765"))
        Category.ANIMATION -> Spec(Query("16"), Query("16"))
        Category.ROMANCE -> Spec(Query("10749"), Query(keyword = "romance"))
        Category.FAMILY -> Spec(Query("10751"), Query("10751"))
        Category.ANIME -> Spec(Query("16", language = "ja"), Query("16", language = "ja"))
        Category.DOCUMENTARY -> Spec(Query("99"), Query("99"))
        Category.ASTROLOGY -> Spec(Query(keyword = "astrology"), Query(keyword = "astrology"))
        Category.STANDUP -> Spec(Query(keyword = "stand-up comedy"), Query(keyword = "stand-up comedy"))
        Category.MOODS -> Spec(Query("35|10749"), Query("35|10749"))
        Category.HORROR -> Spec(Query("27"), Query(keyword = "horror"))
        Category.SPANISH -> Spec(Query(country = "ES"), Query(country = "ES"))
        Category.ADVENTURE -> Spec(Query("12"), Query("10759"))
        Category.MYSTERY -> Spec(Query("9648"), Query("9648"))
        Category.KIDS -> Spec(Query("16,10751"), Query("10762"))
        Category.KOREAN -> Spec(Query(country = "KR"), Query(country = "KR"))
        Category.TRUE_CRIME -> Spec(Query("99,80"), Query("99,80"))
        Category.WAR_HISTORY -> Spec(Query("10752|36"), Query("10768"))
        Category.WESTERN -> Spec(Query("37"), Query("37"))
        Category.MUSIC -> Spec(Query("10402"), null)
    }

    fun title(category: Category): String = when (category) {
        Category.ACCLAIMED -> "Aclamadas"
        Category.CLASSICS -> "Películas clásicas"
        Category.DRAMAS -> "Dramas"
        Category.THRILLERS -> "Thrillers"
        Category.HORROR -> "Terror"
        Category.MOODS -> "Estados de ánimo"
        Category.CRIME -> "Policiacas"
        Category.SPANISH -> "Españolas"
        Category.ACTION -> "Acción"
        Category.ADVENTURE -> "Aventuras"
        Category.BOOKS -> "Adaptaciones de libros"
        Category.COMEDY -> "Comedias"
        Category.HALLOWEEN -> "Halloween"
        Category.REALITY -> "Realities"
        Category.FANTASY -> "Fantásticas"
        Category.SCIFI -> "Ciencia ficción"
        Category.MYSTERY -> "Misterio"
        Category.ANIMATION -> "Animación"
        Category.KIDS -> "Infantiles"
        Category.ROMANCE -> "Románticas"
        Category.FAMILY -> "Para toda la familia"
        Category.ANIME -> "Anime"
        Category.KOREAN -> "Coreanas"
        Category.DOCUMENTARY -> "Documentales"
        Category.TRUE_CRIME -> "Crímenes reales"
        Category.WAR_HISTORY -> "Bélicas e históricas"
        Category.WESTERN -> "Western"
        Category.MUSIC -> "Musicales"
        Category.ASTROLOGY -> "Astrología"
        Category.STANDUP -> "Stand-up"
    }

    /** Las que se ven en una pestaña: las visibles del perfil que tienen sentido ahí (sin series en «Películas»…). */
    fun visible(hidden: Set<String>, catalog: Catalog, kids: Boolean): List<Category> = Category.entries.filter { c ->
        c.name !in hidden &&
            (!kids || c in Category.KIDS_SAFE) &&
            when (catalog) {
                Catalog.ALL -> true
                Catalog.MOVIES -> c.forMovies && of(c).movie != null
                Catalog.SHOWS -> c.forTv && of(c).tv != null
            }
    }
}
