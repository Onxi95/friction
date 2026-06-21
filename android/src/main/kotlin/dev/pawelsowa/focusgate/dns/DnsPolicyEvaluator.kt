package dev.pawelsowa.focusgate.dns

import dev.pawelsowa.focusgate.config.AppConfig
import dev.pawelsowa.focusgate.domain.DomainMatcher
import dev.pawelsowa.focusgate.domain.DomainNormalizer
import dev.pawelsowa.focusgate.domain.RuleEvaluator
import java.time.ZonedDateTime

enum class DnsAction {
    ALLOW,
    BLOCK,
}

data class DnsDecision(
    val action: DnsAction,
    val matchedRuleId: String? = null,
    val reason: String,
)

class DnsPolicyEvaluator(
    private val domainNormalizer: DomainNormalizer = DomainNormalizer(),
) {
    fun evaluate(
        queryDomain: String,
        now: ZonedDateTime,
        config: AppConfig,
    ): DnsDecision {
        val normalizedQuery =
            try {
                domainNormalizer.normalize(queryDomain)
            } catch (_: IllegalArgumentException) {
                return DnsDecision(
                    action = DnsAction.ALLOW,
                    reason = "INVALID_QUERY_DOMAIN",
                )
            }

        val matchedRule =
            config.rules.firstOrNull { rule ->
                DomainMatcher.matchesRule(normalizedQuery, rule)
            }

        if (matchedRule == null) {
            return DnsDecision(
                action = DnsAction.ALLOW,
                reason = "NO_MATCHING_RULE",
            )
        }

        return if (RuleEvaluator.shouldBlock(matchedRule, now)) {
            DnsDecision(
                action = DnsAction.BLOCK,
                matchedRuleId = matchedRule.id,
                reason = "SCHEDULE_BLOCKED",
            )
        } else {
            DnsDecision(
                action = DnsAction.ALLOW,
                matchedRuleId = matchedRule.id,
                reason = "SCHEDULE_ALLOWED",
            )
        }
    }
}
