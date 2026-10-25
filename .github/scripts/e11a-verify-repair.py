#!/usr/bin/env python3
"""Gate repaired portfolio evidence using the unchanged strict Phase11A checker."""
import importlib.util
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path("app/build")
out = root / "reports/e11a03-repair"
out.mkdir(parents=True, exist_ok=True)
spec = importlib.util.spec_from_file_location("strict_integrity", Path(__file__).with_name("phase11a-integrity-check.py"))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)
try:
    expected = set()
    names = ("ProductionDriveNamespaceTest", "ReceiptSyncPreservationE2ETest", "ReceiptDocumentSyncStateTest",
             "ReceiptDocumentIntegrityAuditTest", "FilelessLogbookContractTest")
    for name in names:
        source = next(Path("app/src/test/java").rglob(name + ".kt")).read_text()
        package = re.search(r"^package (\S+)", source, re.M)[1]
        expected.update((package + "." + name, method) for method in re.findall(r"@Test\s+fun\s+(\w+)\s*\(", source))
    actual = []
    for path in (root / "test-results/testDebugUnitTest").glob("*.xml"):
        for case in ET.parse(path).getroot().iter("testcase"):
            key = (case.get("classname"), case.get("name"))
            if key in expected:
                assert all(case.find(k) is None for k in ("failure", "error", "skipped")), key
                actual.append(key)
    assert len(actual) == len(set(actual)) == len(expected) and set(actual) == expected, "missing/repeated repair methods"
    read = lambda label: json.loads((out / (label + ".json")).read_text())
    baseline = read("before-fresh-full-backed")
    after = read("after-fresh-full-and-two-merges")
    full = checker.compare(baseline, after, 17)
    (out / "full-restore-integrity.json").write_text(json.dumps(full, indent=2) + "\n")
    merge_snapshot = read("preserved-after-two-merges")
    merge = checker.compare(merge_snapshot, merge_snapshot, 17)
    (out / "merge-structure-integrity.json").write_text(json.dumps(merge, indent=2) + "\n")
    summary = dict(ok=full["ok"] and merge["ok"], actualRepairMethods=len(actual),
        originalHashesMatched=full["originalHashesMatched"], fullRestoreErrors=len(full["errors"]),
        mergeErrors=len(merge["errors"]), counts=after["counts"], commit=after["meta"]["commit"],
        runId=after["meta"]["runId"], transport="IN_MEMORY_HTTP", practicalStatus="NOCH OFFEN")
    (out / "repair-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, ensure_ascii=False))
    if not summary["ok"]:
        print(json.dumps(dict(fullErrors=full["errors"], contractDifferences=full["contractDifferences"][:12],
                             businessDifferences=full["businessDifferences"][:12]), ensure_ascii=False))
    sys.exit(0 if summary["ok"] and summary["originalHashesMatched"] == 60 else 1)
except (OSError, ValueError, KeyError, TypeError, AssertionError, StopIteration) as error:
    print("Repair evidence invalid: " + str(error), file=sys.stderr)
    sys.exit(2)
