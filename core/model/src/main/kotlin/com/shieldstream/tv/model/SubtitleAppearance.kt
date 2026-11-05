package com.shieldstream.tv.model

data class SubtitleAppearance(
    val sizePercent: Int = 100,
    val verticalPositionPercent: Int = 5,
    val textColor: SubtitleColor = SubtitleColor.WHITE,
    val backgroundColor: SubtitleColor = SubtitleColor.TRANSPARENT,
    val outlineColor: SubtitleColor = SubtitleColor.BLACK,
    val applyEmbeddedStyles: Boolean = true,
) {
    fun normalized(): SubtitleAppearance = copy(
        sizePercent = sizePercent.coerceIn(MIN_SIZE, MAX_SIZE) / SIZE_STEP * SIZE_STEP,
        verticalPositionPercent = verticalPositionPercent.coerceIn(MIN_POSITION, MAX_POSITION) / POSITION_STEP * POSITION_STEP,
    )

    companion object {
        const val MIN_SIZE = 75
        const val MAX_SIZE = 200
        const val SIZE_STEP = 25
        const val MIN_POSITION = 0
        const val MAX_POSITION = 40
        const val POSITION_STEP = 5

        val textColors = listOf(
            SubtitleColor.WHITE,
            SubtitleColor.YELLOW,
            SubtitleColor.GREEN,
            SubtitleColor.CYAN,
            SubtitleColor.RED,
            SubtitleColor.BLACK,
        )
        val backgroundColors = listOf(
            SubtitleColor.TRANSPARENT,
            SubtitleColor.BLACK,
            SubtitleColor.WHITE,
        )
        val outlineColors = listOf(
            SubtitleColor.BLACK,
            SubtitleColor.WHITE,
            SubtitleColor.TRANSPARENT,
        )
    }
}

enum class SubtitleColor(val argb: Long) {
    WHITE(0xFFFFFFFF),
    YELLOW(0xFFFFF200),
    GREEN(0xFF46D369),
    CYAN(0xFF75DFFF),
    RED(0xFFFF5A5F),
    BLACK(0xFF000000),
    TRANSPARENT(0x00000000),
}
