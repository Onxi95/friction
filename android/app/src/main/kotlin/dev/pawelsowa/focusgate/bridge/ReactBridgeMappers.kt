package dev.pawelsowa.focusgate.bridge

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import dev.pawelsowa.focusgate.native.AppConfigDto
import dev.pawelsowa.focusgate.native.DomainRuleDto
import dev.pawelsowa.focusgate.native.UnlockStatusDto
import dev.pawelsowa.focusgate.native.UpstreamDnsDto
import dev.pawelsowa.focusgate.native.VpnConfigDto
import dev.pawelsowa.focusgate.native.VpnDiagnosticsDto

fun AppConfigDto.toWritableMap(): WritableMap =
    Arguments.createMap().apply {
        putArray(
            "rules",
            Arguments.createArray().also { array ->
                rules.forEach { array.pushMap(it.toWritableMap()) }
            },
        )
        putString("lockState", lockState)
        putString("vpnStatus", vpnStatus)
        putArray(
            "filteredApplications",
            Arguments.createArray().apply {
                filteredApplications.forEach { pushString(it) }
            },
        )
        putMap("upstreamDns", upstreamDns.toWritableMap())
    }

private fun DomainRuleDto.toWritableMap(): WritableMap =
    Arguments.createMap().apply {
        putString("id", id)
        putString("domain", domain)
        putBoolean("enabled", enabled)
        putString("matchMode", matchMode)
        putString("scheduleMode", scheduleMode)
        putArray(
            "weeklySlots",
            Arguments.createArray().also { array ->
                weeklySlots.forEach { array.pushBoolean(it) }
            },
        )
    }

fun UnlockStatusDto.toWritableMap(): WritableMap =
    Arguments.createMap().apply {
        putString("state", state)
        if (remainingMs != null) {
            putDouble("remainingMs", remainingMs.toDouble())
        }
        if (canConfirm != null) {
            putBoolean("canConfirm", canConfirm)
        }
    }

fun VpnDiagnosticsDto.toWritableMap(): WritableMap =
    Arguments.createMap().apply {
        putBoolean("vpnActive", vpnActive)
        putString("dnsInterception", dnsInterception)
        putString("braveFilteringTest", braveFilteringTest)
        putString("secureDnsWarning", secureDnsWarning)
        putInt("currentlyBlocked", blockedDomainsNow)
        putBoolean("restartRequired", restartRequired)
        putString("restartReason", restartReason)
        putBoolean("networkAvailable", networkAvailable)
    }

private fun UpstreamDnsDto.toWritableMap(): WritableMap =
    Arguments.createMap().apply {
        putString("ip", ip)
        putInt("port", port)
    }

fun ReadableMap.toDomainRuleDto(): DomainRuleDto =
    DomainRuleDto(
        id = getString("id").orEmpty(),
        domain = getString("domain").orEmpty(),
        enabled = getBoolean("enabled"),
        matchMode = getString("matchMode").orEmpty(),
        scheduleMode = getString("scheduleMode").orEmpty(),
        weeklySlots = getArray("weeklySlots").toBooleanList(),
    )

fun ReadableMap.toAppConfigDto(): AppConfigDto =
    AppConfigDto(
        rules = getArray("rules").toDomainRuleList(),
        lockState = getString("lockState").orEmpty(),
        vpnStatus = getString("vpnStatus").orEmpty(),
        filteredApplications = getArray("filteredApplications").toStringList(),
        upstreamDns =
            getMap("upstreamDns").let { upstreamDns ->
                UpstreamDnsDto(
                    ip = upstreamDns?.getString("ip").orEmpty(),
                    port = upstreamDns?.getInt("port") ?: 0,
                )
            },
    )

fun ReadableMap.toVpnConfigDto(): VpnConfigDto =
    VpnConfigDto(
        filteredApplications = getArray("filteredApplications").toStringList(),
        upstreamDns =
            getMap("upstreamDns").let { upstreamDns ->
                UpstreamDnsDto(
                    ip = upstreamDns?.getString("ip").orEmpty(),
                    port = upstreamDns?.getInt("port") ?: 0,
                )
            },
    )

private fun com.facebook.react.bridge.ReadableArray?.toBooleanList(): List<Boolean> {
    if (this == null) {
        return emptyList()
    }
    val values = mutableListOf<Boolean>()
    for (index in 0 until size()) {
        values += getBoolean(index)
    }
    return values
}

private fun com.facebook.react.bridge.ReadableArray?.toStringList(): List<String> {
    if (this == null) {
        return emptyList()
    }
    val values = mutableListOf<String>()
    for (index in 0 until size()) {
        values += getString(index).orEmpty()
    }
    return values
}

private fun com.facebook.react.bridge.ReadableArray?.toDomainRuleList(): List<DomainRuleDto> {
    if (this == null) {
        return emptyList()
    }
    val values = mutableListOf<DomainRuleDto>()
    for (index in 0 until size()) {
        values += getMap(index).toDomainRuleDto()
    }
    return values
}
