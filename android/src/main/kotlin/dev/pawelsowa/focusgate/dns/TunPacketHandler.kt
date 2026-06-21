package dev.pawelsowa.focusgate.dns

import dev.pawelsowa.focusgate.config.AppConfig
import dev.pawelsowa.focusgate.vpn.FocusGateVpnService
import java.time.ZonedDateTime

enum class TunDropReason {
    NON_DNS_PORT,
    UNSUPPORTED_IP_PACKET,
    UNSUPPORTED_IP_PROTOCOL,
    MALFORMED_PACKET,
}

sealed interface TunPacketResult {
    data class Respond(
        val packet: ByteArray,
        val domain: String?,
    ) : TunPacketResult

    data class Drop(
        val reason: TunDropReason,
    ) : TunPacketResult
}

data class TunLoopStats(
    val respondedPackets: Long = 0,
    val droppedPackets: Long = 0,
    val lastDropReason: TunDropReason? = null,
)

class TunPacketHandler(
    private val vpnService: FocusGateVpnService,
) {
    fun handle(
        packet: ByteArray,
        now: ZonedDateTime,
        config: AppConfig,
    ): TunPacketResult {
        val udpPacket =
            try {
                parseUdpPacket(packet)
            } catch (error: IllegalArgumentException) {
                return TunPacketResult.Drop(error.toDropReason())
            }

        if (udpPacket.destinationPort != 53) {
            return TunPacketResult.Drop(TunDropReason.NON_DNS_PORT)
        }

        val result = vpnService.handleDnsPacket(udpPacket.payload, now, config)
        return TunPacketResult.Respond(
            packet = buildResponsePacket(udpPacket, result.responsePacket),
            domain = result.domain,
        )
    }

    private fun parseUdpPacket(packet: ByteArray): UdpIpPacket {
        require(packet.size >= 4) { "MALFORMED_IP_PACKET" }
        return when ((packet[0].toInt() ushr 4) and 0x0F) {
            4 -> Ipv4UdpPacketCodec.parse(packet)
            6 -> Ipv6UdpPacketCodec.parse(packet)
            else -> throw IllegalArgumentException("UNSUPPORTED_IP_VERSION")
        }
    }

    private fun buildResponsePacket(
        requestPacket: UdpIpPacket,
        responsePayload: ByteArray,
    ): ByteArray =
        when (requestPacket) {
            is Ipv4UdpPacket -> Ipv4UdpPacketCodec.buildResponse(requestPacket, responsePayload)
            is Ipv6UdpPacket -> Ipv6UdpPacketCodec.buildResponse(requestPacket, responsePayload)
        }
}

private fun IllegalArgumentException.toDropReason(): TunDropReason =
    when (message) {
        "UNSUPPORTED_IP_PROTOCOL" -> TunDropReason.UNSUPPORTED_IP_PROTOCOL
        "UNSUPPORTED_IP_VERSION" -> TunDropReason.UNSUPPORTED_IP_PACKET
        "MALFORMED_IP_PACKET", "MALFORMED_UDP_PACKET" -> TunDropReason.MALFORMED_PACKET
        else -> TunDropReason.MALFORMED_PACKET
    }
