package dev.pawelsowa.focusgate.data

import dev.pawelsowa.focusgate.domain.model.DomainRule
import dev.pawelsowa.focusgate.domain.model.FocusGateErrorCode
import dev.pawelsowa.focusgate.domain.model.FocusGateException
import dev.pawelsowa.focusgate.domain.model.MatchMode
import dev.pawelsowa.focusgate.domain.model.ScheduleMode
import dev.pawelsowa.focusgate.domain.model.UnlockStatus
import dev.pawelsowa.focusgate.domain.model.WeeklySchedule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class InMemoryFocusGateRepositoryTest {
    @Test
    fun writesRelockEditing() = runTest {
        val repository = InMemoryFocusGateRepository(initiallyUnlocked = true)

        repository.addRule(rule())

        assertEquals(UnlockStatus.Locked, repository.getUnlockStatus())
    }

    @Test
    fun lockedRepositoryRejectsWrites() = runTest {
        val repository = InMemoryFocusGateRepository()
        repository.enableEditLock()

        val error = try {
            repository.addRule(rule())
            fail("Expected FocusGateException")
            error("Unreachable")
        } catch (exception: FocusGateException) {
            exception
        }

        assertEquals(FocusGateErrorCode.EDITING_LOCKED, error.code)
    }

    private fun rule(): DomainRule =
        DomainRule(
            id = "example",
            domain = "example.com",
            enabled = true,
            matchMode = MatchMode.EXACT,
            scheduleMode = ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
            schedule = WeeklySchedule.empty(),
        )
}
