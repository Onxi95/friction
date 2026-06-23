package dev.pawelsowa.focusgate.domain

import dev.pawelsowa.focusgate.domain.model.DomainRule
import dev.pawelsowa.focusgate.domain.model.ScheduleMode
import java.time.ZonedDateTime

class RuleEvaluator {
    fun shouldBlock(rule: DomainRule, now: ZonedDateTime): Boolean {
        if (!rule.enabled) return false

        val selected = rule.schedule[now.dayOfWeek.value - 1, now.hour]
        return when (rule.scheduleMode) {
            ScheduleMode.BLOCK_DURING_SELECTED_HOURS -> selected
            ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS -> !selected
        }
    }
}
