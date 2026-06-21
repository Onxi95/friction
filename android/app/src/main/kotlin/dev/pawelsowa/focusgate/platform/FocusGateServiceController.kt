package dev.pawelsowa.focusgate.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.pawelsowa.focusgate.FocusGateAndroidVpnService

object FocusGateServiceController {
    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, FocusGateAndroidVpnService::class.java),
        )
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, FocusGateAndroidVpnService::class.java))
    }
}
