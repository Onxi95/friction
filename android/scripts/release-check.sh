#!/usr/bin/env bash
set -euo pipefail

android_home="${ANDROID_HOME:-/home/paul/Android/Sdk}"
gradle_user_home="${GRADLE_USER_HOME:-$(pwd)/.gradle-user-home}"
apk_path="app/build/outputs/apk/release/app-release.apk"

latest_build_tools="$(find "$android_home/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
apksigner="$latest_build_tools/apksigner"

if [[ ! -x "$apksigner" ]]; then
  echo "apksigner not found under $android_home/build-tools" >&2
  exit 1
fi

ANDROID_HOME="$android_home" GRADLE_USER_HOME="$gradle_user_home" \
  ./gradlew testDebugUnitTest assembleRelease

if [[ ! -f "$apk_path" ]]; then
  echo "Release APK missing: $apk_path" >&2
  exit 1
fi

"$apksigner" verify --verbose "$apk_path"

if [[ -n "${FOCUSGATE_STORE_FILE:-}" &&
      -n "${FOCUSGATE_STORE_PASSWORD:-}" &&
      -n "${FOCUSGATE_KEY_ALIAS:-}" &&
      -n "${FOCUSGATE_KEY_PASSWORD:-}" ]]; then
  echo "Release APK is signed with configured FocusGate keystore."
else
  echo "Release APK verified with fallback debug signing. Do not distribute this APK."
fi
