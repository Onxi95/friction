package dev.pawelsowa.focusgate.vpn

data class Ipv4TcpPacket(
    val identification: Int,
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val sequenceNumber: Long,
    val acknowledgmentNumber: Long,
    val payload: ByteArray,
)

class Ipv4TcpPacketCodec {
    fun decode(packet: ByteArray, length: Int = packet.size): Ipv4TcpPacket? {
        if (length < MIN_PACKET_SIZE || packet[0].toInt() ushr 4 != IPV4_VERSION) return null

        val ipHeaderLength = (packet[0].toInt() and 0x0f) * 4
        if (ipHeaderLength < IPV4_HEADER_SIZE || length < ipHeaderLength + MIN_TCP_HEADER_SIZE) {
            return null
        }
        if (packet[9].toInt() and 0xff != TCP_PROTOCOL) return null

        val totalLength = DnsMessageParser.readUnsignedShort(packet, 2)
        if (totalLength > length || totalLength < ipHeaderLength + MIN_TCP_HEADER_SIZE) return null

        val tcpHeaderOffset = ipHeaderLength
        val tcpHeaderLength = (packet[tcpHeaderOffset + 12].toInt() ushr 4) * 4
        if (
            tcpHeaderLength < MIN_TCP_HEADER_SIZE ||
            tcpHeaderOffset + tcpHeaderLength > totalLength
        ) {
            return null
        }

        return Ipv4TcpPacket(
            identification = DnsMessageParser.readUnsignedShort(packet, 4),
            sourceAddress = packet.copyOfRange(12, 16),
            destinationAddress = packet.copyOfRange(16, 20),
            sourcePort = DnsMessageParser.readUnsignedShort(packet, tcpHeaderOffset),
            destinationPort = DnsMessageParser.readUnsignedShort(packet, tcpHeaderOffset + 2),
            sequenceNumber = readUnsignedInt(packet, tcpHeaderOffset + 4),
            acknowledgmentNumber = readUnsignedInt(packet, tcpHeaderOffset + 8),
            payload = packet.copyOfRange(tcpHeaderOffset + tcpHeaderLength, totalLength),
        )
    }

    fun response(request: Ipv4TcpPacket, payload: ByteArray): ByteArray {
        val totalLength = IPV4_HEADER_SIZE + MIN_TCP_HEADER_SIZE + payload.size
        val packet = ByteArray(totalLength)
        packet[0] = 0x45.toByte()
        DnsMessageParser.writeUnsignedShort(packet, 2, totalLength)
        DnsMessageParser.writeUnsignedShort(packet, 4, request.identification)
        packet[8] = DEFAULT_TTL
        packet[9] = TCP_PROTOCOL.toByte()
        request.destinationAddress.copyInto(packet, 12)
        request.sourceAddress.copyInto(packet, 16)
        DnsMessageParser.writeUnsignedShort(packet, 10, checksum(packet, 0, IPV4_HEADER_SIZE))

        val tcpOffset = IPV4_HEADER_SIZE
        DnsMessageParser.writeUnsignedShort(packet, tcpOffset, request.destinationPort)
        DnsMessageParser.writeUnsignedShort(packet, tcpOffset + 2, request.sourcePort)
        writeUnsignedInt(packet, tcpOffset + 4, request.acknowledgmentNumber)
        writeUnsignedInt(packet, tcpOffset + 8, request.sequenceNumber + request.payload.size)
        packet[tcpOffset + 12] = (MIN_TCP_HEADER_SIZE / 4 shl 4).toByte()
        packet[tcpOffset + 13] = (TCP_FLAG_ACK.toInt() or TCP_FLAG_PSH.toInt()).toByte()
        DnsMessageParser.writeUnsignedShort(packet, tcpOffset + 14, TCP_WINDOW)
        payload.copyInto(packet, tcpOffset + MIN_TCP_HEADER_SIZE)
        DnsMessageParser.writeUnsignedShort(
            packet,
            tcpOffset + 16,
            tcpChecksum(packet, request.destinationAddress, request.sourceAddress),
        )
        return packet
    }

    private fun tcpChecksum(
        packet: ByteArray,
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
    ): Int {
        val tcpLength = packet.size - IPV4_HEADER_SIZE
        var sum = sumWords(sourceAddress, 0, sourceAddress.size)
        sum += sumWords(destinationAddress, 0, destinationAddress.size)
        sum += TCP_PROTOCOL
        sum += tcpLength
        sum += sumWords(packet, IPV4_HEADER_SIZE, tcpLength)
        return foldChecksum(sum)
    }

    private fun checksum(bytes: ByteArray, offset: Int, length: Int): Int =
        foldChecksum(sumWords(bytes, offset, length))

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

    private fun foldChecksum(sumInput: Long): Int {
        var sum = sumInput
        while (sum shr 16 != 0L) {
            sum = (sum and 0xffff) + (sum shr 16)
        }
        val checksum = sum.inv().toInt() and 0xffff
        return if (checksum == 0) 0xffff else checksum
    }

    private fun readUnsignedInt(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)

    private fun writeUnsignedInt(bytes: ByteArray, offset: Int, value: Long) {
        bytes[offset] = (value shr 24).toByte()
        bytes[offset + 1] = (value shr 16).toByte()
        bytes[offset + 2] = (value shr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    companion object {
        private const val IPV4_VERSION = 4
        private const val IPV4_HEADER_SIZE = 20
        private const val MIN_TCP_HEADER_SIZE = 20
        private const val MIN_PACKET_SIZE = IPV4_HEADER_SIZE + MIN_TCP_HEADER_SIZE
        private const val TCP_PROTOCOL = 6
        private const val DEFAULT_TTL: Byte = 64
        private const val TCP_FLAG_ACK: Byte = 0x10
        private const val TCP_FLAG_PSH: Byte = 0x08
        private const val TCP_WINDOW = 65_535
    }
}
