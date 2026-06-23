package dev.pawelsowa.focusgate.domain.model

enum class FocusGateErrorCode {
    EDITING_LOCKED,
    INVALID_DOMAIN,
    DUPLICATE_DOMAIN,
    INVALID_SCHEDULE,
    COUNTDOWN_STILL_ACTIVE,
    COUNTDOWN_INVALIDATED,
    VPN_PERMISSION_REQUIRED,
    VPN_ALREADY_IN_USE,
    VPN_START_FAILED,
    UPSTREAM_DNS_UNAVAILABLE,
    BRAVE_SECURE_DNS_BYPASS,
}

class FocusGateException(
    val code: FocusGateErrorCode,
    message: String,
) : IllegalStateException(message)
