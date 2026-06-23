# FocusGate — Implementation Plan

A local Android focus tool that blocks selected domains according to a weekly schedule and requires a five-minute waiting period before configuration can be edited.

## 1. Project overview

### Project name

**FocusGate**

Possible identifiers:

```text
Repository: focusgate
Android package: dev.pawelsowa.focusgate
VPN service: FocusGateVpnService
UI: Jetpack Compose
```

### Main goal

Build an Android application that:

- blocks selected domains,
- supports a separate weekly schedule for every domain,
- allows hourly configuration for every day of the week,
- filters Brave through Android `VpnService`,
- works without root access,
- works without a remote server,
- stores configuration in the native Android layer,
- adds a five-minute delay before editing can be unlocked.

---

## 2. MVP scope

The first version should support:

- Android only,
- Jetpack Compose interface,
- Kotlin native implementation,
- DNS-only filtering,
- Brave-only filtering,
- one schedule per domain,
- hourly schedule precision,
- 168 schedule slots per domain,
- exact-domain matching,
- domain-and-subdomain matching,
- five-minute editing lock,
- native persistent storage,
- VPN status reporting,
- signed APK distribution.

The first version should not include:

- iOS support,
- remote administration,
- cloud synchronization,
- minute-level schedules,
- family accounts,
- full-device TCP/UDP forwarding,
- VPN lockdown mode,
- usage analytics,
- support for multiple active VPN providers.

---

## 3. Technical architecture

```text
Jetpack Compose
├── domain list
├── domain editor
├── weekly schedule editor
├── VPN status screen
├── lock controls
└── unlock countdown
Kotlin
├── FocusGateVpnService
├── DnsPacketProcessor
├── RuleEvaluator
├── DomainMatcher
├── DomainNormalizer
├── LockManager
├── ConfigRepository
├── Proto DataStore
└── UpstreamDnsClient
```

### Compose UI responsibilities

Compose should handle:

- navigation,
- domain-list interface,
- domain form,
- weekly schedule grid,
- readable schedule summaries,
- lock interface,
- countdown display,
- VPN status display,
- user-facing validation messages.

### Kotlin responsibilities

Kotlin should handle:

- `VpnService`,
- TUN interface creation,
- DNS packet parsing,
- domain matching,
- schedule evaluation,
- editing-lock enforcement,
- persistent configuration,
- upstream DNS forwarding,
- blocking DNS responses,
- operation while the app UI is not running.

Compose UI must not be the source of truth for:

- domain rules,
- lock state,
- VPN state,
- current schedule state.

---

## 4. Domain rule model

```ts
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
```

### Schedule indexing

```ts
fun getSlotIndex(dayIndex: Int, hour: Int): Int = dayIndex * 24 + hour
```

Day indexes:

```text
0 = Monday
1 = Tuesday
2 = Wednesday
3 = Thursday
4 = Friday
5 = Saturday
6 = Sunday
```

Example:

```ts
val facebookRule = DomainRule(
    id = "facebook",
    domain = "facebook.com",
    enabled = true,
    matchMode = MatchMode.DOMAIN_AND_SUBDOMAINS,
    scheduleMode = ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
    schedule = WeeklySchedule.empty(),
)
```

---

## 5. Domain validation and normalization

Users should enter:

```text
facebook.com
```

Users should not enter:

```text
https://facebook.com/feed
```

Before saving a domain:

1. trim whitespace,
2. convert it to lowercase,
3. remove the trailing dot,
4. reject protocols,
5. reject URL paths,
6. reject ports,
7. convert internationalized domains with `IDN.toASCII`,
8. validate the final hostname,
9. check for duplicate rules.

Suggested Kotlin interface:

```kotlin
interface DomainNormalizer {
    fun normalize(input: String): Result<String>
}
```

### Exact matching

```kotlin
fun matchesExact(
    query: String,
    rule: String,
): Boolean {
    return query == rule
}
```

### Domain and subdomain matching

```kotlin
fun matchesDomainAndSubdomains(
    query: String,
    rule: String,
): Boolean {
    return query == rule || query.endsWith(".$rule")
}
```

This should match:

```text
facebook.com
www.facebook.com
m.facebook.com
example.facebook.com
```

It should not match:

