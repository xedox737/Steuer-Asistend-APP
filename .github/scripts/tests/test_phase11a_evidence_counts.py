import contextlib
import base64
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import runpy
import sys
import tempfile
import tarfile
import unittest
from unittest.mock import patch


class EvidenceMethodCountTests(unittest.TestCase):
    def test_nested_android_xml_counts_actual_methods_and_failure_skip_nodes(self):
        script = Path(__file__).resolve().parents[1] / "phase10b-evidence.py"
        previous = Path.cwd()
        with tempfile.TemporaryDirectory() as directory:
            try:
                os.chdir(directory)
                report = Path("app/build/outputs/androidTest-results/sample.xml")
                report.parent.mkdir(parents=True)
                report.write_text('<testsuites tests="999"><testsuite tests="999"><testcase classname="Example" name="failed"><failure/></testcase><testcase classname="Example" name="skipped"><skipped/></testcase></testsuite></testsuites>')
                # adb pull places individual JUnit diagnostics below emulator-e2e/device/.
                diagnostics = {
                    "app/build/emulator-e2e/device/phase11a-drive-namespace.json": b'{"transport":"IN_MEMORY_HTTP_SAME_ACCOUNT"}',
                    "app/build/emulator-e2e/device/Phase6BWorkflowEmulatorTest-case-failure.txt": "Prüfung wird geladen …".encode(),
                }
                for name, data in diagnostics.items():
                    path = Path(name)
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(data)
                output = io.StringIO()
                with patch.object(sys, "argv", [str(script), "--emulator"]), contextlib.redirect_stdout(output):
                    runpy.run_path(str(script), run_name="__main__")
                summary = json.loads(Path("app/build/reports/phase10b/emulator-ci-evidence-summary.json").read_text())
                self.assertEqual(dict(tests=2, failures=1, errors=0, skipped=1), summary["totals"])
                lines = output.getvalue().splitlines()
                encoded = "".join(line.split()[2] for line in lines if line.startswith("PHASE10B_EVIDENCE_PART "))
                packed = base64.b64decode(encoded)
                digest = next(line.split()[1] for line in lines if line.startswith("PHASE10B_EVIDENCE_SHA256 "))
                self.assertEqual(digest, hashlib.sha256(packed).hexdigest())
                with tarfile.open(fileobj=io.BytesIO(gzip.decompress(packed))) as archive:
                    for name, data in diagnostics.items():
                        self.assertEqual(data, archive.extractfile(name).read())
            finally:
                os.chdir(previous)


if __name__ == "__main__":
    unittest.main()
