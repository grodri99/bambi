package com.shieldstream.tv.ui.component

import com.shieldstream.tv.model.Lang
import java.text.SimpleDateFormat
import java.util.Locale

fun formatEpisodeDate(isoDate: String?, includeYear: Boolean = false): String {
    if (isoDate.isNullOrBlank()) return ""
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val pattern = when {
            Lang.isEnglish && includeYear -> "MMM d, yyyy"
            Lang.isEnglish -> "MMM d"
            includeYear -> "d MMM yyyy"
            else -> "d MMM"
        }
        SimpleDateFormat(pattern, Lang.locale).format(parser.parse(isoDate)!!)
    }.getOrDefault(isoDate)
}
