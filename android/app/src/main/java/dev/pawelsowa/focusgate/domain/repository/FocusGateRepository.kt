package dev.pawelsowa.focusgate.domain.repository

import dev.pawelsowa.focusgate.domain.model.AppConfig
import dev.pawelsowa.focusgate.domain.model.DomainRule
import dev.pawelsowa.focusgate.domain.model.UnlockStatus
import dev.pawelsowa.focusgate.domain.model.VpnStatus
import kotlinx.coroutines.flow.Flow

interface FocusGateRepository {
    fun observeConfig(): Flow<AppConfig>
    suspend fun getConfig(): AppConfig

    suspend fun addRule(rule: DomainRule)
    suspend fun updateRule(rule: DomainRule)
    suspend fun deleteRule(ruleId: String)

    suspend fun startVpn()
    suspend fun stopVpn()
    suspend fun getVpnStatus(): VpnStatus

    suspend fun exportConfig(): String
    suspend fun importConfig(encodedConfig: String)

    suspend fun enableEditLock()
    suspend fun startUnlockCountdown(): UnlockStatus
    suspend fun getUnlockStatus(): UnlockStatus
    suspend fun confirmUnlock()
    suspend fun cancelUnlockCountdown()
}
