#!/usr/bin/env python3
"""QA-only evidence transport for synthetic Phase10B data; never changes app/test results."""
import base64
import gzip
import hashlib
import io
import importlib.util
import json
import os
from pathlib import Path
import sys
import tarfile
import xml.etree.ElementTree as ET

root = Path("app/build")
emulator = "--emulator" in sys.argv
# Additional post-sync diagnosis is deliberately separate from the unchanged Phase10
# acceptance fixtures. Persist the strict failure too; namespace success is not portfolio approval.
namespace_snapshot = root / "reports/phase11a-drive/after-merge-2.json"
if not emulator and namespace_snapshot.exists():
    spec = importlib.util.spec_from_file_location("phase11a_integrity", Path(__file__).with_name("phase11a-integrity-check.py"))
    checker = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(checker)
    snapshot = json.loads(namespace_snapshot.read_text())
    diagnosis = checker.compare(snapshot, snapshot, 17)
    diagnosis["diagnosticScope"] = "POST_SYNC_PORTFOLIO_STRUCTURE_ONLY"
    diagnostic_path = namespace_snapshot.with_name("post-sync-integrity-diagnostic.json")
    diagnostic_path.write_text(json.dumps(diagnosis, indent=2) + "\n")
    namespace = json.loads(namespace_snapshot.with_name("namespace.json").read_text())
    namespace_snapshot.with_name("scope-summary.json").write_text(json.dumps(dict(
        runId=diagnosis["runId"], namespaceScope="DRIVE_NAMESPACE_ONLY",
        namespaceStatus=namespace["status"], fullUiSyncSuccess=namespace["fullUiSyncSuccess"],
        portfolioIntegrityOk=diagnosis["ok"], portfolioIntegrityErrors=len(diagnosis["errors"]),
        originalHashesMatched=diagnosis["originalHashesMatched"], practicalStatus="NOCH OFFEN"), indent=2) + "\n")
    print("PHASE11A_POST_SYNC_INTEGRITY_DIAGNOSTIC " + json.dumps(dict(
        ok=diagnosis["ok"], errors=len(diagnosis["errors"]), originalHashesMatched=diagnosis["originalHashesMatched"],
        practicalStatus="NOCH OFFEN", report=str(diagnostic_path))), flush=True)
if emulator:
    paths = sorted(set(root.glob("outputs/androidTest-results/**/*.xml")) |
                   set(root.glob("emulator-e2e/phase11a-isolation/*.json")) |
                   set(root.glob("emulator-e2e/phase11a-ui/**/*.json")) |
                   set(root.glob("emulator-e2e/phase11a-ui/*.txt")) |
                   set(root.glob("emulator-e2e/**/phase11a-drive-namespace.json")) |
                   set(root.glob("emulator-e2e/**/*failure*.txt")) |
                   {root / "emulator-e2e" / name for name in ("result.txt", "device-environment.txt", "logcat-crash.txt")})
else:
    paths = sorted(set(root.glob("reports/phase10b/**/*")) | set(root.glob("test-results/testDebugUnitTest/*.xml")) |
                   set(root.glob("reports/phase10d/*.json")) | set(root.glob("reports/phase11a-ui/*.json")) |
                   set(root.glob("reports/phase11a-drive/*.json")))
files = [p for p in paths if p.is_file()]
totals = dict(tests=0, failures=0, errors=0, skipped=0)
suites = []
for p in files:
    if p.suffix == ".xml":
        e = ET.parse(p).getroot()
        # Android emits <testsuites> around <testsuite>; root counters may be absent.
        # Count actual methods once, including failures/skips, rather than trusting aggregates.
        cases = list(e.iter("testcase"))
        row = dict(name=e.get("name") or p.name, tests=len(cases),
                   failures=sum(c.find("failure") is not None for c in cases),
                   errors=sum(c.find("error") is not None for c in cases),
                   skipped=sum(c.find("skipped") is not None for c in cases))
        suites.append(row)
        for k in totals:
            totals[k] += row[k]
summary = dict(head=os.environ.get("GITHUB_SHA"), run=os.environ.get("GITHUB_RUN_ID"), totals=totals,
               suites=suites, files=[dict(path=str(p), size=p.stat().st_size,
               sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in files])
out = root / "reports/phase10b"
out.mkdir(parents=True, exist_ok=True)
summary_path = out / ("emulator-ci-evidence-summary.json" if emulator else "ci-evidence-summary.json")
summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2))
files.append(summary_path)
buffer = io.BytesIO()
with tarfile.open(fileobj=buffer, mode="w") as archive:
    for p in files:
        archive.add(p, arcname=str(p), recursive=False)
packed = gzip.compress(buffer.getvalue(), mtime=0)
print("PHASE10B_EVIDENCE_SHA256 " + hashlib.sha256(packed).hexdigest(), flush=True)
encoded = base64.b64encode(packed).decode("ascii")
for offset in range(0, len(encoded), 12000):
    print("PHASE10B_EVIDENCE_PART %06d %s" % (offset // 12000, encoded[offset:offset+12000]), flush=True)
print("PHASE10B_EVIDENCE_END %d" % len(packed), flush=True)
print("PHASE10B_TEST_TOTALS " + json.dumps(totals), flush=True)
