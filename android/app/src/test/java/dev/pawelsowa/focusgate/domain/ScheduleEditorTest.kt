package dev.pawelsowa.focusgate.domain

import dev.pawelsowa.focusgate.domain.model.ScheduleMode
import dev.pawelsowa.focusgate.domain.model.WeeklySchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEditorTest {
    @Test
    fun `day and hour toggles select whole axes`() {
        val day = ScheduleEditor.toggleDay(WeeklySchedule.empty(), 2)
        assertTrue((0 until 24).all { day[2, it] })
        assertFalse(day[1, 0])

        val hour = ScheduleEditor.toggleHour(WeeklySchedule.empty(), 9)
        assertTrue((0 until 7).all { hour[it, 9] })
        assertFalse(hour[0, 10])
    }

    @Test
    fun `copy and paste preserve selected day`() {
        val monday = WeeklySchedule.empty().withSlot(0, 9, true)
        val copied = ScheduleEditor.day(monday, 0)
        val pasted = ScheduleEditor.pasteDay(monday, 6, copied)

        assertTrue(pasted[6, 9])
        assertFalse(pasted[6, 10])
    }

    @Test
    fun `summary combines contiguous hours`() {
        val schedule = WeeklySchedule.empty()
            .withSlot(0, 9, true)
            .withSlot(0, 10, true)

        assertEquals(
            "Allowed: Mon 09:00-11:00",
            ScheduleSummary().format(
                schedule,
                ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
            ),
        )
    }
}
