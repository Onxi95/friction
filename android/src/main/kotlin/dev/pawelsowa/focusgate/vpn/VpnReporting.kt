package dev.pawelsowa.focusgate.vpn

import dev.pawelsowa.focusgate.config.AppConfig
import dev.pawelsowa.focusgate.domain.RuleEvaluator
import java.time.ZonedDateTime

data class ForegroundNotification(
    val title: String,
    val message: String,
    val actions: List<String>,
)

data class VpnDiagnostics(
    val vpnActive: Boolean,
    val dnsInterception: String,
    val braveFilteringTest: String,
    val secureDnsWarning: String,
    val blockedDomainsNow: Int,
    val restartRequired: Boolean,
    val restartReason: String?,
    val networkAvailable: Boolean,
)

object VpnReporting {
    fun buildForegroundNotification(
        config: AppConfig,
        vpnStatus: VpnStatus,
        now: ZonedDateTime,
        runtimeState: VpnRuntimeState = VpnRuntimeState(),
    ): ForegroundNotification {
        val blockedDomainsNow = countBlockedDomains(config, now)
        return ForegroundNotification(
            title =
                if (runtimeState.restartRequired) {
                    "FocusGate restart required"
                } else if (vpnStatus == VpnStatus.RUNNING) {
                    "FocusGate is active"
                } else {
                    "FocusGate is inactive"
                },
            message =
                if (runtimeState.restartRequired) {
                    "Reconnect VPN to apply ${runtimeState.restartReason}"
                } else {
                    "$blockedDomainsNow domains are currently blocked"
                },
            actions = listOf("Open", "View status"),
        )
    }

    fun buildDiagnostics(
        config: AppConfig,
        vpnStatus: VpnStatus,
        now: ZonedDateTime,
        runtimeState: VpnRuntimeState = VpnRuntimeState(),
    ): VpnDiagnostics {
        val vpnActive = vpnStatus == VpnStatus.RUNNING
        val blockedDomainsNow = countBlockedDomains(config, now)
        return VpnDiagnostics(
            vpnActive = vpnActive,
            dnsInterception =
                if (!runtimeState.networkAvailable) {
                    "Network unavailable"
                } else if (runtimeState.restartRequired) {
                    "Restart required"
                } else if (vpnActive) {
                    "Working"
                } else {
                    "Inactive"
                },
            braveFilteringTest =
                if (!config.vpnConfig.filteredApplications.contains("com.brave.browser")) {
                    "Brave missing"
                } else if (vpnActive && config.rules.isNotEmpty()) {
                    "Passed"
                } else if (config.rules.isNotEmpty()) {
                    "Pending"
                } else {
                    "Not configured"
                },
            secureDnsWarning =
                "Disable Secure DNS in Brave or configure it to use the current provider. Otherwise FocusGate may not see DNS requests and domain filtering may not work.",
            blockedDomainsNow = blockedDomainsNow,
            restartRequired = runtimeState.restartRequired,
            restartReason = runtimeState.restartReason?.name,
            networkAvailable = runtimeState.networkAvailable,
        )
    }

    private fun countBlockedDomains(
        config: AppConfig,
        now: ZonedDateTime,
    ): Int = config.rules.count { rule -> RuleEvaluator.shouldBlock(rule, now) }
}
