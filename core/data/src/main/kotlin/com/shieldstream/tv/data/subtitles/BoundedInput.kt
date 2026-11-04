package com.shieldstream.tv.data.subtitles

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal object SubtitleLimits {
    const val DOWNLOAD_BYTES = 16 * 1024 * 1024
    const val SUBTITLE_BYTES = 8 * 1024 * 1024
    const val ARCHIVE_BYTES = 32 * 1024 * 1024
    const val ARCHIVE_ENTRIES = 64
}

/** Reads at most [maxBytes], failing as soon as the input contains one more byte. */
internal fun InputStream.readBounded(maxBytes: Int): ByteArray {
    require(maxBytes >= 0)
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, maxBytes - total + 1))
        if (count < 0) break
        if (count == 0) continue
        total += count
        if (total > maxBytes) throw IOException("Subtitle exceeds the $maxBytes-byte limit")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
