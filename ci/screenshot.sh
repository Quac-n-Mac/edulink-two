#!/bin/bash
# Drive the login flow on a booted emulator and capture each screen.
set -x
APK="$1"; OUT="${2:-shots}"
mkdir -p "$OUT"
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do sleep 2; done
sleep 5
adb shell input keyevent 82 || true
adb install -r "$APK"
adb shell monkey -p dev.edulinktwo -c android.intent.category.LAUNCHER 1
sleep 8
adb exec-out screencap -p > "$OUT/1-school-code.png"
# tap the code field (1080x2400: card is centered)
adb shell input tap 540 1050
sleep 1
adb shell input text "SA32%208DN"
sleep 1
adb exec-out screencap -p > "$OUT/2-code-typed.png"
# hide keyboard, tap NEXT
adb shell input keyevent 111
sleep 1
adb shell input tap 540 1230
sleep 10
adb exec-out screencap -p > "$OUT/3-credentials.png"
ls -la "$OUT"
