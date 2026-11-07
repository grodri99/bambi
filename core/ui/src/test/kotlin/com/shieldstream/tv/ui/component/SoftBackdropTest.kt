package com.shieldstream.tv.ui.component

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftBackdropTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test
    fun `uniform image is unchanged`() {
        val pixels = IntArray(12 * 8) { gray(120) }
        val expected = pixels.copyOf()
        boxBlur(pixels, 12, 8, radius = 3)
        assertArrayEquals(expected, pixels)
    }

    @Test
    fun `hard edge becomes a gradient and keeps full alpha`() {
        val width = 20
        val height = 4
        val pixels = IntArray(width * height) { i -> if (i % width < width / 2) gray(0) else gray(255) }
        boxBlur(pixels, width, height, radius = 2)
        val row = (0 until width).map { pixels[it] and 0xFF }
        assertEquals(0, row.first())
        assertEquals(255, row.last())
        assertTrue("edge should soften", row[width / 2 - 1] in 1..254 && row[width / 2] in 1..254)
        assertTrue("monotonic across the edge", row.zipWithNext().all { (a, b) -> a <= b })
        assertTrue(pixels.all { it ushr 24 == 0xFF })
    }

    @Test
    fun `zero radius is a no-op`() {
        val pixels = IntArray(9) { gray(it * 20) }
        val expected = pixels.copyOf()
        boxBlur(pixels, 3, 3, radius = 0)
        assertArrayEquals(expected, pixels)
    }
}
