package dev.pawelsowa.focusgate.domain.usecase

import dev.pawelsowa.focusgate.domain.model.AppConfig
import dev.pawelsowa.focusgate.domain.model.UnlockStatus
import dev.pawelsowa.focusgate.domain.model.VpnStatus
import dev.pawelsowa.focusgate.domain.repository.FocusGateRepository
import dev.pawelsowa.focusgate.ui.DashboardState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dev.pawelsowa.focusgate.domain.RuleEvaluator
import java.time.ZonedDateTime

class ObserveDashboardStateUseCase(
    private val repository: FocusGateRepository,
    private val now: () -> ZonedDateTime = ZonedDateTime::now,
    private val evaluator: RuleEvaluator = RuleEvaluator(),
) {
    operator fun invoke(): Flow<DashboardState> =
        repository.observeConfig().map(::toDashboardState)

    private fun toDashboardState(config: AppConfig): DashboardState {
        val activeRules = config.rules.count { it.enabled }

        return DashboardState(
            vpnStatus = when (config.vpnStatus) {
                VpnStatus.STOPPED -> "Inactive"
                VpnStatus.STARTING -> "Starting"
                VpnStatus.RUNNING -> "Active"
                VpnStatus.ERROR -> "Error"
            },
            editingStatus = when (val unlockStatus = config.unlockStatus) {
                UnlockStatus.Unlocked -> "Unlocked"
                UnlockStatus.Locked -> "Locked"
                is UnlockStatus.UnlockPending -> {
                    val remainingMinutes = unlockStatus.remainingMs / 60_000
                    "Unlocking in ${remainingMinutes}m"
                }
            },
            activeRuleCount = activeRules,
            blockedNowCount = blockedNowCount(config),
            warning = if (config.vpnStatus == VpnStatus.RUNNING) null else "Filtering inactive",
        )
    }

    private fun blockedNowCount(config: AppConfig): Int =
        config.rules.count { rule -> evaluator.shouldBlock(rule, now()) }
}
