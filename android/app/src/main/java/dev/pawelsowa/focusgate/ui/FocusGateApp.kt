package dev.pawelsowa.focusgate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.pawelsowa.focusgate.domain.ScheduleSummary
import dev.pawelsowa.focusgate.domain.model.MatchMode
import dev.pawelsowa.focusgate.domain.model.ScheduleMode
import dev.pawelsowa.focusgate.domain.model.UnlockStatus
import dev.pawelsowa.focusgate.domain.model.WeeklySchedule
import kotlin.math.floor

private enum class FocusGateRoute(val label: String) {
    Dashboard("Status"),
    Domains("Domains"),
    Diagnostics("Diagnostics"),
    Lock("Lock"),
    Editor("Editor"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusGateApp(
    viewModel: FocusGateViewModel,
    onStartVpn: () -> Unit = viewModel::startVpn,
    onStopVpn: () -> Unit = viewModel::stopVpn,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event == FocusGateEvent.RuleSaved) {
                navController.navigate(FocusGateRoute.Domains.name) {
                    popUpTo(FocusGateRoute.Domains.name) { inclusive = true }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FocusGate", fontWeight = FontWeight.Bold)
                        Text(
                            text = FocusGateRoute.entries
                                .firstOrNull { it.name == currentRoute }
                                ?.label
                                .orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (currentRoute != FocusGateRoute.Editor.name) {
                NavigationBar {
                    FocusGateRoute.entries.filterNot { it == FocusGateRoute.Editor }.forEach { route ->
                        NavigationBarItem(
                            selected = currentRoute == route.name,
                            onClick = {
                                navController.navigate(route.name) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(route.label.take(1)) },
                            label = { Text(route.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = FocusGateRoute.Dashboard.name,
            modifier = Modifier.padding(padding),
        ) {
            composable(FocusGateRoute.Dashboard.name) {
                DashboardScreen(
                    viewModel = viewModel,
                    onViewDomains = { navController.navigate(FocusGateRoute.Domains.name) },
                    onStartVpn = onStartVpn,
                    onStopVpn = onStopVpn,
                )
            }
            composable(FocusGateRoute.Domains.name) {
                DomainListScreen(
                    viewModel = viewModel,
                    onAdd = {
                        viewModel.beginAddRule()
                        navController.navigate(FocusGateRoute.Editor.name)
                    },
                    onEdit = { id ->
                        viewModel.beginEditRule(id)
                        navController.navigate(FocusGateRoute.Editor.name)
                    },
                )
            }
            composable(FocusGateRoute.Lock.name) {
                LockScreen(viewModel)
            }
            composable(FocusGateRoute.Diagnostics.name) {
                DiagnosticsScreen(viewModel)
            }
            composable(FocusGateRoute.Editor.name) {
                DomainEditorScreen(
                    viewModel = viewModel,
                    onCancel = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    viewModel: FocusGateViewModel,
    onViewDomains: () -> Unit,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
) {
    val state by viewModel.dashboardState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Keep distracting domains behind a deliberate pause.",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        item { StatusCard("VPN", state.vpnStatus) }
        item { StatusCard("Editing", state.editingStatus) }
        item { StatusCard("Active rules", state.activeRuleCount.toString()) }
        item { StatusCard("Blocked now", "${state.blockedNowCount} domains") }
        state.warning?.let { warning ->
            item {
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStartVpn) { Text("Start VPN") }
                OutlinedButton(onClick = onStopVpn) { Text("Stop VPN") }
                OutlinedButton(onClick = onViewDomains) { Text("View domains") }
            }
        }
    }
}

@Composable
private fun DomainListScreen(
    viewModel: FocusGateViewModel,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val domains by viewModel.domains.collectAsStateWithLifecycle()
    val unlockStatus by viewModel.unlockStatus.collectAsStateWithLifecycle()
    val editingEnabled = unlockStatus == UnlockStatus.Unlocked

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${domains.size} configured", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onAdd, enabled = editingEnabled) { Text("Add domain") }
            }
        }
        if (!editingEnabled) {
            item {
                Text(
                    "Unlock editing to change rules.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(domains, key = DomainListItem::id) { domain ->
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(domain.domain, style = MaterialTheme.typography.titleMedium)
                        Text(
                            domain.status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = domain.enabled,
                        onCheckedChange = { enabled ->
                            viewModel.setRuleEnabled(domain.id, enabled)
                        },
                        enabled = editingEnabled,
                    )
                    TextButton(
                        onClick = { onEdit(domain.id) },
                        enabled = editingEnabled,
                    ) {
                        Text("Edit")
                    }
                    TextButton(
                        onClick = { viewModel.deleteRule(domain.id) },
                        enabled = editingEnabled,
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun DomainEditorScreen(
    viewModel: FocusGateViewModel,
    onCancel: () -> Unit,
) {
    val state by viewModel.editorState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.domain,
                onValueChange = viewModel::setDomain,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Domain") },
                placeholder = { Text("example.com") },
                singleLine = true,
                isError = state.error != null,
                supportingText = { state.error?.let { Text(it) } },
            )
        }
        item {
            ChoiceSection(
                title = "Match mode",
                options = listOf(
                    MatchMode.DOMAIN_AND_SUBDOMAINS to "Domain and all subdomains",
                    MatchMode.EXACT to "Exact domain only",
                ),
                selected = state.matchMode,
                onSelected = viewModel::setMatchMode,
            )
        }
        item {
            ChoiceSection(
                title = "Schedule mode",
                options = listOf(
                    ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS to
                        "Allow only during selected hours",
                    ScheduleMode.BLOCK_DURING_SELECTED_HOURS to
                        "Block during selected hours",
                ),
                selected = state.scheduleMode,
                onSelected = viewModel::setScheduleMode,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Weekly schedule", style = MaterialTheme.typography.titleLarge)
                ScheduleGrid(
                    schedule = state.schedule,
                    onSlot = viewModel::setScheduleSlot,
                    onDay = viewModel::toggleScheduleDay,
                    onHour = viewModel::toggleScheduleHour,
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Presets", style = MaterialTheme.typography.titleMedium)
                val presets = listOf(
                    "Clear",
                    "Select all",
                    "Weekdays",
                    "Weekend",
                    "Copy Monday to weekdays",
                    "Copy selected day",
                    "Paste schedule",
                )
                presets.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.applySchedulePreset(preset) },
                                label = { Text(preset) },
                            )
                        }
                    }
                }
            }
        }
        item {
            Card {
                Text(
                    text = viewModel.scheduleSummary(),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = viewModel::saveRule,
                    enabled = !state.saving && state.domain.isNotBlank(),
                ) {
                    Text(if (state.saving) "Saving..." else "Save and lock")
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun <T> ChoiceSection(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun ScheduleGrid(
    schedule: WeeklySchedule,
    onSlot: (Int, Int, Boolean) -> Unit,
    onDay: (Int) -> Unit,
    onHour: (Int) -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(48.dp))
            repeat(24) { hour ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onHour(hour) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(hour.toString().padStart(2, '0'), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        repeat(7) { day ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(32.dp)
                        .clickable { onDay(day) },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(ScheduleSummary.DAY_NAMES[day], style = MaterialTheme.typography.labelMedium)
                }
                repeat(24) { hour ->
                    ScheduleCell(
                        day = day,
                        hour = hour,
                        schedule = schedule,
                        onSlot = onSlot,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleCell(
    day: Int,
    hour: Int,
    schedule: WeeklySchedule,
    onSlot: (Int, Int, Boolean) -> Unit,
) {
    val selected = schedule[day, hour]
    val cellPx = with(LocalDensity.current) { 32.dp.toPx() }
    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .size(32.dp)
            .padding(1.dp)
            .background(if (selected) selectedColor else unselectedColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .clickable { onSlot(day, hour, !selected) }
            .pointerInput(day, hour, selected) {
                var dragStart = Offset.Zero
                val dragValue = !selected
                detectDragGestures(
                    onDragStart = {
                        dragStart = it
                        onSlot(day, hour, dragValue)
                    },
                    onDrag = { change, _ ->
                        val targetHour = hour +
                            floor((change.position.x - dragStart.x) / cellPx).toInt()
                        val targetDay = day +
                            floor((change.position.y - dragStart.y) / cellPx).toInt()
                        if (targetDay in 0..6 && targetHour in 0..23) {
                            onSlot(targetDay, targetHour, dragValue)
                        }
                    },
                )
            },
    )
}

@Composable
private fun DiagnosticsScreen(viewModel: FocusGateViewModel) {
    val state by viewModel.dashboardState.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnosticsState.collectAsStateWithLifecycle()
    val vpnActive = state.vpnStatus == "Active"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Diagnostics", style = MaterialTheme.typography.headlineSmall) }
        item { StatusCard("VPN active", if (vpnActive) "Yes" else "No") }
        item { StatusCard("Brave installed", if (diagnostics.braveInstalled) "Yes" else "No") }
        item { StatusCard("VPN failure", diagnostics.vpnFailure) }
        item {
            StatusCard(
                "DNS interception",
                if (diagnostics.observedQueries > 0) "Working" else "No DNS queries seen",
            )
        }
        item {
            StatusCard(
                "Brave filtering test",
                if (diagnostics.blockedQueries > 0) "Passed" else "No blocked DNS query seen",
            )
        }
        item { StatusCard("DNS queries seen", diagnostics.observedQueries.toString()) }
        item { StatusCard("Blocked DNS queries", diagnostics.blockedQueries.toString()) }
        item { StatusCard("Forwarded DNS queries", diagnostics.forwardedQueries.toString()) }
        item { StatusCard("Last DNS domain", diagnostics.lastDomain) }
        item { StatusCard("Last blocked domain", diagnostics.lastBlockedDomain) }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Brave Secure DNS", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Disable Secure DNS in Brave or configure it to use the current provider. Otherwise FocusGate may not see DNS requests.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Always-on VPN", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Settings -> Network and Internet -> VPN -> FocusGate -> Always-on VPN.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Do not enable Block connections without VPN for this DNS-only MVP.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun LockScreen(viewModel: FocusGateViewModel) {
    val status by viewModel.unlockStatus.collectAsStateWithLifecycle()
    val title: String
    val detail: String
    when (val current = status) {
        UnlockStatus.Unlocked -> {
            title = "Editing unlocked"
            detail = "Saving a rule locks editing again."
        }
        UnlockStatus.Locked -> {
            title = "Editing locked"
            detail = "Start a five-minute delay before configuration can change."
        }
        is UnlockStatus.UnlockPending -> {
            title = if (current.canConfirm) "Delay complete" else "Unlock pending"
            val seconds = (current.remainingMs + 999) / 1_000
            detail = if (current.canConfirm) {
                "Confirm explicitly to unlock editing."
            } else {
                "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')} remaining"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (val current = status) {
            UnlockStatus.Unlocked ->
                Button(onClick = viewModel::enableEditLock) { Text("Lock editing") }
            UnlockStatus.Locked ->
                Button(onClick = viewModel::startUnlockCountdown) { Text("Start unlocking") }
            is UnlockStatus.UnlockPending ->
                Button(
                    onClick = viewModel::confirmUnlock,
                    enabled = current.canConfirm,
                ) {
                    Text("Confirm unlock")
                }
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
