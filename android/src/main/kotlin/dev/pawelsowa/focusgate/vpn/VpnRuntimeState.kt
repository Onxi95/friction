package dev.pawelsowa.focusgate.vpn

enum class RestartReason {
    NETWORK_CHANGED,
    SERVICE_RECREATED,
}

data class VpnRuntimeState(
    val restartRequired: Boolean = false,
    val restartReason: RestartReason? = null,
    val networkAvailable: Boolean = true,
)

class VpnRuntimeController {
    private var state = VpnRuntimeState()

    fun currentState(): VpnRuntimeState = state

    fun markServiceRecreated() {
        state =
            state.copy(
                restartRequired = true,
                restartReason = RestartReason.SERVICE_RECREATED,
            )
    }

    fun onNetworkChanged(available: Boolean) {
        state =
            if (!available) {
                state.copy(networkAvailable = false)
            } else {
                state.copy(
                    networkAvailable = true,
                    restartRequired = true,
                    restartReason = RestartReason.NETWORK_CHANGED,
                )
            }
    }

    fun acknowledgeRestart() {
        state =
            state.copy(
                restartRequired = false,
                restartReason = null,
            )
    }
}
