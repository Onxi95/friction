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

    @Test
    fun `day boundary moves from Monday twenty three to Tuesday zero`() {
        val rule = rule(
            ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
            WeeklySchedule.empty()
                .withSlot(0, 23, true)
                .withSlot(1, 0, false),
        )
        val mondayAtTwentyThree =
            ZonedDateTime.of(2026, 6, 22, 23, 30, 0, 0, ZoneId.of("Europe/Warsaw"))

        assertTrue(evaluator.shouldBlock(rule, mondayAtTwentyThree))
        assertFalse(evaluator.shouldBlock(rule, mondayAtTwentyThree.plusHours(1)))
    }

    @Test
    fun `week boundary moves from Sunday twenty three to Monday zero`() {
        val rule = rule(
            ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
            WeeklySchedule.empty()
                .withSlot(6, 23, true)
                .withSlot(0, 0, false),
        )
        val sundayAtTwentyThree =
            ZonedDateTime.of(2026, 6, 28, 23, 30, 0, 0, ZoneId.of("Europe/Warsaw"))

        assertTrue(evaluator.shouldBlock(rule, sundayAtTwentyThree))
        assertFalse(evaluator.shouldBlock(rule, sundayAtTwentyThree.plusHours(1)))
    }

    @Test
    fun `repeated daylight saving hour uses same schedule slot`() {
        val zone = ZoneId.of("Europe/Warsaw")
        val rule = rule(
            ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
            WeeklySchedule.empty().withSlot(6, 2, true),
        )
        val firstLocalTwo =
            ZonedDateTime.of(2026, 10, 25, 2, 30, 0, 0, zone).withEarlierOffsetAtOverlap()
        val secondLocalTwo = firstLocalTwo.withLaterOffsetAtOverlap()

        assertTrue(evaluator.shouldBlock(rule, firstLocalTwo))
        assertTrue(evaluator.shouldBlock(rule, secondLocalTwo))
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
