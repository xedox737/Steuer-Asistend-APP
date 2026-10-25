#!/usr/bin/env python3
"""Inspect built binaries (not source manifests); fail on package/authority/label collisions."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

PRODUCTION = "com.aistudio.steuerassistent.vnhqkp"
TEST = PRODUCTION + ".phase11a"
ANDROID = "{http://schemas.android.com/apk/res/android}"
sdk = Path(os.environ["ANDROID_HOME"])
build_tools = sdk / "build-tools/36.0.0"
analyzers = list(sdk.glob("cmdline-tools/*/bin/apkanalyzer"))
if not analyzers:
    # Local workspace SDK command-line tools may be provisioned separately.
    analyzers = [Path(os.environ["PHASE11A_APKANALYZER"])]


def call(*args):
    return subprocess.run([str(x) for x in args], check=True, capture_output=True, text=True).stdout


def inspect(path):
    manifest = ET.fromstring(call(analyzers[0], "manifest", "print", path))
    badging = call(build_tools / "aapt", "dump", "badging", path)
    app = manifest.find("application")
    providers = [dict(name=p.get(ANDROID + "name"), authorities=p.get(ANDROID + "authorities"),
                      exported=p.get(ANDROID + "exported", "false"),
                      grantUriPermissions=p.get(ANDROID + "grantUriPermissions", "false"))
                 for p in app.findall("provider")]
    components = []
    for kind in ("activity", "activity-alias", "service", "receiver", "provider"):
        for component in app.findall(kind):
            components.append(dict(kind=kind, name=component.get(ANDROID + "name"),
                exported=component.get(ANDROID + "exported", "false"),
                permission=component.get(ANDROID + "permission"),
                intentFilters=[ET.tostring(f, encoding="unicode") for f in component.findall("intent-filter")]))
    certificates = call(build_tools / "apksigner", "verify", "--print-certs", path)
    label = re.search(r"^application-label:'([^']*)'", badging, re.M)
    launcher = re.search(r"^launchable-activity:.*? label='([^']*)'", badging, re.M)
    return dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
        applicationId=manifest.get("package"), versionName=manifest.get(ANDROID + "versionName"),
        versionCode=manifest.get(ANDROID + "versionCode"),
        label=label[1] if label else None,
        launcherLabel=launcher[1] if launcher else None,
        sharedUserId=manifest.get(ANDROID + "sharedUserId"),
        debuggable=app.get(ANDROID + "debuggable", "false"),
        allowBackup=app.get(ANDROID + "allowBackup"), providers=providers, components=components,
        permissions=[p.get(ANDROID + "name") for p in manifest.findall("uses-permission")],
        declaredPermissions=[p.get(ANDROID + "name") for p in manifest.findall("permission")],
        signingSha1=re.search(r"Signer #1 certificate SHA-1 digest: (\S+)", certificates)[1],
        signingSha256=re.search(r"Signer #1 certificate SHA-256 digest: (\S+)", certificates)[1])


def main():
    reference = inspect(Path("app/build/phase11a-reference/app-reference.apk"))
    test = inspect(Path("app/build/outputs/apk/debug/app-debug.apk"))
    assert reference["applicationId"] == PRODUCTION and test["applicationId"] == TEST
    assert reference["label"] == reference["launcherLabel"] == "Steuer-Assistent Anlage V"
    assert test["label"] == test["launcherLabel"] == "ImmoPilot Test"
    assert not any("android.intent.category.BROWSABLE" in str(c["intentFilters"])
                   for c in test["components"]), "Isolated debug app must not compete for library browser callbacks"
    assert reference["sharedUserId"] is None and test["sharedUserId"] is None
    assert reference["signingSha256"] == test["signingSha256"]
    authorities = []
    for apk in (reference, test):
        own = [a for p in apk["providers"] for a in p["authorities"].split(";")]
        assert own and len(own) == len(set(own))
        assert all(a.startswith(apk["applicationId"] + ".") for a in own), own
        assert apk["applicationId"] + ".provider" in own
        assert all(p.startswith(apk["applicationId"] + ".") for p in apk["declaredPermissions"])
        authorities.append(set(own))
    assert authorities[0].isdisjoint(authorities[1])
    release_manifests = list(Path("app/build/intermediates/merged_manifests/release").rglob("AndroidManifest.xml"))
    assert len(release_manifests) == 1, "Expected actual merged release manifest"
    release = ET.parse(release_manifests[0]).getroot()
    release_app = release.find("application")
    release_authorities = {a for p in release_app.findall("provider") for a in p.get(ANDROID + "authorities").split(";")}
    assert release.get("package") == PRODUCTION and release.get(ANDROID + "sharedUserId") is None
    assert release_app.get(ANDROID + "debuggable", "false") == "false"
    assert release_app.get(ANDROID + "label") == "@string/app_name"
    assert release_authorities == authorities[0]
    test_apk = Path("app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
    qa = inspect(test_apk)
    assert qa["applicationId"] == TEST + ".test" and qa["signingSha256"] == test["signingSha256"]
    qa_authorities = {a for p in qa["providers"] for a in p["authorities"].split(";")}
    assert qa_authorities.isdisjoint(authorities[0] | authorities[1])
    instrumentation = ET.fromstring(call(analyzers[0], "manifest", "print", test_apk)).find("instrumentation")
    assert instrumentation.get(ANDROID + "targetPackage") == TEST
    result = dict(status="AUTOMATISIERT BESTANDEN", practicalStatus="NOCH OFFEN",
        commit=os.environ.get("GITHUB_SHA"), ciRunId=os.environ.get("GITHUB_RUN_ID"),
        runId="P11A-" + datetime.now(timezone.utc).strftime("%Y-%m-%d") + "-CI-" +
            os.environ.get("GITHUB_RUN_ID", "LOCAL") + "-" + os.environ.get("GITHUB_RUN_ATTEMPT", "1") + "-isolation",
        reference=reference, test=test, instrumentation=qa,
        releaseManifest=dict(applicationId=release.get("package"), debuggable=False,
            labelResource=release_app.get(ANDROID + "label"), authorities=sorted(release_authorities)),
        instrumentationApkSha256=qa["sha256"])
    output = Path("app/build/emulator-e2e/phase11a-isolation/apks.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
