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

maestro test .maestro/guest-core.yaml --format junit --output artifacts/maestro-guest.xml --test-output-dir=artifacts/maestro-guest
guest_status=$?
if [ "$guest_status" -ne 0 ]; then
  capture_failure guest
  exit "$guest_status"
fi

maestro test .maestro/settings-account.yaml --format junit --output artifacts/maestro-settings.xml --test-output-dir=artifacts/maestro-settings
settings_status=$?
if [ "$settings_status" -ne 0 ]; then
  capture_failure settings
  exit "$settings_status"
fi

maestro test .maestro/ai-english-v2.yaml --format junit --output artifacts/maestro-ai-english.xml --test-output-dir=artifacts/maestro-ai-english
ai_english_status=$?
if [ "$ai_english_status" -ne 0 ]; then
  capture_failure ai-english
  exit "$ai_english_status"
fi

# Require seven non-empty screenshots including the AI English v2 configuration journey.
count=$(find artifacts/maestro-guest artifacts/maestro-settings artifacts/maestro-ai-english -type f -name '*.png' -size +1k | wc -l)
echo "Maestro screenshot evidence: $count PNG files"
if [ "$count" -lt 7 ]; then
  capture_failure screenshots
  exit 1
fi
adb exec-out screencap -p > artifacts/screenshots/07-final.png || true
adb logcat -d > artifacts/logcat.txt || true
exit 0
