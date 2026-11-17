package com.shieldstream.tv.player

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.shieldstream.tv.model.SubtitleAppearance
import com.shieldstream.tv.model.SubtitleColor
import com.shieldstream.tv.model.SubtitleEdge
import com.shieldstream.tv.model.SubtitleFont
import android.graphics.Typeface

@OptIn(UnstableApi::class)
internal fun SubtitleView.applySubtitleAppearance(appearance: SubtitleAppearance) {
    val normalized = appearance.normalized()
    setStyle(
        CaptionStyleCompat(
            normalized.textColor.argb.toInt(),
            normalized.backgroundColor.argb.toInt(),
            Color.TRANSPARENT,
            when {
                normalized.outlineColor == SubtitleColor.TRANSPARENT -> CaptionStyleCompat.EDGE_TYPE_NONE
                normalized.edge == SubtitleEdge.SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                else -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            },
            normalized.outlineColor.argb.toInt(),
            normalized.font.typeface(),
        ),
    )
    setFractionalTextSize(normalized.sizePercent * BASE_SUBTITLE_SIZE_FRACTION / 100f)
    setBottomPaddingFraction(normalized.verticalPositionPercent / 100f)
    setApplyEmbeddedStyles(normalized.applyEmbeddedStyles)
    setApplyEmbeddedFontSizes(false)
}

internal fun SubtitleFont.typeface(): Typeface? = when (this) {
    SubtitleFont.DEFAULT -> null
    SubtitleFont.SERIF -> Typeface.SERIF
    SubtitleFont.MONOSPACE -> Typeface.MONOSPACE
    SubtitleFont.CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    SubtitleFont.BOLD -> Typeface.DEFAULT_BOLD
}

private const val BASE_SUBTITLE_SIZE_FRACTION = 0.0533f
