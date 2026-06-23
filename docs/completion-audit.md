# Completion Audit

Scope: native Android/Jetpack Compose version of `docs/plan.md`.

## Verified Locally

- Android project, Kotlin, Compose, navigation, CI workflow.
- Domain normalization, exact matching, domain-and-subdomain matching.
- 168-slot weekly schedules and schedule summaries.
- Proto DataStore persistence.
- Native write lock with five-minute monotonic countdown.
- Device boot-count invalidation logic.
- VPN permission flow and foreground `VpnService`.
- Brave-only VPN configuration via `addAllowedApplication("com.brave.browser")`.
- IPv4/IPv6 UDP DNS packet parsing.
- DNS A/AAAA blocking with deterministic `NXDOMAIN`.
- Allowed DNS forwarding through protected upstream socket.
- DNS-over-TCP frame processor coverage.
- Diagnostics screen with Brave Secure DNS warning.
- Diagnostics screen shows Brave package availability, VPN failure reason, DNS query counters, last DNS domain, and last blocked domain.
- Always-on VPN guidance and lockdown warning.
- Export/import API with native lock enforcement.
- Release/debug APK packaging.

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

## Known Test Harness Limitation

`connectedDebugAndroidTest` currently fails on the API 36 emulator before app assertions with:

```text
NoSuchMethodException: android.hardware.input.InputManager.getInstance
```

The failure is in the Espresso/AndroidX test harness on that emulator image. UI acceptance is therefore covered by the adb-based acceptance script until the instrumentation dependency/image mismatch is resolved.

## Not Fully Proven Locally

- Brave is not installed in the emulator, so real Brave DNS interception was not proven.
- Messenger/non-Brave unaffected traffic was not proven.
- Filtering after app UI process kill was not proven against real Brave traffic.
- Real device restart invalidation was unit-tested but not device-tested.
- System-clock manipulation resistance was unit-tested but not device-tested.
- GrapheneOS checklist has not been run.

Do not mark public-release acceptance complete until the manual/device checks in `docs/acceptance.md` and `docs/grapheneos-test.md` pass.
