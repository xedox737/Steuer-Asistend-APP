#!/usr/bin/env python3
"""Parallel-install proof on a fresh disposable CI emulator. Never clears/uninstalls product data."""
import json
import os
from pathlib import Path
import re
import shlex
import sqlite3
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone

PRODUCTION = "com.aistudio.steuerassistent.vnhqkp"
TEST = PRODUCTION + ".phase11a"
ERROR = "Phase 11A evidence/seed is not allowed on the production applicationId."
OUT = Path("app/build/emulator-e2e/phase11a-isolation")


def command(*args):
    return subprocess.run(args, check=True, capture_output=True, timeout=180).stdout


def main():
    action = sys.argv[1]
    if action not in ("prepare", "verify", "final"):
        raise RuntimeError("Unknown sandbox-check action")
    if os.environ.get("GITHUB_ACTIONS") != "true":
        raise RuntimeError("CI-only disposable reference setup")
    serial = command("adb", "get-serialno").decode().strip()

    def adb(*args):
        return command("adb", "-s", serial, *args)

    if adb("shell", "getprop", "ro.hardware").decode().strip() not in ("ranchu", "goldfish"):
        raise RuntimeError("Disposable emulator hardware required")
    if adb("shell", "am", "get-current-user").decode().strip() != "0":
        raise RuntimeError("Fresh emulator user 0 required")
    OUT.mkdir(parents=True, exist_ok=True)

    def installed():
        return adb("shell", "pm", "list", "packages").decode().splitlines()

    def domain_hashes():
        # Stable persisted domain stores, plus explicit cache sentinel. No credentials/content in report.
        result = adb("shell", "run-as", PRODUCTION, "sh", "-c", shlex.quote(
            "find databases shared_prefs files -type f -exec sha256sum {} \\; ; sha256sum cache/phase11a-isolation.txt"))
        return dict(sorted((line.split(None, 1)[1], line.split(None, 1)[0])
                           for line in result.decode().splitlines()))

    def room_counts(package):
        content = adb("exec-out", "run-as", package, "cat", "databases/receipt_database")
        # Force-stop may leave committed WAL pages; copy sidecars from the quiescent app too.
        available = adb("shell", "run-as", package, "ls", "databases").decode().split()
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "room.sqlite"
            path.write_bytes(content)
            for suffix in ("-wal", "-shm"):
                if "receipt_database" + suffix in available:
                    path.with_name(path.name + suffix).write_bytes(adb(
                        "exec-out", "run-as", package, "cat", "databases/receipt_database" + suffix))
            with sqlite3.connect(f"file:{path}?mode=ro", uri=True) as db:
                assert db.execute("pragma integrity_check").fetchone()[0] == "ok"
                return {table: db.execute(f"select count(*) from {table}").fetchone()[0]
                        for table in ("property_metadata", "receipts")}

    if action == "prepare":
        if (OUT / "baseline.json").exists():
            raise RuntimeError("Existing reference baseline; refusing overwrite")
        if "package:" + PRODUCTION in installed():
            raise RuntimeError("Reference must be absent; never replace a product installation")
        adb("install", "-t", "app/build/phase11a-reference/app-reference.apk")
        adb("install", "-t", "app/build/phase11a-reference/app-reference-androidTest.apk")
        adb("shell", "am", "start", "-W", "-n", PRODUCTION + "/com.example.MainActivity")
        # Wait for actual reference Room creation without running any QA seed against it.
        for _ in range(20):
            ready = adb("shell", "run-as", PRODUCTION, "sh", "-c", shlex.quote(
                "if [ -s databases/receipt_database ] && [ -d shared_prefs ]; then echo ready; fi")).decode().strip()
            if ready == "ready":
                try:
                    # Initial schema transaction/WAL may still be committing. This is a
                    # readiness poll only; all checks after force-stop remain strict.
                    initial_counts = room_counts(PRODUCTION)
                except (sqlite3.DatabaseError, AssertionError):
                    pass
                else:
                    if initial_counts != dict(property_metadata=0, receipts=0):
                        raise RuntimeError("Reference UI unexpectedly contains domain data")
                    break
            time.sleep(0.5)
        else:
            raise RuntimeError("Reference UI did not create its persistent stores")
        adb("shell", "am", "force-stop", PRODUCTION)
        counts = room_counts(PRODUCTION)
        assert counts == dict(property_metadata=0, receipts=0), counts
        # Initial sentinels ONLY in this freshly installed synthetic CI reference, before baseline.
        adb("shell", "run-as", PRODUCTION, "sh", "-c", shlex.quote(
            "mkdir -p files cache shared_prefs; "
            "printf reference > files/phase11a-isolation.txt; "
            "printf reference > cache/phase11a-isolation.txt; "
            "printf '%s' '<map><string name=\"sentinel\">reference</string></map>' > shared_prefs/phase11a_isolation.xml"))
        baseline = dict(hashes=domain_hashes(), counts=counts, commit=os.environ["GITHUB_SHA"],
            runId="P11A-" + datetime.now(timezone.utc).strftime("%Y-%m-%d") + "-CI-" +
                os.environ["GITHUB_RUN_ID"] + "-" + os.environ.get("GITHUB_RUN_ATTEMPT", "1") + "-isolation")
        (OUT / "baseline.json").write_text(json.dumps(baseline, indent=2) + "\n")
        # Actual Android guard probes. Invalid args would reach the DB/seed path in an unguarded runner.
        for qa_action in ("seed", "snapshot"):
            result = adb("shell", "am", "instrument", "-w", "-r", "-e", "phase11aAction", qa_action,
                         PRODUCTION + ".test/com.example.qa.Phase11AInstrumentation").decode()
            assert ERROR in result and "INSTRUMENTATION_CODE: 1" in result, result
            assert domain_hashes() == baseline["hashes"], "Product reference changed during rejected QA action"
        print("Production seed/snapshot refused by Android guard; reference stores unchanged")
        return

    baseline = json.loads((OUT / "baseline.json").read_text())
    assert domain_hashes() == baseline["hashes"], "Product reference domain bytes changed"
    assert room_counts(PRODUCTION) == baseline["counts"]
    if action == "final":
        report = json.loads((OUT / "sandbox.json").read_text())
        report["productionUnchangedAfterAllE2E"] = True
        (OUT / "sandbox.json").write_text(json.dumps(report, indent=2) + "\n")
        print("Product reference unchanged after all Android E2E tests")
        return
    assert all("package:" + p in installed() for p in (PRODUCTION, TEST))
    uids = {p: int(adb("shell", "run-as", p, "id", "-u").decode().strip()) for p in (PRODUCTION, TEST)}
    assert uids[PRODUCTION] != uids[TEST]
    roots = {p: adb("shell", "run-as", p, "pwd").decode().strip() for p in (PRODUCTION, TEST)}
    assert roots[PRODUCTION] != roots[TEST]
    output = adb("shell", "am", "instrument", "-w", "-r", "-e", "phase11aAction", "isolation",
                 "-e", "runId", baseline["runId"], "-e", "testPortfolioOnly", "true",
                 "-e", "immopilotDisposableEmulator", "true",
                 TEST + ".test/com.example.qa.Phase11AInstrumentation").decode()
    assert "INSTRUMENTATION_CODE: 0" in output and "INSTRUMENTATION_RESULT: error=" not in output, output
    probe = json.loads(re.search(r"^INSTRUMENTATION_RESULT: isolation=(.+)$", output, re.M)[1])
    assert probe["uid"] == uids[TEST] and probe["applicationId"] == TEST
    assert domain_hashes() == baseline["hashes"]
    assert room_counts(TEST) == dict(property_metadata=5, receipts=70)
    assert adb("shell", "run-as", TEST, "cat", "files/phase11a-isolation.txt") == b"isolated-test"
    assert adb("shell", "run-as", PRODUCTION, "cat", "files/phase11a-isolation.txt") == b"reference"
    assert b"isolated-test" in adb("shell", "run-as", TEST, "cat", "shared_prefs/phase11a_isolation.xml")
    assert b"reference" in adb("shell", "run-as", PRODUCTION, "cat", "shared_prefs/phase11a_isolation.xml")
    # Launch the isolated UI in the presence of the product package; explicit component avoids ambiguity.
    launch = adb("shell", "am", "start", "-W", "-n", TEST + "/com.example.MainActivity").decode()
    assert "Status: ok" in launch, launch
    (OUT / "test-launch.png").write_bytes(adb("exec-out", "screencap", "-p"))
    report = dict(status="AUTOMATISIERT BESTANDEN", practicalStatus="NOCH OFFEN",
        commit=os.environ["GITHUB_SHA"], ciRunId=os.environ["GITHUB_RUN_ID"], runId=baseline["runId"],
        packages=[PRODUCTION, TEST], uids=uids, dataDirectories=roots, probe=probe,
        productionCounts=baseline["counts"], productionHashesUnchanged=True,
        productionGuardSeedAndSnapshot=True)
    (OUT / "sandbox.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
