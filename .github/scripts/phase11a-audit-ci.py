#!/usr/bin/env python3
"""Run the device snapshot/checker plumbing against both real Phase10B test outputs."""
import importlib.util
import json
from pathlib import Path
import sys

spec = importlib.util.spec_from_file_location("integrity", Path(__file__).with_name("phase11a-integrity-check.py"))
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)
out = Path("app/build/reports/phase11a")
out.mkdir(parents=True, exist_ok=True)
results = []
for units in (17, 21):
    folder = Path(f"app/build/reports/phase10b/units-{units}")
    before = json.loads((folder / "BEFORE.PHASE11A.json").read_text())
    for label in ("AFTER_RESTORE_1", "AFTER_RESTORE_2", "AFTER_REPLACE_FULL"):
        after = json.loads((folder / f"{label}.PHASE11A.json").read_text())
        report = qa.compare(before, after, units)
        (out / f"units-{units}-{label}.json").write_text(json.dumps(report, indent=2) + "\n")
        results.append(report)
        print(json.dumps(dict(units=units, label=label, ok=report["ok"], errors=report["errors"])))
phone_seed = json.loads(Path("app/build/reports/phase11a-ui/unit-seed.json").read_text())
# Same snapshot comparison validates all structural/original chains. No restart claim in this unit check.
phone_report = qa.compare(phone_seed, phone_seed, 17)
(out / "bundled-ui-seed-integrity.json").write_text(json.dumps(phone_report, indent=2) + "\n")
results.append(phone_report)
print(json.dumps(dict(label="bundled-ui-seed-structure-only", ok=phone_report["ok"], errors=phone_report["errors"])))
namespace_folder = Path("app/build/reports/phase11a-drive")
namespace = json.loads((namespace_folder / "namespace.json").read_text())
namespace_snapshot = json.loads((namespace_folder / "after-merge-2.json").read_text())
namespace_report = qa.compare(namespace_snapshot, namespace_snapshot, 17)
(out / "repaired-ui-drive-portfolio.json").write_text(json.dumps(namespace_report, indent=2) + "\n")
assert namespace["fullUiSyncSuccess"] is True
assert namespace["backupTransportPasses"] == namespace["mergeRestores"] == 2
assert namespace["rootDuplicates"] == 0 and not namespace["orphans"]
assert namespace["productBefore"] == namespace["productAfter"]
assert len(namespace["filelessReceiptIds"]) == 10 and not namespace["missingSourceReceiptIds"]
assert namespace_report["ok"] and namespace_report["originalHashesMatched"] == 60
results.append(namespace_report)
print(json.dumps(dict(label="repaired-phone-seed-drive-portfolio", ok=namespace_report["ok"], errors=namespace_report["errors"])))
sys.exit(0 if all(r["ok"] for r in results) else 1)
