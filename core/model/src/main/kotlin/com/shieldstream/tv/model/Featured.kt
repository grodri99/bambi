package com.shieldstream.tv.model

enum class FeaturedBadge(val label: String) {
    NEW(tr("Recién llegada", "New arrival")),
    POPULAR(tr("La más popular", "Most popular")),
    SERIES_NOW(tr("La serie del momento", "Show of the moment")),
    MOVIE_NOW(tr("La película del momento", "Movie of the moment")),
    RISING(tr("Arrasa esta semana", "Trending this week")),
    ACCLAIMED(tr("Aclamada por la crítica", "Critically acclaimed")),
}

data class FeaturedItem(
    val item: CatalogItem,
    val badge: FeaturedBadge,
    val logoUrl: String?,
    val genre: String?,
    val durationText: String?,
    val certification: String?,
)

enum class Category(val title: String, val forMovies: Boolean = true, val forTv: Boolean = true) {
    ACCLAIMED(tr("Aclamadas", "Critically acclaimed")),
    DRAMAS("Dramas"),
    THRILLERS("Thrillers"),
    HORROR(tr("Terror", "Horror")),
    MOODS(tr("Estados de ánimo", "Moods")),
    CRIME(tr("Policiacas", "Crime")),
    SPANISH(tr("Españolas", "Spanish")),
    ACTION(tr("Acción", "Action")),
    ADVENTURE(tr("Aventuras", "Adventure")),
    BOOKS(tr("Adaptaciones de libros", "Based on books")),
    COMEDY(tr("Comedias", "Comedies")),
    HALLOWEEN("Halloween"),
    REALITY(tr("Realities", "Reality TV"), forMovies = false),
    FANTASY(tr("Fantásticas", "Fantasy")),
    SCIFI(tr("Ciencia ficción", "Sci-fi")),
    MYSTERY(tr("Misterio", "Mystery")),
    ANIMATION(tr("Animación", "Animation")),
    KIDS(tr("Infantiles", "Kids")),
    ROMANCE(tr("Románticas", "Romance")),
    FAMILY(tr("Para toda la familia", "Family")),
    ANIME("Anime"),
    KOREAN(tr("Coreanas", "Korean")),
    DOCUMENTARY(tr("Documentales", "Documentaries")),
    TRUE_CRIME(tr("Crímenes reales", "True crime")),
    WAR_HISTORY(tr("Bélicas e históricas", "War & history")),
    WESTERN("Western"),
    MUSIC(tr("Musicales", "Musicals"), forTv = false),
    ASTROLOGY(tr("Astrología", "Astrology")),
    STANDUP("Stand-up"),
    ;

    companion object {
        val DEFAULT_VISIBLE = setOf(ACCLAIMED, DRAMAS, SPANISH, ACTION, COMEDY, SCIFI, KIDS, FAMILY, DOCUMENTARY)
        val DEFAULT_HIDDEN: Set<String> = entries.filterNot { it in DEFAULT_VISIBLE }.map { it.name }.toSet()

        val KIDS_SAFE = setOf(KIDS, FAMILY)

        fun forFilter(filter: HomeFilter): List<Category> = entries.filter {
            when (filter) {
                HomeFilter.ALL -> true
                HomeFilter.MOVIES -> it.forMovies
                HomeFilter.TV -> it.forTv
            }
        }
    }
}
