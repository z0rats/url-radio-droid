package com.urlradiodroid.ui

import com.urlradiodroid.data.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Test

class StationAdapterTest {
    @Test
    fun `test StationAdapter submitList updates item count`() {
        var clickedStation: RadioStation? = null
        val adapter = StationAdapter { station ->
            clickedStation = station
        }

        val stations = listOf(
            RadioStation(id = 1L, name = "Station 1", streamUrl = "http://example.com/1"),
            RadioStation(id = 2L, name = "Station 2", streamUrl = "http://example.com/2")
        )

        adapter.submitList(stations)

        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `test StationAdapter with empty list`() {
        val adapter = StationAdapter { }

        adapter.submitList(emptyList())

        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `test StationAdapter handles list updates`() {
        val adapter = StationAdapter { }

        val initialStations = listOf(
            RadioStation(id = 1L, name = "Station 1", streamUrl = "http://example.com/1")
        )
        adapter.submitList(initialStations)
        assertEquals(1, adapter.itemCount)

        val updatedStations = listOf(
            RadioStation(id = 1L, name = "Station 1 Updated", streamUrl = "http://example.com/1"),
            RadioStation(id = 2L, name = "Station 2", streamUrl = "http://example.com/2")
        )
        adapter.submitList(updatedStations)
        assertEquals(2, adapter.itemCount)
    }
}
