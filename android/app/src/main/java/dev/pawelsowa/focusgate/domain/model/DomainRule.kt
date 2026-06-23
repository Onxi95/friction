package dev.pawelsowa.focusgate.domain.model

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
    val schedule: WeeklySchedule,
)

data class WeeklySchedule(
    val slots: List<Boolean>,
) {
    init {
        require(slots.size == SLOT_COUNT) {
            "Weekly schedule must contain exactly $SLOT_COUNT slots"
        }
    }

    operator fun get(dayIndex: Int, hour: Int): Boolean = slots[slotIndex(dayIndex, hour)]

    fun withSlot(dayIndex: Int, hour: Int, selected: Boolean): WeeklySchedule =
        WeeklySchedule(
            slots = slots.toMutableList().apply {
                this[slotIndex(dayIndex, hour)] = selected
            },
        )

    companion object {
        const val DAYS = 7
        const val HOURS_PER_DAY = 24
        const val SLOT_COUNT = DAYS * HOURS_PER_DAY

        fun empty(): WeeklySchedule = WeeklySchedule(List(SLOT_COUNT) { false })

        fun selected(): WeeklySchedule = WeeklySchedule(List(SLOT_COUNT) { true })

        fun slotIndex(dayIndex: Int, hour: Int): Int {
            require(dayIndex in 0 until DAYS) { "Day index must be between 0 and 6" }
            require(hour in 0 until HOURS_PER_DAY) { "Hour must be between 0 and 23" }
            return dayIndex * HOURS_PER_DAY + hour
        }
    }
}
