package dev.pawelsowa.focusgate.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import dev.pawelsowa.focusgate.native.FocusGateBridge
import dev.pawelsowa.focusgate.platform.FocusGateRuntimeContainer
import dev.pawelsowa.focusgate.platform.FocusGateServiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FocusGateModule(
    reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bridge: FocusGateBridge

    init {
        FocusGateRuntimeContainer.initialize(reactContext.applicationContext)
        bridge = FocusGateRuntimeContainer.bridge
    }

    override fun getName(): String = "FocusGateNative"

    @ReactMethod
    fun getVpnStatus(promise: Promise) {
        scope.launch {
            runCatching { bridge.getVpnStatus() }
                .onSuccess { promise.resolve(it) }
                .onFailure { promise.reject("VPN_STATUS_ERROR", it) }
        }
    }

    @ReactMethod
    fun getDiagnostics(promise: Promise) {
        scope.launch {
            runCatching { bridge.getDiagnostics().toWritableMap() }
                .onSuccess { promise.resolve(it) }
                .onFailure { promise.reject("DIAGNOSTICS_ERROR", it) }
        }
    }

    @ReactMethod
    fun startVpn(promise: Promise) {
        scope.launch {
            runCatching { FocusGateServiceController.start(reactApplicationContext) }
                .onSuccess { promise.resolve(null) }
                .onFailure { promise.reject("VPN_START_FAILED", it) }
        }
    }

    @ReactMethod
    fun stopVpn(promise: Promise) {
        scope.launch {
            runCatching {
                FocusGateServiceController.stop(reactApplicationContext)
                bridge.stopVpn()
            }
                .onSuccess { promise.resolve(null) }
                .onFailure { promise.reject("VPN_STOP_FAILED", it) }
        }
    }

    @ReactMethod
    fun getConfig(promise: Promise) {
        scope.launch {
            runCatching { bridge.getConfig().toWritableMap() }
                .onSuccess { promise.resolve(it) }
                .onFailure { promise.reject("CONFIG_ERROR", it) }
        }
    }

    @ReactMethod
    fun exportConfig(promise: Promise) {
        scope.launch {
            runCatching { bridge.exportConfig().toWritableMap() }
                .onSuccess { promise.resolve(it) }
                .onFailure { promise.reject("EXPORT_CONFIG_ERROR", it) }
        }
    }

    @ReactMethod
    fun importConfig(config: ReadableMap, promise: Promise) {
        scope.launch {
            runCatching { bridge.importConfig(config.toAppConfigDto()) }
                .onSuccess { promise.resolve(null) }
                .onFailure { promise.reject("IMPORT_CONFIG_ERROR", it) }
        }
    }

    @ReactMethod
    fun resetConfig(promise: Promise) {
        scope.launch {
            runCatching { bridge.resetConfig() }
                .onSuccess { promise.resolve(null) }
                .onFailure { promise.reject("RESET_CONFIG_ERROR", it) }
        }
    }

    @ReactMethod
    fun startUnlockCountdown(promise: Promise) {
        scope.launch {
            runCatching { bridge.startUnlockCountdown().toWritableMap() }
                .onSuccess { promise.resolve(it) }
                .onFailure { promise.reject("UNLOCK_ERROR", it) }
        }
    }

    @ReactMethod
    fun getUnlockStatus(promise: Promise) {
        scope.launch {
            runCatching { bridge.getUnlockStatus().toWritableMap() }
                .onSuccess { promise.resolve(it) }
                .onFailure { promise.reject("UNLOCK_STATUS_ERROR", it) }
        }
    }

    @ReactMethod
    fun confirmUnlock(promise: Promise) {
        scope.launch {
            runCatching { bridge.confirmUnlock() }
                .onSuccess { promise.resolve(null) }
                .onFailure { promise.reject("UNLOCK_CONFIRM_ERROR", it) }
        }
    }

    @ReactMethod
    fun enableEditLock(promise: Promise) {
        scope.launch {
            runCatching { bridge.enableEditLock() }
                .onSuccess { promise.resolve(null) }
                .onFailure { promise.reject("LOCK_ERROR", it) }
        }
    }

    @ReactMethod
    fun cancelUnlockCountdown(promise: Promise) {
        scope.launch {
            runCatching { bridge.cancelUnlockCountdown() }
                .onSuccess { promise.resolve(null) }
                .onFailure { promise.reject("UNLOCK_CANCEL_ERROR", it) }
        }
    }

    @ReactMethod
    fun addRule(rule: ReadableMap, promise: Promise) {
        scope.launch {
            runCatching { bridge.addRule(rule.toDomainRuleDto()) }
                .onSuccess { promise.resolve(null) }
                .onFailure { promise.reject("ADD_RULE_ERROR", it) }
        }
    }

    @ReactMethod
    fun updateRule(rule: ReadableMap, promise: Promise) {
        scope.launch {
            runCatching { bridge.updateRule(rule.toDomainRuleDto()) }
                .onSuccess { promise.resolve(null) }
                .onFailure { promise.reject("UPDATE_RULE_ERROR", it) }
        }
    }

    @ReactMethod
    fun deleteRule(ruleId: String, promise: Promise) {
        scope.launch {
            runCatching { bridge.deleteRule(ruleId) }
                .onSuccess { promise.resolve(null) }
                .onFailure { promise.reject("DELETE_RULE_ERROR", it) }
        }
    }

    @ReactMethod
    fun updateVpnConfig(config: ReadableMap, promise: Promise) {
        scope.launch {
            runCatching { bridge.updateVpnConfig(config.toVpnConfigDto()) }
                .onSuccess { promise.resolve(null) }
                .onFailure { promise.reject("UPDATE_VPN_CONFIG_ERROR", it) }
        }
    }
}
