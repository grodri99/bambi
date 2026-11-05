package com.shieldstream.tv.player

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.shieldstream.tv.model.SubtitleAppearance
import com.shieldstream.tv.model.SubtitleColor

@OptIn(UnstableApi::class)
internal fun SubtitleView.applySubtitleAppearance(appearance: SubtitleAppearance) {
    val normalized = appearance.normalized()
    setStyle(
        CaptionStyleCompat(
            normalized.textColor.argb.toInt(),
            normalized.backgroundColor.argb.toInt(),
            Color.TRANSPARENT,
            if (normalized.outlineColor == SubtitleColor.TRANSPARENT) {
                CaptionStyleCompat.EDGE_TYPE_NONE
            } else {
                CaptionStyleCompat.EDGE_TYPE_OUTLINE
            },
            normalized.outlineColor.argb.toInt(),
            null,
        ),
    )
    setFractionalTextSize(normalized.sizePercent * BASE_SUBTITLE_SIZE_FRACTION / 100f)
    setBottomPaddingFraction(normalized.verticalPositionPercent / 100f)
    setApplyEmbeddedStyles(normalized.applyEmbeddedStyles)
    setApplyEmbeddedFontSizes(false)
}

private const val BASE_SUBTITLE_SIZE_FRACTION = 0.0533f
