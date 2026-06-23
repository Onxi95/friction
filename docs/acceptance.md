# Acceptance Evidence

Automated checks:

- `./gradlew testDebugUnitTest assembleDebug`
- `./gradlew assembleRelease`
- `android/scripts/emulator-acceptance.sh`

Manual/device checks still required before treating a public release as proven:

- Brave installed with package `com.brave.browser`.
- Brave Secure DNS disabled.
- Blocked domain returns `NXDOMAIN` in Brave.
- Allowed domain resolves in Brave.
- Non-Brave app traffic is unaffected.
- VPN keeps filtering after the app UI process is killed.
- Pending unlock is invalidated after a real device restart.
- Changing system clock does not shorten the unlock countdown.
- GrapheneOS checklist in `docs/grapheneos-test.md` passes.

## Brave Reaches A Blocked Domain

Use the Diagnostics screen to separate rule mistakes from DNS bypass.

Expected block setup for `m.facebook.com`:

- Domain rule is `facebook.com`.
- Match mode is domain and subdomains.
- Rule is enabled.
- Current hour is blocked by the schedule.

If Brave still opens `m.facebook.com` while the VPN is active:

- If `DNS queries seen` stays at `0`, Brave did not send normal DNS through FocusGate. Disable Brave Secure DNS, force stop Brave, close existing tabs, and retry.
- If `Last DNS domain` changes to `m.facebook.com` but `Blocked DNS queries` does not increase, the rule or schedule does not block the current hour.
- If `Blocked DNS queries` increases and Brave still loads the page, Brave reused an existing connection or cache. Force stop Brave and retry in a new tab.
