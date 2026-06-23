# FocusGate Android

Android DNS-only focus blocker for Brave.

## Build

```bash
ANDROID_HOME=/home/paul/Android/Sdk ./gradlew testDebugUnitTest assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Signed Release

Set signing environment variables, then build release:

```bash
export FOCUSGATE_STORE_FILE=/absolute/path/focusgate-release.jks
export FOCUSGATE_STORE_PASSWORD=...
export FOCUSGATE_KEY_ALIAS=focusgate
export FOCUSGATE_KEY_PASSWORD=...
./gradlew assembleRelease
```

Without these variables, `assembleRelease` uses debug signing and is only suitable for local smoke tests.

Release APK:

```text
app/build/outputs/apk/release/app-release.apk
```
