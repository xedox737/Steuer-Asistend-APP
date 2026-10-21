#!/usr/bin/env python3
"""Prove the shipped debug APK includes the pinned original fixture; release excludes all seed UI/code/data."""
import hashlib
import json
import os
import uuid
from datetime import datetime, timezone
from pathlib import Path
import zipfile


def main():
    asset = Path("app/src/debug/assets/phase11a/qa-transport-snapshot.zip")
    expected = asset.with_suffix(".sha256").read_text().strip()
    assert hashlib.sha256(asset.read_bytes()).hexdigest() == expected
    markers = (b"Phase11APortfolioSeeder", b"Phase11ATestToolsViewModel", b"Phase 11A Testwerkzeuge")
    debug = Path("app/build/outputs/apk/debug/app-debug.apk")
    releases = list(Path("app/build/outputs/apk/release").glob("*.apk"))
    assert len(releases) == 1, "one actual release APK required"
    with zipfile.ZipFile(debug) as apk:
        assert apk.read("assets/phase11a/qa-transport-snapshot.zip") == asset.read_bytes()
        dex = b"".join(apk.read(name) for name in apk.namelist() if name.endswith(".dex"))
        assert all(marker in dex for marker in markers)
    with zipfile.ZipFile(releases[0]) as apk:
        assert not any("phase11a" in name.lower() for name in apk.namelist())
        dex = b"".join(apk.read(name) for name in apk.namelist() if name.endswith(".dex"))
        assert not any(marker in dex for marker in markers), "QA seed/tools leaked into release"
        assert b"Phase11AIsolation" not in dex
    out = Path("app/build/reports/phase11a-ui/packaging.json")
    out.parent.mkdir(parents=True, exist_ok=True)
    result = dict(status="AUTOMATISIERT BESTANDEN", practicalStatus="NOCH OFFEN",
                  commit=os.environ.get("GITHUB_SHA"), ciRunId=os.environ.get("GITHUB_RUN_ID"),
                  runId="P11A-" + datetime.now(timezone.utc).strftime("%Y-%m-%d") + "-CI-" + os.environ.get("GITHUB_RUN_ID", "LOCAL-" + uuid.uuid4().hex[:8]) + "-" + os.environ.get("GITHUB_RUN_ATTEMPT", "1") + "-ui-packaging",
                  fixtureSha256=expected, debugApkSha256=hashlib.sha256(debug.read_bytes()).hexdigest(),
                  releaseApkSha256=hashlib.sha256(releases[0].read_bytes()).hexdigest(),
                  debugIncludesOriginalFixture=True, releaseExcludesUiSeedGuardAndAssets=True)
    out.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
