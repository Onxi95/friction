package dev.pawelsowa.focusgate.env

interface DeviceContext {
    fun elapsedRealtimeMs(): Long

    fun bootCount(): Int
}

class MutableDeviceContext(
    private var elapsedRealtimeMsValue: Long = 0,
    private var bootCountValue: Int = 0,
) : DeviceContext {
    override fun elapsedRealtimeMs(): Long = elapsedRealtimeMsValue

    override fun bootCount(): Int = bootCountValue

    fun setElapsedRealtimeMs(value: Long) {
        elapsedRealtimeMsValue = value
    }

    fun setBootCount(value: Int) {
        bootCountValue = value
    }
}
