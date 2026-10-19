#!/usr/bin/env python3
"""Independently audit saved Phase10B evidence; no contract normalization/ignore rules.

Usage: python3 .github/scripts/phase10b-audit-evidence.py <app/build> <output.json>
The Kotlin RestoreContractSnapshot remains the sole contract implementation.
"""
import csv
import hashlib
import io
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile


def load(path):
    return json.loads(path.read_text())


def audit(build):
    report = {"portfolios": {}, "failures": []}
    failures = report["failures"]

    def check(valid, message):
        if not valid:
            failures.append(message)

    states = ["BEFORE", "AFTER_RESTORE_1", "AFTER_RESTORE_2", "AFTER_LOCAL_CHANGES",
              "AFTER_LOCAL_MERGE_1", "AFTER_LOCAL_MERGE_2", "AFTER_REPLACE_FULL"]
    for units in (17, 21):
        folder = build / "reports/phase10b" / ("units-%d" % units)
        summary = load(folder / "comparison-summary.json")
        expected_pairs = {"BEFORE--AFTER_RESTORE_1", "BEFORE--AFTER_RESTORE_2", "BEFORE--AFTER_REPLACE_FULL",
                          "AFTER_RESTORE_1--AFTER_RESTORE_2", "AFTER_LOCAL_CHANGES--AFTER_LOCAL_MERGE_1",
                          "AFTER_LOCAL_CHANGES--AFTER_LOCAL_MERGE_2", "AFTER_LOCAL_MERGE_1--AFTER_LOCAL_MERGE_2"}
        check(set(summary) == expected_pairs, "%d required comparisons" % units)
        check(not load(folder / "assertion-results.json")["failures"], "%d portfolio assertions" % units)
        empty = load(folder / "empty-target.json")
        check(all(value == 0 for value in empty.values()), "%d empty logical target" % units)
        check(load(folder / "bank-import-results.json")["inserted"] == [120, 0, 0], "%d bank imports" % units)
        manifest = {item["documentId"]: item for item in load(folder / "hash-manifest.json")}
        check(len(manifest) == 60, "%d original manifest count" % units)
        checkpoints = {}
        for state in states:
            evidence = load(folder / (state + ".INTEGRITY.json"))
            checkpoints[state] = evidence
            local = state.startswith("AFTER_LOCAL_")
            expected = {"properties": 5, "units": units, "receipts": 71 if local else 70,
                        "documents": 71 if local else 70, "trips": 11 if local else 10,
                        "bankTransactions": 120, "bankAccounts": 1, "bankLinks": 1,
                        "rentAssignments": 6, "loans": 1, "auditRuns": 100}
            check(all(evidence[key] == value for key, value in expected.items()), "%d %s counts" % (units, state))
            check(not evidence["orphans"], "%d %s orphans" % (units, state))
            check(len(evidence["originals"]) == 60, "%d %s original count" % (units, state))
            for original in evidence["originals"]:
                baseline = manifest[original["documentId"]]
                check(original["readable"] and original["sha256"] == baseline["sha256"] and original["size"] == baseline["size"],
                      "%d %s original %s" % (units, state, original["documentId"]))
        for name, counts in summary.items():
            before, after = name.split("--")
            for suffix, key in (("RAW-DIFF", "raw"), ("ROOM-RAW-DIFF", "roomRaw"), ("CONTRACT-DIFF", "contract")):
                check(len(load(folder / (name + "." + suffix + ".json"))) == counts[key], "%d %s %s count" % (units, name, key))
            check(counts["contract"] == 0, "%d %s contract diff" % (units, name))
            check((folder / (before + ".CONTRACT.json")).read_bytes() == (folder / (after + ".CONTRACT.json")).read_bytes(),
                  "%d %s exact contract snapshot" % (units, name))
            if before != "BEFORE":
                check(counts["raw"] == counts["roomRaw"] == 0, "%d %s idempotence" % (units, name))
        for state in ("AFTER_RESTORE_1", "AFTER_RESTORE_2", "AFTER_REPLACE_FULL"):
            for key in ("receiptIds", "receiptInternalIds", "documentIds", "tripIds"):
                check(checkpoints["BEFORE"][key] == checkpoints[state][key], "%d %s %s" % (units, state, key))
        transport = folder / "qa-transport-snapshot.zip"
        check(hashlib.sha256(transport.read_bytes()).hexdigest() == (folder / "qa-transport-snapshot.sha256").read_text().strip(),
              "%d transport hash" % units)
        with zipfile.ZipFile(transport) as archive:
            check(archive.testzip() is None, "%d transport ZIP integrity" % units)
            check(len([name for name in archive.namelist() if name.startswith("originals/")]) == 60, "%d transport originals" % units)
        report["portfolios"][str(units)] = {"comparisons": summary, "checkpoints": checkpoints, "emptyTarget": empty}

    folder = build / "reports/phase10b/datev"
    content = (folder / "batch-120.csv").read_bytes()
    check(content.startswith(b"\xef\xbb\xbf"), "DATEV UTF-8 BOM")
    text = content.decode("utf-8-sig")
    check("\n" not in text.replace("\r\n", ""), "DATEV CRLF")
    rows = list(csv.reader(io.StringIO(text), delimiter=";"))
    check(rows[0][0] == "EXTF" and len(rows[0]) == 31, "DATEV EXTF header")
    check(len(rows) == 122 and all(len(row) == 125 for row in rows[1:]), "DATEV 120 bookings / 125 fields")
    with zipfile.ZipFile(folder / "batch-120.zip") as archive:
        names = archive.namelist()
        originals = [name for name in names if name.startswith("02_Originalbelege/") and not name.endswith("/")]
        check(archive.testzip() is None and len(names) == len(set(names)), "DATEV ZIP integrity")
        check(len(originals) == 120 and "08_Pruefprotokoll/manifest.json" in names, "DATEV original count / manifest")
        check(len({hashlib.sha256(archive.read(name)).hexdigest() for name in originals}) == 120, "DATEV 120 distinct originals")
        manifest = json.loads(archive.read("08_Pruefprotokoll/manifest.json"))
        check(manifest["totalRecords"] == 120 and len(manifest["files"]) == 120, "DATEV manifest count")
        check({entry["receiptId"] for entry in manifest["files"]} == set(range(1, 121)), "DATEV manifest receipt IDs")
        for entry in manifest["files"]:
            receipt_id = entry["receiptId"]
            data = archive.read(entry["filename"])
            check(hashlib.sha256(data).hexdigest() == entry["sha256Hash"] and len(data) == entry["fileSizeBytes"],
                  "DATEV manifest hash/size %d" % receipt_id)
            check(entry["receiptInternalId"] == "export-receipt-%d" % receipt_id and entry["belegnummer"] == "E10B-%d" % receipt_id,
                  "DATEV manifest receipt mapping %d" % receipt_id)
        package_csv = [name for name in names if name.startswith("01_DATEV/") and name.endswith(".csv")]
        check(len(package_csv) == 1, "DATEV product ZIP CSV")
        package_bytes = archive.read(package_csv[0])
        package_text = package_bytes.decode("utf-8-sig")
        package_rows = list(csv.reader(io.StringIO(package_text), delimiter=";"))
        check(package_bytes.startswith(b"\xef\xbb\xbf") and "\n" not in package_text.replace("\r\n", "") and
              len(package_rows) == 122 and package_rows[0][0] == "EXTF" and all(len(row) == 125 for row in package_rows[1:]),
              "DATEV product ZIP CSV format")
    report["datev"] = {"bookings": len(rows) - 2, "headerFields": len(rows[0]), "bookingFields": len(rows[2]), "originals": len(originals),
                       "csvSha256": hashlib.sha256(content).hexdigest()}
    cases = []
    for path in sorted((build / "test-results/testDebugUnitTest").glob("TEST-*.xml")):
        cases.extend(ET.parse(path).getroot().iter("testcase"))
    placeholders = [case for case in cases if case.get("classname", "").startswith("Gradle Test Run ")]
    real = [case for case in cases if case not in placeholders]
    report["unit"] = {"executed": sum(case.find("skipped") is None for case in real), "placeholders": len(placeholders),
                      **{tag: sum(case.find(tag) is not None for case in real) for tag in ("failure", "error", "skipped")}}
    check(bool(real) and not placeholders and all(report["unit"][tag] == 0 for tag in ("failure", "error", "skipped")), "Unit results")
    report["testExecutionGuard"] = load(build / "reports/phase10d/test-execution.json")
    check(report["testExecutionGuard"]["ok"], "SDK test execution guard")
    check(report["testExecutionGuard"]["actualMethods"] == len(real), "Test execution guard and Unit XML belong to the same execution")
    report["ok"] = not failures
    return report


if __name__ == "__main__":
    result = audit(Path(sys.argv[1]))
    Path(sys.argv[2]).write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"ok": result["ok"], "unit": result["unit"], "datev": result["datev"], "failures": result["failures"]}, ensure_ascii=False))
    sys.exit(0 if result["ok"] else 1)
