package com.shieldstream.tv.data.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleLanguageTest {
    @Test
    fun `rejects Portuguese release names mislabeled as Spanish`() {
        assertTrue(hasConflictingLanguageName("es", "Dersu Uzala Portugu"))
        assertTrue(hasConflictingLanguageName("es", "Movie Portuguese.srt"))
    }

    @Test
    fun `selects only the movie archive part matching the video`() {
        val names = listOf("Movie CD1.srt", "Movie CD2.srt")
        assertEquals(0, movieArchiveCandidate(names, "Movie.2026.CD1.mkv"))
        assertEquals(1, movieArchiveCandidate(names, "Movie.2026.Disc2.mkv"))
        assertNull(movieArchiveCandidate(names, "Movie.2026.mkv"))
        assertNull(movieArchiveCandidate(names, null))
    }

    @Test
    fun `ranks CD subtitles only for the matching video part`() {
        assertTrue(hasPartMismatch("Dersu Uzala - CD1 - Spanish", "Dersu.Uzala.1975.BluRay.mkv"))
        assertTrue(hasPartMismatch("Movie CD2", "Movie.CD1.mkv"))
        assertFalse(hasPartMismatch("Movie CD1", "Movie.CD1.mkv"))
        assertFalse(hasPartMismatch("Derusu Uzara (1974)", "Dersu.Uzala.1975.BluRay.mkv"))
    }

    @Test
    fun `keeps valid names and other languages`() {
        assertFalse(hasConflictingLanguageName("es", "Dersu Uzala Spanish.srt"))
        assertFalse(hasConflictingLanguageName("pt", "Dersu Uzala Portugues.srt"))
    }
}
