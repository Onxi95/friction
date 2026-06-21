package dev.pawelsowa.focusgate.dns

sealed interface UdpIpPacket {
    val sourceAddress: ByteArray
    val destinationAddress: ByteArray
    val sourcePort: Int
    val destinationPort: Int
    val payload: ByteArray
}

data class Ipv6UdpPacket(
    override val sourceAddress: ByteArray,
    override val destinationAddress: ByteArray,
    override val sourcePort: Int,
    override val destinationPort: Int,
    override val payload: ByteArray,
) : UdpIpPacket

object Ipv6UdpPacketCodec {
    fun parse(packet: ByteArray): Ipv6UdpPacket {
        require(packet.size >= 48) { "MALFORMED_IP_PACKET" }
        val version = (packet[0].toInt() ushr 4) and 0x0F
        require(version == 6) { "UNSUPPORTED_IP_VERSION" }
        val payloadLength = packet.readUnsignedShort(4)
        val nextHeader = packet[6].toInt() and 0xFF
        require(nextHeader == 17) { "UNSUPPORTED_IP_PROTOCOL" }
        require(packet.size >= 40 + payloadLength) { "MALFORMED_UDP_PACKET" }

        val sourceAddress = packet.copyOfRange(8, 24)
        val destinationAddress = packet.copyOfRange(24, 40)
        val udpOffset = 40
        val sourcePort = packet.readUnsignedShort(udpOffset)
        val destinationPort = packet.readUnsignedShort(udpOffset + 2)
        val udpLength = packet.readUnsignedShort(udpOffset + 4)

        require(udpLength >= 8) { "MALFORMED_UDP_PACKET" }
        require(packet.size >= udpOffset + udpLength) { "MALFORMED_UDP_PACKET" }

        return Ipv6UdpPacket(
            sourceAddress = sourceAddress,
            destinationAddress = destinationAddress,
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            payload = packet.copyOfRange(udpOffset + 8, udpOffset + udpLength),
        )
    }

    fun buildResponse(
        requestPacket: Ipv6UdpPacket,
        responsePayload: ByteArray,
    ): ByteArray {
        val udpLength = 8 + responsePayload.size
        val packet = ByteArray(40 + udpLength)

        packet[0] = 0x60
        packet[1] = 0
        packet[2] = 0
        packet[3] = 0
        packet.writeUnsignedShort(4, udpLength)
        packet[6] = 17
        packet[7] = 64
        requestPacket.destinationAddress.copyInto(packet, destinationOffset = 8)
        requestPacket.sourceAddress.copyInto(packet, destinationOffset = 24)
        packet.writeUnsignedShort(40, requestPacket.destinationPort)
        packet.writeUnsignedShort(42, requestPacket.sourcePort)
        packet.writeUnsignedShort(44, udpLength)
        packet.writeUnsignedShort(46, 0)
        responsePayload.copyInto(packet, destinationOffset = 48)

        return packet
    }

    private fun ByteArray.readUnsignedShort(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

    private fun ByteArray.writeUnsignedShort(
        offset: Int,
        value: Int,
    ) {
        this[offset] = ((value ushr 8) and 0xFF).toByte()
        this[offset + 1] = (value and 0xFF).toByte()
    }
}
