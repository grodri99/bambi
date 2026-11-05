package com.shieldstream.tv.model

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class LangTest {
    private lateinit var previousLanguage: AppLanguage
    private lateinit var previousLocale: Locale

    @Before
    fun saveLocaleState() {
        previousLanguage = Lang.current
        previousLocale = Locale.getDefault()
    }

    @After
    fun restoreLocaleState() {
        Lang.current = previousLanguage
        Locale.setDefault(previousLocale)
    }

    @Test
    fun `default language uses Spanish for every Spanish regional locale`() {
        Locale.setDefault(Locale.forLanguageTag("es-MX"))
        assertEquals(AppLanguage.ES, AppLanguage.default())

        Locale.setDefault(Locale.forLanguageTag("es-AR"))
        assertEquals(AppLanguage.ES, AppLanguage.default())
    }

    @Test
    fun `default language falls back to English outside Spanish locales`() {
        Locale.setDefault(Locale.forLanguageTag("en-GB"))
        assertEquals(AppLanguage.EN, AppLanguage.default())

        Locale.setDefault(Locale.forLanguageTag("fr-FR"))
        assertEquals(AppLanguage.EN, AppLanguage.default())
    }

    @Test
    fun `language persistence accepts only known names`() {
        assertEquals(AppLanguage.ES, AppLanguage.fromName("ES"))
        assertEquals(AppLanguage.EN, AppLanguage.fromName("EN"))
        assertNull(AppLanguage.fromName("en"))
        assertNull(AppLanguage.fromName("FR"))
        assertNull(AppLanguage.fromName(null))
    }

    @Test
    fun `each language exposes consistent Android and TMDB locale metadata`() {
        assertEquals("es", AppLanguage.ES.locale.language)
        assertEquals("ES", AppLanguage.ES.locale.country)
        assertEquals("es-ES", AppLanguage.ES.tmdb)

        assertEquals("en", AppLanguage.EN.locale.language)
        assertEquals("US", AppLanguage.EN.locale.country)
        assertEquals("en-US", AppLanguage.EN.tmdb)
    }
}
