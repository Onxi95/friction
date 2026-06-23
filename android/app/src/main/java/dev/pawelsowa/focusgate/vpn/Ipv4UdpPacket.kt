package dev.pawelsowa.focusgate.vpn

data class Ipv4UdpPacket(
    val identification: Int,
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray,
)

class Ipv4UdpPacketCodec {
    fun decode(packet: ByteArray, length: Int = packet.size): Ipv4UdpPacket? {
        if (length < MIN_PACKET_SIZE || packet[0].toInt() ushr 4 != IPV4_VERSION) return null

        val headerLength = (packet[0].toInt() and 0x0f) * 4
        if (headerLength < IPV4_HEADER_SIZE || length < headerLength + UDP_HEADER_SIZE) return null
        if (packet[9].toInt() and 0xff != UDP_PROTOCOL) return null

        val totalLength = DnsMessageParser.readUnsignedShort(packet, 2)
        if (totalLength > length || totalLength < headerLength + UDP_HEADER_SIZE) return null
        val udpLength = DnsMessageParser.readUnsignedShort(packet, headerLength + 4)
        if (udpLength < UDP_HEADER_SIZE || headerLength + udpLength > totalLength) return null

        return Ipv4UdpPacket(
            identification = DnsMessageParser.readUnsignedShort(packet, 4),
            sourceAddress = packet.copyOfRange(12, 16),
            destinationAddress = packet.copyOfRange(16, 20),
            sourcePort = DnsMessageParser.readUnsignedShort(packet, headerLength),
            destinationPort = DnsMessageParser.readUnsignedShort(packet, headerLength + 2),
            payload = packet.copyOfRange(
                headerLength + UDP_HEADER_SIZE,
                headerLength + udpLength,
            ),
        )
    }

    fun response(request: Ipv4UdpPacket, payload: ByteArray): ByteArray {
        val totalLength = IPV4_HEADER_SIZE + UDP_HEADER_SIZE + payload.size
        val packet = ByteArray(totalLength)
        packet[0] = 0x45.toByte()
        DnsMessageParser.writeUnsignedShort(packet, 2, totalLength)
        DnsMessageParser.writeUnsignedShort(packet, 4, request.identification)
        packet[8] = DEFAULT_TTL
        packet[9] = UDP_PROTOCOL.toByte()
        request.destinationAddress.copyInto(packet, 12)
        request.sourceAddress.copyInto(packet, 16)
        DnsMessageParser.writeUnsignedShort(packet, 10, checksum(packet, 0, IPV4_HEADER_SIZE))

        DnsMessageParser.writeUnsignedShort(packet, IPV4_HEADER_SIZE, request.destinationPort)
        DnsMessageParser.writeUnsignedShort(packet, IPV4_HEADER_SIZE + 2, request.sourcePort)
        DnsMessageParser.writeUnsignedShort(
            packet,
            IPV4_HEADER_SIZE + 4,
            UDP_HEADER_SIZE + payload.size,
        )
        payload.copyInto(packet, IPV4_HEADER_SIZE + UDP_HEADER_SIZE)
        return packet
    }

    private fun checksum(bytes: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var index = offset
        while (index < offset + length) {
            sum += ((bytes[index].toInt() and 0xff) shl 8) or
                (bytes[index + 1].toInt() and 0xff)
            index += 2
        }
        while (sum shr 16 != 0L) {
            sum = (sum and 0xffff) + (sum shr 16)
        }
        return sum.inv().toInt() and 0xffff
    }

    companion object {
        private const val IPV4_VERSION = 4
        private const val IPV4_HEADER_SIZE = 20
        private const val UDP_HEADER_SIZE = 8
        private const val MIN_PACKET_SIZE = IPV4_HEADER_SIZE + UDP_HEADER_SIZE
        private const val UDP_PROTOCOL = 17
        private const val DEFAULT_TTL: Byte = 64
    }
}
