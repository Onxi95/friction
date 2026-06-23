package dev.pawelsowa.focusgate.domain.usecase

import app.cash.turbine.test
import dev.pawelsowa.focusgate.data.InMemoryFocusGateRepository
import kotlinx.coroutines.test.runTest
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveDashboardStateUseCaseTest {
    @Test
    fun `maps repository config into dashboard state`() = runTest {
        val repository = InMemoryFocusGateRepository()
        val useCase = ObserveDashboardStateUseCase(
            repository = repository,
            now = {
                ZonedDateTime.of(
                    2026,
                    6,
                    22,
                    9,
                    0,
                    0,
                    0,
                    ZoneId.of("Europe/Warsaw"),
                )
            },
        )

        useCase().test {
            val state = awaitItem()
            assertEquals("Inactive", state.vpnStatus)
            assertEquals("Locked", state.editingStatus)
            assertEquals(2, state.activeRuleCount)
            assertEquals(1, state.blockedNowCount)
            assertEquals("Filtering inactive", state.warning)
        }
    }
}
