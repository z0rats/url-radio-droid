package com.urlradiodroid.util

import org.junit.Assert.assertEquals
import org.junit.Test

class VoteCountFormatterTest {
    @Test
    fun `format returns the raw number below 1000`() {
        assertEquals("0", VoteCountFormatter.format(0))
        assertEquals("42", VoteCountFormatter.format(42))
        assertEquals("999", VoteCountFormatter.format(999))
    }

    @Test
    fun `format abbreviates thousands with one decimal and a k suffix`() {
        assertEquals("1.0k", VoteCountFormatter.format(1_000))
        assertEquals("1.5k", VoteCountFormatter.format(1_500))
        assertEquals("12.3k", VoteCountFormatter.format(12_345))
    }

    @Test
    fun `format abbreviates millions with one decimal and an M suffix`() {
        assertEquals("1.0M", VoteCountFormatter.format(1_000_000))
        assertEquals("2.5M", VoteCountFormatter.format(2_500_000))
    }
}
