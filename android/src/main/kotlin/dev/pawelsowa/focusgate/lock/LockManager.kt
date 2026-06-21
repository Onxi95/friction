package dev.pawelsowa.focusgate.lock

private const val UNLOCK_DELAY_MS = 5 * 60 * 1000L

sealed interface EditLockState {
    data object Unlocked : EditLockState
    data object Locked : EditLockState
    data class UnlockPending(
        val startedElapsedMs: Long,
        val bootCount: Int,
    ) : EditLockState
}

data class UnlockStatus(
    val state: String,
    val remainingMs: Long = 0,
    val canConfirm: Boolean = false,
)

class LockManager {
    fun startUnlockCountdown(
        elapsedRealtimeMs: Long,
        bootCount: Int,
    ): EditLockState = EditLockState.UnlockPending(elapsedRealtimeMs, bootCount)

    fun getUnlockStatus(
        state: EditLockState,
        elapsedRealtimeMs: Long,
        bootCount: Int,
    ): UnlockStatus =
        when (state) {
            EditLockState.Locked -> UnlockStatus(state = "LOCKED")
            EditLockState.Unlocked -> UnlockStatus(state = "UNLOCKED")
            is EditLockState.UnlockPending -> {
                if (state.bootCount != bootCount) {
                    UnlockStatus(state = "LOCKED")
                } else {
                    val elapsed = (elapsedRealtimeMs - state.startedElapsedMs).coerceAtLeast(0)
                    val remainingMs = (UNLOCK_DELAY_MS - elapsed).coerceAtLeast(0)
                    UnlockStatus(
                        state = "UNLOCK_PENDING",
                        remainingMs = remainingMs,
                        canConfirm = remainingMs == 0L,
                    )
                }
            }
        }

    fun confirmUnlock(
        state: EditLockState,
        elapsedRealtimeMs: Long,
        bootCount: Int,
    ): EditLockState {
        val status = getUnlockStatus(state, elapsedRealtimeMs, bootCount)
        require(status.state == "UNLOCK_PENDING" && status.canConfirm) { "COUNTDOWN_STILL_ACTIVE" }
        return EditLockState.Unlocked
    }

    fun requireEditingUnlocked(state: EditLockState) {
        require(state == EditLockState.Unlocked) { "EDITING_LOCKED" }
    }

    fun relockAfterWrite(): EditLockState = EditLockState.Locked
}
