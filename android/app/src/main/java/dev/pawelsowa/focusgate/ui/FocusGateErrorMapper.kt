package dev.pawelsowa.focusgate.ui

import dev.pawelsowa.focusgate.domain.model.FocusGateErrorCode
import dev.pawelsowa.focusgate.domain.model.FocusGateException

class FocusGateErrorMapper {
    fun message(exception: FocusGateException): String =
        when (exception.code) {
            FocusGateErrorCode.EDITING_LOCKED -> "Editing is locked"
            FocusGateErrorCode.INVALID_DOMAIN -> exception.message ?: "Domain is invalid"
            FocusGateErrorCode.DUPLICATE_DOMAIN -> "Domain already exists"
            FocusGateErrorCode.INVALID_SCHEDULE -> "Schedule is invalid"
            FocusGateErrorCode.COUNTDOWN_STILL_ACTIVE -> "Unlock countdown is still active"
            FocusGateErrorCode.COUNTDOWN_INVALIDATED ->
                "Unlock countdown was invalidated by device restart"
            FocusGateErrorCode.VPN_PERMISSION_REQUIRED -> "VPN permission is required"
            FocusGateErrorCode.VPN_ALREADY_IN_USE -> "Another VPN is already active"
            FocusGateErrorCode.VPN_START_FAILED -> "VPN could not start"
            FocusGateErrorCode.UPSTREAM_DNS_UNAVAILABLE -> "Upstream DNS unavailable"
            FocusGateErrorCode.BRAVE_SECURE_DNS_BYPASS ->
                "Browser Secure DNS may be bypassing FocusGate"
        }
}