```text
notfacebook.com
facebook.com.example.org
```

---

## 6. Main screens

### 6.1 Dashboard

Display:

- VPN status,
- editing-lock status,
- number of active rules,
- number of domains currently blocked,
- warnings when filtering is inactive.

Example:

```text
FocusGate

VPN
Active

Editing
Locked

Currently blocked
2 domains

[View domains]
[VPN settings]
```

### 6.2 Domain list

Each domain row should display:

```text
facebook.com
Blocked now · available from 19:00

[enabled switch] [edit]
```

Possible statuses:

- `Allowed now`
- `Blocked now`
- `Available from 19:00`
- `Blocked from 20:00`
- `Rule disabled`
- `VPN inactive`

Example:

```text
Domains

facebook.com
Blocked now · available from 19:00

reddit.com
Allowed now · blocked from 20:00

[Add domain]
```

### 6.3 Domain editor

Fields:

```text
Domain
[ facebook.com ]

Match mode
(o) Domain and all subdomains
( ) Exact domain only

Schedule mode
(o) Allow only during selected hours
( ) Block during selected hours
```

Below the fields, display the weekly schedule editor.

### 6.4 Lock screen

Display:

- current lock state,
- countdown status,
- explanation of the five-minute delay,
- confirmation action after the countdown finishes.

---

## 7. Weekly schedule editor

Use a `7 × 24` grid.

```text
       00 01 02 03 ... 21 22 23
Mon    ■  ■  ■  ■      ■  ■  ■
Tue    ■  ■  ■  ■      ■  ■  ■
Wed    ■  ■  ■  ■      ■  ■  ■
Thu    ■  ■  ■  ■      ■  ■  ■
Fri    ■  ■  ■  ■      ■  ■  ■
Sat    □  □  □  □      □  □  □
Sun    □  □  □  □      □  □  □
```

### Required interactions

- Tap a cell to toggle one hour.
- Drag across cells to select a range.
- Tap a day label to toggle the entire day.
- Tap an hour label to toggle that hour for every day.
- Support horizontal scrolling on smaller screens.
- Visually distinguish selected and unselected cells.
- Show a preview before saving.

### Schedule presets

Provide:

- `Clear`
- `Select all`
- `Weekdays`
- `Weekend`
- `Copy Monday to weekdays`
- `Copy selected day`
- `Paste schedule`

### Readable summary

Generate a readable summary from the schedule.

Example:

> Facebook is available Monday through Friday from 19:00 to 20:00 and on weekends from 10:00 to 12:00.

The summary should update immediately when the schedule changes.

---

## 8. Schedule evaluation

Schedule evaluation must happen in Kotlin.

```kotlin
fun shouldBlock(
    rule: DomainRule,
    now: ZonedDateTime,
): Boolean {
    if (!rule.enabled) {
        return false
    }

    val dayIndex = now.dayOfWeek.value - 1
    val slotIndex = dayIndex * 24 + now.hour
    val selected = rule.weeklySlots[slotIndex]

    return when (rule.scheduleMode) {
        ScheduleMode.BLOCK_DURING_SELECTED_HOURS -> selected
        ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS -> !selected
    }
}
```

No hourly alarm is required.

The schedule should be evaluated for every DNS request. A schedule change therefore takes effect on the first DNS request after the hour changes.

### Time zone

For the MVP:

```text
Use the device's current time zone
```

A later version may support:

```text
Always use Europe/Warsaw
```

During daylight-saving transitions, repeated instances of the same local hour should use the same schedule slot.

---

## 9. Editing lock

### Lock states

```ts
sealed interface EditLockState {
    data object Unlocked : EditLockState
    data object Locked : EditLockState
    data class UnlockPending(
        val startedElapsedMs: Long,
        val bootCount: Int,
    ) : EditLockState
}
```

### Lock flow

1. The user configures the application.
2. The user selects `Lock editing`.
3. All configuration-changing operations become unavailable.
4. The user selects `Start unlocking`.
5. A five-minute countdown starts.
6. Closing the app does not cancel or shorten the countdown.
7. After five minutes, the `Unlock editing` action becomes available.
8. Unlocking requires another explicit confirmation.
9. After the user saves changes, editing is locked automatically again.

### Countdown implementation

