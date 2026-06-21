package dev.pawelsowa.focusgate.platform

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import dev.pawelsowa.focusgate.config.AppConfig
import dev.pawelsowa.focusgate.config.ConfigRepository
import dev.pawelsowa.focusgate.config.VpnConfig
import dev.pawelsowa.focusgate.domain.DomainRule
import dev.pawelsowa.focusgate.domain.MatchMode
import dev.pawelsowa.focusgate.domain.ScheduleMode
import dev.pawelsowa.focusgate.lock.EditLockState
import dev.pawelsowa.focusgate.proto.Empty
import dev.pawelsowa.focusgate.proto.LockState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ProtoDataStoreConfigRepository(
    private val dataStore: DataStore<dev.pawelsowa.focusgate.proto.AppConfig>,
) : ConfigRepository {
    override fun read(): AppConfig = runBlocking { dataStore.data.first().toDomain() }

    override fun write(transform: (AppConfig) -> AppConfig): AppConfig =
        runBlocking {
            var updated: AppConfig? = null
            dataStore.updateData { current ->
                transform(current.toDomain()).toProto().also {
                    updated = it.toDomain()
                }
            }
            requireNotNull(updated)
        }

    companion object {
        fun create(applicationContext: android.content.Context): ProtoDataStoreConfigRepository {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val store =
                DataStoreFactory.create(
                    serializer = AppConfigSerializer,
                    scope = scope,
                    produceFile = { applicationContext.dataStoreFile("focusgate_config.pb") },
                )
            return ProtoDataStoreConfigRepository(store)
        }
    }
}

private fun dev.pawelsowa.focusgate.proto.AppConfig.toDomain(): AppConfig =
    AppConfig(
        rulesList.map { rule ->
            DomainRule(
                id = rule.id,
                domain = rule.domain,
                enabled = rule.enabled,
                matchMode =
                    when (rule.matchMode) {
                        dev.pawelsowa.focusgate.proto.MatchMode.MATCH_MODE_EXACT -> MatchMode.EXACT
                        else -> MatchMode.DOMAIN_AND_SUBDOMAINS
                    },
                scheduleMode =
                    when (rule.scheduleMode) {
                        dev.pawelsowa.focusgate.proto.ScheduleMode.SCHEDULE_MODE_BLOCK_DURING_SELECTED_HOURS -> {
                            ScheduleMode.BLOCK_DURING_SELECTED_HOURS
                        }
                        else -> ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS
                    },
                weeklySlots = rule.weeklySlots.toByteArray().toBooleanArray(),
            )
        },
        lockState =
            when (lockState.stateCase) {
                LockState.StateCase.UNLOCKED -> EditLockState.Unlocked
                LockState.StateCase.UNLOCK_PENDING ->
                    EditLockState.UnlockPending(
                        startedElapsedMs = lockState.unlockPending.startedElapsedMs,
                        bootCount = lockState.unlockPending.bootCount,
                    )
                else -> EditLockState.Locked
            },
        vpnConfig =
            VpnConfig(
                upstreamDnsIp = vpnConfig.upstreamDnsIp.ifBlank { "1.1.1.1" },
                upstreamDnsPort = vpnConfig.upstreamDnsPort.takeIf { it > 0 } ?: 53,
                filteredApplications =
                    if (vpnConfig.filteredApplicationsList.isEmpty()) {
                        listOf("com.brave.browser")
                    } else {
                        vpnConfig.filteredApplicationsList
                    },
            ),
        revision = revision,
    )

private fun AppConfig.toProto(): dev.pawelsowa.focusgate.proto.AppConfig =
    dev.pawelsowa.focusgate.proto.AppConfig
        .newBuilder()
        .addAllRules(
            rules.map { rule ->
                dev.pawelsowa.focusgate.proto.DomainRule
                    .newBuilder()
                    .setId(rule.id)
                    .setDomain(rule.domain)
                    .setEnabled(rule.enabled)
                    .setMatchMode(
                        when (rule.matchMode) {
                            MatchMode.EXACT -> dev.pawelsowa.focusgate.proto.MatchMode.MATCH_MODE_EXACT
                            MatchMode.DOMAIN_AND_SUBDOMAINS -> {
                                dev.pawelsowa.focusgate.proto.MatchMode.MATCH_MODE_DOMAIN_AND_SUBDOMAINS
                            }
                        },
                    ).setScheduleMode(
                        when (rule.scheduleMode) {
                            ScheduleMode.BLOCK_DURING_SELECTED_HOURS -> {
                                dev.pawelsowa.focusgate.proto.ScheduleMode.SCHEDULE_MODE_BLOCK_DURING_SELECTED_HOURS
                            }
                            ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS -> {
                                dev.pawelsowa.focusgate.proto.ScheduleMode.SCHEDULE_MODE_ALLOW_ONLY_DURING_SELECTED_HOURS
                            }
                        },
                    ).setWeeklySlots(com.google.protobuf.ByteString.copyFrom(rule.weeklySlots.toByteArray()))
                    .build()
            },
        ).setLockState(lockState.toProto())
        .setVpnConfig(
            dev.pawelsowa.focusgate.proto.VpnConfig
                .newBuilder()
                .setUpstreamDnsIp(vpnConfig.upstreamDnsIp)
                .setUpstreamDnsPort(vpnConfig.upstreamDnsPort)
                .addAllFilteredApplications(vpnConfig.filteredApplications)
                .build(),
        ).setRevision(revision)
        .build()

private fun EditLockState.toProto(): LockState =
    when (this) {
        EditLockState.Locked ->
            LockState
                .newBuilder()
                .setLocked(Empty.getDefaultInstance())
                .build()
        EditLockState.Unlocked ->
            LockState
                .newBuilder()
                .setUnlocked(Empty.getDefaultInstance())
                .build()
        is EditLockState.UnlockPending ->
            LockState
                .newBuilder()
                .setUnlockPending(
                    dev.pawelsowa.focusgate.proto.UnlockPending
                        .newBuilder()
                        .setStartedElapsedMs(startedElapsedMs)
                        .setBootCount(bootCount)
                        .build(),
                ).build()
    }

private fun ByteArray.toBooleanArray(): BooleanArray = BooleanArray(size * 8) { index ->
    if (index >= 168) {
        false
    } else {
        val byteIndex = index / 8
        val bitIndex = index % 8
        (this[byteIndex].toInt() shr bitIndex and 1) == 1
    }
}

private fun BooleanArray.toByteArray(): ByteArray {
    val bytes = ByteArray(21)
    forEachIndexed { index, value ->
        if (value) {
            val byteIndex = index / 8
            val bitIndex = index % 8
            bytes[byteIndex] = (bytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
        }
    }
    return bytes
}
