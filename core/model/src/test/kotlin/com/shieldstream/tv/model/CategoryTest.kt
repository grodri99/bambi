package com.shieldstream.tv.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTest {

    @Test
    fun `classics is a visible movie category but not a TV category`() {
        assertTrue(Category.CLASSICS in Category.DEFAULT_VISIBLE)
        assertFalse(Category.CLASSICS.name in Category.DEFAULT_HIDDEN)
        assertTrue(Category.CLASSICS in Category.forFilter(HomeFilter.ALL))
        assertTrue(Category.CLASSICS in Category.forFilter(HomeFilter.MOVIES))
        assertFalse(Category.CLASSICS in Category.forFilter(HomeFilter.TV))
    }
}
