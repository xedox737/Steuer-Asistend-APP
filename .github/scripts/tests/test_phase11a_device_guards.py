import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("device", SCRIPTS / "phase11a-device-evidence.py")
device = importlib.util.module_from_spec(spec)
spec.loader.exec_module(device)


class DeviceGuardTests(unittest.TestCase):
    def invoke(self, output, extra=(), action="snapshot"):
        args = ["helper", action, "--serial", "TEST-DEVICE", "--user", "10", "--run-id", "P11A-2026-10-21-TEST",
                "--label", "baseline", "--commit", "a"*40, "--output", str(output), "--test-portfolio-only", *extra]
        with patch.object(sys, "argv", args):
            device.main()

    def test_wrong_foreground_profile_never_starts_instrumentation(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(device.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, b"0\n")) as call:
            with self.assertRaises(SystemExit):
                self.invoke(folder)
            self.assertEqual(1, call.call_count)

    def test_existing_snapshot_refused_before_device_mutation(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(device.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, b"10\n")) as call:
            target = Path(folder) / "P11A-2026-10-21-TEST" / "phase11a-baseline.json"
            target.parent.mkdir()
            target.write_text("existing")
            with self.assertRaisesRegex(RuntimeError, "refusing overwrite"):
                self.invoke(folder)
            self.assertEqual("existing", target.read_text())
            self.assertEqual(1, call.call_count)

    def test_untrusted_fixture_never_reaches_device(self):
        outputs = [subprocess.CompletedProcess([], 0, b"10\n"), self.runner_output()]
        with tempfile.TemporaryDirectory() as folder, patch.object(device.subprocess, "run", side_effect=outputs) as call:
            fixture = Path(folder) / "fixture.zip"
            fixture.write_bytes(b"untrusted")
            with self.assertRaises(SystemExit):
                self.invoke(folder, ["--fixture", str(fixture), "--fixture-sha256", "b"*64, "--confirm-empty-test-installation"], action="seed")
            self.assertEqual(2, call.call_count)

    def test_android_instrumentation_failure_is_not_success(self):
        outputs = [subprocess.CompletedProcess([], 0, b"10\n"),
                   self.runner_output(),
                   subprocess.CompletedProcess([], 0, b"INSTRUMENTATION_RESULT: error=failed\nINSTRUMENTATION_CODE: 0\n")]
        with tempfile.TemporaryDirectory() as folder, patch.object(device.subprocess, "run", side_effect=outputs):
            with self.assertRaisesRegex(RuntimeError, "instrumentation failed"):
                self.invoke(folder)
            self.assertFalse(any(Path(folder).rglob("*.json")))

    @staticmethod
    def runner_output(target=None):
        return subprocess.CompletedProcess([], 0,
            f"instrumentation:{device.RUNNER} (target={target or device.PACKAGE})\n".encode())

    def test_production_binding_refused_before_any_seed_write(self):
        outputs = [subprocess.CompletedProcess([], 0, b"10\n"), self.runner_output(device.PRODUCTION_PACKAGE)]
        with tempfile.TemporaryDirectory() as folder, patch.object(device.subprocess, "run", side_effect=outputs) as call:
            with self.assertRaisesRegex(RuntimeError, "not allowed on the production applicationId"):
                self.invoke(folder, action="seed")
            self.assertEqual(2, call.call_count)
            self.assertFalse(any("instrument" in args.args[0] or "run-as" in args.args[0] for args in call.call_args_list))

    def test_only_product_installation_refused(self):
        legacy = device.PRODUCTION_PACKAGE + ".test/com.example.qa.Phase11AInstrumentation"
        outputs = [subprocess.CompletedProcess([], 0, b"10\n"),
                   subprocess.CompletedProcess([], 0, f"instrumentation:{legacy} (target={device.PRODUCTION_PACKAGE})\n".encode())]
        with tempfile.TemporaryDirectory() as folder, patch.object(device.subprocess, "run", side_effect=outputs) as call:
            with self.assertRaisesRegex(RuntimeError, "not allowed on the production applicationId"):
                self.invoke(folder, action="seed")
            self.assertEqual(2, call.call_count)

    def test_parallel_product_and_test_installation_selects_only_test(self):
        legacy = device.PRODUCTION_PACKAGE + ".test/com.example.qa.Phase11AInstrumentation"
        device.require_isolated_runner(
            f"instrumentation:{legacy} (target={device.PRODUCTION_PACKAGE})\n"
            f"instrumentation:{device.RUNNER} (target={device.PACKAGE})\n")

    def test_misconfigured_host_product_package_is_rejected(self):
        with patch.object(device, "PACKAGE", device.PRODUCTION_PACKAGE):
            with self.assertRaisesRegex(RuntimeError, "not allowed on the production applicationId"):
                device.require_isolated_runner("")

    def test_run_initializer_opens_all_tests_and_preserves_existing_run(self):
        with tempfile.TemporaryDirectory() as folder:
            args = [sys.executable, str(SCRIPTS / "phase11a-init-run.py"), "--run-id", "P11A-2026-10-21-TEST",
                    "--commit", "a"*40, "--units", "17", "--output", folder]
            subprocess.run(args, check=True, capture_output=True)
            path = Path(folder) / "P11A-2026-10-21-TEST" / "run.json"
            data = path.read_bytes()
            self.assertEqual({"NOCH OFFEN"}, set(json.loads(data)["tests"].values()))
            self.assertNotEqual(0, subprocess.run(args, capture_output=True).returncode)
            self.assertEqual(data, path.read_bytes())


if __name__ == "__main__":
    unittest.main()
