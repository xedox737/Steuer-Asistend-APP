import importlib.util
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).resolve().parents[1] / "backup-ci.py"
SPEC = importlib.util.spec_from_file_location("backup_ci", MODULE)
backup_ci = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(backup_ci)


class BackupCiTest(unittest.TestCase):
    def test_source_selection_contains_android_and_jvm_methods(self):
        self.assertIn(("com.example.e2e.OfflineBackupScheduleEmulatorTest",
                       "missingRegistrationIsRecreatedOnceOnAndroid"), backup_ci.expected("androidTest"))
        self.assertIn(("com.example.ui.EncryptedRestoreConfirmationComposeTest",
                       "credentialImportRequiresExplicitSelectionAtReplacementConfirmation"), backup_ci.expected("test"))
        self.assertIn(("com.example.data.OfflineBackupScheduleStoreTest",
                       "clockRefreshReplacesPendingRegistrationAndKeepsActiveClaim"), backup_ci.expected("test"))

    def test_multisdk_methods_require_each_configured_variant(self):
        wanted = backup_ci.expected("test")
        classname = "com.example.data.OfflineBackupDeviceKeyTest"
        method = "alteredRecoveryContextOrCiphertextCannotOpenMaster"
        variants = {(owner, name) for owner, name in wanted if owner == classname and name.startswith(method)}
        self.assertEqual(variants, {(classname, method + "[24]"), (classname, method)})
        with tempfile.TemporaryDirectory() as folder:
            report = Path(folder)
            (report / "TEST-device.xml").write_text(
                f'<testsuite><testcase classname="{classname}" name="{method}[24]"/></testsuite>')
            with self.assertRaisesRegex(ValueError, "Missing methods"):
                backup_ci.verify(report, variants)

    def test_requires_exactly_one_success_per_method(self):
        case = '<testcase classname="example.Test" name="selected"/>'
        wanted = {("example.Test", "selected")}
        with tempfile.TemporaryDirectory() as folder:
            report = Path(folder)
            xml = report / "TEST-example.xml"
            for content in ("", case * 2, case.replace("selected", "unexpected"),
                            case.replace("/>", "><skipped/></testcase>"),
                            case.replace("/>", "><failure/></testcase>"),
                            case.replace("example.Test", "Gradle Test Run :app")):
                with self.subTest(content=content):
                    xml.write_text(f"<testsuite tests=\"1\">{content}</testsuite>")
                    with self.assertRaises(ValueError):
                        backup_ci.verify(report, wanted)
            xml.write_text(f"<testsuite>{case}</testsuite>")
            backup_ci.verify(report, wanted)