Use:

```kotlin
SystemClock.elapsedRealtime()
```

Do not use:

```kotlin
System.currentTimeMillis()
```

`elapsedRealtime()` is monotonic and cannot be shortened by changing the system clock.

Store the device boot count:

```kotlin
Settings.Global.BOOT_COUNT
```

Example:

```kotlin
private const val UNLOCK_DELAY_MS = 5 * 60 * 1000L

data class UnlockAttempt(
    val startedElapsedMs: Long,
    val bootCount: Int,
)

fun canUnlock(
    attempt: UnlockAttempt,
    currentBootCount: Int,
): Boolean {
    if (attempt.bootCount != currentBootCount) {
        return false
    }

    val elapsed =
        SystemClock.elapsedRealtime() - attempt.startedElapsedMs

    return elapsed >= UNLOCK_DELAY_MS
}
```

### Device restart behavior

After a device restart:

- cancel any pending unlock,
- restore the `LOCKED` state,
- require a new five-minute countdown.

### Operations protected by the lock

While editing is locked, prevent:

- adding domains,
- deleting domains,
- editing domains,
- editing schedules,
- enabling or disabling rules,
- changing match mode,
- changing schedule mode,
- changing filtered applications,
- changing upstream DNS,
- importing configuration,
- disabling filtering from inside the app,
- resetting settings.

Viewing rules, schedules, status, and diagnostics should remain available.

### Native lock enforcement

The lock must be validated in Kotlin before every write.

```kotlin
suspend fun updateRule(rule: DomainRule) {
    lockManager.requireEditingUnlocked()
    configRepository.updateRule(rule)
}
```

Disabling Compose buttons is not sufficient.

---

## 10. Native storage

Use **Proto DataStore**.

Do not store authoritative configuration in UI state.

Example schema:

```proto
syntax = "proto3";

message AppConfig {
  repeated DomainRule rules = 1;
  LockState lock_state = 2;
  VpnConfig vpn_config = 3;
  int64 revision = 4;
}

message DomainRule {
  string id = 1;
  string domain = 2;
  bool enabled = 3;
  MatchMode match_mode = 4;
  ScheduleMode schedule_mode = 5;
  bytes weekly_slots = 6;
}
```

A complete weekly schedule requires:

```text
168 bits = 21 bytes
```

Use Room later only for:

- large DNS-query histories,
- detailed statistics,
- long-term event logs.

---

## 11. Native repository API

```kotlin
interface FocusGateRepository {
    fun observeConfig(): Flow<AppConfig>
    suspend fun getConfig(): AppConfig

    suspend fun addRule(rule: DomainRule)
    suspend fun updateRule(rule: DomainRule)
    suspend fun deleteRule(ruleId: String)

    suspend fun startVpn()
    suspend fun stopVpn()
    suspend fun getVpnStatus(): VpnStatus

    suspend fun exportConfig(): String
    suspend fun importConfig(encodedConfig: String)

    suspend fun enableEditLock()
    suspend fun startUnlockCountdown(): UnlockStatus
    suspend fun getUnlockStatus(): UnlockStatus
    suspend fun confirmUnlock()
    suspend fun cancelUnlockCountdown()
}
```

The Kotlin implementation must validate:

- lock state,
- domain validity,
- duplicate domains,
- schedule length,
- VPN permission,
- VPN state,
- application package availability.

---

## 12. VPN and DNS flow

### MVP traffic flow

```text
Brave
  ↓ DNS request
FocusGate VpnService
  ↓
DnsPacketProcessor
  ↓
DomainMatcher
  ↓
RuleEvaluator
  ├── blocked → return NXDOMAIN
  └── allowed → forward to upstream DNS
```

### VPN startup flow

1. Call `VpnService.prepare()`.
2. Request VPN permission when necessary.
3. Start the foreground service.
4. Establish the TUN interface.
5. Start the DNS packet-processing loop.
6. Load rules from the native repository.
7. Report VPN status to the Compose UI.

### Brave-only configuration

```kotlin
val tun = Builder()
    .setSession("FocusGate")
    .addAddress("10.10.0.1", 32)
    .addDnsServer("10.10.0.2")
    .addRoute("10.10.0.2", 32)
    .addAllowedApplication("com.brave.browser")
    .establish()
```

