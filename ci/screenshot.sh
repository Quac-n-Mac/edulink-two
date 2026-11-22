#!/bin/bash
# Drive the app on a booted emulator and capture screens + a parallax video.
set -x
APK="$1"; OUT="${2:-shots}"
mkdir -p "$OUT"
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do sleep 2; done
sleep 5
adb shell input keyevent 82 || true
adb install -r "$APK"

tap_text() {
  # Tap the centre of the first UI node whose text matches $1.
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1
  adb pull /sdcard/window.xml /tmp/window.xml >/dev/null 2>&1
  B=$(grep -o "text=\"$1\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" /tmp/window.xml | head -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]')
  if [ -z "$B" ]; then
    B=$(grep -o "bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"[^>]*text=\"$1\"" /tmp/window.xml | head -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]')
  fi
  echo "bounds for $1: $B"
  L=$(echo "$B" | sed 's/\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]/\1/')
  T=$(echo "$B" | sed 's/\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]/\2/')
  R=$(echo "$B" | sed 's/\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]/\3/')
  B2=$(echo "$B" | sed 's/\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]/\4/')
  adb shell input tap $(( (L + R) / 2 )) $(( (T + B2) / 2 ))
}

# ---- demo-mode home screens (offline fixtures) ----
adb shell monkey -p dev.edulinktwo -c android.intent.category.LAUNCHER 1
sleep 8
adb exec-out screencap -p > "$OUT/1-school-code.png"
tap_text "DEMO"
sleep 6
adb exec-out screencap -p > "$OUT/2-home-top.png"

# parallax video: slow scroll down + up, then a tab switch
adb shell screenrecord --time-limit 45 /sdcard/parallax.mp4 &
REC=$!
sleep 2
adb shell input swipe 540 1700 540 900 1800
sleep 1
adb exec-out screencap -p > "$OUT/3-home-scrolled.png"
adb shell input swipe 540 1000 540 1800 1500
sleep 1
tap_text "Timetable"
sleep 3
adb shell input swipe 540 1700 540 900 1500
sleep 1
adb exec-out screencap -p > "$OUT/4-timetable.png"
tap_text "Points"
sleep 3
adb exec-out screencap -p > "$OUT/5-points.png"
wait $REC || true
sleep 2
adb pull /sdcard/parallax.mp4 "$OUT/parallax.mp4" || true

# ---- classic login flow (fresh state) ----
adb shell pm clear dev.edulinktwo
adb shell monkey -p dev.edulinktwo -c android.intent.category.LAUNCHER 1
sleep 8
adb shell input tap 540 1050
sleep 1
adb shell input text "SA32%208DN"
sleep 1
adb exec-out screencap -p > "$OUT/6-code-typed.png"
adb shell input keyevent 111
sleep 1
adb shell input tap 540 1230
sleep 10
adb exec-out screencap -p > "$OUT/7-credentials.png"
ls -la "$OUT"
