import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("sandbox", Path(__file__).resolve().parents[1] / "phase11a-sandbox-check.py")
sandbox = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sandbox)


class SandboxGuardTests(unittest.TestCase):
    def test_reference_setup_refused_outside_ci(self):
        with patch.dict(sandbox.os.environ, {"GITHUB_ACTIONS": "false"}), patch.object(sys, "argv", ["helper", "prepare"]), patch.object(sandbox, "command") as command:
            with self.assertRaisesRegex(RuntimeError, "CI-only"):
                sandbox.main()
            command.assert_not_called()

    def test_physical_device_never_reaches_install(self):
        with patch.dict(sandbox.os.environ, {"GITHUB_ACTIONS": "true"}), patch.object(sys, "argv", ["helper", "prepare"]), patch.object(sandbox, "command", side_effect=[b"physical-serial", b"real-hardware"]) as command:
            with self.assertRaisesRegex(RuntimeError, "emulator hardware"):
                sandbox.main()
            self.assertEqual(2, command.call_count)
            self.assertFalse(any("install" in c.args for c in command.call_args_list))

    def test_existing_product_is_never_replaced_or_seeded(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(sandbox, "OUT", Path(folder)), patch.dict(sandbox.os.environ, {"GITHUB_ACTIONS": "true"}), patch.object(sys, "argv", ["helper", "prepare"]), patch.object(sandbox, "command", side_effect=[b"emulator-5554", b"ranchu", b"0", ("package:" + sandbox.PRODUCTION).encode()]) as command:
            with self.assertRaisesRegex(RuntimeError, "never replace"):
                sandbox.main()
            self.assertEqual(4, command.call_count)
            self.assertFalse(any("install" in c.args or "run-as" in c.args for c in command.call_args_list))


if __name__ == "__main__":
    unittest.main()
