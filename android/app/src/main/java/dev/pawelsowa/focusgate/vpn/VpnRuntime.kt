package dev.pawelsowa.focusgate.vpn

import android.content.pm.PackageManager
import dev.pawelsowa.focusgate.domain.model.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow

enum class VpnFailureReason {
    NONE,
    VPN_PERMISSION_DENIED,
    BRAVE_NOT_INSTALLED,
    NETWORK_PERMISSION_MISSING,
    TUN_START_FAILED,
}

data class DnsDiagnostics(
    val observedQueries: Long = 0,
    val blockedQueries: Long = 0,
    val forwardedQueries: Long = 0,
    val lastDomain: String? = null,
    val lastBlockedDomain: String? = null,
)

object VpnRuntime {
    const val BRAVE_PACKAGE = "com.brave.browser"

    val status = MutableStateFlow(VpnStatus.STOPPED)
    val failureReason = MutableStateFlow(VpnFailureReason.NONE)
    val braveInstalled = MutableStateFlow(false)
    val dnsDiagnostics = MutableStateFlow(DnsDiagnostics())

    fun refreshBraveInstalled(packageManager: PackageManager) {
        braveInstalled.value = runCatching {
            packageManager.getPackageInfo(BRAVE_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    fun recordDnsQuery(domain: String, blocked: Boolean) {
        dnsDiagnostics.value = dnsDiagnostics.value.let { current ->
            current.copy(
                observedQueries = current.observedQueries + 1,
                blockedQueries = current.blockedQueries + if (blocked) 1 else 0,
                forwardedQueries = current.forwardedQueries + if (blocked) 0 else 1,
                lastDomain = domain,
                lastBlockedDomain = if (blocked) domain else current.lastBlockedDomain,
            )
        }
    }
}
