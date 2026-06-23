package dev.pawelsowa.focusgate

import android.app.Application
import androidx.datastore.core.DataStoreFactory
import dev.pawelsowa.focusgate.data.AndroidLockTimeSource
import dev.pawelsowa.focusgate.data.DataStoreFocusGateRepository
import dev.pawelsowa.focusgate.data.FocusGateConfigSerializer
import dev.pawelsowa.focusgate.domain.repository.FocusGateRepository
import dev.pawelsowa.focusgate.vpn.VpnRuntime

class FocusGateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FocusGateGraph.repository =
            DataStoreFocusGateRepository(
                dataStore = DataStoreFactory.create(
                    serializer = FocusGateConfigSerializer,
                    produceFile = { filesDir.resolve("focusgate.pb") },
                ),
                timeSource = AndroidLockTimeSource(contentResolver),
                vpnStatus = VpnRuntime.status,
            )
        VpnRuntime.refreshBraveInstalled(packageManager)
    }
}

object FocusGateGraph {
    lateinit var repository: FocusGateRepository
}
