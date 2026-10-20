#!/usr/bin/env bash
# Installs the release APK on a running Android emulator and checks the launcher end to end:
# install, native Dolphin core loading, folder creation, scanning + validation of synthetic
# disc images, and the settings screens. Writes evidence (UI dumps, screenshots, logcat) to OUT.
#
#   scripts/emulator_smoke_test.sh <apk> <out-dir>
set -uo pipefail
APK="$1"
OUT="$2"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PKG=com.codblackops.mobile
ACT=com.codblackops.mobile.ui.LauncherActivity
mkdir -p "$OUT"
RESULTS="$OUT/smoke-results.txt"
: > "$RESULTS"
FAIL=0

check() { # name, command
  if eval "$2"; then echo "PASS $1" | tee -a "$RESULTS"; else echo "FAIL $1" | tee -a "$RESULTS"; FAIL=1; fi
}

dump_ui() { # name
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null 2>&1
  adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null
}

tap_text() { # ui-dump-name, exact text
  local xy
  xy=$(python3 - "$OUT/$1.xml" "$2" <<'EOF'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    if n.get("text") == sys.argv[2] or n.get("content-desc") == sys.argv[2]:
        a, b, c, d = map(int, re.findall(r"\d+", n.get("bounds")))
        print((a + c) // 2, (b + d) // 2)
        break
EOF
)
  [ -n "$xy" ] && adb shell input tap $xy
}

adb wait-for-device
adb shell getprop ro.product.cpu.abilist | tee "$OUT/abilist.txt"
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0

adb install -r -g "$APK" 2>&1 | tee "$OUT/install.txt"
check "apk_installs" "grep -q Success '$OUT/install.txt'"
adb shell dumpsys package $PKG | grep -E "versionName|primaryCpuAbi" | tee "$OUT/package.txt"

# "All files access" for /storage/emulated/0/CODBlackOps (what the user grants in Settings).
adb shell appops set --uid $PKG MANAGE_EXTERNAL_STORAGE allow

python3 "$ROOT/scripts/make_test_discs.py" "$OUT/test-discs"
adb shell rm -rf /sdcard/CODBlackOps
adb shell mkdir -p /sdcard/CODBlackOps/sub
for f in "$OUT"/test-discs/*; do adb push "$f" /sdcard/CODBlackOps/ > /dev/null; done
adb shell mv /sdcard/CODBlackOps/random_bytes.iso /sdcard/CODBlackOps/sub/

adb logcat -c
adb shell am start -W -n $PKG/$ACT | tee "$OUT/am-start.txt"

# Wait until Dolphin's user directory is initialised (Sys files copied) and the UI says so.
READY=0
for i in $(seq 1 90); do
  sleep 2
  dump_ui launcher
  if grep -q "Siap (Dolphin" "$OUT/launcher.xml" 2>/dev/null; then READY=1; break; fi
done
check "dolphin_core_loaded_and_initialised" "[ $READY = 1 ]"

# Rescan via onResume (home + relaunch) so validation runs with the backend ready.
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell am start -W -n $PKG/$ACT > /dev/null
for i in $(seq 1 30); do
  sleep 2
  dump_ui games
  grep -q "GAME LAIN" "$OUT/games.xml" 2>/dev/null && break
done
adb logcat -d -s CODBlackOps > "$OUT/codbo-log.txt"

check "scan_counts_three_candidates" "grep -q 'Pindai selesai: 3 file kandidat' '$OUT/codbo-log.txt'"
check "gc_image_listed" "grep -q 'homebrew_test_gc.iso' '$OUT/games.xml'"
check "gc_image_validated_by_dolphin_as_other_game" "grep -q 'GAME LAIN' '$OUT/games.xml'"
check "dolphin_reads_title" "grep -q 'CODBO SMOKE TEST DISC' '$OUT/games.xml'"
check "invalid_images_flagged" "grep -q 'TIDAK VALID' '$OUT/games.xml'"
check "non_disc_file_ignored" "! grep -q 'readme.txt' '$OUT/games.xml'"
check "save_folder_created" "adb shell ls -d /sdcard/CODBlackOps/Save/Backups > /dev/null 2>&1"

# Settings screens render and show the honest capability notes.
tap_text games "Grafis"; sleep 3; dump_ui graphics
check "graphics_screen_shows_resolutions" "grep -q '1080p' '$OUT/graphics.xml'"
tap_text graphics "Kontrol"; sleep 3; dump_ui controls
check "controls_screen_renders" "grep -q 'Touch FPS' '$OUT/controls.xml'"
tap_text controls "Diagnostik"; sleep 3; dump_ui diagnostics
tap_text diagnostics "Save"; sleep 3; dump_ui saves
tap_text saves "Performa"; sleep 3; dump_ui performance
check "performance_screen_renders" "grep -q 'Seimbang' '$OUT/performance.xml'"

adb logcat -d > "$OUT/logcat.txt"
PID=$(adb shell pidof $PKG | tr -d '\r')
check "app_process_alive" "[ -n '$PID' ]"
check "no_fatal_exception" "! grep -q 'FATAL EXCEPTION' '$OUT/logcat.txt'"
check "no_native_crash" "! grep -q 'Fatal signal' '$OUT/logcat.txt'"

echo "---" | tee -a "$RESULTS"
[ $FAIL = 0 ] && echo "SMOKE TEST PASSED" | tee -a "$RESULTS" || echo "SMOKE TEST FAILED" | tee -a "$RESULTS"
exit $FAIL
