package dev.pawelsowa.focusgate.domain

enum class MatchMode {
    EXACT,
    DOMAIN_AND_SUBDOMAINS,
}

enum class ScheduleMode {
    BLOCK_DURING_SELECTED_HOURS,
    ALLOW_ONLY_DURING_SELECTED_HOURS,
}

data class DomainRule(
    val id: String,
    val domain: String,
    val enabled: Boolean,
    val matchMode: MatchMode,
    val scheduleMode: ScheduleMode,
    val weeklySlots: BooleanArray,
) {
    init {
        require(weeklySlots.size == 168) { "INVALID_SCHEDULE" }
    }
}
