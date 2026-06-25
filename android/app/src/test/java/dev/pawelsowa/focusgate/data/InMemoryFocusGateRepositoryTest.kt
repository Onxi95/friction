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

    @Test
    fun lockedRepositoryRejectsStoppingVpn() = runTest {
        val repository = InMemoryFocusGateRepository()

        val error = try {
            repository.stopVpn()
            fail("Expected FocusGateException")
            error("Unreachable")
        } catch (exception: FocusGateException) {
            exception
        }

        assertEquals(FocusGateErrorCode.EDITING_LOCKED, error.code)
    }

    @Test
    fun stoppingVpnRelocksEditing() = runTest {
        val repository = InMemoryFocusGateRepository(initiallyUnlocked = true)

        repository.stopVpn()

        assertEquals(UnlockStatus.Locked, repository.getUnlockStatus())
    }

    @Test
    fun addRuleNormalizesDomain() = runTest {
        val repository = InMemoryFocusGateRepository(initiallyUnlocked = true)

        repository.addRule(rule(domain = " Example.COM. "))

        assertEquals("example.com", repository.getConfig().rules.last().domain)
    }

    @Test
    fun invalidDomainIsRejected() = runTest {
        val repository = InMemoryFocusGateRepository(initiallyUnlocked = true)

        val error = try {
            repository.addRule(rule(domain = "https://example.com/path"))
            fail("Expected FocusGateException")
            error("Unreachable")
        } catch (exception: FocusGateException) {
            exception
        }

        assertEquals(FocusGateErrorCode.INVALID_DOMAIN, error.code)
    }

    @Test
    fun duplicateDomainIsRejected() = runTest {
        val repository = InMemoryFocusGateRepository(initiallyUnlocked = true)

        val error = try {
            repository.addRule(rule(domain = "FACEBOOK.COM"))
            fail("Expected FocusGateException")
            error("Unreachable")
        } catch (exception: FocusGateException) {
            exception
        }

        assertEquals(FocusGateErrorCode.DUPLICATE_DOMAIN, error.code)
    }

    private fun rule(domain: String = "example.com"): DomainRule =
        DomainRule(
            id = "example",
            domain = domain,
            enabled = true,
            matchMode = MatchMode.EXACT,
            scheduleMode = ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
            schedule = WeeklySchedule.empty(),
        )
}
