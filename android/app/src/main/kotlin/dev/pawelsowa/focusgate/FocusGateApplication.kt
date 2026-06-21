package dev.pawelsowa.focusgate

import android.app.Application
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactPackage
import com.facebook.react.ReactNativeHost
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.defaults.DefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.soloader.SoLoader
import dev.pawelsowa.focusgate.bridge.FocusGatePackage
import dev.pawelsowa.focusgate.platform.FocusGateRuntimeContainer

class FocusGateApplication : Application(), ReactApplication {
    private val packages: List<ReactPackage> = listOf(FocusGatePackage())

    override val reactNativeHost: ReactNativeHost =
        object : DefaultReactNativeHost(this) {
            override fun getPackages(): List<ReactPackage> = packages

            override fun getJSMainModuleName(): String = "index"

            override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

            override val isNewArchEnabled: Boolean = false
            override val isHermesEnabled: Boolean = false
        }

    override val reactHost: ReactHost
        get() = DefaultReactHost.getDefaultReactHost(applicationContext, reactNativeHost)

    override fun onCreate() {
        super.onCreate()
        FocusGateRuntimeContainer.initialize(applicationContext)
        SoLoader.init(this, false)
        if (false) {
            DefaultNewArchitectureEntryPoint.load()
        }
    }
}
