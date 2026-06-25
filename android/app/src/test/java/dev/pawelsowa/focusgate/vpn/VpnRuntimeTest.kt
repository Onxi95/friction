package dev.pawelsowa.focusgate.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnRuntimeTest {
    @Test
    fun supportedBrowserAllowlistIncludesReleaseTargets() {
        assertEquals(
            listOf(
                "com.brave.browser",
                "com.android.chrome",
                "app.vanadium.browser",
            ),
            VpnRuntime.SUPPORTED_BROWSER_PACKAGES,
        )
    }
}
