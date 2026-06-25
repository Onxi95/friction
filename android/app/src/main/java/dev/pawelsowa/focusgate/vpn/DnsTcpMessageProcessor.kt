package dev.pawelsowa.focusgate.vpn

import dev.pawelsowa.focusgate.domain.DomainMatcher
import dev.pawelsowa.focusgate.domain.RuleEvaluator
import dev.pawelsowa.focusgate.domain.model.DomainRule
import java.time.ZonedDateTime

class DnsTcpMessageProcessor(
    private val upstream: UpstreamDnsClient,
    private val matcher: DomainMatcher = DomainMatcher(),
    private val evaluator: RuleEvaluator = RuleEvaluator(),
    private val now: () -> ZonedDateTime = ZonedDateTime::now,
) {
    private val dnsParser = DnsMessageParser()

    fun process(frame: ByteArray, rules: List<DomainRule>): ByteArray? {
        if (frame.size < LENGTH_PREFIX_SIZE) return null
        val messageLength = DnsMessageParser.readUnsignedShort(frame, 0)
        if (messageLength != frame.size - LENGTH_PREFIX_SIZE) return null
        if (messageLength > MAX_DNS_MESSAGE_SIZE) return null

        val message = frame.copyOfRange(LENGTH_PREFIX_SIZE, frame.size)
        val query = dnsParser.parseQuery(message) ?: return null
        val dnsQuestions = query.questions.filter(::isSupportedQuestion)
        if (dnsQuestions.isEmpty()) return null

        val blockedQuestion = dnsQuestions.firstOrNull { question ->
            rules.any { rule ->
                matcher.matches(question.domain, rule) && evaluator.shouldBlock(rule, now())
            }
        }
        val blocked = blockedQuestion != null
        val observedDomain = blockedQuestion?.domain ?: dnsQuestions.first().domain
        VpnRuntime.recordDnsQuery(observedDomain, blocked)
        val response = if (blocked) {
            dnsParser.nxdomain(message, query)
        } else {
            val upstreamResponse = upstream.query(message)
            recordUpstreamResult(upstreamResponse)
            upstreamResponse ?: return null
        }
        val framed = ByteArray(LENGTH_PREFIX_SIZE + response.size)
        DnsMessageParser.writeUnsignedShort(framed, 0, response.size)
        response.copyInto(framed, LENGTH_PREFIX_SIZE)
        return framed
    }

    private fun recordUpstreamResult(response: ByteArray?) {
        if (response == null) {
            VpnRuntime.failureReason.value = VpnFailureReason.UPSTREAM_DNS_UNAVAILABLE
        } else if (VpnRuntime.failureReason.value == VpnFailureReason.UPSTREAM_DNS_UNAVAILABLE) {
            VpnRuntime.failureReason.value = VpnFailureReason.NONE
        }
    }

    companion object {
        private const val LENGTH_PREFIX_SIZE = 2
        private const val MAX_DNS_MESSAGE_SIZE = 4_096

        private fun isSupportedQuestion(question: DnsQuestion): Boolean =
            question.type == DnsMessageParser.TYPE_A ||
                question.type == DnsMessageParser.TYPE_AAAA
    }
}
