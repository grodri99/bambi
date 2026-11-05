package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleAppearanceTest {
    @Test
    fun `defaults preserve the existing subtitle appearance`() {
        assertEquals(
            SubtitleAppearance(
                sizePercent = 100,
                verticalPositionPercent = 5,
                textColor = SubtitleColor.WHITE,
                backgroundColor = SubtitleColor.TRANSPARENT,
                outlineColor = SubtitleColor.BLACK,
                applyEmbeddedStyles = true,
            ),
            SubtitleAppearance(),
        )
    }

    @Test
    fun `normalization clamps values and uses supported steps`() {
        assertEquals(
            SubtitleAppearance(sizePercent = 200, verticalPositionPercent = 40),
            SubtitleAppearance(sizePercent = 999, verticalPositionPercent = 42).normalized(),
        )
        assertEquals(
            SubtitleAppearance(sizePercent = 75, verticalPositionPercent = 0),
            SubtitleAppearance(sizePercent = 10, verticalPositionPercent = -5).normalized(),
        )
        assertEquals(100, SubtitleAppearance(sizePercent = 113).normalized().sizePercent)
    }
}
