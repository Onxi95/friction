package dev.pawelsowa.focusgate.vpn

import dev.pawelsowa.focusgate.config.VpnConfig
import dev.pawelsowa.focusgate.dns.DnsMessageCodec
import dev.pawelsowa.focusgate.dns.DnsTcpCodec

interface VpnSocketProtector {
    fun protect(host: String, port: Int): Boolean
}

data class UpstreamDnsRequest(
    val host: String,
    val port: Int,
    val packet: ByteArray,
)

data class UpstreamDnsResult(
    val responsePacket: ByteArray,
)

interface UpstreamDnsTransport {
    fun send(request: UpstreamDnsRequest): UpstreamDnsResult
}

class UpstreamDnsClient(
    private val socketProtector: VpnSocketProtector,
    private val transport: UpstreamDnsTransport,
    private val tcpTransport: UpstreamDnsTransport? = null,
    private val maxAttempts: Int = 2,
) {
    fun forward(
        packet: ByteArray,
        vpnConfig: VpnConfig,
    ): ByteArray {
        require(maxAttempts > 0) { "INVALID_RETRY_COUNT" }
        require(socketProtector.protect(vpnConfig.upstreamDnsIp, vpnConfig.upstreamDnsPort)) {
            "COULD_NOT_PROTECT_SOCKET"
        }

        val request =
            UpstreamDnsRequest(
                host = vpnConfig.upstreamDnsIp,
                port = vpnConfig.upstreamDnsPort,
                packet = packet,
            )

        var lastError: RuntimeException? = null
        repeat(maxAttempts) {
            try {
                val response = transport.send(request).responsePacket
                val truncated =
                    try {
                        DnsMessageCodec.isTruncatedResponse(response)
                    } catch (_: IllegalArgumentException) {
                        false
                    }
                if (truncated && tcpTransport != null) {
                    return DnsTcpCodec.decodeResponse(
                        tcpTransport.send(
                            request.copy(packet = DnsTcpCodec.encodeQuery(packet)),
                        ).responsePacket,
                    )
                }
                return response
            } catch (error: RuntimeException) {
                lastError = error
            }
        }

        throw IllegalStateException("UPSTREAM_DNS_UNAVAILABLE", lastError)
    }
}
