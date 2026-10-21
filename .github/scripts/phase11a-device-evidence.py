#!/usr/bin/env python3
"""Explicit device selection; test installations only. Never resets app data or grants acceptance."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

PACKAGE = "com.aistudio.steuerassistent.vnhqkp"
RUNNER = PACKAGE + ".test/com.example.qa.Phase11AInstrumentation"


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("action", choices=("seed", "snapshot", "screenshot", "logs"))
    p.add_argument("--serial", required=True)
    p.add_argument("--user", required=True, type=int, help="Android user/profile ID (must be foreground)")
    p.add_argument("--run-id", required=True)
    p.add_argument("--label", required=True, help="baseline or test/step name, lowercase and hyphens")
    p.add_argument("--commit", required=True, help="installed APK source commit, verify build provenance first")
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--test-portfolio-only", action="store_true", required=True)
    p.add_argument("--fixture", type=Path)
    p.add_argument("--fixture-sha256")
    p.add_argument("--confirm-empty-test-installation", action="store_true")
    a = p.parse_args()
    if not re.fullmatch(r"P11A-\d{4}-\d{2}-\d{2}-[A-Za-z0-9-]+", a.run_id) or not re.fullmatch(r"[a-z0-9-]+", a.label) or not re.fullmatch(r"[0-9a-f]{40}", a.commit):
        p.error("invalid run-id/label/commit")
    adb = ["adb", "-s", a.serial]

    def call(*args, data=None):
        return subprocess.run(adb + list(args), input=data, check=True, capture_output=True, timeout=180).stdout

    current = int(call("shell", "am", "get-current-user").decode().strip())
    if current != a.user:
        p.error("Select/switch the intended foreground test profile first; no implicit profile switch")
    runas = ["run-as", PACKAGE, "--user", str(a.user)]
    directory = a.output / a.run_id
    directory.mkdir(parents=True, exist_ok=True)
    prefix = directory / (a.run_id + "-" + a.label)
    name = "phase11a-baseline.json" if a.label == "baseline" else "phase11a-after-" + a.label + ".json"
    target = (prefix.with_suffix(".png") if a.action == "screenshot" else
              prefix.with_suffix(".log") if a.action == "logs" else directory / name)
    if target.exists():
        raise RuntimeError("Evidence exists; use another label/run; refusing overwrite before device action")
    provenance = None
    if a.action == "screenshot":
        target, content = prefix.with_suffix(".png"), call("exec-out", "screencap", "-p")
    elif a.action == "logs":
        target = prefix.with_suffix(".log")
        content = call("logcat", "-d", "-v", "threadtime", "-t", "4000", "Phase11A:I", "AndroidRuntime:E", "SQLiteLog:E", "Room:E", "*:S")
        # Review locally before sharing. No full logcat, token/prefs dump or automatic upload.
    else:
        if a.action == "seed":
            if not a.confirm_empty_test_installation or not a.fixture or not a.fixture_sha256:
                p.error("seed requires fixture, trusted fixture-sha256 and confirm-empty-test-installation")
            data = a.fixture.read_bytes()
            if hashlib.sha256(data).hexdigest() != a.fixture_sha256:
                p.error("fixture hash mismatch")
            call("shell", *runas, "mkdir", "-p", "files")
            call("shell", "-T", *runas, "sh", "-c", "'cat > files/phase11a-input.zip'", data=data)
        args = ["shell", "am", "instrument", "--user", str(a.user), "-w", "-r"]
        extras = dict(phase11aAction=a.action, runId=a.run_id, label=a.label, commit=a.commit, testPortfolioOnly="true")
        if a.action == "seed":
            extras.update(confirmEmptyTestInstallation="true", fixtureSha256=a.fixture_sha256)
        for key, value in extras.items():
            args += ["-e", key, value]
        output = call(*args, RUNNER).decode()
        if "INSTRUMENTATION_CODE: 0" not in output or "INSTRUMENTATION_RESULT: error=" in output:
            raise RuntimeError("QA instrumentation failed: " + output)
        name = "phase11a-baseline.json" if a.label == "baseline" else "phase11a-after-" + a.label + ".json"
        target = directory / name
        content = call("exec-out", *runas, "cat", "files/phase11a/" + a.run_id + "/" + name)
        snapshot = json.loads(content)
        apk_paths = call("shell", "pm", "path", "--user", str(a.user), PACKAGE).decode().splitlines()
        if len(apk_paths) != 1 or not apk_paths[0].startswith("package:"):
            raise RuntimeError("Expected one universal debug APK; record split-APK provenance separately")
        if snapshot["meta"]["runId"] != a.run_id or snapshot["meta"]["commit"] != a.commit:
            raise RuntimeError("device snapshot provenance mismatch")
        # Preserve the exact bytes hashed in the device log. Host metadata is separate.
        provenance = dict(runId=a.run_id, androidUser=a.user, deviceAlias=a.serial,
            snapshotSha256=hashlib.sha256(content).hexdigest(),
            apkSha256=hashlib.sha256(call("exec-out", *runas, "cat", apk_paths[0].removeprefix("package:"))).hexdigest())
    if target.exists():
        raise RuntimeError("Evidence exists; use another label/run; refusing overwrite")
    target.write_bytes(content)
    if provenance is not None:
        target.with_name(target.name + ".device.json").write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + "\n")
    manifest = target.with_name(target.name + ".sha256")
    manifest.write_text(f"{hashlib.sha256(content).hexdigest()}  {a.run_id}/{target.name}\n")
    print(f"{a.run_id}: saved {target.name}; practical status NOCH OFFEN")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        # Only bounded stderr; stdout might be partial APK/image bytes.
        detail = (error.stderr or b"").decode(errors="replace")
        print(f"adb exit {error.returncode}: {detail[-4000:]}", file=sys.stderr)
        sys.exit(2)
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(2)
