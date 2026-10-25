#!/usr/bin/env python3
"""Reproduce the read-only branch audit in disposable JVM/Room worktrees. No installed app/real Drive.

Returns nonzero when Gradle/tests fail; intentional raw-difference findings remain failures.
Both targets run so a failure in one cannot hide the other target's evidence.
Requires JDK21, Gradle9.3.1 and an Android SDK. Does not install APKs or reset user data.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

COMMITS = {"main": "69f35772b6480a958ca7479b024dbd4d8bd42fa1", "test": "8ddd556061d6d9a5ed607163aff42dbf3578f8c1"}
FILTERS = ["com.example.comparison.BranchComparisonTest", "com.example.data.Phase10BReviewedDocumentRestoreTest",
           "com.example.data.Phase10DRestoreMetadataTest", "com.example.data.Phase10CPortfolioRelationRestoreTest"]

def run():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gradle", required=True, help="Gradle9.3.1 executable (JDK21)")
    parser.add_argument("--output", required=True, type=Path, help="New, nonexistent audit directory")
    parser.add_argument("--init-script", type=Path, help="Optional environment-only proxy/CA init")
    args = parser.parse_args()
    qa = Path(__file__).resolve().parent
    root = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], cwd=qa, text=True).strip())
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise RuntimeError("ANDROID_HOME or ANDROID_SDK_ROOT required")
    fixture = out / "qa-transport-snapshot.zip"
    fixture.write_bytes(subprocess.check_output(["git", "show", COMMITS["test"]+":app/src/debug/assets/phase11a/qa-transport-snapshot.zip"], cwd=root))
    assert hashlib.sha256(fixture.read_bytes()).hexdigest() == "b052a1ae4569196abb791375aa1fa01fe6a636a1d0082820acb576fa497ea638"
    checker = out / "phase11a-integrity-check.py"
    checker.write_bytes(subprocess.check_output(["git", "show", COMMITS["test"]+":.github/scripts/phase11a-integrity-check.py"], cwd=root))
    summary = {"commits": COMMITS, "realDriveUsed": False, "installedAppUsed": False, "targets": {}}
    for label, sha in COMMITS.items():
        worktree = out / (label+"-worktree")
        subprocess.run(["git", "worktree", "add", "--detach", str(worktree), sha], cwd=root, check=True)
        (worktree / "local.properties").write_text("sdk.dir="+sdk+"\n")
        evidence = out / (label+"-final")
        evidence.mkdir()
        command = [args.gradle]
        if args.init_script:
            command += ["-I", str(args.init_script.resolve())]
        command += ["-I", str(qa / "comparison.init.gradle"), "-Dcomparison.qaRoot="+str(qa),
                    "-Dcomparison.fixture="+str(fixture), "-Dcomparison.commit="+sha,
                    "-Dcomparison.variant="+label, "-Dcomparison.output="+str(evidence),
                    "testDebugUnitTest", "--no-daemon", "--stacktrace"]
        for pattern in FILTERS:
            command += ["--tests", pattern]
        with (out / (label+".log")).open("w") as log:
            result = subprocess.run(command, cwd=worktree, stdout=log, stderr=subprocess.STDOUT)
        junit = worktree / "app/build/test-results/testDebugUnitTest"
        if junit.exists():
            shutil.copytree(junit, evidence / "junit", ignore=shutil.ignore_patterns("binary"))
        cases = []
        for xml in junit.glob("*.xml"):
            for case in ET.parse(xml).getroot().iter("testcase"):
                failure = case.find("failure")
                cases.append({"class": case.get("classname"), "name": case.get("name"),
                              "failure": failure.get("message") if failure is not None else None,
                              "skipped": case.find("skipped") is not None, "error": case.find("error") is not None})
        subprocess.run(["git", "diff", "--exit-code", sha, "--", "app/src/main", "app/src/debug", "app/src/release", "app/build.gradle.kts"], cwd=worktree, check=True)
        counts = {"actualMethods": len(cases), "passed":sum(not c["failure"] and not c["skipped"] and not c["error"] for c in cases),
                  "failed":sum(bool(c["failure"]) for c in cases), "skipped":sum(c["skipped"] for c in cases),
                  "errors":sum(c["error"] for c in cases), "cases":cases}
        counts["executionGuardPassed"] = len(cases)==len({(c["class"],c["name"]) for c in cases})==20 and sum(c["class"]==FILTERS[0] for c in cases)==6 and counts["skipped"]==0
        (evidence / "junit-summary.json").write_text(json.dumps(counts,indent=2)+"\n")
        summary["targets"][label] = {"gradleExit": result.returncode, **counts}
    (out / "run-summary.json").write_text(json.dumps(summary, indent=2)+"\n")
    if all(v["executionGuardPassed"] for v in summary["targets"].values()):
        from source_audit import audit
        from analyse_results import analyse
        # Source audit invokes git in the repository; no application mutation.
        previous_directory = Path.cwd()
        try:
            os.chdir(root)
            audit(out / "source")
        finally:
            os.chdir(previous_directory)
        analyse(out, checker)
    print(json.dumps({k: {"gradleExit": v["gradleExit"], "actualMethods": v["actualMethods"]} for k, v in summary["targets"].items()}, indent=2))
    return int(any(v["gradleExit"] != 0 or not v["executionGuardPassed"] for v in summary["targets"].values()))

if __name__ == "__main__":
    sys.exit(run())
