package dev.pawelsowa.focusgate.vpn

data class Ipv6UdpPacket(
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray,
)

class Ipv6UdpPacketCodec {
    fun decode(packet: ByteArray, length: Int = packet.size): Ipv6UdpPacket? {
        if (length < IPV6_HEADER_SIZE + UDP_HEADER_SIZE) return null
        if (packet[0].toInt() ushr 4 != IPV6_VERSION) return null
        if (packet[6].toInt() and 0xff != UDP_PROTOCOL) return null

        val payloadLength = DnsMessageParser.readUnsignedShort(packet, 4)
        if (payloadLength < UDP_HEADER_SIZE || IPV6_HEADER_SIZE + payloadLength > length) {
            return null
        }
        val udpOffset = IPV6_HEADER_SIZE
        val udpLength = DnsMessageParser.readUnsignedShort(packet, udpOffset + 4)
        if (udpLength < UDP_HEADER_SIZE || udpLength > payloadLength) return null

        return Ipv6UdpPacket(
            sourceAddress = packet.copyOfRange(8, 24),
            destinationAddress = packet.copyOfRange(24, 40),
            sourcePort = DnsMessageParser.readUnsignedShort(packet, udpOffset),
            destinationPort = DnsMessageParser.readUnsignedShort(packet, udpOffset + 2),
            payload = packet.copyOfRange(
                udpOffset + UDP_HEADER_SIZE,
                udpOffset + udpLength,
            ),
        )
    }

    fun response(request: Ipv6UdpPacket, payload: ByteArray): ByteArray {
        val udpLength = UDP_HEADER_SIZE + payload.size
        val packet = ByteArray(IPV6_HEADER_SIZE + udpLength)
        packet[0] = 0x60.toByte()
        DnsMessageParser.writeUnsignedShort(packet, 4, udpLength)
        packet[6] = UDP_PROTOCOL.toByte()
        packet[7] = DEFAULT_HOP_LIMIT
        request.destinationAddress.copyInto(packet, 8)
        request.sourceAddress.copyInto(packet, 24)

        DnsMessageParser.writeUnsignedShort(packet, IPV6_HEADER_SIZE, request.destinationPort)
        DnsMessageParser.writeUnsignedShort(packet, IPV6_HEADER_SIZE + 2, request.sourcePort)
        DnsMessageParser.writeUnsignedShort(packet, IPV6_HEADER_SIZE + 4, udpLength)
        payload.copyInto(packet, IPV6_HEADER_SIZE + UDP_HEADER_SIZE)
        DnsMessageParser.writeUnsignedShort(
            packet,
            IPV6_HEADER_SIZE + 6,
            udpChecksum(packet, request.destinationAddress, request.sourceAddress, udpLength),
        )
        return packet
    }

    private fun udpChecksum(
        packet: ByteArray,
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        udpLength: Int,
    ): Int {
        var sum = sumWords(sourceAddress, 0, sourceAddress.size)
        sum += sumWords(destinationAddress, 0, destinationAddress.size)
        sum += UDP_PROTOCOL
        sum += udpLength
        sum += sumWords(packet, IPV6_HEADER_SIZE, udpLength)
        while (sum shr 16 != 0L) {
            sum = (sum and 0xffff) + (sum shr 16)
        }
        val checksum = sum.inv().toInt() and 0xffff
        return if (checksum == 0) 0xffff else checksum
    }

    private fun sumWords(bytes: ByteArray, offset: Int, length: Int): Long {
        var sum = 0L
        var index = offset
        while (index < offset + length) {
            val high = bytes[index].toInt() and 0xff
            val low = if (index + 1 < offset + length) bytes[index + 1].toInt() and 0xff else 0
            sum += (high shl 8) or low
            index += 2
        }
        return sum
    }

    companion object {
        private const val IPV6_VERSION = 6
        private const val IPV6_HEADER_SIZE = 40
        private const val UDP_HEADER_SIZE = 8
        private const val UDP_PROTOCOL = 17
        private const val DEFAULT_HOP_LIMIT: Byte = 64
    }
}
