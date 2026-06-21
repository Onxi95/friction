package dev.pawelsowa.focusgate.domain

import java.time.ZonedDateTime

object RuleEvaluator {
    fun getSlotIndex(dayIndex: Int, hour: Int): Int {
        require(dayIndex in 0..6) { "INVALID_SCHEDULE" }
        require(hour in 0..23) { "INVALID_SCHEDULE" }
        return dayIndex * 24 + hour
    }

    fun shouldBlock(rule: DomainRule, now: ZonedDateTime): Boolean {
        if (!rule.enabled) {
            return false
        }

        val dayIndex = now.dayOfWeek.value - 1
        val slotIndex = getSlotIndex(dayIndex, now.hour)
        val selected = rule.weeklySlots[slotIndex]

        return when (rule.scheduleMode) {
            ScheduleMode.BLOCK_DURING_SELECTED_HOURS -> selected
            ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS -> !selected
        }
    }
}
