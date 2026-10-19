#!/usr/bin/env python3
"""Fail CI on suite placeholders, missing methods, skipped methods, or failed methods.

Counts are derived from the checked-in @Test methods, never from testsuite.tests.
SDKs remain those explicitly declared in the test sources (three 36, two 34).
"""
import json
import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

CLASSES = {
    "com.example.ExampleRobolectricTest": 36,
    "com.example.GreetingScreenshotTest": 36,
    "com.example.RestoreSafetyTest": 36,
    "com.example.ui.AdvisorAnnualSummaryIntegrationTest": 34,
    "com.example.ui.SyntheticTaxExportE2eTest": 34,
}


def evaluate(results, sources):
    cases, placeholders = [], []
    for path in sorted(results.glob("TEST-*.xml")):
        root = ET.parse(path).getroot()
        for case in root.iter("testcase"):
            (placeholders if case.get("classname", "").startswith("Gradle Test Run ") else cases).append(case)
    rows = []
    for name, sdk in CLASSES.items():
        source = sources / (name.replace(".", "/") + ".kt")
        text = source.read_text()
        expected = len(re.findall(r"@Test\b", text))
        declared = re.search(r"sdk\s*=\s*\[(\d+)\]", text)
        matched = [case for case in cases if case.get("classname") == name]
        skipped = sum(case.find("skipped") is not None for case in matched)
        row = dict(name=name, sdk=sdk, expected=expected, executed=len(matched) - skipped,
                   failures=sum(case.find("failure") is not None for case in matched),
                   errors=sum(case.find("error") is not None for case in matched), skips=skipped,
                   placeholders=sum(case.get("name") == name.rsplit(".", 1)[-1] for case in placeholders))
        row["ok"] = (declared is not None and int(declared[1]) == sdk and expected > 0 and
                     row["executed"] == expected and len({case.get("name") for case in matched}) == expected and
                     all(row[key] == 0 for key in ("failures", "errors", "skips", "placeholders")))
        rows.append(row)
    return dict(classes=rows, actualMethods=len(cases), suitePlaceholders=len(placeholders),
                ok=all(row["ok"] for row in rows))


if __name__ == "__main__":
    results = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("app/build/test-results/testDebugUnitTest")
    summary = evaluate(results, Path("app/src/test/java"))
    output = Path("app/build/reports/phase10d")
    output.mkdir(parents=True, exist_ok=True)
    (output / "test-execution.json").write_text(json.dumps(summary, indent=2) + "\n")
    for row in summary["classes"]:
        print(json.dumps(row), flush=True)
    print("Actual methods: %d; suite placeholders: %d" % (summary["actualMethods"], summary["suitePlaceholders"]))
    sys.exit(0 if summary["ok"] else 1)
