#!/usr/bin/env bash
# Dedicated, synthetic before/after workload; the regular E2E suite still runs separately.
set -euo pipefail
artifact_dir=app/build/bank-performance
device_artifact_dir=/sdcard/Download/immopilot-emulator-e2e
package=com.aistudio.steuerassistent.vnhqkp

mkdir -p "$artifact_dir"
case "$(adb shell getprop ro.hardware | tr -d '\r')" in
  ranchu|goldfish) ;;
  *) echo 'Bank performance tests require a disposable emulator.' >&2; exit 1 ;;
esac
stop_trace() {
  if [ -n "${trace_pid:-}" ]; then
    adb shell kill -TERM "$trace_pid" || true
    # Allow Perfetto to flush before copying its output.
    for attempt in 1 2 3 4 5; do
      adb shell kill -0 "$trace_pid" >/dev/null 2>&1 || break
      sleep 1
    done
    adb pull "$trace_file" "$artifact_dir/bank-performance-$count.pftrace"
  fi
  trace_pid=
}
collect() {
  result=$?
  trap - EXIT
  set +e
  stop_trace
  adb pull "$device_artifact_dir/." "$artifact_dir/device"
  adb logcat -d -v threadtime > "$artifact_dir/logcat.txt"
  adb shell dumpsys activity lastanr > "$artifact_dir/lastanr.txt"
  adb shell dumpsys meminfo "$package" > "$artifact_dir/meminfo-final.txt"
  adb shell dumpsys gfxinfo "$package" framestats > "$artifact_dir/framestats-final.txt"
  echo "$result" > "$artifact_dir/exit-code.txt"
  exit "$result"
}
trap collect EXIT
adb logcat -c
adb shell settings put system font_scale 1.0
adb shell am force-stop com.google.android.apps.nexuslauncher
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME > "$artifact_dir/home.txt"
adb shell getprop > "$artifact_dir/device.txt"
overall_result=0
for count in 1250 5000; do
  # One trace per size, bounded even if instrumentation hangs. Stop/flush below.
  trace_file="/data/misc/perfetto-traces/bank-performance-$count.pftrace"
  trace_pid=$(adb shell perfetto --background-wait -o "$trace_file" -t 600s --app "$package" sched freq idle am wm gfx view binder_driver dalvik | tr -d '\r' | tail -n 1)
  adb shell rm -f "$device_artifact_dir/bank-fixture-$count.json"
  if gradle connectedDebugAndroidTest \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
    -Pandroid.testInstrumentationRunnerArguments.class="com.example.e2e.BankPerformanceEmulatorTest#imported${count}RowsStayResponsiveAndPreserveAllData" \
    -Pandroid.testInstrumentationRunnerArguments.immopilotDisposableEmulator=true \
    -Pandroid.testInstrumentationRunnerArguments.immopilotBankProfile=true \
    -Pandroid.testInstrumentationRunnerArguments.immopilotBankBaseline="${BANK_PERFORMANCE_BASELINE:-false}" \
    --stacktrace --no-daemon; then
    : # Assertions passed; baseline failures below remain failures.
  else
    overall_result=1
  fi
  # A completed, count-checked seed permits cold starts even after baseline UI OOM.
  # Keep the instrumentation failure; do not turn a failed bank test green.
  if adb shell test -f "$device_artifact_dir/bank-fixture-$count.json"; then
    adb pull "$device_artifact_dir/bank-fixture-$count.json" "$artifact_dir/bank-fixture-$count.json"
    python3 - "$artifact_dir/bank-fixture-$count.json" "$count" <<'PY_FIXTURE'
import json, pathlib, sys
assert json.loads(pathlib.Path(sys.argv[1]).read_text()) == {'transactions': int(sys.argv[2]), 'receipts': 40}
PY_FIXTURE
    # Fixture survives the test. Process-cold first-frame timing is distinct
    # from the harness's Activity relaunch and full bank-ready metrics.
    for run in 1 2 3; do
      adb shell am force-stop "$package"
      adb shell am start -W -n "$package/com.example.MainActivity" > "$artifact_dir/cold-start-$count-$run.txt"
      python3 - "$artifact_dir/cold-start-$count-$run.txt" <<'PY_CHECK'
import pathlib, re, sys
output = pathlib.Path(sys.argv[1]).read_text()
assert re.search(r"^Status: ok\s*$", output, re.M), output
assert re.search(r"^TotalTime: [0-9]+\s*$", output, re.M), output
PY_CHECK
      adb shell uiautomator dump /sdcard/bank-cold-start.xml > /dev/null
      adb shell dumpsys meminfo "$package" > "$artifact_dir/cold-start-$count-$run-meminfo.txt"
    done
    # Separate sampled process-start profile: do not mix profiler overhead into
    # the three unprofiled cold first-frame measurements above.
    startup_trace="/data/local/tmp/bank-startup-$count.trace"
    adb shell am force-stop "$package"
    adb shell am start -W --start-profiler "$startup_trace" --sampling 1000 \
      -n "$package/com.example.MainActivity" > "$artifact_dir/cold-start-profile-$count.txt"
    adb shell uiautomator dump /sdcard/bank-profile-start.xml > /dev/null
    adb shell am profile stop "$package"
    adb pull "$startup_trace" "$artifact_dir/bank-startup-$count.trace"
    test -s "$artifact_dir/bank-startup-$count.trace"
  else
    # No validated fixture: report missing measurements rather than timing empty data.
    overall_result=1
  fi
  stop_trace
  cp -r app/build/outputs/androidTest-results "$artifact_dir/results-$count"
  adb logcat -d -v threadtime > "$artifact_dir/logcat-$count.txt"
done
exit "$overall_result"
