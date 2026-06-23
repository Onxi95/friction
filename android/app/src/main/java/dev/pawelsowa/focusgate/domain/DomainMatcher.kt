package dev.pawelsowa.focusgate.domain

import dev.pawelsowa.focusgate.domain.model.DomainRule
import dev.pawelsowa.focusgate.domain.model.MatchMode

class DomainMatcher {
    fun matches(query: String, rule: DomainRule): Boolean =
        when (rule.matchMode) {
            MatchMode.EXACT -> query == rule.domain
            MatchMode.DOMAIN_AND_SUBDOMAINS ->
                query == rule.domain || query.endsWith(".${rule.domain}")
        }
}
