#!/usr/bin/env python3
"""Record actual APK identity and manifest; reject package/provider sandbox collisions."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

PRODUCTION = "com.aistudio.steuerassistent.vnhqkp"
TEST = PRODUCTION + ".phase11a"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--package", required=True, choices=(PRODUCTION, TEST))
    parser.add_argument("--aapt", type=Path)
    parser.add_argument("--allow-unsigned", action="store_true", help="Only for a local unsigned production release")
    parser.add_argument("--output", type=Path, default=Path("app/build/reports/e11a03-repair/apk-debug.json"))
    args = parser.parse_args()
    if args.aapt:
        aapt = args.aapt
    else:
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        if not sdk:
            raise SystemExit("ANDROID_HOME/ANDROID_SDK_ROOT or --aapt is required")
        candidates = list((Path(sdk) / "build-tools").glob("*/aapt2"))
        aapt = max(candidates, key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.parent.name)))
    run = lambda *parts: subprocess.check_output([str(aapt), *parts], text=True)
    badging = run("dump", "badging", str(args.apk))
    manifest = run("dump", "xmltree", str(args.apk), "--file", "AndroidManifest.xml")
    package, version_code, version_name = re.search(
        r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging).groups()
    label = re.search(r"^application-label:'(.*)'$", badging, re.M)[1]
    assert package == args.package, (package, args.package)
    assert label == ("ImmoPilot Test" if package == TEST else "Steuer-Assistent Anlage V"), label
    assert "android:sharedUserId" not in manifest, "sharedUserId breaks sandbox isolation"
    authorities = [item for value in re.findall(r'android:authorities[^\n]*?="([^"]+)"', manifest)
                   for item in value.split(";")]
    assert authorities and len(authorities) == len(set(authorities)), authorities
    assert all(item.startswith(package + ".") for item in authorities), authorities
    assert package + ".provider" in authorities, "Own FileProvider authority missing"
    components, current = [], None
    for line in manifest.splitlines():
        indent = len(line) - len(line.lstrip())
        found = re.match(r"\s*E: (activity|activity-alias|service|receiver|provider) \(", line)
        if found:
            current = dict(kind=found[1], name=None, exported=None, permission=None, indent=indent)
            components.append(current)
        elif current and line.lstrip().startswith("E:") and indent <= current["indent"]:
            current = None
        elif current and indent == current["indent"] + 2:
            attr = re.search(r'android:(name|permission)\([^)]*\)="([^"]+)"', line)
            if attr:
                current[attr[1]] = attr[2]
            if "android:exported(" in line:
                current["exported"] = "0xffffffff" in line
    for component in components:
        component.pop("indent")
    digest = hashlib.sha256()
    with args.apk.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    signer = subprocess.run([str(aapt.with_name("apksigner")), "verify", "--print-certs", str(args.apk)],
                            text=True, capture_output=True)
    signed = signer.returncode == 0
    assert signed or (args.allow_unsigned and package == PRODUCTION), "APK signature verification failed"
    certificate = lambda algorithm: (re.search(r"Signer #1 certificate " + algorithm + r" digest: (\S+)", signer.stdout)[1]
                                     if signed else None)
    result = dict(status="AUTOMATISIERT BESTANDEN", practicalStatus="NOCH OFFEN",
        commit=os.environ.get("GITHUB_SHA"), ciRunId=os.environ.get("GITHUB_RUN_ID"),
        apk=str(args.apk), sha256=digest.hexdigest(), sizeBytes=args.apk.stat().st_size,
        package=package, label=label, versionCode=version_code, versionName=version_name,
        signatureVerified=signed, signingSha1=certificate("SHA-1"), signingSha256=certificate("SHA-256"),
        providerAuthorities=authorities, components=components,
        permissions=re.findall(r"^uses-permission: name='([^']+)'", badging, re.M))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n")
    args.output.with_suffix(".manifest.txt").write_text(manifest)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
