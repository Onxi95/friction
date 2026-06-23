#!/usr/bin/env bash
set -euo pipefail

package_name="dev.pawelsowa.focusgate"
activity_name=".MainActivity"
apk_path="app/build/outputs/apk/debug/app-debug.apk"

adb install -r "$apk_path" >/dev/null
adb logcat -c
adb shell am start -n "$package_name/$activity_name" >/dev/null
sleep 2

adb shell uiautomator dump /sdcard/focusgate-ui.xml >/dev/null
ui_xml="$(adb shell cat /sdcard/focusgate-ui.xml)"
if [[ "$ui_xml" != *"Start VPN"* ]]; then
  echo "Start VPN button not visible" >&2
  exit 1
fi

start_bounds="$(grep -o 'text="Start VPN"[^>]*bounds="[^"]*"' <<<"$ui_xml" | head -n 1 | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')"
read -r left top right bottom <<<"$start_bounds"
tap_x=$(((left + right) / 2))
tap_y=$(((top + bottom) / 2))
adb shell input tap "$tap_x" "$tap_y"
sleep 3

adb shell uiautomator dump /sdcard/focusgate-ui.xml >/dev/null
ui_xml="$(adb shell cat /sdcard/focusgate-ui.xml)"
if [[ "$ui_xml" == *"Connection request"* && "$ui_xml" == *"OK"* ]]; then
  ok_bounds="$(grep -o 'text="OK"[^>]*bounds="[^"]*"' <<<"$ui_xml" | head -n 1 | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')"
  read -r left top right bottom <<<"$ok_bounds"
  tap_x=$(((left + right) / 2))
  tap_y=$(((top + bottom) / 2))
  adb shell input tap "$tap_x" "$tap_y"
  sleep 3
fi

crashes="$(adb logcat -d -t 500 AndroidRuntime:E '*:S')"
if [[ -n "$crashes" ]]; then
  echo "$crashes" >&2
  exit 1
fi

adb shell uiautomator dump /sdcard/focusgate-ui.xml >/dev/null
ui_xml="$(adb shell cat /sdcard/focusgate-ui.xml)"
if [[ "$ui_xml" != *"Active"* && "$ui_xml" != *"Error"* && "$ui_xml" != *"Starting"* ]]; then
  echo "VPN status did not update after Start VPN" >&2
  exit 1
fi

adb shell dumpsys package "$package_name" | grep -q "android.permission.ACCESS_NETWORK_STATE: granted=true"
adb shell dumpsys package "$package_name" | grep -q "android.net.VpnService"

echo "Emulator acceptance passed"
