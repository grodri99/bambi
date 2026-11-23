package com.shieldstream.tv.desktop

import com.shieldstream.tv.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExternalSubsTest {
    @Test
    fun `the right episode is taken from a season zip`() {
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                listOf("Show.S01E01.srt" to "uno", "Show.S01E02.srt" to "dos", "leeme.txt" to "x").forEach { (name, text) ->
                    z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry()
                }
            }
        }.toByteArray()
        val (bytes, ext) = ExternalSubs.unpack(zip, Episode(1, 2, "", "", null, null))
        assertEquals("dos", String(bytes))
        assertEquals("srt", ext)
    }

    @Test
    fun `old spanish subtitles in windows-1252 are read right`() {
        val text = "¿Qué pasó, señor?"
        assertEquals(text, ExternalSubs.decode(text.toByteArray(charset("windows-1252")), "es"))
        assertEquals(text, ExternalSubs.decode(("﻿" + text).toByteArray(), "es"))
    }

    @Test
    fun `release names are compared by their words`() {
        assertTrue(ExternalSubs.similarity("The.Odyssey.2026.1080p.WEB-DL.srt", "The.Odyssey.2026.1080p.WEB-DL.x264.mkv") > 0.6f)
        assertEquals("es", ExternalSubs.iso1("spl"))
        assertEquals(null, ExternalSubs.iso1("jpn"))
    }
}
