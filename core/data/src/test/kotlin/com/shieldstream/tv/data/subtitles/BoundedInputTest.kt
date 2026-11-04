package com.shieldstream.tv.data.subtitles

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class BoundedInputTest {
    @Test
    fun inputAtLimitIsAccepted() {
        val bytes = ByteArray(1024) { it.toByte() }
        assertArrayEquals(bytes, ByteArrayInputStream(bytes).readBounded(bytes.size))
    }

    @Test
    fun inputOverLimitIsRejected() {
        assertThrows(IOException::class.java) {
            ByteArrayInputStream(ByteArray(1025)).readBounded(1024)
        }
    }

    @Test
    fun gzipExpansionCannotBypassLimit() {
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(ByteArray(4096)) }
        }.toByteArray()
        assertThrows(IOException::class.java) {
            GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBounded(128) }
        }
    }
}
