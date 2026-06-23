package dev.pawelsowa.focusgate.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.pawelsowa.focusgate.domain.RuleEvaluator
import dev.pawelsowa.focusgate.domain.ScheduleEditor
import dev.pawelsowa.focusgate.domain.ScheduleSummary
import dev.pawelsowa.focusgate.domain.model.DomainRule
import dev.pawelsowa.focusgate.domain.model.FocusGateException
import dev.pawelsowa.focusgate.domain.model.MatchMode
import dev.pawelsowa.focusgate.domain.model.ScheduleMode
import dev.pawelsowa.focusgate.domain.model.UnlockStatus
import dev.pawelsowa.focusgate.domain.model.WeeklySchedule
import dev.pawelsowa.focusgate.domain.repository.FocusGateRepository
import dev.pawelsowa.focusgate.domain.usecase.ObserveDashboardStateUseCase
import dev.pawelsowa.focusgate.vpn.VpnFailureReason
import dev.pawelsowa.focusgate.vpn.VpnRuntime
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DashboardState(
    val vpnStatus: String = "Inactive",
    val editingStatus: String = "Locked",
    val activeRuleCount: Int = 0,
    val blockedNowCount: Int = 0,
    val warning: String? = null,
)

data class DomainListItem(
    val id: String,
    val domain: String,
    val status: String,
    val enabled: Boolean,
)

data class DomainEditorState(
    val id: String? = null,
    val domain: String = "",
    val enabled: Boolean = true,
    val matchMode: MatchMode = MatchMode.DOMAIN_AND_SUBDOMAINS,
    val scheduleMode: ScheduleMode = ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
    val schedule: WeeklySchedule = WeeklySchedule.empty(),
    val selectedDay: Int = 0,
    val copiedDay: List<Boolean>? = null,
    val error: String? = null,
    val saving: Boolean = false,
)

data class DiagnosticsState(
    val braveInstalled: Boolean = false,
    val vpnFailure: String = "None",
    val observedQueries: Long = 0,
    val blockedQueries: Long = 0,
    val forwardedQueries: Long = 0,
    val lastDomain: String = "None",
    val lastBlockedDomain: String = "None",
)

sealed interface FocusGateEvent {
    data object RuleSaved : FocusGateEvent
}

