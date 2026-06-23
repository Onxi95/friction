# Release Checklist

## Build

1. Create or reuse a local Android signing keystore.
2. Export `FOCUSGATE_STORE_FILE`, `FOCUSGATE_STORE_PASSWORD`, `FOCUSGATE_KEY_ALIAS`, and `FOCUSGATE_KEY_PASSWORD`.
3. Run `./gradlew clean testDebugUnitTest assembleRelease`.
4. Verify APK signature with `apksigner verify --verbose app/build/outputs/apk/release/app-release.apk`.
5. Run `android/scripts/emulator-acceptance.sh` from the `android/` directory with an emulator connected.

If signing variables are absent, Gradle uses debug signing so local release packaging can still be smoke-tested. Do not distribute that APK.

## Manual Acceptance

1. Brave can access allowed domains.
2. Brave receives `NXDOMAIN` for blocked domains.
3. Another app, such as Messenger, is not routed through FocusGate.
4. Filtering continues after FocusGate UI is swiped away.
5. Rules survive app restart.
6. Pending unlock is invalidated after device restart.
7. Changing system clock does not shorten unlock countdown.
8. Diagnostics reports inactive VPN clearly.
9. Brave Secure DNS warning is visible.
10. Always-on VPN instructions are visible.
