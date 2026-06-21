package dev.pawelsowa.focusgate.platform

import android.content.Context
import dev.pawelsowa.focusgate.native.FocusGateBridge
import dev.pawelsowa.focusgate.vpn.FocusGateVpnService

object FocusGateRuntimeContainer {
    @Volatile
    private var initialized = false

    lateinit var repository: ProtoDataStoreConfigRepository
        private set

    lateinit var vpnRuntime: FocusGateVpnService
        private set

    lateinit var bridge: FocusGateBridge
        private set

    @Synchronized
    fun initialize(applicationContext: Context) {
        if (initialized) {
            return
        }

        repository = ProtoDataStoreConfigRepository.create(applicationContext)
        vpnRuntime = FocusGateVpnService()
        bridge =
            FocusGateBridge(
                repository = repository,
                vpnService = vpnRuntime,
                deviceContext = AndroidDeviceContext(applicationContext),
            )
        initialized = true
    }
}
