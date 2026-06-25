package dev.pawelsowa.focusgate.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.pawelsowa.focusgate.FocusGateGraph
import dev.pawelsowa.focusgate.MainActivity
import dev.pawelsowa.focusgate.domain.RuleEvaluator
import dev.pawelsowa.focusgate.domain.model.DomainRule
import dev.pawelsowa.focusgate.domain.model.VpnStatus
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusGateVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ruleEvaluator = RuleEvaluator()
    private val rules = AtomicReference<List<DomainRule>>(emptyList())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetJob: Job? = null
    private var networkCallbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (vpnInterface != null) {
                VpnRuntime.failureReason.value = VpnFailureReason.NONE
                VpnRuntime.status.value = VpnStatus.RUNNING
            }
        }

        override fun onLost(network: Network) {
            if (vpnInterface != null) {
                VpnRuntime.status.value = VpnStatus.ERROR
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        registerNetworkCallback()
        if (packetJob?.isActive != true) {
            packetJob = scope.launch { runVpn() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runVpn() {
        val rulesJob = scope.launch {
            FocusGateGraph.repository.observeConfig().collectLatest { config ->
                rules.set(config.rules)
                updateNotification(blockedNowCount(config.rules))
            }
        }
        try {
            val descriptor = establishInterface()
            if (descriptor == null) {
                if (VpnRuntime.failureReason.value == VpnFailureReason.NONE) {
                    VpnRuntime.failureReason.value = VpnFailureReason.TUN_START_FAILED
                }
                VpnRuntime.status.value = VpnStatus.ERROR
                stopSelf()
                return
            }
            vpnInterface = descriptor
            VpnRuntime.status.value = VpnStatus.RUNNING
            pumpPackets(descriptor)
        } catch (_: IOException) {
            VpnRuntime.status.value = VpnStatus.ERROR
        } finally {
            rulesJob.cancel()
            vpnInterface?.close()
            vpnInterface = null
            if (VpnRuntime.status.value != VpnStatus.ERROR) {
                VpnRuntime.status.value = VpnStatus.STOPPED
            }
        }
    }

    private fun establishInterface(): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("FocusGate")
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VPN_DNS_ADDRESS)
            .addRoute(VPN_DNS_ADDRESS, 32)
        val browserPackages = VpnRuntime.installedBrowserPackages(packageManager)
        if (browserPackages.isEmpty()) {
            VpnRuntime.filteredBrowserPackages.value = emptyList()
            VpnRuntime.failureReason.value = VpnFailureReason.NO_SUPPORTED_BROWSER_INSTALLED
            return null
        }
        VpnRuntime.filteredBrowserPackages.value = browserPackages
        return try {
            browserPackages.forEach(builder::addAllowedApplication)
            builder.establish()
        } catch (_: PackageManager.NameNotFoundException) {
            VpnRuntime.failureReason.value = VpnFailureReason.NO_SUPPORTED_BROWSER_INSTALLED
            null
        } catch (_: IllegalArgumentException) {
            VpnRuntime.failureReason.value = VpnFailureReason.TUN_START_FAILED
            null
        } catch (_: IllegalStateException) {
            VpnRuntime.failureReason.value = VpnFailureReason.TUN_START_FAILED
            null
        }
    }

    private suspend fun pumpPackets(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(MAX_PACKET_SIZE)
        val processor = DnsPacketProcessor(
            upstream = DatagramSocketUpstreamDnsClient(
                protectSocket = { socket -> protect(socket) },
            ),
            tcpUpstream = TcpSocketUpstreamDnsClient(
                protectSocket = { socket -> protect(socket) },
            ),
        )
        while (scope.isActive) {
            val length = input.read(buffer)
            if (length <= 0) continue
            val response = processor.process(buffer, length, rules.get()) ?: continue
            output.write(response)
        }
    }

    private fun stopVpn() {
        packetJob?.cancel()
        packetJob = null
        vpnInterface?.close()
        vpnInterface = null
        unregisterNetworkCallback()
        VpnRuntime.status.value = VpnStatus.STOPPED
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        try {
            getSystemService(ConnectivityManager::class.java)
                .registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        } catch (_: SecurityException) {
            VpnRuntime.failureReason.value = VpnFailureReason.NETWORK_PERMISSION_MISSING
            VpnRuntime.status.value = VpnStatus.ERROR
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        networkCallbackRegistered = false
    }

    private fun updateNotification(blockedNowCount: Int) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(blockedNowCount))
    }

    private fun blockedNowCount(rules: List<DomainRule>): Int {
        val now = ZonedDateTime.now()
        return rules.count { rule -> ruleEvaluator.shouldBlock(rule, now) }
    }

    private fun notification(blockedNowCount: Int = 0): Notification {
        ensureNotificationChannel()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("FocusGate active")
            .setContentText(notificationText(blockedNowCount))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun notificationText(blockedNowCount: Int): String =
        when (blockedNowCount) {
            0 -> "No domains currently blocked"
            1 -> "1 domain is currently blocked"
            else -> "$blockedNowCount domains are currently blocked"
        }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "FocusGate VPN",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        private const val ACTION_START = "dev.pawelsowa.focusgate.vpn.START"
        private const val ACTION_STOP = "dev.pawelsowa.focusgate.vpn.STOP"
        private const val CHANNEL_ID = "focusgate_vpn"
        private const val NOTIFICATION_ID = 10
        private const val VPN_ADDRESS = "10.10.0.1"
        private const val VPN_DNS_ADDRESS = "10.10.0.2"
        private const val MAX_PACKET_SIZE = 32_767

        fun start(context: Context) {
            VpnRuntime.refreshBrowserAvailability(context.packageManager)
            VpnRuntime.failureReason.value = VpnFailureReason.NONE
            VpnRuntime.status.value = VpnStatus.STARTING
            ContextCompat.startForegroundService(
                context,
                Intent(context, FocusGateVpnService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FocusGateVpnService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}

class DatagramSocketUpstreamDnsClient(
    private val protectSocket: (DatagramSocket) -> Boolean,
    private val server: InetAddress = InetAddress.getByName("1.1.1.1"),
    private val port: Int = 53,
    private val timeoutMs: Int = 3_000,
    private val attempts: Int = 2,
) : UpstreamDnsClient {
    override fun query(request: ByteArray): ByteArray? {
        repeat(attempts) {
            DatagramSocket().use { socket ->
                if (!protectSocket(socket)) return null
                socket.soTimeout = timeoutMs
                try {
                    socket.send(DatagramPacket(request, request.size, server, port))
                    val buffer = ByteArray(MAX_DNS_RESPONSE_SIZE)
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    return buffer.copyOf(response.length)
                } catch (_: IOException) {
                }
            }
        }
        return null
    }

    companion object {
        private const val MAX_DNS_RESPONSE_SIZE = 4_096
    }
}

class TcpSocketUpstreamDnsClient(
    private val protectSocket: (Socket) -> Boolean,
    private val server: InetAddress = InetAddress.getByName("1.1.1.1"),
    private val port: Int = 53,
    private val timeoutMs: Int = 3_000,
) : UpstreamDnsClient {
    override fun query(request: ByteArray): ByteArray? =
        try {
            Socket().use { socket ->
                if (!protectSocket(socket)) return null
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(server, port), timeoutMs)
                val frame = ByteArray(LENGTH_PREFIX_SIZE + request.size)
                DnsMessageParser.writeUnsignedShort(frame, 0, request.size)
                request.copyInto(frame, LENGTH_PREFIX_SIZE)
                socket.getOutputStream().write(frame)
                socket.getOutputStream().flush()

                val input = socket.getInputStream()
                val lengthPrefix = input.readExact(LENGTH_PREFIX_SIZE) ?: return null
                val responseLength = DnsMessageParser.readUnsignedShort(lengthPrefix, 0)
                if (responseLength > MAX_DNS_RESPONSE_SIZE) return null
                input.readExact(responseLength)
            }
        } catch (_: IOException) {
            null
        }

    private fun java.io.InputStream.readExact(size: Int): ByteArray? {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = read(bytes, offset, size - offset)
            if (read < 0) return null
            offset += read
        }
        return bytes
    }

    companion object {
        private const val LENGTH_PREFIX_SIZE = 2
        private const val MAX_DNS_RESPONSE_SIZE = 4_096
    }
}
