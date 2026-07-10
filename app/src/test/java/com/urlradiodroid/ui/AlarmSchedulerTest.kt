package com.urlradiodroid.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/** Covers [AlarmScheduler.nextTriggerMillis]'s pure "today or tomorrow" decision. */
class AlarmSchedulerTest {
    private fun calendarAt(
        hour: Int,
        minute: Int,
        second: Int = 0,
    ): Calendar =
        Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 15, hour, minute, second)
            set(Calendar.MILLISECOND, 0)
        }

    @Test
    fun `schedules later today when the target time hasn't passed yet`() {
        val now = calendarAt(6, 0)
        val expected = calendarAt(7, 30)

        val result = AlarmScheduler.nextTriggerMillis(7, 30, now.timeInMillis)

        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `schedules tomorrow when the target time already passed today`() {
        val now = calendarAt(8, 0)
        val expected =
            calendarAt(7, 30).apply { add(Calendar.DAY_OF_YEAR, 1) }

        val result = AlarmScheduler.nextTriggerMillis(7, 30, now.timeInMillis)

        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `schedules tomorrow when now is exactly the target time`() {
        val now = calendarAt(7, 30, second = 0)
        val expected = calendarAt(7, 30).apply { add(Calendar.DAY_OF_YEAR, 1) }

        val result = AlarmScheduler.nextTriggerMillis(7, 30, now.timeInMillis)

        assertEquals(expected.timeInMillis, result)
    }
}
