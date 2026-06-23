package dev.pawelsowa.focusgate.vpn

import dev.pawelsowa.focusgate.domain.DomainMatcher
import dev.pawelsowa.focusgate.domain.RuleEvaluator
import dev.pawelsowa.focusgate.domain.model.DomainRule
import java.time.ZonedDateTime

fun interface UpstreamDnsClient {
    fun query(request: ByteArray): ByteArray?
}

class DnsPacketProcessor(
    private val upstream: UpstreamDnsClient,
    private val matcher: DomainMatcher = DomainMatcher(),
    private val evaluator: RuleEvaluator = RuleEvaluator(),
    private val now: () -> ZonedDateTime = ZonedDateTime::now,
) {
    private val ipv4Codec = Ipv4UdpPacketCodec()
    private val ipv6Codec = Ipv6UdpPacketCodec()
    private val dnsParser = DnsMessageParser()

    fun process(packetBytes: ByteArray, length: Int, rules: List<DomainRule>): ByteArray? {
        val packet = decode(packetBytes, length) ?: return null
        if (packet.destinationPort != DNS_PORT) return null
        val question = dnsParser.parseQuestion(packet.payload) ?: return null
        if (question.type != DnsMessageParser.TYPE_A && question.type != DnsMessageParser.TYPE_AAAA) {
            return null
        }

        val blocked = rules.any { rule ->
            matcher.matches(question.domain, rule) && evaluator.shouldBlock(rule, now())
        }
        VpnRuntime.recordDnsQuery(question.domain, blocked)
        val dnsResponse = if (blocked) {
            dnsParser.nxdomain(packet.payload, question)
        } else {
            upstream.query(packet.payload) ?: return null
        }
        return packet.response(dnsResponse)
    }

    private fun decode(packetBytes: ByteArray, length: Int): DnsUdpPacket? {
        val ipv4 = ipv4Codec.decode(packetBytes, length)
        if (ipv4 != null) {
            return DnsUdpPacket(
                destinationPort = ipv4.destinationPort,
                payload = ipv4.payload,
                response = { payload -> ipv4Codec.response(ipv4, payload) },
            )
        }
        val ipv6 = ipv6Codec.decode(packetBytes, length)
        if (ipv6 != null) {
            return DnsUdpPacket(
                destinationPort = ipv6.destinationPort,
                payload = ipv6.payload,
                response = { payload -> ipv6Codec.response(ipv6, payload) },
            )
        }
        return null
    }

    private data class DnsUdpPacket(
        val destinationPort: Int,
        val payload: ByteArray,
        val response: (ByteArray) -> ByteArray,
    )

    companion object {
        private const val DNS_PORT = 53
    }
}
