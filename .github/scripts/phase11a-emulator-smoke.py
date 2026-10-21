#!/usr/bin/env python3
"""Automated plumbing check on a fresh disposable emulator; no practical acceptance."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from datetime import datetime, timezone


def call(*args):
    result = subprocess.run(args, capture_output=True, text=True, timeout=240)
    if result.returncode:
        print(result.stdout, file=sys.stderr)
        print(result.stderr, file=sys.stderr)
        raise subprocess.CalledProcessError(result.returncode, args)
    return result.stdout.strip()


serial = call("adb", "get-serialno")
assert call("adb", "-s", serial, "shell", "getprop", "ro.hardware") in ("ranchu", "goldfish"), "disposable emulator only"
user = call("adb", "-s", serial, "shell", "am", "get-current-user")
assert user == "0", "fresh emulator user expected"
fixtures = [p for p in Path("app/build/phase11a-ci-input").rglob("qa-transport-snapshot.zip") if p.parent.name == "units-17"]
assert len(fixtures) == 1, "expected this run's Phase10B portfolio artifact"
fixture = fixtures[0]
expected = fixture.with_suffix(".sha256").read_text().strip()
assert hashlib.sha256(fixture.read_bytes()).hexdigest() == expected
call("adb", "-s", serial, "install", "-r", "-t", "app/build/outputs/apk/debug/app-debug.apk")
call("adb", "-s", serial, "install", "-r", "-t", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
run_id = "P11A-" + datetime.now(timezone.utc).strftime("%Y-%m-%d") + "-CI-" + os.environ["GITHUB_RUN_ID"] + "-" + os.environ.get("GITHUB_RUN_ATTEMPT", "1") + "-emulator"
output = Path("app/build/emulator-e2e/phase11a")
common = ["--serial", serial, "--user", user, "--run-id", run_id, "--commit", os.environ["GITHUB_SHA"],
          "--output", str(output), "--test-portfolio-only"]
helper = ".github/scripts/phase11a-device-evidence.py"
print(call(sys.executable, helper, "seed", *common, "--label", "setup", "--fixture", str(fixture),
           "--fixture-sha256", expected, "--confirm-empty-test-installation"))
# A second seed must fail specifically at the occupied-domain-table gate.
refused = subprocess.run([sys.executable, helper, "seed", *common, "--label", "refused-second-seed",
                         "--fixture", str(fixture), "--fixture-sha256", expected,
                         "--confirm-empty-test-installation"], capture_output=True, text=True, timeout=240)
assert refused.returncode == 2 and "Nichtleere Test-Datentabelle" in refused.stderr, refused.stderr
print("Occupied seed target correctly refused; existing test portfolio preserved")
print(call(sys.executable, helper, "snapshot", *common, "--label", "baseline"))
call("adb", "-s", serial, "shell", "am", "force-stop", "--user", user, "com.aistudio.steuerassistent.vnhqkp")
print(call(sys.executable, helper, "snapshot", *common, "--label", "emulator-process-restart"))
print(call(sys.executable, ".github/scripts/phase11a-integrity-check.py", str(output / run_id / "phase11a-baseline.json"),
           str(output / run_id / "phase11a-after-emulator-process-restart.json"), "--units", "17",
           "--report", str(output / run_id / "integrity.json")))
print("Phase11A emulator instrumentation/seed/persisted snapshot plumbing complete; practicalStatus=NOCH OFFEN")
