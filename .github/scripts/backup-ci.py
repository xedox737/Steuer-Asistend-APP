#!/usr/bin/env python3
"""Scope the backup PR and require a successful XML result for each selected method."""
import collections
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
PATHS = ROOT / ".github/scripts/backup-ci-paths.txt"
PICKER = "app/src/androidTest/java/com/example/e2e/OfflineBackupPickerEmulatorTest.kt"
METHOD = re.compile(r"@Test\b(?:\s*\([^)]*\))?\s+fun\s+([A-Za-z_]\w*|`[^`]+`)")


def expected(kind):
    prefix = f"app/src/{kind}/java/"
    paths = set(PATHS.read_text().splitlines())
    if kind == "androidTest":
        paths.add(PICKER)
    result = set()
    for name in sorted(paths):
        if not name.startswith(prefix) or not name.endswith("Test.kt"):
            continue
        source = (ROOT / name).read_text()
        methods = [method.strip("`") for method in METHOD.findall(source)]
        if not methods or len(methods) != len(re.findall(r"@Test\b", source)) or len(set(methods)) != len(methods):
            raise ValueError(f"Cannot determine test methods in {name}")
        classname = name[len(prefix):-3].replace("/", ".")
        result.update((classname, method) for method in methods)
    if not result:
        raise ValueError(f"No test methods selected for {kind}")
    return result


def verify(report, wanted):
    cases = collections.Counter()
    for path in sorted(report.rglob("TEST-*.xml")):
        for case in ET.parse(path).iter("testcase"):
            key = (case.get("classname"), case.get("name"))
            if key not in wanted or any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
                raise ValueError(f"Unexpected or unsuccessful test: {key}")
            cases[key] += 1
    missing = wanted - cases.keys()
    duplicates = [key for key, count in cases.items() if count != 1]
    if missing or duplicates:
        raise ValueError(f"Missing methods: {sorted(missing)}. Duplicate methods: {sorted(duplicates)}")
    print(f"Verified {len(cases)} test methods")


def main():
    command, kind, *args = sys.argv[1:]
    if command == "scope":
        changes = subprocess.check_output(["git", "diff", "--name-only", "--no-renames", "-z", kind, "HEAD"])
        changed = {path.decode() for path in changes.split(b"\0") if path}
        unknown = changed - set(PATHS.read_text().splitlines())
        if not changed or unknown:
            raise ValueError(f"Backup profile requires a nonempty, approved diff. Unknown paths: {sorted(unknown)}")
        return
    wanted = expected(kind)
    if command == "classes":
        print("\n".join(sorted({classname for classname, _ in wanted})))
    elif command == "verify":
        verify(pathlib.Path(args[0]), wanted)
    else:
        raise ValueError(f"Unknown command {command}")


if __name__ == "__main__":
    main()
