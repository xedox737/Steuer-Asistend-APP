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
        self.assertIn(("com.example.data.OfflineBackupScheduleStoreTest",
                       "clockRefreshReplacesPendingRegistrationAndKeepsActiveClaim"), backup_ci.expected("test"))

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