The Brave package identifier should be verified on the target device.

### Upstream forwarding

Allowed DNS requests should be forwarded to an upstream resolver.

The upstream socket must be excluded from the VPN:

```kotlin
if (!protect(socket)) {
    throw IllegalStateException(
        "Could not exclude the DNS socket from the VPN",
    )
}
```

This prevents the forwarded request from entering the VPN again.

### Blocking response

For the MVP, return:

```text
NXDOMAIN
```

Possible future alternatives:

- `0.0.0.0`,
- `::`,
- local sink address,
- custom local block page.

---

## 13. DNS support

### Initial implementation

Support:

- IPv4,
- UDP,
- DNS port `53`,
- `A` records,
- `AAAA` records,
- one-question DNS requests.

### Stable-release requirements

Add support for:

- DNS over TCP,
- IPv6 packets,
- truncated responses,
- multiple questions,
- EDNS,
- malformed packets,
- packet-size limits,
- upstream timeouts,
- retry logic,
- network changes.

Do not process packets in the UI layer.

The packet-processing loop must run in Kotlin or native code.

---

## 14. Brave configuration

Brave can use its own Secure DNS or DNS-over-HTTPS.

That may bypass DNS filtering.

The onboarding process should display:

```text
Disable Secure DNS in Brave or configure it to use the current provider.

Otherwise FocusGate may not see DNS requests and domain filtering may not work.
```

Add a diagnostics screen:

```text
VPN active: Yes
DNS interception: Working
Brave filtering test: Passed
```

---

## 15. Foreground notification

The VPN must run as a foreground service.

Suggested notification:

```text
FocusGate is active
2 domains are currently blocked
```

Possible notification actions:

```text
Open
View status
```

Do not include a direct `Disable` action while editing is locked.

---

## 16. Always-on VPN

Provide setup instructions:

```text
Settings
→ Network and Internet
→ VPN
→ FocusGate
→ Always-on VPN
```

### Lockdown mode

Do not enable or recommend:

```text
Block connections without VPN
```

in the DNS-only MVP.

Full lockdown support may require routing all TCP and UDP traffic through the TUN interface.

Treat that as a version-two feature.

---

## 17. Error handling

Support clear errors for:

- VPN permission denied,
- another VPN currently active,
- TUN interface creation failure,
- upstream DNS unavailable,
- invalid domain,
- duplicate domain,
- invalid schedule,
- editing currently locked,
- countdown still active,
- countdown invalidated by device restart,
- Brave Secure DNS bypassing the filter.

Suggested error codes:

```ts
export type NativeErrorCode =
  | 'EDITING_LOCKED'
  | 'INVALID_DOMAIN'
  | 'DUPLICATE_DOMAIN'
  | 'INVALID_SCHEDULE'
  | 'VPN_PERMISSION_REQUIRED'
  | 'VPN_ALREADY_IN_USE'
  | 'VPN_START_FAILED'
  | 'UPSTREAM_DNS_UNAVAILABLE';
```

---

## 18. Testing strategy

### Kotlin unit tests

Test:

- domain normalization,
- exact matching,
- subdomain matching,
- invalid-domain rejection,
- duplicate-domain detection,
- schedule indexing,
- both schedule modes,
- day boundaries,
- week boundaries,
- daylight-saving behavior,
- lock countdown,
- system-clock changes,
- device-restart invalidation,
- native write protection.

Example:

```kotlin
@Test
fun `subdomain rule does not match suffix-only domain`() {
    assertFalse(
        matchesDomainAndSubdomains(
            query = "notfacebook.com",
            rule = "facebook.com",
        ),
    )
}
```

### Compose UI tests

Test:

- domain form validation,
- schedule-cell toggling,
- drag selection,
- presets,
- readable schedule summaries,
- lock-screen states,
- countdown rendering,
- disabled editing controls,
- native error mapping.

### Integration tests

Verify:

- Brave can access allowed domains,
- Brave cannot access blocked domains,
- filtering changes after an hour boundary,
- Messenger outside Brave continues receiving notifications,
- killing the app UI process does not stop filtering,
- app restart restores rules,
- device restart restores the lock,
- changing the system clock does not shorten the countdown,
- upstream DNS requests do not enter a VPN loop.

