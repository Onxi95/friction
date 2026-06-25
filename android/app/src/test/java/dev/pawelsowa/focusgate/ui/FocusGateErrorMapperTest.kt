package dev.pawelsowa.focusgate.ui

import dev.pawelsowa.focusgate.domain.model.FocusGateErrorCode
import dev.pawelsowa.focusgate.domain.model.FocusGateException
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusGateErrorMapperTest {
    private val mapper = FocusGateErrorMapper()

    @Test
    fun mapsStableNativeErrorMessages() {
        assertEquals(
            "Editing is locked",
            mapper.message(FocusGateException(FocusGateErrorCode.EDITING_LOCKED, "raw")),
        )
        assertEquals(
            "Domain already exists",
            mapper.message(FocusGateException(FocusGateErrorCode.DUPLICATE_DOMAIN, "raw")),
        )
        assertEquals(
            "Another VPN is already active",
            mapper.message(FocusGateException(FocusGateErrorCode.VPN_ALREADY_IN_USE, "raw")),
        )
        assertEquals(
            "Upstream DNS unavailable",
            mapper.message(FocusGateException(FocusGateErrorCode.UPSTREAM_DNS_UNAVAILABLE, "raw")),
        )
    }

    @Test
    fun keepsInvalidDomainDetail() {
        assertEquals(
            "Domain must not include a URL path",
            mapper.message(
                FocusGateException(
                    FocusGateErrorCode.INVALID_DOMAIN,
                    "Domain must not include a URL path",
                ),
            ),
        )
    }
}
