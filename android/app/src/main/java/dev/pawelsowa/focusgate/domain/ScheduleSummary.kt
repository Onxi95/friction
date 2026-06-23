package dev.pawelsowa.focusgate.domain

import dev.pawelsowa.focusgate.domain.model.ScheduleMode
import dev.pawelsowa.focusgate.domain.model.WeeklySchedule

class ScheduleSummary {
    fun format(schedule: WeeklySchedule, mode: ScheduleMode): String {
        if (schedule.slots.none { it }) {
            return when (mode) {
                ScheduleMode.BLOCK_DURING_SELECTED_HOURS -> "Never blocked by schedule"
                ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS -> "Always blocked"
            }
        }
        if (schedule.slots.all { it }) {
            return when (mode) {
                ScheduleMode.BLOCK_DURING_SELECTED_HOURS -> "Always blocked"
                ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS -> "Always allowed"
            }
        }

        val action = when (mode) {
            ScheduleMode.BLOCK_DURING_SELECTED_HOURS -> "Blocked"
            ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS -> "Allowed"
        }
        val days = DAY_NAMES.mapIndexedNotNull { dayIndex, name ->
            val ranges = rangesForDay(schedule, dayIndex)
            if (ranges.isEmpty()) null else "$name ${ranges.joinToString(", ")}"
        }
        return "$action: ${days.joinToString("; ")}"
    }

    private fun rangesForDay(schedule: WeeklySchedule, dayIndex: Int): List<String> {
        val ranges = mutableListOf<String>()
        var start: Int? = null
        for (hour in 0..WeeklySchedule.HOURS_PER_DAY) {
            val selected = hour < WeeklySchedule.HOURS_PER_DAY && schedule[dayIndex, hour]
            if (selected && start == null) start = hour
            if (!selected && start != null) {
                ranges += "${formatHour(start)}-${formatHour(hour)}"
                start = null
            }
        }
        return ranges
    }

    private fun formatHour(hour: Int): String = hour.toString().padStart(2, '0') + ":00"

    companion object {
        val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }
}
