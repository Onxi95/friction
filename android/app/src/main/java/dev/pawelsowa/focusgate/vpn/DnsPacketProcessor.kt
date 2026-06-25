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
    private val tcpUpstream: UpstreamDnsClient = upstream,
    private val matcher: DomainMatcher = DomainMatcher(),
    private val evaluator: RuleEvaluator = RuleEvaluator(),
    private val now: () -> ZonedDateTime = ZonedDateTime::now,
) {
    private val ipv4Codec = Ipv4UdpPacketCodec()
    private val ipv6Codec = Ipv6UdpPacketCodec()
    private val ipv4TcpCodec = Ipv4TcpPacketCodec()
    private val dnsParser = DnsMessageParser()
    private val tcpProcessor = DnsTcpMessageProcessor(
        upstream = tcpUpstream,
        matcher = matcher,
        evaluator = evaluator,
        now = now,
    )

    fun process(packetBytes: ByteArray, length: Int, rules: List<DomainRule>): ByteArray? {
        val packet = decode(packetBytes, length) ?: return null
        if (packet.destinationPort != DNS_PORT) return null
        val dnsResponse = packet.process(packet.payload, rules) ?: return null
        return packet.response(dnsResponse)
    }

    private fun processUdpPayload(payload: ByteArray, rules: List<DomainRule>): ByteArray? {
        if (payload.size > MAX_DNS_MESSAGE_SIZE) return null
        val query = dnsParser.parseQuery(payload) ?: return null
        val dnsQuestions = query.questions.filter(::isSupportedQuestion)
        if (dnsQuestions.isEmpty()) return null

        val blockedQuestion = dnsQuestions.firstOrNull { question ->
            rules.any { rule ->
                matcher.matches(question.domain, rule) && evaluator.shouldBlock(rule, now())
            }
        }
        val observedDomain = blockedQuestion?.domain ?: dnsQuestions.first().domain
        val blocked = blockedQuestion != null
        VpnRuntime.recordDnsQuery(observedDomain, blocked)
        return if (blocked) {
            dnsParser.nxdomain(payload, query)
        } else {
            upstream.query(payload).also { response ->
                recordUpstreamResult(response)
            }
        }
    }

    private fun recordUpstreamResult(response: ByteArray?) {
        if (response == null) {
            VpnRuntime.failureReason.value = VpnFailureReason.UPSTREAM_DNS_UNAVAILABLE
        } else if (VpnRuntime.failureReason.value == VpnFailureReason.UPSTREAM_DNS_UNAVAILABLE) {
            VpnRuntime.failureReason.value = VpnFailureReason.NONE
        }
    }

    private fun decode(packetBytes: ByteArray, length: Int): DnsPacket? {
        val ipv4 = ipv4Codec.decode(packetBytes, length)
        if (ipv4 != null) {
            return DnsPacket(
                destinationPort = ipv4.destinationPort,
                payload = ipv4.payload,
                response = { payload -> ipv4Codec.response(ipv4, payload) },
                process = ::processUdpPayload,
            )
        }
        val ipv6 = ipv6Codec.decode(packetBytes, length)
        if (ipv6 != null) {
            return DnsPacket(
                destinationPort = ipv6.destinationPort,
                payload = ipv6.payload,
                response = { payload -> ipv6Codec.response(ipv6, payload) },
                process = ::processUdpPayload,
            )
        }
        val tcp = ipv4TcpCodec.decode(packetBytes, length)
        if (tcp != null) {
            return DnsPacket(
                destinationPort = tcp.destinationPort,
                payload = tcp.payload,
                response = { payload -> ipv4TcpCodec.response(tcp, payload) },
                process = { frame, rules -> tcpProcessor.process(frame, rules) },
            )
        }
        return null
    }

    private data class DnsPacket(
        val destinationPort: Int,
        val payload: ByteArray,
        val response: (ByteArray) -> ByteArray,
        val process: (ByteArray, List<DomainRule>) -> ByteArray?,
    )

    companion object {
        private const val DNS_PORT = 53
        private const val MAX_DNS_MESSAGE_SIZE = 4_096

        private fun isSupportedQuestion(question: DnsQuestion): Boolean =
            question.type == DnsMessageParser.TYPE_A ||
                question.type == DnsMessageParser.TYPE_AAAA
    }
}
