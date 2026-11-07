package com.shieldstream.tv.ui.component

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation

/** Decode size for softened backdrops. A quarter of TMDB's w1280, so decoding can subsample exactly. */
private const val SoftWidth = 320
private const val SoftHeight = 180

/**
 * Box-blur radius in decoded pixels (three passes). 4 was too soft on the Nokia; 3 keeps shapes
 * readable while still quieting detail behind the rows.
 */
private const val SoftRadius = 3

/**
 * Full-screen backdrop that is blurred once while loading, Muzei-style. The blur runs on Coil's
 * background decoder on a 320×180 bitmap; the result is an ordinary bitmap, so drawing and
 * scrolling cost nothing extra per frame. Avoid GPU `Modifier.blur` for this: it needs API 31 and
 * re-blurs a full-screen layer whenever that layer redraws.
 */
@Composable
fun SoftBackdropImage(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .size(SoftWidth, SoftHeight)
            .transformations(SoftenTransformation)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

private object SoftenTransformation : Transformation {
    override val cacheKey: String = "soften-$SoftWidth-$SoftRadius"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val source = if (input.width > SoftWidth) {
            val height = (input.height * SoftWidth.toFloat() / input.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(input, SoftWidth, height, true)
        } else {
            input
        }
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        boxBlur(pixels, width, height, SoftRadius)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}

/**
 * Approximates a Gaussian blur with [passes] horizontal and vertical box blurs. Edges clamp, so
 * the borders do not darken. Operates in place on ARGB pixels.
 */
internal fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
    if (radius <= 0 || width <= 0 || height <= 0) return
    val scratch = IntArray(pixels.size)
    repeat(passes) {
        blurLine(pixels, scratch, lines = height, length = width, lineStride = width, step = 1, radius = radius)
        blurLine(scratch, pixels, lines = width, length = height, lineStride = 1, step = width, radius = radius)
    }
}

private fun blurLine(src: IntArray, dst: IntArray, lines: Int, length: Int, lineStride: Int, step: Int, radius: Int) {
    val window = radius * 2 + 1
    val last = length - 1
    for (line in 0 until lines) {
        val base = line * lineStride
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (k in -radius..radius) {
            val p = src[base + k.coerceIn(0, last) * step]
            a += p ushr 24
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        for (i in 0..last) {
            dst[base + i * step] = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            val out = src[base + (i - radius).coerceIn(0, last) * step]
            val enter = src[base + (i + radius + 1).coerceIn(0, last) * step]
            a += (enter ushr 24) - (out ushr 24)
            r += ((enter shr 16) and 0xFF) - ((out shr 16) and 0xFF)
            g += ((enter shr 8) and 0xFF) - ((out shr 8) and 0xFF)
            b += (enter and 0xFF) - (out and 0xFF)
        }
    }
}
