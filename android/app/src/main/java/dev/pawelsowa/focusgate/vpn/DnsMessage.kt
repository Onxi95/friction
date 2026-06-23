package dev.pawelsowa.focusgate.vpn

data class DnsQuestion(
    val domain: String,
    val type: Int,
    val questionEnd: Int,
)

class DnsMessageParser {
    fun parseQuestion(message: ByteArray): DnsQuestion? {
        if (message.size < HEADER_SIZE || readUnsignedShort(message, 4) != 1) return null

        var offset = HEADER_SIZE
        val labels = mutableListOf<String>()
        while (offset < message.size) {
            val length = message[offset].toInt() and 0xff
            offset += 1
            if (length == 0) break
            if (length > MAX_LABEL_LENGTH || offset + length > message.size) return null
            labels += message.decodeToString(offset, offset + length)
            offset += length
        }
        if (labels.isEmpty() || offset + QUESTION_TRAILER_SIZE > message.size) return null

        val type = readUnsignedShort(message, offset)
        val questionEnd = offset + QUESTION_TRAILER_SIZE
        return DnsQuestion(
            domain = labels.joinToString(".").lowercase(),
            type = type,
            questionEnd = questionEnd,
        )
    }

    fun nxdomain(query: ByteArray, question: DnsQuestion): ByteArray {
        val response = query.copyOf(question.questionEnd)
        val requestFlags = readUnsignedShort(query, 2)
        writeUnsignedShort(response, 2, requestFlags or RESPONSE_FLAG or NXDOMAIN_CODE)
        writeUnsignedShort(response, 6, 0)
        writeUnsignedShort(response, 8, 0)
        writeUnsignedShort(response, 10, 0)
        return response
    }

    companion object {
        const val TYPE_A = 1
        const val TYPE_AAAA = 28
        private const val HEADER_SIZE = 12
        private const val QUESTION_TRAILER_SIZE = 4
        private const val MAX_LABEL_LENGTH = 63
        private const val RESPONSE_FLAG = 0x8000
        private const val NXDOMAIN_CODE = 3

        fun readUnsignedShort(bytes: ByteArray, offset: Int): Int =
            ((bytes[offset].toInt() and 0xff) shl 8) or
                (bytes[offset + 1].toInt() and 0xff)

        fun writeUnsignedShort(bytes: ByteArray, offset: Int, value: Int) {
            bytes[offset] = (value shr 8).toByte()
            bytes[offset + 1] = value.toByte()
        }
    }
}
