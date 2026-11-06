#!/usr/bin/env bash
set -u

mkdir -p artifacts/screenshots

capture_failure() {
  local prefix="$1"
  adb exec-out screencap -p > "artifacts/screenshots/99-${prefix}-failure.png" || true
  adb shell uiautomator dump /sdcard/window.xml || true
  adb pull /sdcard/window.xml "artifacts/${prefix}-window.xml" || true
  adb logcat -d > "artifacts/${prefix}-logcat.txt" || true
}

adb install -r daw-drive-v0.1/app/build/outputs/apk/debug/app-debug.apk

maestro test .maestro/guest-core.yaml --format junit --output artifacts/maestro-guest.xml
guest_status=$?
if [ "$guest_status" -ne 0 ]; then
  capture_failure guest
  exit "$guest_status"
fi

maestro test .maestro/settings-account.yaml --format junit --output artifacts/maestro-settings.xml
settings_status=$?
if [ "$settings_status" -ne 0 ]; then
  capture_failure settings
  exit "$settings_status"
fi

adb exec-out screencap -p > artifacts/screenshots/07-final.png || true
adb logcat -d > artifacts/logcat.txt || true
exit 0
