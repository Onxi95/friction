package dev.pawelsowa.focusgate.data

import com.google.protobuf.ByteString
import dev.pawelsowa.focusgate.data.proto.StoredAppConfig
import dev.pawelsowa.focusgate.data.proto.StoredDomainRule
import dev.pawelsowa.focusgate.data.proto.StoredLockMode
import dev.pawelsowa.focusgate.data.proto.StoredMatchMode
import dev.pawelsowa.focusgate.data.proto.StoredScheduleMode
import dev.pawelsowa.focusgate.domain.model.AppConfig
import dev.pawelsowa.focusgate.domain.model.DomainRule
import dev.pawelsowa.focusgate.domain.model.MatchMode
import dev.pawelsowa.focusgate.domain.model.ScheduleMode
import dev.pawelsowa.focusgate.domain.model.UnlockStatus
import dev.pawelsowa.focusgate.domain.model.VpnStatus
import dev.pawelsowa.focusgate.domain.model.WeeklySchedule

class StoredConfigMapper(
    private val timeSource: LockTimeSource,
) {
    fun toDomain(
        stored: StoredAppConfig,
        vpnStatus: VpnStatus,
    ): AppConfig =
        AppConfig(
            rules = stored.rulesList.map(::toDomainRule),
            unlockStatus = unlockStatus(stored),
            vpnStatus = vpnStatus,
            revision = stored.revision,
        )

    fun toStoredRule(rule: DomainRule): StoredDomainRule =
        StoredDomainRule.newBuilder()
            .setId(rule.id)
            .setDomain(rule.domain)
            .setEnabled(rule.enabled)
            .setMatchMode(
                when (rule.matchMode) {
                    MatchMode.EXACT -> StoredMatchMode.STORED_MATCH_MODE_EXACT
                    MatchMode.DOMAIN_AND_SUBDOMAINS ->
                        StoredMatchMode.STORED_MATCH_MODE_DOMAIN_AND_SUBDOMAINS
                },
            )
            .setScheduleMode(
                when (rule.scheduleMode) {
                    ScheduleMode.BLOCK_DURING_SELECTED_HOURS ->
                        StoredScheduleMode.STORED_SCHEDULE_MODE_BLOCK_DURING_SELECTED_HOURS
                    ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS ->
                        StoredScheduleMode.STORED_SCHEDULE_MODE_ALLOW_ONLY_DURING_SELECTED_HOURS
                },
            )
            .setWeeklySlots(ByteString.copyFrom(pack(rule.schedule)))
            .build()

    private fun toDomainRule(rule: StoredDomainRule): DomainRule =
        DomainRule(
            id = rule.id,
            domain = rule.domain,
            enabled = rule.enabled,
            matchMode = when (rule.matchMode) {
                StoredMatchMode.STORED_MATCH_MODE_DOMAIN_AND_SUBDOMAINS ->
                    MatchMode.DOMAIN_AND_SUBDOMAINS
                else -> MatchMode.EXACT
            },
            scheduleMode = when (rule.scheduleMode) {
                StoredScheduleMode.STORED_SCHEDULE_MODE_ALLOW_ONLY_DURING_SELECTED_HOURS ->
                    ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS
                else -> ScheduleMode.BLOCK_DURING_SELECTED_HOURS
            },
            schedule = unpack(rule.weeklySlots.toByteArray()),
        )

    private fun unlockStatus(stored: StoredAppConfig): UnlockStatus {
        val lock = stored.lockState
        return when (lock.mode) {
            StoredLockMode.STORED_LOCK_MODE_UNLOCKED -> UnlockStatus.Unlocked
            StoredLockMode.STORED_LOCK_MODE_UNLOCK_PENDING -> {
                if (lock.bootCount != timeSource.bootCount()) {
                    UnlockStatus.Locked
                } else {
                    val remaining = (UNLOCK_DELAY_MS -
                        (timeSource.elapsedRealtime() - lock.startedElapsedMs)).coerceAtLeast(0)
                    UnlockStatus.UnlockPending(
                        remainingMs = remaining,
                        canConfirm = remaining == 0L,
                    )
                }
            }
            else -> UnlockStatus.Locked
        }
    }

    private fun pack(schedule: WeeklySchedule): ByteArray =
        ByteArray(SCHEDULE_BYTES) { byteIndex ->
            var packed = 0
            repeat(BITS_PER_BYTE) { bitIndex ->
                val slotIndex = byteIndex * BITS_PER_BYTE + bitIndex
                if (schedule.slots[slotIndex]) {
                    packed = packed or (1 shl bitIndex)
                }
            }
            packed.toByte()
        }

    private fun unpack(bytes: ByteArray): WeeklySchedule {
        if (bytes.size != SCHEDULE_BYTES) return WeeklySchedule.empty()

        return WeeklySchedule(
            List(WeeklySchedule.SLOT_COUNT) { slotIndex ->
                val byte = bytes[slotIndex / BITS_PER_BYTE].toInt()
                byte and (1 shl (slotIndex % BITS_PER_BYTE)) != 0
            },
        )
    }

    companion object {
        const val UNLOCK_DELAY_MS = 5 * 60 * 1000L
        private const val BITS_PER_BYTE = 8
        private const val SCHEDULE_BYTES = WeeklySchedule.SLOT_COUNT / BITS_PER_BYTE
    }
}
