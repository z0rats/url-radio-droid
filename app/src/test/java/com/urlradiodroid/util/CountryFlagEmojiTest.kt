package com.urlradiodroid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountryFlagEmojiTest {
    @Test
    fun `from converts a two-letter code to its flag emoji`() {
        assertEquals("🇩🇪", CountryFlagEmoji.from("DE"))
        assertEquals("🇺🇸", CountryFlagEmoji.from("US"))
    }

    @Test
    fun `from is case-insensitive`() {
        assertEquals(CountryFlagEmoji.from("DE"), CountryFlagEmoji.from("de"))
    }

    @Test
    fun `from returns null for a blank or wrong-length code`() {
        assertNull(CountryFlagEmoji.from(""))
        assertNull(CountryFlagEmoji.from("D"))
        assertNull(CountryFlagEmoji.from("DEU"))
    }

    @Test
    fun `from returns null for a non-letter code`() {
        assertNull(CountryFlagEmoji.from("12"))
        assertNull(CountryFlagEmoji.from("D1"))
    }
}
