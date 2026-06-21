package dev.pawelsowa.focusgate.dns

import dev.pawelsowa.focusgate.config.AppConfig
import java.time.ZonedDateTime

sealed interface PacketProcessingResult {
    data class Respond(
        val packet: ByteArray,
        val reason: String,
    ) : PacketProcessingResult

    data class Forward(
        val queryPacket: ByteArray,
        val questionName: String,
        val reason: String,
    ) : PacketProcessingResult
}

class DnsPacketProcessor(
    private val policyEvaluator: DnsPolicyEvaluator = DnsPolicyEvaluator(),
) {
    fun processQuery(
        packet: ByteArray,
        now: ZonedDateTime,
        config: AppConfig,
    ): PacketProcessingResult {
        val query = DnsMessageCodec.parseQuery(packet)
        val decision = policyEvaluator.evaluate(query.question.name, now, config)

        return if (decision.action == DnsAction.BLOCK) {
            PacketProcessingResult.Respond(
                packet = DnsMessageCodec.buildNxdomainResponse(packet),
                reason = decision.reason,
            )
        } else {
            PacketProcessingResult.Forward(
                queryPacket = packet,
                questionName = query.question.name,
                reason = decision.reason,
            )
        }
    }
}
