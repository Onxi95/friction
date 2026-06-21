package dev.pawelsowa.focusgate.native

interface FocusGateNativeModule {
    suspend fun getConfig(): AppConfigDto
    suspend fun getDiagnostics(): VpnDiagnosticsDto
    suspend fun addRule(rule: DomainRuleDto)
    suspend fun updateRule(rule: DomainRuleDto)
    suspend fun deleteRule(ruleId: String)
    suspend fun exportConfig(): AppConfigDto
    suspend fun importConfig(config: AppConfigDto)
    suspend fun resetConfig()
    suspend fun updateVpnConfig(config: VpnConfigDto)
    suspend fun startVpn()
    suspend fun stopVpn()
    suspend fun getVpnStatus(): String
    suspend fun enableEditLock()
    suspend fun startUnlockCountdown(): UnlockStatusDto
    suspend fun getUnlockStatus(): UnlockStatusDto
    suspend fun confirmUnlock()
    suspend fun cancelUnlockCountdown()
}

data class AppConfigDto(
    val rules: List<DomainRuleDto>,
    val lockState: String,
    val vpnStatus: String,
    val filteredApplications: List<String>,
    val upstreamDns: UpstreamDnsDto,
)

data class DomainRuleDto(
    val id: String,
    val domain: String,
    val enabled: Boolean,
    val matchMode: String,
    val scheduleMode: String,
    val weeklySlots: List<Boolean>,
)

data class UnlockStatusDto(
    val state: String,
    val remainingMs: Long? = null,
    val canConfirm: Boolean? = null,
)

data class UpstreamDnsDto(
    val ip: String,
    val port: Int,
)

data class VpnConfigDto(
    val filteredApplications: List<String>,
    val upstreamDns: UpstreamDnsDto,
)

data class VpnDiagnosticsDto(
    val vpnActive: Boolean,
    val dnsInterception: String,
    val braveFilteringTest: String,
    val secureDnsWarning: String,
    val blockedDomainsNow: Int,
    val restartRequired: Boolean,
    val restartReason: String?,
    val networkAvailable: Boolean,
)
