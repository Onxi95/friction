package dev.pawelsowa.focusgate.domain

import dev.pawelsowa.focusgate.domain.model.WeeklySchedule

object ScheduleEditor {
    fun toggleDay(schedule: WeeklySchedule, dayIndex: Int): WeeklySchedule {
        val select = (0 until WeeklySchedule.HOURS_PER_DAY).any { !schedule[dayIndex, it] }
        return WeeklySchedule(
            schedule.slots.mapIndexed { index, current ->
                if (index / WeeklySchedule.HOURS_PER_DAY == dayIndex) select else current
            },
        )
    }

    fun toggleHour(schedule: WeeklySchedule, hour: Int): WeeklySchedule {
        val select = (0 until WeeklySchedule.DAYS).any { !schedule[it, hour] }
        return WeeklySchedule(
            schedule.slots.mapIndexed { index, current ->
                if (index % WeeklySchedule.HOURS_PER_DAY == hour) select else current
            },
        )
    }

    fun weekdays(schedule: WeeklySchedule): WeeklySchedule =
        WeeklySchedule(
            schedule.slots.mapIndexed { index, current ->
                if (index / WeeklySchedule.HOURS_PER_DAY < 5) true else current
            },
        )

    fun weekend(schedule: WeeklySchedule): WeeklySchedule =
        WeeklySchedule(
            schedule.slots.mapIndexed { index, current ->
                if (index / WeeklySchedule.HOURS_PER_DAY >= 5) true else current
            },
        )

    fun copyMondayToWeekdays(schedule: WeeklySchedule): WeeklySchedule =
        WeeklySchedule(
            schedule.slots.mapIndexed { index, current ->
                val day = index / WeeklySchedule.HOURS_PER_DAY
                val hour = index % WeeklySchedule.HOURS_PER_DAY
                if (day in 1..4) schedule[0, hour] else current
            },
        )

    fun day(schedule: WeeklySchedule, dayIndex: Int): List<Boolean> =
        (0 until WeeklySchedule.HOURS_PER_DAY).map { schedule[dayIndex, it] }

    fun pasteDay(
        schedule: WeeklySchedule,
        dayIndex: Int,
        day: List<Boolean>,
    ): WeeklySchedule {
        require(day.size == WeeklySchedule.HOURS_PER_DAY)
        return WeeklySchedule(
            schedule.slots.mapIndexed { index, current ->
                val hour = index % WeeklySchedule.HOURS_PER_DAY
                if (index / WeeklySchedule.HOURS_PER_DAY == dayIndex) day[hour] else current
            },
        )
    }
}
