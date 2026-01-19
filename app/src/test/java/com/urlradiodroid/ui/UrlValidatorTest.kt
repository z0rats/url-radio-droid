package com.urlradiodroid.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {
    @Test
    fun `test isValidUrl with valid http URL`() {
        assertTrue(AddStationActivity.isValidUrl("http://example.com/stream"))
    }

    @Test
    fun `test isValidUrl with valid https URL`() {
        assertTrue(AddStationActivity.isValidUrl("https://example.com/stream"))
    }

    @Test
    fun `test isValidUrl with URL containing port`() {
        assertTrue(AddStationActivity.isValidUrl("http://stream.example.com:8000/live"))
    }

    @Test
    fun `test isValidUrl with URL containing path`() {
        assertTrue(AddStationActivity.isValidUrl("https://radio.example.com/stream/audio"))
    }

    @Test
    fun `test isValidUrl rejects invalid URL without protocol`() {
        assertFalse(AddStationActivity.isValidUrl("example.com/stream"))
    }

    @Test
    fun `test isValidUrl rejects invalid URL with ftp protocol`() {
        assertFalse(AddStationActivity.isValidUrl("ftp://example.com/file"))
    }

    @Test
    fun `test isValidUrl rejects empty string`() {
        assertFalse(AddStationActivity.isValidUrl(""))
    }

    @Test
    fun `test isValidUrl rejects malformed URL`() {
        assertFalse(AddStationActivity.isValidUrl("not a url"))
    }

    @Test
    fun `test isValidUrl rejects URL with spaces`() {
        assertFalse(AddStationActivity.isValidUrl("http://example.com/stream with spaces"))
    }
}
