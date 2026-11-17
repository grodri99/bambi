package com.shieldstream.tv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastellanoMatcherTest {
    private val got = CastellanoMatcher(listOf("Juego de tronos", "Game of Thrones"), season = 1, episode = 1, year = 2011)

    @Test
    fun `queries use the spanish titles without accents`() {
        val m = CastellanoMatcher(listOf("El corazón de la bestia", "Beast of War"), season = null, episode = null, year = 2026)
        assertEquals(
            listOf("El corazon de la bestia castellano", "El corazon de la bestia 2026", "Beast of War castellano", "Beast of War 2026"),
            m.queries,
        )
        assertEquals(
            listOf("Juego de tronos temporada 1", "Juego de tronos castellano", "Game of Thrones temporada 1", "Game of Thrones castellano"),
            got.queries,
        )
    }

    @Test
    fun `titles in other alphabets are not searched`() {
        val m = CastellanoMatcher(listOf("Dragon Ball", "ドラゴンボール", "Dragon Ball"), season = 1, episode = 2, year = 1986)
        assertEquals(listOf("Dragon Ball temporada 1", "Dragon Ball castellano"), m.queries)
        assertTrue(m.accepts("Dragon Ball - Temporada 1 [DVDRip][Cap.102][Castellano]"))
        assertFalse(m.accepts("Dragon Ball Z - Temporada 1 [DVDRip][Cap.102][Castellano]"))
    }

    @Test
    fun `season packs and the requested episode in castellano are accepted`() {
        listOf(
            "Juego de Tronos temporada 1 [BluRay 1080p][AC3 5.1 Castellano AC3 5.1-Ingles+Subs][ES-EN]",
            "Juego De Tronos - Temporada 1 [BluRay Rip 1080p][Cap.101_110][AC3 5.1 Castellano][www.pctnew.com]",
            "Juego de Tronos - Temp.1 [HDTV][Cap.101][Spanish_English]",
            "Juego de Tronos 1x01 BD-Rip 1080p Castellano-Inglés por E",
            "Game.of.Thrones.S01E01.1080p.BluRay.Castellano.x264",
            "Juego De Tronos - Temporada 1 COMPLETA [DVDRIP][Spanish]",
        ).forEach { assertTrue(it, got.accepts(it)) }
    }

    @Test
    fun `other episodes seasons shows and languages are rejected`() {
        listOf(
            "Juego de Tronos - Temp.1 [HDTV][Cap.102][Castellano]",
            "Juego de Tronos 1x02 HDTV Castellano",
            "Juego de Tronos temporada 2 [BluRay 1080p][Castellano]",
            "Juego De Tronos - Temporada 1 [Cap.105_110][Castellano]",
            "El juego de la piramide [HDTV 720p][Cap.101][Castellano]",
            "Game of Thrones Temporada 1 Español Latino",
            "Game.of.Thrones.Season1.720p - VO + Sub esp",
            "Game.of.Thrones.S01E01.1080p.BluRay.x264",
            "Juego de Tronos Temporada 1 Dublado",
        ).forEach { assertFalse(it, got.accepts(it)) }
    }

    @Test
    fun `movies need the title and the year`() {
        val m = CastellanoMatcher(listOf("En el corazón de la bestia"), season = null, episode = null, year = 2026)
        assertTrue(m.accepts("En el corazon de la bestia (2026) [4k 2160p][Esp]"))
        assertTrue(m.accepts("En el corazón de la bestia 2026 BluRay 1080p Castellano"))
        assertFalse(m.accepts("La bestia en la jungla (2026) [Bluray 1080p][Esp]"))
        assertFalse(m.accepts("En el corazon de la bestia (1998) [DVDRip][Castellano]"))
        assertFalse(m.accepts("En el corazon de la bestia 2 (2026) [Esp]"))
    }
}
