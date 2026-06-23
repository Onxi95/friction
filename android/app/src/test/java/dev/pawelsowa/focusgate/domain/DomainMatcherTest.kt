package dev.pawelsowa.focusgate.domain

import dev.pawelsowa.focusgate.domain.model.DomainRule
import dev.pawelsowa.focusgate.domain.model.MatchMode
import dev.pawelsowa.focusgate.domain.model.ScheduleMode
import dev.pawelsowa.focusgate.domain.model.WeeklySchedule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMatcherTest {
    private val matcher = DomainMatcher()

    @Test
    fun `exact mode matches only exact domain`() {
        val rule = rule(MatchMode.EXACT)

        assertTrue(matcher.matches("facebook.com", rule))
        assertFalse(matcher.matches("www.facebook.com", rule))
    }

    @Test
    fun `subdomain mode matches label boundary but not suffix-only domain`() {
        val rule = rule(MatchMode.DOMAIN_AND_SUBDOMAINS)

        assertTrue(matcher.matches("m.facebook.com", rule))
        assertFalse(matcher.matches("notfacebook.com", rule))
        assertFalse(matcher.matches("facebook.com.example.org", rule))
    }

    private fun rule(matchMode: MatchMode): DomainRule =
        DomainRule(
            id = "facebook",
            domain = "facebook.com",
            enabled = true,
            matchMode = matchMode,
            scheduleMode = ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
            schedule = WeeklySchedule.empty(),
        )
}
