import contextlib
import io
import json
import os
from pathlib import Path
import runpy
import sys
import tempfile
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
                with patch.object(sys, "argv", [str(script), "--emulator"]), contextlib.redirect_stdout(io.StringIO()):
                    runpy.run_path(str(script), run_name="__main__")
                summary = json.loads(Path("app/build/reports/phase10b/emulator-ci-evidence-summary.json").read_text())
                self.assertEqual(dict(tests=2, failures=1, errors=0, skipped=1), summary["totals"])
            finally:
                os.chdir(previous)


if __name__ == "__main__":
    unittest.main()