class FocusGateViewModel(
    private val repository: FocusGateRepository,
    observeDashboardState: ObserveDashboardStateUseCase,
) : ViewModel() {
    private val evaluator = RuleEvaluator()
    private val summary = ScheduleSummary()
    private val mutableEditorState = MutableStateFlow(DomainEditorState())
    private val mutableEvents = MutableSharedFlow<FocusGateEvent>()

    val editorState: StateFlow<DomainEditorState> = mutableEditorState.asStateFlow()
    val events: SharedFlow<FocusGateEvent> = mutableEvents.asSharedFlow()

    val dashboardState: StateFlow<DashboardState> =
        observeDashboardState()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DashboardState(),
            )

    val domains: StateFlow<List<DomainListItem>> =
        repository.observeConfig()
            .map { config ->
                config.rules.map { rule ->
                    DomainListItem(
                        id = rule.id,
                        domain = rule.domain,
                        status = when {
                            !rule.enabled -> "Rule disabled"
                            evaluator.shouldBlock(rule, ZonedDateTime.now()) ->
                                "Blocked now · ${summary.format(rule.schedule, rule.scheduleMode)}"
                            else -> "Allowed now · ${summary.format(rule.schedule, rule.scheduleMode)}"
                        },
                        enabled = rule.enabled,
                    )
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    val unlockStatus: StateFlow<UnlockStatus> =
        flow {
            while (currentCoroutineContext().isActive) {
                emit(repository.getUnlockStatus())
                delay(1_000)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UnlockStatus.Locked,
        )

    val diagnosticsState: StateFlow<DiagnosticsState> =
        combine(
            VpnRuntime.braveInstalled,
            VpnRuntime.failureReason,
            VpnRuntime.dnsDiagnostics,
        ) { braveInstalled, failureReason, dns ->
            DiagnosticsState(
                braveInstalled = braveInstalled,
                vpnFailure = failureReason.message(),
                observedQueries = dns.observedQueries,
                blockedQueries = dns.blockedQueries,
                forwardedQueries = dns.forwardedQueries,
                lastDomain = dns.lastDomain ?: "None",
                lastBlockedDomain = dns.lastBlockedDomain ?: "None",
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DiagnosticsState(),
        )

    init {
        repository.observeConfig()
            .map { it.unlockStatus }
            .onEach { status ->
                if (status == UnlockStatus.Unlocked) {
                    mutableEditorState.value = mutableEditorState.value.copy(error = null)
                }
            }
            .launchIn(viewModelScope)
    }

    fun startVpn() {
        viewModelScope.launch {
            repository.startVpn()
        }
    }

    fun stopVpn() {
        viewModelScope.launch {
            repository.stopVpn()
        }
    }

    fun beginAddRule() {
        mutableEditorState.value = DomainEditorState()
    }

    fun beginEditRule(ruleId: String) {
        viewModelScope.launch {
            val rule = repository.getConfig().rules.firstOrNull { it.id == ruleId } ?: return@launch
            mutableEditorState.value = DomainEditorState(
                id = rule.id,
                domain = rule.domain,
                enabled = rule.enabled,
                matchMode = rule.matchMode,
                scheduleMode = rule.scheduleMode,
                schedule = rule.schedule,
            )
        }
    }

    fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        viewModelScope.launch {
            val rule = repository.getConfig().rules.firstOrNull { it.id == ruleId } ?: return@launch
            try {
                repository.updateRule(rule.copy(enabled = enabled))
            } catch (exception: FocusGateException) {
                mutableEditorState.value =
                    mutableEditorState.value.copy(error = exception.message)
            }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            try {
                repository.deleteRule(ruleId)
            } catch (exception: FocusGateException) {
                mutableEditorState.value =
                    mutableEditorState.value.copy(error = exception.message)
            }
        }
    }

    fun setDomain(value: String) {
        mutableEditorState.value = mutableEditorState.value.copy(domain = value, error = null)
    }

    fun setMatchMode(value: MatchMode) {
        mutableEditorState.value = mutableEditorState.value.copy(matchMode = value)
    }

    fun setScheduleMode(value: ScheduleMode) {
        mutableEditorState.value = mutableEditorState.value.copy(scheduleMode = value)
    }

    fun setScheduleSlot(dayIndex: Int, hour: Int, selected: Boolean) {
        val state = mutableEditorState.value
        mutableEditorState.value = state.copy(
            schedule = state.schedule.withSlot(dayIndex, hour, selected),
            selectedDay = dayIndex,
        )
    }

    fun toggleScheduleDay(dayIndex: Int) {
        val state = mutableEditorState.value
        mutableEditorState.value = state.copy(
            schedule = ScheduleEditor.toggleDay(state.schedule, dayIndex),
            selectedDay = dayIndex,
        )
    }

    fun toggleScheduleHour(hour: Int) {
        val state = mutableEditorState.value
        mutableEditorState.value =
            state.copy(schedule = ScheduleEditor.toggleHour(state.schedule, hour))
    }

    fun applySchedulePreset(preset: String) {
        val state = mutableEditorState.value
        val updated = when (preset) {
            "Clear" -> WeeklySchedule.empty()
            "Select all" -> WeeklySchedule.selected()
            "Weekdays" -> ScheduleEditor.weekdays(WeeklySchedule.empty())
            "Weekend" -> ScheduleEditor.weekend(WeeklySchedule.empty())
            "Copy Monday to weekdays" -> ScheduleEditor.copyMondayToWeekdays(state.schedule)
            "Paste schedule" -> state.copiedDay?.let {
                ScheduleEditor.pasteDay(state.schedule, state.selectedDay, it)
            } ?: state.schedule
            else -> state.schedule
        }
        mutableEditorState.value = if (preset == "Copy selected day") {
            state.copy(copiedDay = ScheduleEditor.day(state.schedule, state.selectedDay))
        } else {
            state.copy(schedule = updated)
        }
    }

    fun scheduleSummary(): String {
        val state = mutableEditorState.value
        return summary.format(state.schedule, state.scheduleMode)
    }

    fun saveRule() {
        val state = mutableEditorState.value
        mutableEditorState.value = state.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val rule = DomainRule(
                    id = state.id ?: UUID.randomUUID().toString(),
                    domain = state.domain,
                    enabled = state.enabled,
                    matchMode = state.matchMode,
                    scheduleMode = state.scheduleMode,
                    schedule = state.schedule,
                )
                if (state.id == null) repository.addRule(rule) else repository.updateRule(rule)
                mutableEditorState.value = state.copy(saving = false)
                mutableEvents.emit(FocusGateEvent.RuleSaved)
            } catch (exception: FocusGateException) {
                mutableEditorState.value = state.copy(
                    saving = false,
                    error = exception.message,
                )
            }
        }
    }

    fun enableEditLock() {
        viewModelScope.launch { repository.enableEditLock() }
    }

    fun startUnlockCountdown() {
        viewModelScope.launch { repository.startUnlockCountdown() }
    }

    fun confirmUnlock() {
        viewModelScope.launch {
            try {
                repository.confirmUnlock()
            } catch (exception: FocusGateException) {
                mutableEditorState.value =
                    mutableEditorState.value.copy(error = exception.message)
            }
        }
    }
}

private fun VpnFailureReason.message(): String =
    when (this) {
        VpnFailureReason.NONE -> "None"
        VpnFailureReason.VPN_PERMISSION_DENIED -> "VPN permission denied"
        VpnFailureReason.BRAVE_NOT_INSTALLED -> "Brave is not installed"
        VpnFailureReason.NETWORK_PERMISSION_MISSING -> "Network state permission missing"
        VpnFailureReason.TUN_START_FAILED -> "VPN interface start failed"
    }

fun focusGateViewModelFactory(
    repository: FocusGateRepository,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            FocusGateViewModel(
                repository = repository,
                observeDashboardState = ObserveDashboardStateUseCase(repository),
            )
        }
    }
