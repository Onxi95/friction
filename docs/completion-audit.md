# Completion Audit

Scope: native Android/Jetpack Compose version of `docs/plan.md`.

## Verified Locally

- Android project, Kotlin, Compose, navigation, CI workflow.
- Domain normalization, duplicate rejection, exact matching, and domain-and-subdomain matching.
- 168-slot weekly schedules, schedule summaries, day/week boundaries, and repeated DST-hour behavior.
- Proto DataStore persistence.
- Native write lock with five-minute monotonic countdown that ignores wall-clock changes.
- Device boot-count invalidation logic and boot-completed receiver path.
- VPN permission flow and foreground `VpnService`.
- Browser allowlist VPN configuration via `addAllowedApplication` for installed Brave, Chrome, and Vanadium packages.
- IPv4/IPv6 UDP DNS packet parsing.
- DNS A/AAAA blocking with deterministic `NXDOMAIN`.
- DNS filtering re-evaluates schedules per request, including hour-boundary changes without processor restart.
- Multi-question DNS requests are parsed, and any blocked supported question blocks the response.
- Allowed DNS requests with EDNS additional records are forwarded unchanged.
- Malformed DNS requests are ignored instead of crashing packet processing.
- Oversized UDP and TCP DNS messages are ignored by packet processors.
- Truncated upstream DNS responses are forwarded unchanged.
- Allowed DNS forwarding through protected upstream socket.
- IPv4 DNS-over-TCP TUN packets are parsed and can be blocked or forwarded to protected TCP upstream DNS.
- Upstream DNS uses a socket timeout and retry attempt, with the upstream socket protected from VPN loops.
- Upstream DNS failures are surfaced in Diagnostics and cleared after a later successful forward.
- Diagnostics screen with browser Secure DNS warning.
- Diagnostics screen shows supported-browser availability, filtered browser packages, VPN failure reason, DNS query counters, last DNS domain, and last blocked domain.
- Repository/dashboard VPN status follows the injected `VpnRuntime.status` used by the Android service.
- Domain rows report `VPN inactive` when filtering is stopped.
- Foreground VPN notification shows the current blocked-domain count and has no disable action.
- Always-on VPN guidance and lockdown warning.
- Export/import API and Diagnostics backup UI with native lock enforcement.
- Backup import normalizes stored domains before persistence.
- Backup import rejects malformed weekly schedule byte arrays with `INVALID_SCHEDULE`.
- Stopping VPN from inside the app is rejected while editing is locked and relocks after an allowed stop.
- Configuration-changing Compose controls are disabled while editing is locked.
- Native error codes are mapped to stable user-facing UI messages.
- Release/debug APK packaging.
- Release-check script builds release and verifies APK signature with `apksigner`.
- CI runs unit tests plus debug and release assembly.
- `android/scripts/device-evidence.sh` captures real-device browser, VPN, process-kill, diagnostics, and optional unfiltered-app evidence.

## Commands Run

```bash
cd android
ANDROID_HOME=/home/paul/Android/Sdk \
GRADLE_USER_HOME=/home/paul/git/friction/android/.gradle-user-home \
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

Result: `BUILD SUCCESSFUL`.

## Emulator Evidence

- APK installs on emulator.
- Package declares `android.net.VpnService`.
- `ACCESS_NETWORK_STATE` is granted.
- `android/scripts/emulator-acceptance.sh` passes.
- Tapping `Start VPN` accepts the Android VPN permission dialog when needed and no longer crashes after the manifest fix.
- On the emulator, Chrome package `com.android.chrome` is routed through the VPN as an installed supported browser.
- On the emulator, `dumpsys connectivity` reports VPN interface filtering rules limited to Chrome app-id UIDs `[10154-10154, 20154-20154]`; installed non-browser apps remain outside the VPN allowlist.
- With a controlled `facebook.com` domain-and-subdomains rule blocking all hours, opening `https://m.facebook.com` in Chrome increments DNS diagnostics.
- Emulator diagnostics after that test showed DNS interception `Working`, browser filtering `Passed`, 47 DNS queries seen, 37 blocked DNS queries, 10 forwarded DNS queries, and last blocked domain `m.facebook.com`.

## Known Test Harness Limitation

`connectedDebugAndroidTest` currently fails on the API 36 emulator before app assertions with:

```text
NoSuchMethodException: android.hardware.input.InputManager.getInstance
```

The failure is in the Espresso/AndroidX test harness on that emulator image. UI acceptance is therefore covered by the adb-based acceptance script until the instrumentation dependency/image mismatch is resolved.

## Not Fully Proven Locally

- Brave and Vanadium are not installed in the emulator, so their real DNS interception was not proven locally.
- Messenger/non-browser unaffected traffic was not proven.
- Filtering after app UI process kill was not proven against real browser traffic.
- Real device restart invalidation was unit-tested but not device-tested.
- System-clock manipulation resistance was unit-tested but not device-tested.
- GrapheneOS checklist has not been run.

Do not mark public-release acceptance complete until the manual/device checks in `docs/acceptance.md` and `docs/grapheneos-test.md` pass.
