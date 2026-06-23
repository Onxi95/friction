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

        val message = frame.copyOfRange(LENGTH_PREFIX_SIZE, frame.size)
        val question = dnsParser.parseQuestion(message) ?: return null
        if (question.type != DnsMessageParser.TYPE_A && question.type != DnsMessageParser.TYPE_AAAA) {
            return null
        }

        val blocked = rules.any { rule ->
            matcher.matches(question.domain, rule) && evaluator.shouldBlock(rule, now())
        }
        VpnRuntime.recordDnsQuery(question.domain, blocked)
        val response = if (blocked) {
            dnsParser.nxdomain(message, question)
        } else {
            upstream.query(message) ?: return null
        }
        val framed = ByteArray(LENGTH_PREFIX_SIZE + response.size)
        DnsMessageParser.writeUnsignedShort(framed, 0, response.size)
        response.copyInto(framed, LENGTH_PREFIX_SIZE)
        return framed
    }

    companion object {
        private const val LENGTH_PREFIX_SIZE = 2
    }
}
