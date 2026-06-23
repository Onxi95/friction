package dev.pawelsowa.focusgate.domain

import dev.pawelsowa.focusgate.domain.model.DomainRule
import dev.pawelsowa.focusgate.domain.model.MatchMode
import dev.pawelsowa.focusgate.domain.model.ScheduleMode
import dev.pawelsowa.focusgate.domain.model.WeeklySchedule
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEvaluatorTest {
    private val evaluator = RuleEvaluator()
    private val mondayAtNine =
        ZonedDateTime.of(2026, 6, 22, 9, 0, 0, 0, ZoneId.of("Europe/Warsaw"))

    @Test
    fun `schedule index maps Monday zero through Sunday twenty three`() {
        assertTrue(WeeklySchedule.slotIndex(0, 0) == 0)
        assertTrue(WeeklySchedule.slotIndex(6, 23) == 167)
    }

    @Test
    fun `block mode blocks selected hour`() {
        val rule = rule(
            ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
            WeeklySchedule.empty().withSlot(0, 9, true),
        )

        assertTrue(evaluator.shouldBlock(rule, mondayAtNine))
        assertFalse(evaluator.shouldBlock(rule, mondayAtNine.plusHours(1)))
    }

    @Test
    fun `allow-only mode blocks unselected hour`() {
        val rule = rule(
            ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
            WeeklySchedule.empty().withSlot(0, 9, true),
        )

        assertFalse(evaluator.shouldBlock(rule, mondayAtNine))
        assertTrue(evaluator.shouldBlock(rule, mondayAtNine.plusHours(1)))
    }

    @Test
    fun `disabled rule never blocks`() {
        val rule = rule(
            ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
            WeeklySchedule.selected(),
        ).copy(enabled = false)

        assertFalse(evaluator.shouldBlock(rule, mondayAtNine))
    }

    private fun rule(
        mode: ScheduleMode,
        schedule: WeeklySchedule,
    ): DomainRule =
        DomainRule(
            id = "example",
            domain = "example.com",
            enabled = true,
            matchMode = MatchMode.EXACT,
            scheduleMode = mode,
            schedule = schedule,
        )
}
