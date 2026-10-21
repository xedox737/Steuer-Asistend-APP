#!/usr/bin/env python3
"""Extra UI-seed/process-death scenario after the full suite. Disposable CI emulator only."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

PACKAGE = "com.aistudio.steuerassistent.vnhqkp.phase11a"
RUNNER = PACKAGE + ".test/com.example.qa.Phase11AInstrumentation"


def call(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True, timeout=240).stdout.strip()


def main():
    if os.environ.get("GITHUB_ACTIONS") != "true":
        raise RuntimeError("CI only")
    serial = call("adb", "get-serialno")
    adb = ["adb", "-s", serial]
    if call(*adb, "shell", "getprop", "ro.hardware") not in ("ranchu", "goldfish"):
        raise RuntimeError("Disposable emulator hardware required")
    if call(*adb, "shell", "am", "get-current-user") != "0":
        raise RuntimeError("Fresh emulator user 0 required")
    # AGP may uninstall its test APKs at suite end. These are exclusively isolated IDs.
    for path in ("app/build/outputs/apk/debug/app-debug.apk", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
        call(*adb, "install", "-r", "-t", path)
    out = Path("app/build/emulator-e2e/phase11a-ui")
    out.mkdir(parents=True, exist_ok=True)
    result = call(*adb, "shell", "am", "instrument", "-w", "-r", "-e", "immopilotDisposableEmulator", "true",
                  "-e", "phase11aCommit", os.environ["GITHUB_SHA"], "-e", "class",
                  "com.example.e2e.Phase11AUiSeedEmulatorTest#realSettingsConfirmationLoadsFullPortfolioAndBlocksRepeat", RUNNER)
    (out / "ui-process-scenario.txt").write_text(result + "\n")
    assert "OK (1 test)" in result and "INSTRUMENTATION_CODE: -1" in result, result
    before_bytes = subprocess.run(adb + ["exec-out", "cat", "/sdcard/Download/immopilot-emulator-e2e/phase11a-ui-seed-snapshot.json"], check=True, capture_output=True).stdout
    before = json.loads(before_bytes)
    assert before["meta"]["commit"] == os.environ["GITHUB_SHA"]
    run_id = before["meta"]["runId"]
    folder = out / run_id
    folder.mkdir(exist_ok=False)
    baseline = folder / "phase11a-baseline.json"
    baseline.write_bytes(before_bytes)
    baseline.with_suffix(".json.sha256").write_text(f"{hashlib.sha256(before_bytes).hexdigest()}  {run_id}/{baseline.name}\n")
    call(*adb, "shell", "am", "start", "-W", "-n", PACKAGE + "/com.example.MainActivity")
    pid_before = call(*adb, "shell", "pidof", PACKAGE)
    assert pid_before, "live launcher process required before force-stop"
    call(*adb, "shell", "am", "force-stop", "--user", "0", PACKAGE)
    stopped = subprocess.run(adb + ["shell", "pidof", PACKAGE], capture_output=True, text=True)
    assert not stopped.stdout.strip(), "test process must be gone"
    call(*adb, "shell", "am", "start", "-W", "-n", PACKAGE + "/com.example.MainActivity")
    pid_after = call(*adb, "shell", "pidof", PACKAGE)
    assert pid_after and pid_after != pid_before, "new launcher process required"
    print(call(sys.executable, ".github/scripts/phase11a-device-evidence.py", "snapshot", "--serial", serial,
               "--user", "0", "--run-id", run_id, "--label", "ui-process-restart", "--commit", os.environ["GITHUB_SHA"],
               "--output", str(out), "--test-portfolio-only"))
    print(call(sys.executable, ".github/scripts/phase11a-integrity-check.py", str(baseline),
               str(folder / "phase11a-after-ui-process-restart.json"), "--units", "17", "--report", str(folder / "integrity.json")))
    (folder / "process.json").write_text(json.dumps(dict(runId=run_id, commit=os.environ["GITHUB_SHA"],
        package=PACKAGE, user=0, pidBefore=pid_before, processAbsentAfterForceStop=True, pidAfter=pid_after,
        uiJUnitExecutions=1, status="AUTOMATISIERT BESTANDEN", practicalStatus="NOCH OFFEN"), indent=2) + "\n")
    print("AUTOMATISIERT BESTANDEN: real UI seed, true test-process stop, launcher restart, stable IDs/counts/60 hashes/0 orphans; practicalStatus=NOCH OFFEN")


if __name__ == "__main__":
    main()
