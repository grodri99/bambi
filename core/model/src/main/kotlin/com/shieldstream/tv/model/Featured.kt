package com.shieldstream.tv.model

enum class FeaturedBadge {
    NEW,
    POPULAR,
    SERIES_NOW,
    MOVIE_NOW,
    RISING,
    ACCLAIMED,
}

data class FeaturedItem(
    val item: CatalogItem,
    val badge: FeaturedBadge,
    val logoUrl: String?,
    val genre: String?,
    val durationText: String?,
    val certification: String?,
)

enum class Category(val forMovies: Boolean = true, val forTv: Boolean = true) {
    ACCLAIMED,
    DRAMAS,
    THRILLERS,
    HORROR,
    MOODS,
    CRIME,
    SPANISH,
    ACTION,
    ADVENTURE,
    BOOKS,
    COMEDY,
    HALLOWEEN,
    REALITY(forMovies = false),
    FANTASY,
    SCIFI,
    MYSTERY,
    ANIMATION,
    KIDS,
    ROMANCE,
    FAMILY,
    ANIME,
    KOREAN,
    DOCUMENTARY,
    TRUE_CRIME,
    WAR_HISTORY,
    WESTERN,
    MUSIC(forTv = false),
    ASTROLOGY,
    STANDUP,
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
