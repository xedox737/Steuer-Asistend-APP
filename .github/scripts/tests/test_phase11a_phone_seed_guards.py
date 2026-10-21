import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("phone_seed_smoke", Path(__file__).resolve().parents[1] / "phase11a-ui-seed-smoke.py")
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


class PhoneSeedSmokeGuardTests(unittest.TestCase):
    def test_outside_ci_never_reaches_adb(self):
        with patch.dict(smoke.os.environ, {"GITHUB_ACTIONS": "false"}), patch.object(smoke, "call") as call:
            with self.assertRaisesRegex(RuntimeError, "CI only"):
                smoke.main()
            call.assert_not_called()

    def test_real_phone_never_reaches_install_or_instrumentation(self):
        with patch.dict(smoke.os.environ, {"GITHUB_ACTIONS": "true"}), patch.object(smoke, "call", side_effect=["real-serial", "physical-hardware"]) as call:
            with self.assertRaisesRegex(RuntimeError, "emulator hardware"):
                smoke.main()
            self.assertEqual(2, call.call_count)
            self.assertFalse(any("install" in invocation.args or "instrument" in invocation.args for invocation in call.call_args_list))

    def test_other_profile_never_reaches_install(self):
        with patch.dict(smoke.os.environ, {"GITHUB_ACTIONS": "true"}), patch.object(smoke, "call", side_effect=["emulator-5554", "ranchu", "10"]) as call:
            with self.assertRaisesRegex(RuntimeError, "user 0"):
                smoke.main()
            self.assertEqual(3, call.call_count)


if __name__ == "__main__":
    unittest.main()
