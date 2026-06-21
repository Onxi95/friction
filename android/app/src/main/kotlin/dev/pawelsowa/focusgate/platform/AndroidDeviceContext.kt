package dev.pawelsowa.focusgate.platform

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dev.pawelsowa.focusgate.env.DeviceContext

class AndroidDeviceContext(
    private val context: Context,
) : DeviceContext {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
}
