package dev.pawelsowa.focusgate.vpn

import android.content.pm.PackageManager
import dev.pawelsowa.focusgate.domain.model.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow

enum class VpnFailureReason {
    NONE,
    VPN_PERMISSION_DENIED,
    NO_SUPPORTED_BROWSER_INSTALLED,
    NETWORK_PERMISSION_MISSING,
    TUN_START_FAILED,
    UPSTREAM_DNS_UNAVAILABLE,
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
    const val CHROME_PACKAGE = "com.android.chrome"
    const val VANADIUM_PACKAGE = "app.vanadium.browser"
    val SUPPORTED_BROWSER_PACKAGES = listOf(
        BRAVE_PACKAGE,
        CHROME_PACKAGE,
        VANADIUM_PACKAGE,
    )

    val status = MutableStateFlow(VpnStatus.STOPPED)
    val failureReason = MutableStateFlow(VpnFailureReason.NONE)
    val filteredBrowserPackages = MutableStateFlow(emptyList<String>())
    val dnsDiagnostics = MutableStateFlow(DnsDiagnostics())

    fun refreshBrowserAvailability(packageManager: PackageManager) {
        filteredBrowserPackages.value = installedBrowserPackages(packageManager)
    }

    fun installedBrowserPackages(packageManager: PackageManager): List<String> =
        SUPPORTED_BROWSER_PACKAGES.filter { packageName ->
            runCatching {
                packageManager.getPackageInfo(packageName, 0)
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
