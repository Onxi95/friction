package dev.pawelsowa.focusgate.config

import dev.pawelsowa.focusgate.domain.DomainRule
import dev.pawelsowa.focusgate.domain.MatchMode
import dev.pawelsowa.focusgate.domain.ScheduleMode
import dev.pawelsowa.focusgate.lock.EditLockState
import java.io.File
import java.util.Base64

class FileConfigRepository(
    private val file: File,
) : ConfigRepository {
    override fun read(): AppConfig {
        if (!file.exists()) {
            return AppConfig()
        }

        val lines = file.readLines()
        if (lines.isEmpty()) {
            return AppConfig()
        }

        var revision = 0L
        var lockState: EditLockState = EditLockState.Locked
        var vpnConfig = VpnConfig()
        val rules = mutableListOf<DomainRule>()

        for (line in lines) {
            when {
                line.startsWith("revision=") -> {
                    revision = line.substringAfter('=').toLong()
                }
                line.startsWith("lock=") -> {
                    lockState = decodeLockState(line.substringAfter('='))
                }
                line.startsWith("vpn=") -> {
                    vpnConfig = decodeVpnConfig(line.substringAfter('='))
                }
                line.startsWith("rule=") -> {
                    rules += decodeRule(line.substringAfter('='))
                }
            }
        }

        return AppConfig(
            rules = rules.toList(),
            lockState = lockState,
            vpnConfig = vpnConfig,
            revision = revision,
        )
    }

    override fun write(transform: (AppConfig) -> AppConfig): AppConfig {
        val current = read()
        val next = transform(current).copy(revision = current.revision + 1)
        file.parentFile?.mkdirs()
        file.writeText(encode(next))
        return next
    }

    private fun encode(config: AppConfig): String {
        val lines = mutableListOf<String>()
        lines += "revision=${config.revision}"
        lines += "lock=${encodeLockState(config.lockState)}"
        lines += "vpn=${encodeVpnConfig(config.vpnConfig)}"
        config.rules.forEach { rule ->
            lines += "rule=${encodeRule(rule)}"
        }
        return lines.joinToString(separator = "\n", postfix = "\n")
    }

    private fun encodeRule(rule: DomainRule): String =
        listOf(
            encodeToken(rule.id),
            encodeToken(rule.domain),
            rule.enabled.toString(),
            rule.matchMode.name,
            rule.scheduleMode.name,
            encodeSlots(rule.weeklySlots),
        ).joinToString("|")

    private fun decodeRule(encoded: String): DomainRule {
        val parts = encoded.split('|')
        require(parts.size == 6) { "INVALID_CONFIG" }
        return DomainRule(
            id = decodeToken(parts[0]),
            domain = decodeToken(parts[1]),
            enabled = parts[2].toBooleanStrict(),
            matchMode = MatchMode.valueOf(parts[3]),
            scheduleMode = ScheduleMode.valueOf(parts[4]),
            weeklySlots = decodeSlots(parts[5]),
        )
    }

    private fun encodeLockState(lockState: EditLockState): String =
        when (lockState) {
            EditLockState.Locked -> "LOCKED"
            EditLockState.Unlocked -> "UNLOCKED"
            is EditLockState.UnlockPending ->
                "UNLOCK_PENDING:${lockState.startedElapsedMs}:${lockState.bootCount}"
        }

    private fun decodeLockState(encoded: String): EditLockState =
        when {
            encoded == "LOCKED" -> EditLockState.Locked
            encoded == "UNLOCKED" -> EditLockState.Unlocked
            encoded.startsWith("UNLOCK_PENDING:") -> {
                val parts = encoded.split(':')
                require(parts.size == 3) { "INVALID_CONFIG" }
                EditLockState.UnlockPending(
                    startedElapsedMs = parts[1].toLong(),
                    bootCount = parts[2].toInt(),
                )
            }
            else -> error("INVALID_CONFIG")
        }

    private fun encodeVpnConfig(vpnConfig: VpnConfig): String =
        listOf(
            encodeToken(vpnConfig.upstreamDnsIp),
            vpnConfig.upstreamDnsPort.toString(),
            vpnConfig.filteredApplications.joinToString(",") { encodeToken(it) },
        ).joinToString("|")

    private fun decodeVpnConfig(encoded: String): VpnConfig {
        val parts = encoded.split('|')
        require(parts.size == 3) { "INVALID_CONFIG" }
        val apps =
            if (parts[2].isEmpty()) {
                emptyList()
            } else {
                parts[2].split(',').map(::decodeToken)
            }
        return VpnConfig(
            upstreamDnsIp = decodeToken(parts[0]),
            upstreamDnsPort = parts[1].toInt(),
            filteredApplications = apps,
        )
    }

    private fun encodeSlots(slots: BooleanArray): String {
        require(slots.size == 168) { "INVALID_SCHEDULE" }
        return slots.joinToString(separator = "") { if (it) "1" else "0" }
    }

    private fun decodeSlots(encoded: String): BooleanArray {
        require(encoded.length == 168) { "INVALID_CONFIG" }
        return BooleanArray(encoded.length) { index -> encoded[index] == '1' }
    }

    private fun encodeToken(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeToken(value: String): String =
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}
