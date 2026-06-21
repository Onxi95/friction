package dev.pawelsowa.focusgate.config

import dev.pawelsowa.focusgate.domain.DomainRule
import dev.pawelsowa.focusgate.lock.EditLockState

data class VpnConfig(
    val upstreamDnsIp: String = "1.1.1.1",
    val upstreamDnsPort: Int = 53,
    val filteredApplications: List<String> = listOf("com.brave.browser"),
)

data class AppConfig(
    val rules: List<DomainRule> = emptyList(),
    val lockState: EditLockState = EditLockState.Locked,
    val vpnConfig: VpnConfig = VpnConfig(),
    val revision: Long = 0,
)
