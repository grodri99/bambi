package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SpanishAudioTest {

    private fun assertScores(expected: Int, vararg names: String) {
        names.forEach { assertEquals(it, expected, SpanishAudio.score(it)) }
    }

    @Test
    fun `castellano said in the release name scores 3`() {
        assertScores(
            3,
            "Pelicula.2023.1080p.CAST.mkv",
            "Dune Parte Dos (2024) [BluRay 1080p][Castellano]",
            "Juego de Tronos - Temporada 1 [HDTV][Cap.101][AC3 5.1 Español Castellano]",
            "El.Padrino.1972.1080p.BluRay.x264.ESP-ENG",
            "Breaking.Bad.S01E02.720p.SPA.ENG.x264",
            "Dragon Ball Z 001 [Cast][DVDRip]",
            "La_casa_de_papel_1x01_castellano_hdtv.avi",
            "Oppenheimer.2023.2160p.UHD.BluRay.Spanish.Castilian.DTS-HD",
            "Shogun.S01E01.1080p.WEB-DL.es-ES.DDP5.1",
            "Interstellar (2014) BDRip 1080p Audio Español AC3 5.1",
            "The.Bear.S03E05esp.720p",
        )
    }

    @Test
    fun `spanish without saying which one scores 2`() {
        assertScores(
            2,
            "Serie.S01E02.SPANISH.720p",
            "Movie.2022.1080p.WEB-DL.Spanish_English.x264",
            "Movie.2021.BluRay.1080p.ES-EN.DTS",
            "Movie 2020 1080p [ES] x265",
        )
    }

    @Test
    fun `latin american spanish scores 1 even next to espanol`() {
        assertScores(
            1,
            "Coco.2017.1080p.BluRay.Latino.x264",
            "Peli.2022.1080p.WEB-DL.LAT.mkv",
            "Avatar (2009) 1080p Español Latino 5.1",
            "Movie.2023.1080p.AMZN.WEB-DL.Spanish.Latin.DDP5.1",
            "Serie.S02E04.1080p.es-419.mkv",
        )
    }

    @Test
    fun `castellano wins when the release has both castellano and latino`() {
        assertScores(3, "Toy Story (1995) 1080p Castellano + Latino + Ingles")
    }

    @Test
    fun `spanish subtitles alone are not spanish audio`() {
        assertScores(
            0,
            "Peli.2022.VOSE.1080p",
            "Parasite.2019.KOREAN.1080p.BluRay.Sub.Español.mkv",
            "Movie.2023.1080p.ENG.Spanish.Subs.x264",
            "Anime 05 [1080p] Subtitulado",
            "Movie.2020.720p.English.Subs.ESP",
            "Peli (2021) Inglés - Subtítulos en Castellano",
        )
    }

    @Test
    fun `spanish audio still counts when spanish subtitles are also listed`() {
        assertScores(
            3,
            "Peli (2019) Castellano-Inglés Subt Castellano",
            "Movie.2018.1080p.Castellano.AC3.Sub.Esp",
        )
    }

    @Test
    fun `words that only look like spanish markers score 0`() {
        assertScores(
            0,
            "Cast Away (2000) 1080p BluRay x264",
            "CAST AWAY 2000 1080P BLURAY",
            "Spa Night 2016 1080p WEB-DL",
            "The Cast of Friends Reunion 2021 720p",
            "Espn 30 for 30 S01E01 720p",
            "Serie.S01E01.1080p.Dublado.Dual.Audio",
            "Anime 12 [1080p] Dual Audio",
            "Anime 12 [1080p][Multi-Subs]",
            "[Erai-raws] Dragon Ball Daima - 05 [1080p][Multiple Subtitle]",
            "Film.2023.MULTi.VFF.1080p.BluRay",
            "Latin Lover 1985 DVDRip",
            "Movie.2024.1080p.WEB-DL.x264",
        )
    }

    @Test
    fun `multi audio is a possible match the player verifies`() {
        assertScores(
            1,
            "[ToonsHub] Dragon Ball Super Beerus S01E05 1080p CR WEB-DL MULTi AAC2.0 H.264",
            "Serie.S01E01.1080p.NF.WEB-DL.MULTI.DDP5.1",
        )
    }

    @Test
    fun `real release names returned by Torrentio for a spanish search`() {
        // Juego de tronos T1E1 con language=spanish (14-nov-2026).
        assertScores(
            3,
            "Juego de Tronos temporada 1 [BluRay 1080p][AC3 5.1 Castellano AC3 5.1-Ingles+Subs][ES-EN]",
            "Juego De Tronos - Temporada 1 [BluRay Rip 1080p][Cap.101_110][AC3 5.1 Castellano][www.pctnew.com]",
            "Juego de tronos Temporada 1 completa [BDremux 1080p][DTS 5.1 Castellano-DTS 5.1 Ingles+Subs][ES-EN]",
            "Juego de Tronos 1x01 BD-Rip 1080p Castellano-Inglés por E",
            "Juego De Tronos Temporada-1 Completa 720p Español De Esp",
            "Juego De Tronos Temporada 1 COMPLETA En Espanol",
        )
        assertScores(
            2,
            "Juego de Tronos T04E01 (Spanish) [1080p]",
            "Juego de Tronos - Temp.1 [HDTV][Cap.101][Spanish_English]",
            "Juego De Tronos - Temporada 1 COMPLETA [DVDRIP][Spanish]",
            "Juego de Tronos 1x01 HDTV Spanish by tony",
            "game of thrones s01E01 spanish",
        )
        assertScores(
            1,
            "Game of Thrones (2011) 1 Temporada 720p (Esp.Latino)",
            "Game of Thrones Temporada 1 Español Latino",
            "game of thrones s01E01 español latino [EM]",
        )
        // Solo subtítulos, aunque Torrentio les ponga la bandera 🇪🇸.
        listOf(
            "Game.of.Thrones.Season1.720p - VO + Sub esp",
            "Game Of Thrones Seasons 1 & 2 Spanish Sub",
            "Game Of Throne Season 1 & 2 With Spanish Subtitles",
            "game of thrones 1-4 temporada sub-español",
        ).forEach { assertEquals(it, 0, SpanishAudio.score(it, listOf("ES"))) }
    }

    @Test
    fun `addon flags count when the name says nothing`() {
        assertEquals(2, SpanishAudio.score("Movie.2024.1080p.WEB-DL", listOf("ES")))
        assertEquals(2, SpanishAudio.score("Movie.2024.1080p.WEB-DL", listOf("ES", "EN")))
        // Bandera entre muchos idiomas: suele ser la lista de subtítulos de un remux.
        assertEquals(1, SpanishAudio.score("Movie.2024.2160p.Remux", listOf("EN", "FR", "DE", "ES", "IT")))
        assertEquals(1, SpanishAudio.score("Movie.2024.1080p.WEB-DL", listOf("LAT")))
        assertEquals(1, SpanishAudio.score("Movie.2024.1080p.WEB-DL", listOf("MULTI")))
        // El nombre manda: VOSE con bandera española sigue siendo solo subtítulos.
        assertEquals(0, SpanishAudio.score("Movie.2024.1080p.VOSE", listOf("ES")))
        assertEquals(0, SpanishAudio.score("Movie.2024.1080p.WEB-DL", listOf("EN")))
    }
}