---

## 19. Implementation stages

### Stage 1 — Project foundation

Tasks:

- create an Android application project,
- enable Kotlin,
- enable Jetpack Compose,
- add navigation,
- configure Kotlin tests,
- configure CI.

Deliverable:

```text
The Compose UI can read native repository state and display it.
```

### Stage 2 — Rules and storage

Tasks:

- implement `DomainRule`,
- implement domain normalization,
- implement matching modes,
- implement 168-slot schedules,
- configure Proto DataStore,
- implement `RuleEvaluator`,
- add unit tests.

Deliverable:

```text
Rules survive application restarts and can be evaluated natively.
```

### Stage 3 — Schedule interface

Tasks:

- build the domain list,
- build the domain form,
- build the 7 × 24 grid,
- add drag selection,
- add presets,
- add readable schedule summaries.

Deliverable:

```text
The user can configure a separate weekly schedule for every domain.
```

### Stage 4 — Editing lock

Tasks:

- implement `LockManager`,
- use `elapsedRealtime()`,
- store the boot count,
- build the countdown interface,
- enforce locking in native write methods,
- add automatic relocking after changes.

Deliverable:

```text
Configuration cannot be changed until the full five-minute delay has elapsed.
```

### Stage 5 — VPN foundation

Tasks:

- implement the VPN permission flow,
- implement the foreground service,
- establish the TUN interface,
- restrict the VPN to Brave,
- expose VPN status to the Compose UI.

Deliverable:

```text
The VPN can start, stop, and continue running after the app UI closes.
```

### Stage 6 — DNS filtering

Tasks:

- parse UDP DNS requests,
- normalize queried domains,
- match domain rules,
- evaluate schedules,
- return `NXDOMAIN` for blocked requests,
- forward allowed requests,
- protect upstream sockets.

Deliverable:

```text
Configured domains are blocked according to their weekly schedules.
```

### Stage 7 — Resilience

Tasks:

- add DNS over TCP,
- add IPv6 support,
- handle network changes,
- handle upstream failure,
- handle service restart,
- add diagnostics,
- add Brave Secure DNS instructions.

Deliverable:

```text
Filtering remains reliable across normal Android lifecycle and network events.
```

### Stage 8 — Release preparation

Tasks:

- create signed release builds,
- add onboarding,
- add configuration export and import,
- add backups,
- add privacy documentation,
- choose an open-source license,
- test on GrapheneOS.

Deliverable:

```text
A reproducible signed APK ready for personal use and public testing.
```

---

## 20. MVP acceptance criteria

The MVP is complete when:

1. Every domain can have an independent weekly schedule.
2. The schedule supports seven days and twenty-four hours per day.
3. Exact-domain matching works.
4. Domain-and-subdomain matching works.
5. Schedule boundaries take effect without restarting the VPN.
6. Filtering continues after the app UI process is killed.
7. Rules survive an application restart.
8. The five-minute countdown cannot be shortened by changing system time.
9. A device restart invalidates a pending unlock.
10. Native write methods reject changes while editing is locked.
11. Brave is filtered without affecting Messenger outside Brave.
12. The application clearly reports when the VPN is inactive.
13. Blocked DNS requests receive a deterministic response.
14. Allowed DNS requests are forwarded without entering a VPN loop.
15. The application works without root access.
16. No remote server is required.

---

## 21. Recommended first release configuration

```text
Project name: FocusGate
Platform: Android
UI: Jetpack Compose
Native layer: Kotlin
Storage: Proto DataStore
Filtering: DNS only
Filtered application: Brave
Schedule precision: One hour
Schedule size: 168 slots per domain
Blocking response: NXDOMAIN
Editing lock: Five-minute monotonic countdown
Time zone: Current device time zone
Distribution: Signed APK
```

---

## 22. Version-two ideas

Possible later features:

- full-device filtering,
- multiple application selection,
- full TCP and UDP tunneling,
- Android lockdown support,
- minute-level schedules,
- temporary access sessions,
- usage statistics,
- DNS-query history,
- multiple rule profiles,
- holiday schedules,
- configuration encryption,
- encrypted backup export,
- remote administration,
- shared family configuration,
- emergency override with a longer delay.
