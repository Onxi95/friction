# Acceptance Evidence

Automated checks:

- `./gradlew testDebugUnitTest assembleDebug`
- `./gradlew assembleRelease`
- `android/scripts/emulator-acceptance.sh`
- `android/scripts/device-evidence.sh` on a real device with a supported browser installed

Manual/device checks still required before treating a public release as proven:

- At least one supported browser is installed: Brave `com.brave.browser`, Chrome `com.android.chrome`, or Vanadium `app.vanadium.browser`.
- Secure DNS is disabled in the tested browser.
- Blocked domain returns `NXDOMAIN` in the tested browser.
- Allowed domain resolves in the tested browser.
- Apps outside the supported-browser allowlist are unaffected.
- VPN keeps filtering after the app UI process is killed.
- Pending unlock is invalidated after a real device restart.
- Changing system clock does not shorten the unlock countdown.
- GrapheneOS checklist in `docs/grapheneos-test.md` passes.

## Device Evidence Script

Run from `android/` after configuring a blocking rule for the current hour:

```bash
BLOCKED_URL=https://m.facebook.com \
ALLOWED_URL=https://example.com \
BROWSER_PACKAGE=com.android.chrome \
UNFILTERED_PACKAGE=org.mozilla.firefox \
./scripts/device-evidence.sh
```

`BROWSER_PACKAGE` defaults to Brave. Use Brave, Chrome, or Vanadium. `UNFILTERED_PACKAGE` is optional; use any installed app outside the supported-browser allowlist.

The script creates `device-evidence/<timestamp>/` with:

- APK install output and package dumps.
- VPN/service dumps and `dumpsys connectivity` UID-routing evidence.
- Filtered-browser blocked/allowed screenshots.
- Optional unfiltered-app screenshot.
- Diagnostics UI dumps before and after killing the FocusGate process.
- AndroidRuntime crash log.

Review the generated `README.txt` and screenshots before marking manual acceptance complete.

In `adb.log`, `dumpsys connectivity` interface filtering rules should contain only installed supported-browser UIDs.

## Browser Reaches A Blocked Domain

Use the Diagnostics screen to separate rule mistakes from DNS bypass.

Expected block setup for `m.facebook.com`:

- Domain rule is `facebook.com`.
- Match mode is domain and subdomains.
- Rule is enabled.
- Current hour is blocked by the schedule.

If the browser still opens `m.facebook.com` while the VPN is active:

- If `DNS queries seen` stays at `0`, the browser did not send normal DNS through FocusGate. Confirm it appears in `Filtered browsers`, disable Secure DNS, force stop the browser, close existing tabs, and retry.
- If `Last DNS domain` changes to `m.facebook.com` but `Blocked DNS queries` does not increase, the rule or schedule does not block the current hour.
- If `Blocked DNS queries` increases and the browser still loads the page, it reused an existing connection or cache. Force stop the browser and retry in a new tab.
