package dev.pawelsowa.focusgate.domain.model

data class AppConfig(
    val rules: List<DomainRule>,
    val unlockStatus: UnlockStatus,
    val vpnStatus: VpnStatus,
    val revision: Long = 0,
)

sealed interface UnlockStatus {
    data object Unlocked : UnlockStatus
    data object Locked : UnlockStatus

    data class UnlockPending(
        val remainingMs: Long,
        val canConfirm: Boolean,
    ) : UnlockStatus
}

enum class VpnStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
}
