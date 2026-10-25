#!/usr/bin/env python3
"""Independent persisted-evidence analysis; never edits/normalizes the integrity checker."""
import importlib.util
import json
from pathlib import Path
import sys

def analyse(root, checker_path):
    spec = importlib.util.spec_from_file_location("strict_p11a_checker", checker_path)
    checker = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(checker)
    output = {"targets": {}, "realDriveUsed": False, "realUserDataAccessed": False}
    labels = ["portfolio-before", "portfolio-after-first-upload", "portfolio-after-upload-1", "portfolio-after-upload-2",
              "portfolio-after-read-only-download", "portfolio-after-merge-1", "portfolio-after-merge-2", "portfolio-after-database-reopen"]
    for target in ("main-final", "test-final"):
        path = root / target
        result = {p.stem: json.loads(p.read_text()) for p in path.glob("*-result.json")}
        assert len(result) == 6
        snapshots = {label: json.loads((path / (label+".json")).read_text()) for label in labels}
        baseline = snapshots[labels[0]]
        stage_results = {}
        receipt_fields = ("id", "internalId", "displayId", "propertyId", "unitId", "name", "tenant", "cent", "date", "time",
                          "category", "subcategory", "account", "description", "approval", "exportId", "allocations", "proposals")
        receipt_projection = lambda s: {r["internalId"]: {k:r.get(k) for k in receipt_fields} for r in s["raw"]["receipts"]}
        unit_projection = lambda s: {p["propertyId"]: {u["unitId"]:u for u in p["units"]} for p in s["raw"]["units"]}
        original_hashes = {o["documentId"]:o["sha256"] for o in baseline["originals"]}
        for label, snapshot in snapshots.items():
            diagnostic = checker.compare(snapshot, snapshot, 17)
            (path / (label+".strict-structure.json")).write_text(json.dumps(diagnostic, indent=2)+"\n")
            assert receipt_projection(snapshot) == receipt_projection(baseline)
            assert unit_projection(snapshot) == unit_projection(baseline)
            assert snapshot["tenancies"] == baseline["tenancies"]
            assert {o["documentId"]:o["sha256"] for o in snapshot["originals"]} == original_hashes
            stage_results[label] = {"ok":diagnostic["ok"], "errors":len(diagnostic["errors"]), "hashes":diagnostic["originalHashesMatched"]}
        assert stage_results[labels[0]]["ok"]
        assert stage_results[labels[1]]["errors"] == 4
        assert all(stage_results[label]["errors"] == 240 for label in labels[2:])
        before = baseline["raw"]["supplemental"]["propertyUnitPrefs"]
        after = snapshots[labels[-1]]["raw"]["supplemental"]["propertyUnitPrefs"]
        added = {k:after[k] for k in after.keys()-before.keys()}
        assert len(before)==102 and len(after)==110 and len(added)==8
        assert not (before.keys()-after.keys()) and all(after[k]==v for k,v in before.items())
        unit_ids = {u["unitId"] for p in baseline["raw"]["units"] for u in p["units"]}
        assert all(k.endswith("_id") and v["value"] in unit_ids for k,v in added.items())
        result["integrityStages"] = stage_results
        result["aliasDifference"] = {"before":102,"after":110,"added":added,"removed":0,"changedExisting":0,
                                     "classification":"RAW_DIFFERENCE_COMPATIBILITY_ALIASES_NOT_DOMAIN_LOSS"}
        result["junit"] = json.loads((path / "junit-summary.json").read_text())
        result["receiptBusinessValuesUnitsAndTenanciesUnchanged"] = True
        output["targets"][target] = result
    a,b=(output["targets"][k] for k in ("main-final","test-final"))
    for key in ("affectedDocumentIds","missingSourceReceiptIds","beforeCounts","rawDomainChecks"):
        assert a["portfolio-result"][key] == b["portfolio-result"][key]
    for target in (a,b):
        assert target["standalone-result"]["unitAndTenantPreserved"]
        assert target["portfolio-result"]["databaseReopenPreservesDefect"]
        assert all(s["localRowPreserved"] and s["sync"] is False for s in target["source-variants-result"]["scenarios"])
    (root / "independent-analysis.json").write_text(json.dumps(output,indent=2)+"\n")
    print(json.dumps({k:{"methods":v["junit"]["actualMethods"],"failed":v["junit"]["failed"],
         "lostDocumentUnits":len(v["portfolio-result"]["affectedDocumentIds"]),"filelessRejected":len(v["portfolio-result"]["missingSourceReceiptIds"]),
         "stages":v["integrityStages"],"aliasesAdded":len(v["aliasDifference"]["added"])} for k,v in output["targets"].items()},indent=2))
    return output

if __name__=="__main__":
    analyse(Path(sys.argv[1]), Path(sys.argv[2]))
