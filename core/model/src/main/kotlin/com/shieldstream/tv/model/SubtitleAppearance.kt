package com.shieldstream.tv.model

data class SubtitleAppearance(
    val sizePercent: Int = 100,
    val verticalPositionPercent: Int = 5,
    val textColor: SubtitleColor = SubtitleColor.WHITE,
    val backgroundColor: SubtitleColor = SubtitleColor.TRANSPARENT,
    val outlineColor: SubtitleColor = SubtitleColor.BLACK,
    val applyEmbeddedStyles: Boolean = true,
    val font: SubtitleFont = SubtitleFont.DEFAULT,
    val edge: SubtitleEdge = SubtitleEdge.OUTLINE,
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
            SubtitleColor.SHADE,
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
    /** Negro semitransparente: el recuadro clásico detrás del texto. */
    SHADE(0xA6000000),
}

enum class SubtitleFont { DEFAULT, SERIF, MONOSPACE, CONDENSED, BOLD }

/** Cómo se separa el texto del vídeo cuando el color del borde no es "Ninguno". */
enum class SubtitleEdge { OUTLINE, SHADOW }

/** Siguiente/anterior valor de una lista cíclica (para cambiar opciones con izquierda/derecha). */
fun <T> T.cycle(values: List<T>, delta: Int): T {
    val index = values.indexOf(this).coerceAtLeast(0)
    return values[((index + delta) % values.size + values.size) % values.size]
}
