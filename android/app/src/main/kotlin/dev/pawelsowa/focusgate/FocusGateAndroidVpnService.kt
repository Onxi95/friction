package dev.pawelsowa.focusgate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import dev.pawelsowa.focusgate.platform.AndroidTcpUpstreamDnsTransport
import dev.pawelsowa.focusgate.platform.AndroidUpstreamDnsTransport
import dev.pawelsowa.focusgate.platform.FocusGateRuntimeContainer
import dev.pawelsowa.focusgate.platform.TunDnsLoop
import dev.pawelsowa.focusgate.vpn.UpstreamDnsClient
import java.time.ZonedDateTime
import kotlin.concurrent.thread

class FocusGateAndroidVpnService : VpnService() {
    private val repository by lazy { FocusGateRuntimeContainer.repository }
    private val runtime by lazy { FocusGateRuntimeContainer.vpnRuntime }
    private var tunInterface: ParcelFileDescriptor? = null
    private var tunLoop: TunDnsLoop? = null
    private var tunThread: Thread? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        createNotificationChannel()
        FocusGateRuntimeContainer.initialize(applicationContext)
        runtime.setUpstreamDnsClient(
            UpstreamDnsClient(
                socketProtector = dev.pawelsowa.focusgate.platform.AndroidVpnSocketProtector(this),
                transport = AndroidUpstreamDnsTransport(this),
                tcpTransport = AndroidTcpUpstreamDnsTransport(this),
            ),
        )
        val config = repository.read()
        runtime.onServiceRecreated()
        runtime.start(config)
        registerNetworkCallback()
        tunInterface = establishTun(config)
        tunLoop = tunInterface?.let { TunDnsLoop(it, repository, runtime) }
        tunThread =
            tunLoop?.let { loop ->
                thread(name = "focusgate-tun-loop", start = true) {
                    runCatching { loop.run() }
                }
            }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        tunLoop?.close()
        tunThread?.interrupt()
        tunLoop = null
        tunThread = null
        tunInterface = null
        runtime.stop()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val notificationState = runtime.buildForegroundNotification(repository.read(), ZonedDateTime.now())
        return NotificationCompat
            .Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(notificationState.title)
            .setContentText(notificationState.message)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "FocusGate VPN",
                NotificationManager.IMPORTANCE_LOW,
            )
        manager.createNotificationChannel(channel)
    }

    private fun registerNetworkCallback() {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request =
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runtime.onNetworkChanged(true)
                }

                override fun onLost(network: Network) {
                    runtime.onNetworkChanged(false)
                }
            }
        manager.registerNetworkCallback(request, callback)
        connectivityManager = manager
        networkCallback = callback
    }

    private fun unregisterNetworkCallback() {
        val manager = connectivityManager ?: return
        val callback = networkCallback ?: return
        runCatching { manager.unregisterNetworkCallback(callback) }
        connectivityManager = null
        networkCallback = null
    }

    private fun establishTun(config: dev.pawelsowa.focusgate.config.AppConfig): ParcelFileDescriptor? =
        Builder()
            .setSession("FocusGate")
            .addAddress("10.10.0.1", 32)
            .addDnsServer("10.10.0.2")
            .addRoute("10.10.0.2", 32)
            .apply {
                config.vpnConfig.filteredApplications.forEach { packageName ->
                    addAllowedApplication(packageName)
                }
            }.establish()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "focusgate_vpn"
        private const val NOTIFICATION_ID = 101
    }
}
