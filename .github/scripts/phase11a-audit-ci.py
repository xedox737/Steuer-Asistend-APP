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
sys.exit(0 if all(r["ok"] for r in results) else 1)
