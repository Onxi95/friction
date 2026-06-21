package dev.pawelsowa.focusgate.domain

object DomainMatcher {
    fun matchesExact(query: String, rule: String): Boolean = query == rule

    fun matchesDomainAndSubdomains(query: String, rule: String): Boolean =
        query == rule || query.endsWith(".$rule")

    fun matchesRule(query: String, rule: DomainRule): Boolean =
        when (rule.matchMode) {
            MatchMode.EXACT -> matchesExact(query, rule.domain)
            MatchMode.DOMAIN_AND_SUBDOMAINS -> matchesDomainAndSubdomains(query, rule.domain)
        }
}
