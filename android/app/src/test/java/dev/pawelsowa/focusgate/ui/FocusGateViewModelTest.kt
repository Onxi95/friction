package dev.pawelsowa.focusgate.ui

import dev.pawelsowa.focusgate.data.InMemoryFocusGateRepository
import dev.pawelsowa.focusgate.domain.usecase.ObserveDashboardStateUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FocusGateViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun stopVpnDoesNotCallCallbackWhenEditingIsLocked() = runTest {
        val repository = InMemoryFocusGateRepository()
        val viewModel = FocusGateViewModel(
            repository = repository,
            observeDashboardState = ObserveDashboardStateUseCase(repository),
        )
        var stopped = false
        val warning = async {
            viewModel.dashboardState.first { it.warning == "Editing is locked" }.warning
        }

        viewModel.stopVpn { stopped = true }
        advanceUntilIdle()

        assertFalse(stopped)
        assertEquals("Editing is locked", warning.await())
    }

    @Test
    fun stopVpnCallsCallbackAfterUnlock() = runTest {
        val repository = InMemoryFocusGateRepository(initiallyUnlocked = true)
        val viewModel = FocusGateViewModel(
            repository = repository,
            observeDashboardState = ObserveDashboardStateUseCase(repository),
        )
        var stopped = false

        viewModel.stopVpn { stopped = true }
        advanceUntilIdle()

        assertTrue(stopped)
    }

    @Test
    fun exportConfigShowsBackupString() = runTest {
        val repository = InMemoryFocusGateRepository()
        val viewModel = FocusGateViewModel(
            repository = repository,
            observeDashboardState = ObserveDashboardStateUseCase(repository),
        )

        viewModel.exportConfig()
        advanceUntilIdle()

        assertTrue(viewModel.backupState.value.exportedConfig.isNotBlank())
        assertEquals("Configuration exported", viewModel.backupState.value.message)
    }

    @Test
    fun lockedImportShowsMappedError() = runTest {
        val repository = InMemoryFocusGateRepository()
        val viewModel = FocusGateViewModel(
            repository = repository,
            observeDashboardState = ObserveDashboardStateUseCase(repository),
        )

        viewModel.setImportText("backup")
        viewModel.importConfig()
        advanceUntilIdle()

        assertEquals("Editing is locked", viewModel.backupState.value.error)
    }

    @Test
    fun unlockedImportShowsSuccess() = runTest {
        val repository = InMemoryFocusGateRepository(initiallyUnlocked = true)
        val viewModel = FocusGateViewModel(
            repository = repository,
            observeDashboardState = ObserveDashboardStateUseCase(repository),
        )

        viewModel.setImportText("backup")
        viewModel.importConfig()
        advanceUntilIdle()

        assertEquals("Configuration imported and editing locked", viewModel.backupState.value.message)
    }

    @Test
    fun domainRowsShowVpnInactiveWhenFilteringIsStopped() = runTest {
        val repository = InMemoryFocusGateRepository()
        val viewModel = FocusGateViewModel(
            repository = repository,
            observeDashboardState = ObserveDashboardStateUseCase(repository),
        )

        val domains = viewModel.domains.first { it.isNotEmpty() }

        assertTrue(domains.all { it.status == "VPN inactive" })
    }

    @Test
    fun domainRowsShowRuleStatusWhenFilteringRuns() = runTest {
        val repository = InMemoryFocusGateRepository()
        val viewModel = FocusGateViewModel(
            repository = repository,
            observeDashboardState = ObserveDashboardStateUseCase(repository),
        )

        repository.startVpn()
        val domains = viewModel.domains.first { items ->
            items.isNotEmpty() && items.all { it.status != "VPN inactive" }
        }

        assertTrue(domains.any { it.status.startsWith("Allowed now") || it.status.startsWith("Blocked now") })
    }

    @Test
    fun schedulePresetsSelectExpectedSlots() = runTest {
        val repository = InMemoryFocusGateRepository()
        val viewModel = FocusGateViewModel(
            repository = repository,
            observeDashboardState = ObserveDashboardStateUseCase(repository),
        )

        viewModel.applySchedulePreset("Select all")

        assertTrue(viewModel.editorState.value.schedule.slots.all { it })

        viewModel.applySchedulePreset("Weekdays")

        assertTrue((0 until 5).all { day ->
            (0 until 24).all { hour -> viewModel.editorState.value.schedule[day, hour] }
        })
        assertFalse((5 until 7).any { day ->
            (0 until 24).any { hour -> viewModel.editorState.value.schedule[day, hour] }
        })

        viewModel.applySchedulePreset("Weekend")

        assertFalse((0 until 5).any { day ->
            (0 until 24).any { hour -> viewModel.editorState.value.schedule[day, hour] }
        })
        assertTrue((5 until 7).all { day ->
            (0 until 24).all { hour -> viewModel.editorState.value.schedule[day, hour] }
        })
    }

    @Test
    fun copiedSelectedDayCanBePastedToAnotherDay() = runTest {
        val repository = InMemoryFocusGateRepository()
        val viewModel = FocusGateViewModel(
            repository = repository,
            observeDashboardState = ObserveDashboardStateUseCase(repository),
        )

        viewModel.setScheduleSlot(dayIndex = 0, hour = 9, selected = true)
        viewModel.applySchedulePreset("Copy selected day")
        viewModel.setScheduleSlot(dayIndex = 6, hour = 22, selected = true)
        viewModel.applySchedulePreset("Paste schedule")

        assertTrue(viewModel.editorState.value.schedule[6, 9])
        assertFalse(viewModel.editorState.value.schedule[6, 22])
    }
}
