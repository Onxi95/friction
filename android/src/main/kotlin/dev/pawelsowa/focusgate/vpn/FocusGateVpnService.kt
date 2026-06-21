package dev.pawelsowa.focusgate.vpn

import dev.pawelsowa.focusgate.config.AppConfig
import dev.pawelsowa.focusgate.dns.DnsPacketProcessor
import dev.pawelsowa.focusgate.dns.PacketProcessingResult
import java.time.ZonedDateTime

enum class VpnStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
}

data class VpnSession(
    val filteredApplications: List<String>,
    val upstreamDnsIp: String,
    val upstreamDnsPort: Int,
    val configRevision: Long,
)

data class VpnDnsResult(
    val responsePacket: ByteArray,
    val action: String,
    val domain: String?,
)

class FocusGateVpnService(
    private val dnsPacketProcessor: DnsPacketProcessor = DnsPacketProcessor(),
    private var upstreamDnsClient: UpstreamDnsClient? = null,
) {
    private var status: VpnStatus = VpnStatus.STOPPED
    private var activeConfigRevision: Long? = null
    private var session: VpnSession? = null
    private val runtimeController = VpnRuntimeController()

    fun start(config: AppConfig) {
        require(config.vpnConfig.filteredApplications.isNotEmpty()) { "VPN_START_FAILED" }
        require(config.vpnConfig.filteredApplications.contains("com.brave.browser")) { "BRAVE_NOT_INCLUDED" }
        require(config.vpnConfig.upstreamDnsPort in 1..65535) { "VPN_START_FAILED" }
        status = VpnStatus.STARTING
        activeConfigRevision = config.revision
        runtimeController.acknowledgeRestart()
        session =
            VpnSession(
                filteredApplications = config.vpnConfig.filteredApplications,
                upstreamDnsIp = config.vpnConfig.upstreamDnsIp,
                upstreamDnsPort = config.vpnConfig.upstreamDnsPort,
                configRevision = config.revision,
            )
        status = VpnStatus.RUNNING
    }

    fun stop() {
        activeConfigRevision = null
        session = null
        status = VpnStatus.STOPPED
    }

    fun getStatus(): VpnStatus = status

    fun activeConfigRevision(): Long? = activeConfigRevision

    fun activeSession(): VpnSession? = session

    fun setUpstreamDnsClient(client: UpstreamDnsClient) {
        upstreamDnsClient = client
    }

    fun runtimeState(): VpnRuntimeState = runtimeController.currentState()

    fun onNetworkChanged(available: Boolean) {
        runtimeController.onNetworkChanged(available)
    }

    fun onServiceRecreated() {
        runtimeController.markServiceRecreated()
    }

    fun handleDnsPacket(
        packet: ByteArray,
        now: ZonedDateTime,
        config: AppConfig,
    ): VpnDnsResult {
        require(status == VpnStatus.RUNNING) { "VPN_NOT_RUNNING" }
        val processingResult = dnsPacketProcessor.processQuery(packet, now, config)

        return when (processingResult) {
            is PacketProcessingResult.Respond ->
                VpnDnsResult(
                    responsePacket = processingResult.packet,
                    action = "BLOCK",
                    domain = null,
                )
            is PacketProcessingResult.Forward -> {
                val client = requireNotNull(upstreamDnsClient) { "UPSTREAM_DNS_UNAVAILABLE" }
                val responsePacket = client.forward(processingResult.queryPacket, config.vpnConfig)
                VpnDnsResult(
                    responsePacket = responsePacket,
                    action = "ALLOW",
                    domain = processingResult.questionName,
                )
            }
        }
    }

    fun buildForegroundNotification(
        config: AppConfig,
        now: ZonedDateTime,
    ): ForegroundNotification =
        VpnReporting.buildForegroundNotification(config, status, now, runtimeController.currentState())

    fun buildDiagnostics(
        config: AppConfig,
        now: ZonedDateTime,
    ): VpnDiagnostics =
        VpnReporting.buildDiagnostics(config, status, now, runtimeController.currentState())
}
