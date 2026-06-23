package dev.pawelsowa.focusgate.data

import androidx.datastore.core.DataStoreFactory
import dev.pawelsowa.focusgate.domain.model.DomainRule
import dev.pawelsowa.focusgate.domain.model.FocusGateErrorCode
import dev.pawelsowa.focusgate.domain.model.FocusGateException
import dev.pawelsowa.focusgate.domain.model.MatchMode
import dev.pawelsowa.focusgate.domain.model.ScheduleMode
import dev.pawelsowa.focusgate.domain.model.UnlockStatus
import dev.pawelsowa.focusgate.domain.model.WeeklySchedule
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreFocusGateRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var timeSource: FakeLockTimeSource
    private lateinit var repository: DataStoreFocusGateRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        timeSource = FakeLockTimeSource()
        repository = createRepository(temporaryFolder.root.resolve("focusgate.pb"))
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `rule persists normalized and relocks editing`() = runTest {
        repository.addRule(rule(domain = " Example.COM. "))

        val config = repository.getConfig()
        assertEquals("example.com", config.rules.single().domain)
        assertEquals(1, config.revision)
        assertEquals(UnlockStatus.Locked, config.unlockStatus)
    }

    @Test
    fun `stored rule survives repository recreation`() = runTest {
        val file = temporaryFolder.root.resolve("persistent.pb")
        val first = createRepository(file)
        first.addRule(rule())
        scope.cancel()

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val second = createRepository(file)
        assertEquals("example.com", second.getConfig().rules.single().domain)
    }

    @Test
    fun `duplicate domain is rejected after unlock`() = runTest {
        repository.addRule(rule(id = "first"))
        unlock()

        val error = expectFocusGateError {
            repository.addRule(rule(id = "second", domain = "EXAMPLE.COM"))
        }
        assertEquals(FocusGateErrorCode.DUPLICATE_DOMAIN, error.code)
    }

    @Test
    fun `locked repository rejects native write`() = runTest {
        repository.enableEditLock()

        val error = expectFocusGateError {
            repository.addRule(rule())
        }
        assertEquals(FocusGateErrorCode.EDITING_LOCKED, error.code)
    }

    @Test
    fun `locked repository rejects update and delete`() = runTest {
        repository.addRule(rule())

        val updateError = expectFocusGateError {
            repository.updateRule(rule().copy(enabled = false))
        }
        val deleteError = expectFocusGateError {
            repository.deleteRule("example")
        }

        assertEquals(FocusGateErrorCode.EDITING_LOCKED, updateError.code)
        assertEquals(FocusGateErrorCode.EDITING_LOCKED, deleteError.code)
    }

    @Test
    fun `update and delete relock editing`() = runTest {
        repository.addRule(rule())
        unlock()

        repository.updateRule(rule().copy(enabled = false))

        assertEquals(UnlockStatus.Locked, repository.getUnlockStatus())
        unlock()

        repository.deleteRule("example")

        assertEquals(UnlockStatus.Locked, repository.getUnlockStatus())
        assertEquals(emptyList<DomainRule>(), repository.getConfig().rules)
    }

    @Test
    fun `countdown uses monotonic elapsed time`() = runTest {
        repository.enableEditLock()
        repository.startUnlockCountdown()
        timeSource.elapsedMs += StoredConfigMapper.UNLOCK_DELAY_MS - 1

        assertTrue(repository.getUnlockStatus() is UnlockStatus.UnlockPending)
        val error = expectFocusGateError(repository::confirmUnlock)
        assertEquals(FocusGateErrorCode.COUNTDOWN_STILL_ACTIVE, error.code)

        timeSource.elapsedMs += 1
        repository.confirmUnlock()
        assertEquals(UnlockStatus.Unlocked, repository.getUnlockStatus())
    }

    @Test
    fun `device restart invalidates pending countdown`() = runTest {
        repository.enableEditLock()
        repository.startUnlockCountdown()
        timeSource.boot += 1
        timeSource.elapsedMs += StoredConfigMapper.UNLOCK_DELAY_MS

        assertEquals(UnlockStatus.Locked, repository.getUnlockStatus())
    }

    @Test
    fun `exported config imports into another repository and relocks editing`() = runTest {
        repository.addRule(rule(domain = "Exported.COM"))
        val exported = repository.exportConfig()

        val imported = createRepository(temporaryFolder.root.resolve("imported.pb"))
        imported.importConfig(exported)

        val config = imported.getConfig()
        assertEquals("exported.com", config.rules.single().domain)
        assertEquals(UnlockStatus.Locked, config.unlockStatus)
        assertEquals(1, config.revision)
    }

    @Test
    fun `locked repository rejects backup import`() = runTest {
        val exported = repository.exportConfig()
        repository.enableEditLock()

        val error = expectFocusGateError {
            repository.importConfig(exported)
        }

        assertEquals(FocusGateErrorCode.EDITING_LOCKED, error.code)
    }

    private suspend fun unlock() {
        repository.startUnlockCountdown()
        timeSource.elapsedMs += StoredConfigMapper.UNLOCK_DELAY_MS
        repository.confirmUnlock()
    }

    private suspend fun expectFocusGateError(
        block: suspend () -> Unit,
    ): FocusGateException =
        try {
            block()
            fail("Expected FocusGateException")
            error("Unreachable")
        } catch (exception: FocusGateException) {
            exception
        }

    private fun createRepository(file: File): DataStoreFocusGateRepository =
        DataStoreFocusGateRepository(
            dataStore = DataStoreFactory.create(
                serializer = FocusGateConfigSerializer,
                scope = scope,
                produceFile = { file },
            ),
            timeSource = timeSource,
        )

    private fun rule(
        id: String = "example",
        domain: String = "example.com",
    ): DomainRule =
        DomainRule(
            id = id,
            domain = domain,
            enabled = true,
            matchMode = MatchMode.DOMAIN_AND_SUBDOMAINS,
            scheduleMode = ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
            schedule = WeeklySchedule.empty().withSlot(0, 9, true),
        )

    private class FakeLockTimeSource : LockTimeSource {
        var elapsedMs = 1_000L
        var boot = 10

        override fun elapsedRealtime(): Long = elapsedMs
        override fun bootCount(): Int = boot
    }
}
