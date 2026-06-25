#!/usr/bin/env bash
set -euo pipefail

package_name="dev.pawelsowa.focusgate"
activity_name=".MainActivity"
browser_package="${BROWSER_PACKAGE:-com.brave.browser}"
unfiltered_package="${UNFILTERED_PACKAGE:-}"
blocked_url="${BLOCKED_URL:-https://m.facebook.com}"
allowed_url="${ALLOWED_URL:-https://example.com}"
apk_path="${APK_PATH:-app/build/outputs/apk/release/app-release.apk}"
evidence_dir="${EVIDENCE_DIR:-device-evidence/$(date +%Y%m%d-%H%M%S)}"

mkdir -p "$evidence_dir"

run_adb() {
  adb "$@" | tee -a "$evidence_dir/adb.log"
}

dump_ui() {
  local name="$1"
  adb shell uiautomator dump /sdcard/focusgate-ui.xml >/dev/null
  adb shell cat /sdcard/focusgate-ui.xml >"$evidence_dir/$name.xml"
}

tap_text() {
  local text="$1"
  local ui_xml bounds left top right bottom tap_x tap_y
  dump_ui "tap-$text"
  ui_xml="$(cat "$evidence_dir/tap-$text.xml")"
  bounds="$(grep -o "text=\"$text\"[^>]*bounds=\"[^\"]*\"" <<<"$ui_xml" | head -n 1 | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')"
  if [[ -z "$bounds" ]]; then
    echo "Could not find UI text: $text" >&2
    return 1
  fi
  read -r left top right bottom <<<"$bounds"
  tap_x=$(((left + right) / 2))
  tap_y=$(((top + bottom) / 2))
  adb shell input tap "$tap_x" "$tap_y"
}

assert_package() {
  local pkg="$1"
  adb shell pm path "$pkg" >"$evidence_dir/package-$pkg.txt" || {
    echo "Missing package: $pkg" >&2
    exit 1
  }
}

echo "Evidence directory: $evidence_dir"
run_adb devices
assert_package "$browser_package"

adb install -r "$apk_path" >"$evidence_dir/install.txt"
adb logcat -c

run_adb shell settings get global always_on_vpn_app
run_adb shell settings get global lockdown_vpn
run_adb shell dumpsys package "$package_name"
run_adb shell dumpsys package "$browser_package"
run_adb shell pm list packages -U

adb shell am start -n "$package_name/$activity_name" >/dev/null
sleep 2
dump_ui "01-started"

tap_text "Start VPN"
sleep 3
dump_ui "02-vpn-permission"
if grep -q 'text="OK"' "$evidence_dir/02-vpn-permission.xml"; then
  tap_text "OK"
  sleep 3
fi

dump_ui "03-after-vpn-start"
run_adb shell dumpsys vpn
run_adb shell dumpsys connectivity
run_adb shell dumpsys activity services "$package_name"

adb shell am force-stop "$browser_package"
adb shell am start -a android.intent.action.VIEW -d "$blocked_url" "$browser_package" >/dev/null
sleep 8
run_adb shell pidof "$browser_package"
adb shell screencap -p "/sdcard/focusgate-blocked.png"
adb pull "/sdcard/focusgate-blocked.png" "$evidence_dir/blocked.png" >/dev/null

adb shell am force-stop "$browser_package"
adb shell am start -a android.intent.action.VIEW -d "$allowed_url" "$browser_package" >/dev/null
sleep 8
adb shell screencap -p "/sdcard/focusgate-allowed.png"
adb pull "/sdcard/focusgate-allowed.png" "$evidence_dir/allowed.png" >/dev/null

if [[ -n "$unfiltered_package" ]]; then
  assert_package "$unfiltered_package"
  adb shell am start -a android.intent.action.VIEW -d "$allowed_url" "$unfiltered_package" >/dev/null
  sleep 8
  adb shell screencap -p "/sdcard/focusgate-unfiltered.png"
  adb pull "/sdcard/focusgate-unfiltered.png" "$evidence_dir/unfiltered.png" >/dev/null
fi

adb shell am start -n "$package_name/$activity_name" >/dev/null
sleep 2
tap_text "Diagnostics"
sleep 1
dump_ui "04-diagnostics-after-browser"

app_pid="$(adb shell pidof "$package_name" | tr -d '\r' || true)"
if [[ -n "$app_pid" ]]; then
  echo "$app_pid" >"$evidence_dir/focusgate-pid-before-kill.txt"
  adb shell kill "$app_pid"
  sleep 8
fi
run_adb shell dumpsys vpn
run_adb shell dumpsys connectivity
run_adb shell dumpsys activity services "$package_name"

adb shell am force-stop "$browser_package"
adb shell am start -a android.intent.action.VIEW -d "$blocked_url" "$browser_package" >/dev/null
sleep 8
adb shell screencap -p "/sdcard/focusgate-blocked-after-kill.png"
adb pull "/sdcard/focusgate-blocked-after-kill.png" "$evidence_dir/blocked-after-kill.png" >/dev/null

adb shell am start -n "$package_name/$activity_name" >/dev/null
sleep 2
tap_text "Diagnostics"
sleep 1
dump_ui "05-diagnostics-after-kill"

crashes="$(adb logcat -d -t 1000 AndroidRuntime:E '*:S')"
printf "%s\n" "$crashes" >"$evidence_dir/android-runtime.log"
if [[ -n "$crashes" ]]; then
  echo "AndroidRuntime errors captured in $evidence_dir/android-runtime.log" >&2
  exit 1
fi

cat >"$evidence_dir/README.txt" <<EOF
Review evidence:
- 01-started.xml, 03-after-vpn-start.xml: FocusGate UI and VPN state.
- blocked.png: filtered browser $browser_package opened $blocked_url with VPN active.
- allowed.png: filtered browser $browser_package opened $allowed_url with VPN active.
- unfiltered.png: optional unfiltered app allowed-network check.
- 04-diagnostics-after-browser.xml: DNS counters after blocked/allowed browser tests.
- blocked-after-kill.png and 05-diagnostics-after-kill.xml: filtering after killing FocusGate process.
- dumpsys vpn, dumpsys connectivity, package UIDs, and activity service output are in adb.log.

Required manual interpretation:
- Blocked URL should show browser network failure or DNS failure, not the site.
- Allowed URL should load.
- Diagnostics should show DNS queries seen and blocked DNS queries increasing.
- dumpsys connectivity should show VPN interface filtering rules limited to the filtered browser UID.
- After process kill, VPN/service should still be present or restarted and blocked URL should remain blocked.
EOF

echo "Device evidence captured: $evidence_dir"
