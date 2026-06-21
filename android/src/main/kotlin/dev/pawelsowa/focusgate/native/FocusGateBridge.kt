package dev.pawelsowa.focusgate.native

import dev.pawelsowa.focusgate.config.AppConfig
import dev.pawelsowa.focusgate.config.ConfigRepository
import dev.pawelsowa.focusgate.config.VpnConfig
import dev.pawelsowa.focusgate.domain.DomainNormalizer
import dev.pawelsowa.focusgate.domain.DomainRule
import dev.pawelsowa.focusgate.domain.MatchMode
import dev.pawelsowa.focusgate.domain.ScheduleMode
import dev.pawelsowa.focusgate.env.DeviceContext
import dev.pawelsowa.focusgate.lock.EditLockState
import dev.pawelsowa.focusgate.lock.LockManager
import dev.pawelsowa.focusgate.vpn.FocusGateVpnService
import dev.pawelsowa.focusgate.vpn.VpnStatus
import java.time.ZonedDateTime

class FocusGateBridge(
    private val repository: ConfigRepository,
    private val vpnService: FocusGateVpnService,
    private val deviceContext: DeviceContext,
    private val domainNormalizer: DomainNormalizer = DomainNormalizer(),
    private val lockManager: LockManager = LockManager(),
) : FocusGateNativeModule {
    override suspend fun getConfig(): AppConfigDto = repository.read().toDto(vpnService.getStatus())

    override suspend fun getDiagnostics(): VpnDiagnosticsDto =
        repository.read().let { config ->
            vpnService
                .buildDiagnostics(config, ZonedDateTime.now())
                .toDto()
        }

    override suspend fun addRule(rule: DomainRuleDto) {
        repository.write { config ->
            val normalizedRule = rule.toDomainRule(config)
            lockManager.requireEditingUnlocked(config.lockState)
            config.copy(
                rules = config.rules + normalizedRule,
                lockState = lockManager.relockAfterWrite(),
            )
        }
    }

    override suspend fun updateRule(rule: DomainRuleDto) {
        repository.write { config ->
            val normalizedRule = rule.toDomainRule(config, rule.id)
            lockManager.requireEditingUnlocked(config.lockState)
            config.copy(
                rules = config.rules.filterNot { it.id == rule.id } + normalizedRule,
                lockState = lockManager.relockAfterWrite(),
            )
        }
    }

    override suspend fun deleteRule(ruleId: String) {
        repository.write { config ->
            lockManager.requireEditingUnlocked(config.lockState)
            config.copy(
                rules = config.rules.filterNot { it.id == ruleId },
                lockState = lockManager.relockAfterWrite(),
            )
        }
    }

    override suspend fun exportConfig(): AppConfigDto = repository.read().toDto(vpnService.getStatus())

    override suspend fun importConfig(config: AppConfigDto) {
        repository.write { current ->
            lockManager.requireEditingUnlocked(current.lockState)
            require(config.filteredApplications.isNotEmpty()) { "INVALID_FILTERED_APPLICATIONS" }
            require(config.upstreamDns.ip.isNotBlank()) { "INVALID_UPSTREAM_DNS" }
            require(config.upstreamDns.port in 1..65535) { "INVALID_UPSTREAM_DNS" }
            current.copy(
                rules = config.rules.map { rule -> rule.toDomainRule(current) },
                vpnConfig =
                    VpnConfig(
                        upstreamDnsIp = config.upstreamDns.ip,
                        upstreamDnsPort = config.upstreamDns.port,
                        filteredApplications = config.filteredApplications,
                    ),
                lockState = lockManager.relockAfterWrite(),
            )
        }
    }

    override suspend fun resetConfig() {
        repository.write { current ->
            lockManager.requireEditingUnlocked(current.lockState)
            current.copy(
                rules = emptyList(),
                vpnConfig = VpnConfig(),
                lockState = lockManager.relockAfterWrite(),
            )
        }
    }

    override suspend fun updateVpnConfig(config: VpnConfigDto) {
        repository.write { current ->
            lockManager.requireEditingUnlocked(current.lockState)
            require(config.filteredApplications.isNotEmpty()) { "INVALID_FILTERED_APPLICATIONS" }
            require(config.upstreamDns.ip.isNotBlank()) { "INVALID_UPSTREAM_DNS" }
            require(config.upstreamDns.port in 1..65535) { "INVALID_UPSTREAM_DNS" }
            current.copy(
                vpnConfig =
                    VpnConfig(
                        upstreamDnsIp = config.upstreamDns.ip,
                        upstreamDnsPort = config.upstreamDns.port,
                        filteredApplications = config.filteredApplications,
                    ),
                lockState = lockManager.relockAfterWrite(),
            )
        }
    }

    override suspend fun startVpn() {
        vpnService.start(repository.read())
    }

    override suspend fun stopVpn() {
        vpnService.stop()
    }

    override suspend fun getVpnStatus(): String = vpnService.getStatus().name

    override suspend fun enableEditLock() {
        repository.write { config ->
            config.copy(lockState = EditLockState.Locked)
        }
    }

    override suspend fun startUnlockCountdown(): UnlockStatusDto {
        val nextConfig =
            repository.write { config ->
                config.copy(
                    lockState =
                        lockManager.startUnlockCountdown(
                            elapsedRealtimeMs = deviceContext.elapsedRealtimeMs(),
                            bootCount = deviceContext.bootCount(),
                        ),
                )
            }
        return nextConfig.unlockStatusDto()
    }

    override suspend fun getUnlockStatus(): UnlockStatusDto = repository.read().unlockStatusDto()

    override suspend fun confirmUnlock() {
        repository.write { config ->
            config.copy(
                lockState =
                    lockManager.confirmUnlock(
                        state = config.lockState,
                        elapsedRealtimeMs = deviceContext.elapsedRealtimeMs(),
                        bootCount = deviceContext.bootCount(),
                    ),
            )
        }
    }

    override suspend fun cancelUnlockCountdown() {
        repository.write { config ->
            config.copy(lockState = EditLockState.Locked)
        }
    }

    private fun DomainRuleDto.toDomainRule(
        config: AppConfig,
        selfId: String? = null,
    ): DomainRule {
        require(weeklySlots.size == 168) { "INVALID_SCHEDULE" }
        val normalizedDomain = domainNormalizer.normalize(domain)
        val duplicateExists =
            config.rules.any { existing ->
                existing.domain == normalizedDomain && existing.id != selfId
            }
        require(!duplicateExists) { "DUPLICATE_DOMAIN" }
        return DomainRule(
            id = id,
            domain = normalizedDomain,
            enabled = enabled,
            matchMode = MatchMode.valueOf(matchMode),
            scheduleMode = ScheduleMode.valueOf(scheduleMode),
            weeklySlots = weeklySlots.toBooleanArray(),
        )
    }

    private fun AppConfig.toDto(vpnStatus: VpnStatus): AppConfigDto =
        AppConfigDto(
            rules =
                rules.map { rule ->
                    DomainRuleDto(
                        id = rule.id,
                        domain = rule.domain,
                        enabled = rule.enabled,
                        matchMode = rule.matchMode.name,
                        scheduleMode = rule.scheduleMode.name,
                        weeklySlots = rule.weeklySlots.toList(),
                    )
                },
            lockState =
                when (lockState) {
                    EditLockState.Locked -> "LOCKED"
                    EditLockState.Unlocked -> "UNLOCKED"
                    is EditLockState.UnlockPending -> "UNLOCK_PENDING"
                },
            vpnStatus = vpnStatus.name,
            filteredApplications = vpnConfig.filteredApplications,
            upstreamDns =
                UpstreamDnsDto(
                    ip = vpnConfig.upstreamDnsIp,
                    port = vpnConfig.upstreamDnsPort,
                ),
        )

    private fun AppConfig.unlockStatusDto(): UnlockStatusDto {
        val status =
            lockManager.getUnlockStatus(
                state = lockState,
                elapsedRealtimeMs = deviceContext.elapsedRealtimeMs(),
                bootCount = deviceContext.bootCount(),
            )
        return UnlockStatusDto(
            state = status.state,
            remainingMs =
                if (status.state == "UNLOCK_PENDING") {
                    status.remainingMs
                } else {
                    null
                },
            canConfirm =
                if (status.state == "UNLOCK_PENDING") {
                    status.canConfirm
                } else {
                    null
                },
        )
    }

    private fun dev.pawelsowa.focusgate.vpn.VpnDiagnostics.toDto(): VpnDiagnosticsDto =
        VpnDiagnosticsDto(
            vpnActive = vpnActive,
            dnsInterception = dnsInterception,
            braveFilteringTest = braveFilteringTest,
            secureDnsWarning = secureDnsWarning,
            blockedDomainsNow = blockedDomainsNow,
            restartRequired = restartRequired,
            restartReason = restartReason,
            networkAvailable = networkAvailable,
        )
}
