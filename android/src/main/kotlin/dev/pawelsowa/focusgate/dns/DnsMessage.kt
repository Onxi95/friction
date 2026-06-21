package dev.pawelsowa.focusgate.dns

private const val DNS_HEADER_SIZE = 12
private const val TYPE_A = 1
private const val TYPE_AAAA = 28
private const val TYPE_OPT = 41
private const val CLASS_IN = 1

enum class DnsRecordType(val code: Int) {
    A(TYPE_A),
    AAAA(TYPE_AAAA),
}

data class DnsQuestion(
    val name: String,
    val type: DnsRecordType,
)

data class DnsQueryMessage(
    val transactionId: Int,
    val flags: Int,
    val question: DnsQuestion,
)

object DnsMessageCodec {
    fun parseQuery(packet: ByteArray): DnsQueryMessage {
        require(packet.size >= DNS_HEADER_SIZE) { "MALFORMED_DNS_PACKET" }

        val transactionId = packet.readUnsignedShort(0)
        val flags = packet.readUnsignedShort(2)
        val questionCount = packet.readUnsignedShort(4)
        val answerCount = packet.readUnsignedShort(6)
        val authorityCount = packet.readUnsignedShort(8)
        val additionalCount = packet.readUnsignedShort(10)

        require(questionCount == 1) { "UNSUPPORTED_DNS_QUESTION_COUNT" }
        require(answerCount == 0) { "UNEXPECTED_DNS_ANSWERS" }
        require(authorityCount == 0) { "UNEXPECTED_DNS_AUTHORITY" }
        require(flags and 0x8000 == 0) { "DNS_PACKET_IS_NOT_A_QUERY" }

        var offset = DNS_HEADER_SIZE
        val (name, nameEndOffset) = parseName(packet, offset)
        offset = nameEndOffset
        require(name.isNotEmpty()) { "MALFORMED_DNS_PACKET" }
        require(offset + 4 <= packet.size) { "MALFORMED_DNS_PACKET" }

        val typeCode = packet.readUnsignedShort(offset)
        offset += 2
        val classCode = packet.readUnsignedShort(offset)
        offset += 2

        require(classCode == CLASS_IN) { "UNSUPPORTED_DNS_CLASS" }

        if (additionalCount > 0) {
            require(additionalCount == 1) { "UNEXPECTED_DNS_ADDITIONAL" }
            offset = skipAdditionalRecord(packet, offset)
        }

        require(offset == packet.size) { "UNSUPPORTED_DNS_TRAILING_BYTES" }

        val type =
            when (typeCode) {
                TYPE_A -> DnsRecordType.A
                TYPE_AAAA -> DnsRecordType.AAAA
                else -> error("UNSUPPORTED_DNS_TYPE")
            }

        return DnsQueryMessage(
            transactionId = transactionId,
            flags = flags,
            question = DnsQuestion(name = name, type = type),
        )
    }

    fun buildNxdomainResponse(queryPacket: ByteArray): ByteArray {
        val query = parseQuery(queryPacket)
        val header = ByteArray(DNS_HEADER_SIZE)
        header.writeUnsignedShort(0, query.transactionId)
        header.writeUnsignedShort(2, 0x8000 or 0x0400 or 0x0003)
        header.writeUnsignedShort(4, 1)
        header.writeUnsignedShort(6, 0)
        header.writeUnsignedShort(8, 0)
        header.writeUnsignedShort(10, 0)

        return header + encodeQuestion(query.question)
    }

    fun isTruncatedResponse(packet: ByteArray): Boolean {
        require(packet.size >= DNS_HEADER_SIZE) { "MALFORMED_DNS_PACKET" }
        val flags = packet.readUnsignedShort(2)
        return flags and 0x0200 != 0
    }

    private fun encodeQuestion(question: DnsQuestion): ByteArray {
        val labels = question.name.split('.')
        val nameBytes = mutableListOf<Byte>()

        labels.forEach { label ->
            nameBytes += label.length.toByte()
            nameBytes += label.encodeToByteArray().toList()
        }
        nameBytes += 0

        val tail = ByteArray(4)
        tail.writeUnsignedShort(0, question.type.code)
        tail.writeUnsignedShort(2, CLASS_IN)
        return nameBytes.toByteArray() + tail
    }

    private fun parseName(
        packet: ByteArray,
        startOffset: Int,
    ): Pair<String, Int> {
        var offset = startOffset
        val labels = mutableListOf<String>()

        while (true) {
            require(offset < packet.size) { "MALFORMED_DNS_PACKET" }
            val labelLength = packet[offset].toInt() and 0xFF
            offset += 1

            if (labelLength == 0) {
                break
            }

            require(labelLength in 1..63) { "MALFORMED_DNS_PACKET" }
            require(offset + labelLength <= packet.size) { "MALFORMED_DNS_PACKET" }

            labels += packet.copyOfRange(offset, offset + labelLength).decodeToString()
            offset += labelLength
        }

        return labels.joinToString(".") to offset
    }

    private fun skipAdditionalRecord(
        packet: ByteArray,
        startOffset: Int,
    ): Int {
        var offset = startOffset
        require(offset < packet.size) { "MALFORMED_DNS_PACKET" }

        val labelLength = packet[offset].toInt() and 0xFF
        require(labelLength == 0) { "UNEXPECTED_DNS_ADDITIONAL" }
        offset += 1
        require(offset + 10 <= packet.size) { "MALFORMED_DNS_PACKET" }

        val typeCode = packet.readUnsignedShort(offset)
        offset += 2
        require(typeCode == TYPE_OPT) { "UNEXPECTED_DNS_ADDITIONAL" }

        offset += 2
        offset += 4
        val dataLength = packet.readUnsignedShort(offset)
        offset += 2
        require(offset + dataLength <= packet.size) { "MALFORMED_DNS_PACKET" }
        return offset + dataLength
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

object DnsTcpCodec {
    fun encodeQuery(packet: ByteArray): ByteArray {
        require(packet.size <= 0xFFFF) { "DNS_PACKET_TOO_LARGE" }
        val framed = ByteArray(packet.size + 2)
        framed[0] = ((packet.size ushr 8) and 0xFF).toByte()
        framed[1] = (packet.size and 0xFF).toByte()
        packet.copyInto(framed, destinationOffset = 2)
        return framed
    }

    fun decodeResponse(packet: ByteArray): ByteArray {
        require(packet.size >= 2) { "MALFORMED_DNS_TCP_PACKET" }
        val expectedLength = ((packet[0].toInt() and 0xFF) shl 8) or (packet[1].toInt() and 0xFF)
        require(packet.size == expectedLength + 2) { "MALFORMED_DNS_TCP_PACKET" }
        return packet.copyOfRange(2, packet.size)
    }

    fun parseQuery(packet: ByteArray): DnsQueryMessage = DnsMessageCodec.parseQuery(decodeResponse(packet))
}
