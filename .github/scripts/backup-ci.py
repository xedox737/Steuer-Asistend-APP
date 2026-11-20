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
        package = name[len(prefix):].rsplit("/", 1)[0].replace("/", ".")
        classes = list(re.finditer(r"^class (\w+)\b", source, re.MULTILINE))
        if not classes:
            raise ValueError(f"No top-level test class in {name}")
        discovered = 0
        previous_end = 0
        for index, declaration in enumerate(classes):
            next_class = classes[index + 1].start() if index + 1 < len(classes) else len(source)
            end = source.rindex("\n}", declaration.end(), next_class) + 2
            body = source[declaration.end():end]
            methods = [method.strip("`") for method in METHOD.findall(body)]
            if len(set(methods)) != len(methods) or "@Config" in body:
                raise ValueError(f"Unsupported test declarations in {name}")
            discovered += len(methods)
            config = source[previous_end:declaration.start()]
            sdk_configs = re.findall(r"@Config\(\s*sdk\s*=\s*\[([^\]]+)\]", config)
            if len(sdk_configs) > 1:
                raise ValueError(f"Ambiguous SDK configuration in {name}")
            sdks = sorted(int(sdk.strip()) for sdk in sdk_configs[0].split(",")) if sdk_configs else []
            # Robolectric 4.16 names the final SDK variant without a suffix.
            suffixes = [f"[{sdk}]" for sdk in sdks[:-1]] + [""]
            classname = f"{package}.{declaration.group(1)}"
            result.update((classname, method + suffix) for method in methods for suffix in suffixes)
            previous_end = end
        if not discovered or discovered != len(re.findall(r"@Test\b", source)):
            raise ValueError(f"Cannot determine test methods in {name}")
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
