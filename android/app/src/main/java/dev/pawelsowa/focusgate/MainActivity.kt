package dev.pawelsowa.focusgate

import android.os.Bundle
import android.app.Activity
import android.net.VpnService
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pawelsowa.focusgate.ui.FocusGateApp
import dev.pawelsowa.focusgate.ui.FocusGateViewModel
import dev.pawelsowa.focusgate.ui.focusGateViewModelFactory
import dev.pawelsowa.focusgate.vpn.FocusGateVpnService
import dev.pawelsowa.focusgate.vpn.VpnFailureReason
import dev.pawelsowa.focusgate.vpn.VpnRuntime
import dev.pawelsowa.focusgate.domain.model.VpnStatus

class MainActivity : ComponentActivity() {
    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                FocusGateVpnService.start(this)
            } else {
                VpnRuntime.failureReason.value = VpnFailureReason.VPN_PERMISSION_DENIED
                VpnRuntime.status.value = VpnStatus.ERROR
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = FocusGateGraph.repository

        setContent {
            val viewModel = viewModel<FocusGateViewModel>(
                factory = focusGateViewModelFactory(repository = repository),
            )

            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    FocusGateApp(
                        viewModel = viewModel,
                        onStartVpn = ::requestVpn,
                        onStopVpn = { FocusGateVpnService.stop(this) },
                    )
                }
            }
        }
    }

    private fun requestVpn() {
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent == null) {
            FocusGateVpnService.start(this)
        } else {
            VpnRuntime.failureReason.value = VpnFailureReason.NONE
            vpnPermission.launch(permissionIntent)
        }
    }
}
