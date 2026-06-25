# Release Checklist

## Build

1. Create or reuse a local Android signing keystore.
2. Export `FOCUSGATE_STORE_FILE`, `FOCUSGATE_STORE_PASSWORD`, `FOCUSGATE_KEY_ALIAS`, and `FOCUSGATE_KEY_PASSWORD`.
3. Run `./scripts/release-check.sh` from the `android/` directory.
4. Confirm the script reports `Release APK is signed with configured FocusGate keystore`.
5. Run `android/scripts/emulator-acceptance.sh` from the `android/` directory with an emulator connected.

If signing variables are absent, the script verifies the fallback debug-signed APK and prints a distribution warning. Do not distribute that APK.

## Manual Acceptance

1. Supported browsers can access allowed domains.
2. Supported browsers receive `NXDOMAIN` for blocked domains.
3. Another app, such as Messenger, is not routed through FocusGate.
4. Filtering continues after FocusGate UI is swiped away.
5. Rules survive app restart.
6. Pending unlock is invalidated after device restart.
7. Changing system clock does not shorten unlock countdown.
8. Diagnostics reports inactive VPN clearly.
9. Browser Secure DNS warning is visible.
10. Always-on VPN instructions are visible.
