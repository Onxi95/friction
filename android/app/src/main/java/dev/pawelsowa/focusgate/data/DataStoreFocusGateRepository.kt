package dev.pawelsowa.focusgate.data

import androidx.datastore.core.DataStore
import com.google.protobuf.InvalidProtocolBufferException
import dev.pawelsowa.focusgate.data.proto.StoredAppConfig
import dev.pawelsowa.focusgate.data.proto.StoredLockMode
import dev.pawelsowa.focusgate.data.proto.StoredLockState
import dev.pawelsowa.focusgate.domain.DomainNormalizer
import dev.pawelsowa.focusgate.domain.model.AppConfig
import dev.pawelsowa.focusgate.domain.model.DomainRule
import dev.pawelsowa.focusgate.domain.model.FocusGateErrorCode
import dev.pawelsowa.focusgate.domain.model.FocusGateException
import dev.pawelsowa.focusgate.domain.model.UnlockStatus
import dev.pawelsowa.focusgate.domain.model.VpnStatus
import dev.pawelsowa.focusgate.domain.repository.FocusGateRepository
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class DataStoreFocusGateRepository(
    private val dataStore: DataStore<StoredAppConfig>,
    private val timeSource: LockTimeSource,
    private val normalizer: DomainNormalizer = DomainNormalizer(),
    private val vpnStatus: MutableStateFlow<VpnStatus> = MutableStateFlow(VpnStatus.STOPPED),
) : FocusGateRepository {
    private val mapper = StoredConfigMapper(timeSource)

    override fun observeConfig(): Flow<AppConfig> =
        combine(dataStore.data, vpnStatus, mapper::toDomain)

    override suspend fun getConfig(): AppConfig =
        mapper.toDomain(dataStore.data.first(), vpnStatus.value)

    override suspend fun addRule(rule: DomainRule) {
        dataStore.updateData { config ->
            requireUnlocked(config)
            val validated = validateRule(rule, config, ignoredRuleId = null)
            config.toBuilder()
                .addRules(mapper.toStoredRule(validated))
                .setLockState(lockedState())
                .setRevision(config.revision + 1)
                .build()
        }
    }

    override suspend fun updateRule(rule: DomainRule) {
        dataStore.updateData { config ->
            requireUnlocked(config)
            val index = config.rulesList.indexOfFirst { it.id == rule.id }
            require(index >= 0) { "Unknown rule: ${rule.id}" }
            val validated = validateRule(rule, config, ignoredRuleId = rule.id)
            config.toBuilder()
                .setRules(index, mapper.toStoredRule(validated))
                .setLockState(lockedState())
                .setRevision(config.revision + 1)
                .build()
        }
    }

    override suspend fun deleteRule(ruleId: String) {
        dataStore.updateData { config ->
            requireUnlocked(config)
            val remaining = config.rulesList.filterNot { it.id == ruleId }
            config.toBuilder()
                .clearRules()
                .addAllRules(remaining)
                .setLockState(lockedState())
                .setRevision(config.revision + 1)
                .build()
        }
    }

    override suspend fun startVpn() {
        vpnStatus.value = VpnStatus.RUNNING
    }

    override suspend fun stopVpn() {
        vpnStatus.value = VpnStatus.STOPPED
    }

    override suspend fun getVpnStatus(): VpnStatus = vpnStatus.value

    override suspend fun exportConfig(): String =
        Base64.getEncoder().encodeToString(dataStore.data.first().toByteArray())

    override suspend fun importConfig(encodedConfig: String) {
        val imported = try {
            StoredAppConfig.parseFrom(Base64.getDecoder().decode(encodedConfig))
        } catch (_: InvalidProtocolBufferException) {
            throw FocusGateException(
                FocusGateErrorCode.INVALID_DOMAIN,
                "Backup is not a valid FocusGate export",
            )
        } catch (_: IllegalArgumentException) {
            throw FocusGateException(
                FocusGateErrorCode.INVALID_DOMAIN,
                "Backup is not a valid FocusGate export",
            )
        }
        validateImportedConfig(imported)
        dataStore.updateData { current ->
            requireUnlocked(current)
            imported.toBuilder()
                .setLockState(lockedState())
                .setRevision(current.revision + 1)
                .build()
        }
    }

    override suspend fun enableEditLock() {
        dataStore.updateData { config ->
            config.toBuilder().setLockState(lockedState()).build()
        }
    }

    override suspend fun startUnlockCountdown(): UnlockStatus {
        val pending = StoredLockState.newBuilder()
            .setMode(StoredLockMode.STORED_LOCK_MODE_UNLOCK_PENDING)
            .setStartedElapsedMs(timeSource.elapsedRealtime())
            .setBootCount(timeSource.bootCount())
            .build()
        val updated = dataStore.updateData { config ->
            config.toBuilder().setLockState(pending).build()
        }
        return mapper.toDomain(updated, vpnStatus.value).unlockStatus
    }

    override suspend fun getUnlockStatus(): UnlockStatus {
        val config = dataStore.data.first()
        if (
            config.lockState.mode == StoredLockMode.STORED_LOCK_MODE_UNLOCK_PENDING &&
            config.lockState.bootCount != timeSource.bootCount()
        ) {
            dataStore.updateData { it.toBuilder().setLockState(lockedState()).build() }
            return UnlockStatus.Locked
        }
        return mapper.toDomain(config, vpnStatus.value).unlockStatus
    }

    override suspend fun confirmUnlock() {
        dataStore.updateData { config ->
            val lock = config.lockState
            if (lock.mode != StoredLockMode.STORED_LOCK_MODE_UNLOCK_PENDING) {
                throw FocusGateException(
                    FocusGateErrorCode.EDITING_LOCKED,
                    "Unlock countdown has not started",
                )
            }
            if (lock.bootCount != timeSource.bootCount()) {
                throw FocusGateException(
                    FocusGateErrorCode.COUNTDOWN_INVALIDATED,
                    "Unlock countdown was invalidated by device restart",
                )
            }
            val elapsed = timeSource.elapsedRealtime() - lock.startedElapsedMs
            if (elapsed < StoredConfigMapper.UNLOCK_DELAY_MS) {
                throw FocusGateException(
                    FocusGateErrorCode.COUNTDOWN_STILL_ACTIVE,
                    "Unlock countdown is still active",
                )
            }
            config.toBuilder()
                .setLockState(
                    StoredLockState.newBuilder()
                        .setMode(StoredLockMode.STORED_LOCK_MODE_UNLOCKED),
                )
                .build()
        }
    }

    override suspend fun cancelUnlockCountdown() {
        enableEditLock()
    }

    private fun validateRule(
        rule: DomainRule,
        config: StoredAppConfig,
        ignoredRuleId: String?,
    ): DomainRule {
        val normalized = normalizer.normalize(rule.domain).getOrElse { cause ->
            throw FocusGateException(
                FocusGateErrorCode.INVALID_DOMAIN,
                cause.message ?: "Invalid domain",
            )
        }
        if (config.rulesList.any { it.id != ignoredRuleId && it.domain == normalized }) {
            throw FocusGateException(
                FocusGateErrorCode.DUPLICATE_DOMAIN,
                "Domain already exists",
            )
        }
        return rule.copy(domain = normalized)
    }

    private fun requireUnlocked(config: StoredAppConfig) {
        if (config.lockState.mode != StoredLockMode.STORED_LOCK_MODE_UNLOCKED) {
            throw FocusGateException(
                FocusGateErrorCode.EDITING_LOCKED,
                "Editing is locked",
            )
        }
    }

    private fun validateImportedConfig(config: StoredAppConfig) {
        val domains = mutableSetOf<String>()
        config.rulesList.forEach { rule ->
            val normalized = normalizer.normalize(rule.domain).getOrElse { cause ->
                throw FocusGateException(
                    FocusGateErrorCode.INVALID_DOMAIN,
                    cause.message ?: "Invalid domain in backup",
                )
            }
            if (!domains.add(normalized)) {
                throw FocusGateException(
                    FocusGateErrorCode.DUPLICATE_DOMAIN,
                    "Backup contains duplicate domains",
                )
            }
        }
        mapper.toDomain(config, vpnStatus.value)
    }

    private fun lockedState(): StoredLockState =
        StoredLockState.newBuilder()
            .setMode(StoredLockMode.STORED_LOCK_MODE_LOCKED)
            .build()
}
