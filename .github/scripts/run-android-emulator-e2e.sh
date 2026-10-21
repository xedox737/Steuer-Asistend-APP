#!/usr/bin/env bash
# Called inside android-emulator-runner, while its emulator is still alive.
set -euo pipefail

artifact_dir="app/build/emulator-e2e"
device_artifact_dir="/sdcard/Download/immopilot-emulator-e2e"
e2e_sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
mkdir -p "$artifact_dir"
started_at=$(date +%s)

collect_diagnostics() {
  result=$?
  trap - EXIT
  set +e
  # The action shuts down the emulator after this script returns. Collect here,
  # including on Gradle/test failure, rather than in a later workflow step.
  adb pull "$device_artifact_dir/." "$artifact_dir/device" > "$artifact_dir/adb-pull.txt" 2>&1
  adb logcat -d -b crash -v threadtime > "$artifact_dir/logcat-crash.txt" 2>&1
  adb shell dumpsys window > "$artifact_dir/window-state.txt" 2>&1
  adb shell dumpsys input > "$artifact_dir/input-state.txt" 2>&1
  adb logcat -d -v threadtime -t 12000 \
    AndroidRuntime:V ActivityManager:I ActivityTaskManager:I SQLiteLog:V \
    SQLiteDatabase:V Room:V TestRunner:V System.err:W '*:S' \
    > "$artifact_dir/logcat-diagnostic.txt" 2>&1
  if [ "$result" -ne 0 ]; then
    adb exec-out screencap -p > "$artifact_dir/failure-display.png"
    adb shell uiautomator dump /sdcard/immopilot-failure.xml > "$artifact_dir/uiautomator.txt" 2>&1
    adb pull /sdcard/immopilot-failure.xml "$artifact_dir/failure-window.xml" >> "$artifact_dir/adb-pull.txt" 2>&1
  fi
  adb shell settings put system font_scale 1.0
  {
    echo "exit_code=$result"
    echo "duration_seconds=$(( $(date +%s) - started_at ))"
  } > "$artifact_dir/result.txt"
  exit "$result"
}
trap collect_diagnostics EXIT

# Boot completion and animation settings are handled by android-emulator-runner.
adb logcat -c
adb shell settings put system font_scale 1.0
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
# Keep launcher cleanup outside ActivityScenario's per-test home transitions.
# Repeated force-stop of Quickstep left recents input consumers behind on API 35,
# despite WindowManager reporting the app focused. Prepare HOME once instead.
case "$(adb shell getprop ro.hardware | tr -d '\r')" in
  ranchu|goldfish) ;;
  *) echo "Launcher-Testvorbereitung ist nur auf einem isolierten Emulator erlaubt." >&2; exit 1 ;;
esac
adb shell am force-stop com.google.android.apps.nexuslauncher
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME \
  > "$artifact_dir/launcher-startup.txt" 2>&1
{
  echo "profile=pixel_5"
  echo "boot_completed=$(adb shell getprop sys.boot_completed | tr -d '\r')"
  echo "api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "android=$(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "abi=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "system_image=$(adb shell getprop ro.build.fingerprint | tr -d '\r')"
  adb shell wm size
  adb shell wm density
  echo "font_scale=$(adb shell settings get system font_scale | tr -d '\r')"
  echo "window_animation_scale=$(adb shell settings get global window_animation_scale | tr -d '\r')"
  echo "transition_animation_scale=$(adb shell settings get global transition_animation_scale | tr -d '\r')"
  echo "animator_duration_scale=$(adb shell settings get global animator_duration_scale | tr -d '\r')"
  # Diagnostics must select the headless binary too; the GUI binary needs
  # desktop/audio libraries that ubuntu-latest does not provide.
  "${e2e_sdk_root:?Android SDK path is missing}/emulator/emulator" -no-window -noaudio -version
  "$e2e_sdk_root/emulator/emulator" -no-window -noaudio -accel-check
} > "$artifact_dir/device-environment.txt" 2>&1

# Exercise the opt-in QA entry point on this fresh emulator before ordinary
# fixtures start. Their existing guarded rule clears all test tables/preferences.
python3 .github/scripts/phase11a-emulator-smoke.py

gradle connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.immopilotDisposableEmulator=true \
  --stacktrace --no-daemon
