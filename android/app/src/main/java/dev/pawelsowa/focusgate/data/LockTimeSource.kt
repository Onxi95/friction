package dev.pawelsowa.focusgate.data

import android.content.ContentResolver
import android.os.SystemClock
import android.provider.Settings

interface LockTimeSource {
    fun elapsedRealtime(): Long
    fun bootCount(): Int
}

class AndroidLockTimeSource(
    private val contentResolver: ContentResolver,
) : LockTimeSource {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int =
        Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)
}
