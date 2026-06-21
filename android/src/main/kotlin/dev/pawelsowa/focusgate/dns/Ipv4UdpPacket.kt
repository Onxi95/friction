package dev.pawelsowa.focusgate.dns

data class Ipv4UdpPacket(
    override val sourceAddress: ByteArray,
    override val destinationAddress: ByteArray,
    override val sourcePort: Int,
    override val destinationPort: Int,
    override val payload: ByteArray,
) : UdpIpPacket

object Ipv4UdpPacketCodec {
    fun parse(packet: ByteArray): Ipv4UdpPacket {
        require(packet.size >= 28) { "MALFORMED_IP_PACKET" }
        val version = (packet[0].toInt() ushr 4) and 0x0F
        require(version == 4) { "UNSUPPORTED_IP_VERSION" }
        val headerLength = (packet[0].toInt() and 0x0F) * 4
        require(headerLength >= 20) { "MALFORMED_IP_PACKET" }
        require(packet.size >= headerLength + 8) { "MALFORMED_IP_PACKET" }
        val protocol = packet[9].toInt() and 0xFF
        require(protocol == 17) { "UNSUPPORTED_IP_PROTOCOL" }

        val sourceAddress = packet.copyOfRange(12, 16)
        val destinationAddress = packet.copyOfRange(16, 20)
        val udpOffset = headerLength
        val sourcePort = packet.readUnsignedShort(udpOffset)
        val destinationPort = packet.readUnsignedShort(udpOffset + 2)
        val udpLength = packet.readUnsignedShort(udpOffset + 4)

        require(udpLength >= 8) { "MALFORMED_UDP_PACKET" }
        require(packet.size >= udpOffset + udpLength) { "MALFORMED_UDP_PACKET" }

        return Ipv4UdpPacket(
            sourceAddress = sourceAddress,
            destinationAddress = destinationAddress,
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            payload = packet.copyOfRange(udpOffset + 8, udpOffset + udpLength),
        )
    }

    fun buildResponse(
        requestPacket: Ipv4UdpPacket,
        responsePayload: ByteArray,
    ): ByteArray {
        val udpLength = 8 + responsePayload.size
        val totalLength = 20 + udpLength
        val packet = ByteArray(totalLength)

        packet[0] = 0x45
        packet[1] = 0
        packet.writeUnsignedShort(2, totalLength)
        packet.writeUnsignedShort(4, 0)
        packet.writeUnsignedShort(6, 0)
        packet[8] = 64
        packet[9] = 17
        requestPacket.destinationAddress.copyInto(packet, destinationOffset = 12)
        requestPacket.sourceAddress.copyInto(packet, destinationOffset = 16)
        packet.writeUnsignedShort(10, headerChecksum(packet))

        packet.writeUnsignedShort(20, requestPacket.destinationPort)
        packet.writeUnsignedShort(22, requestPacket.sourcePort)
        packet.writeUnsignedShort(24, udpLength)
        packet.writeUnsignedShort(26, 0)
        responsePayload.copyInto(packet, destinationOffset = 28)

        return packet
    }

    private fun headerChecksum(packet: ByteArray): Int {
        var sum = 0
        for (offset in 0 until 20 step 2) {
            if (offset == 10) {
                continue
            }
            sum += packet.readUnsignedShort(offset)
        }

        while (sum ushr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }

        return sum.inv() and 0xFFFF
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
