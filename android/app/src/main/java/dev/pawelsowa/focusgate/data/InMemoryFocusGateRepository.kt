package dev.pawelsowa.focusgate.data

import dev.pawelsowa.focusgate.domain.DomainNormalizer
import dev.pawelsowa.focusgate.domain.model.AppConfig
import dev.pawelsowa.focusgate.domain.model.DomainRule
import dev.pawelsowa.focusgate.domain.model.FocusGateErrorCode
import dev.pawelsowa.focusgate.domain.model.FocusGateException
import dev.pawelsowa.focusgate.domain.model.MatchMode
import dev.pawelsowa.focusgate.domain.model.ScheduleMode
import dev.pawelsowa.focusgate.domain.model.UnlockStatus
import dev.pawelsowa.focusgate.domain.model.VpnStatus
import dev.pawelsowa.focusgate.domain.repository.FocusGateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class InMemoryFocusGateRepository(
    initiallyUnlocked: Boolean = false,
    private val normalizer: DomainNormalizer = DomainNormalizer(),
) : FocusGateRepository {
    private val state = MutableStateFlow(sampleConfig(initiallyUnlocked))

    override fun observeConfig(): Flow<AppConfig> = state
    override suspend fun getConfig(): AppConfig = state.value

    override suspend fun addRule(rule: DomainRule) {
        requireUnlocked()
        state.update { config ->
            config.copy(
                rules = config.rules + validateRule(rule, config, ignoredRuleId = null),
                unlockStatus = UnlockStatus.Locked,
            )
        }
    }

    override suspend fun updateRule(rule: DomainRule) {
        requireUnlocked()
        state.update { config ->
            val validated = validateRule(rule, config, ignoredRuleId = rule.id)
            config.copy(
                rules = config.rules.map { existing ->
                    if (existing.id == rule.id) validated else existing
                },
                unlockStatus = UnlockStatus.Locked,
            )
        }
    }

    override suspend fun deleteRule(ruleId: String) {
        requireUnlocked()
        state.update {
            it.copy(
                rules = it.rules.filterNot { rule -> rule.id == ruleId },
                unlockStatus = UnlockStatus.Locked,
            )
        }
    }

    override suspend fun startVpn() {
        state.update { it.copy(vpnStatus = VpnStatus.RUNNING) }
    }

    override suspend fun stopVpn() {
        requireUnlocked()
        state.update { it.copy(vpnStatus = VpnStatus.STOPPED, unlockStatus = UnlockStatus.Locked) }
    }

    override suspend fun getVpnStatus(): VpnStatus = state.value.vpnStatus

    override suspend fun exportConfig(): String = state.value.toString()

    override suspend fun importConfig(encodedConfig: String) {
        requireUnlocked()
        state.update { it.copy(unlockStatus = UnlockStatus.Locked) }
    }

    override suspend fun enableEditLock() {
        state.update { it.copy(unlockStatus = UnlockStatus.Locked) }
    }

    override suspend fun startUnlockCountdown(): UnlockStatus {
        val status = UnlockStatus.UnlockPending(
            remainingMs = 5 * 60 * 1000L,
            canConfirm = false,
        )
        state.update { it.copy(unlockStatus = status) }
        return status
    }

    override suspend fun getUnlockStatus(): UnlockStatus = state.value.unlockStatus

    override suspend fun confirmUnlock() {
        state.update { it.copy(unlockStatus = UnlockStatus.Unlocked) }
    }

    override suspend fun cancelUnlockCountdown() {
        state.update { it.copy(unlockStatus = UnlockStatus.Locked) }
    }

    private fun requireUnlocked() {
        if (state.value.unlockStatus != UnlockStatus.Unlocked) {
            throw FocusGateException(
                FocusGateErrorCode.EDITING_LOCKED,
                "Editing is locked",
            )
        }
    }

    private fun validateRule(
        rule: DomainRule,
        config: AppConfig,
        ignoredRuleId: String?,
    ): DomainRule {
        val normalized = normalizer.normalize(rule.domain).getOrElse { cause ->
            throw FocusGateException(
                FocusGateErrorCode.INVALID_DOMAIN,
                cause.message ?: "Invalid domain",
            )
        }
        if (config.rules.any { it.id != ignoredRuleId && it.domain == normalized }) {
            throw FocusGateException(
                FocusGateErrorCode.DUPLICATE_DOMAIN,
                "Domain already exists",
            )
        }
        return rule.copy(domain = normalized)
    }

    private fun sampleConfig(initiallyUnlocked: Boolean): AppConfig =
        AppConfig(
            rules = listOf(
                DomainRule(
                    id = "facebook",
                    domain = "facebook.com",
                    enabled = true,
                    matchMode = MatchMode.DOMAIN_AND_SUBDOMAINS,
                    scheduleMode = ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
                    schedule = dev.pawelsowa.focusgate.domain.model.WeeklySchedule(
                        List(168) { index -> index in 19..20 },
                    ),
                ),
                DomainRule(
                    id = "reddit",
                    domain = "reddit.com",
                    enabled = true,
                    matchMode = MatchMode.EXACT,
                    scheduleMode = ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
                    schedule = dev.pawelsowa.focusgate.domain.model.WeeklySchedule.empty(),
                ),
            ),
            unlockStatus = if (initiallyUnlocked) UnlockStatus.Unlocked else UnlockStatus.Locked,
            vpnStatus = VpnStatus.STOPPED,
        )
}
