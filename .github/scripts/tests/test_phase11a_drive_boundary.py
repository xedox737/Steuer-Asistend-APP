"""Security regression: new Drive HTTP entrypoints must not bypass the namespace fence."""
from pathlib import Path
import re
import unittest


class DriveBoundaryTests(unittest.TestCase):
    def test_raw_drive_transport_is_used_only_by_guard_callbacks(self):
        root = Path(__file__).resolve().parents[3]
        source = root / "app/src/main/java"
        endpoints = {p for p in source.rglob("*.kt") if re.search(r"googleapis\.com/(?:upload/)?drive/v3/", p.read_text())}
        client = source / "com/example/api/GoogleDriveClient.kt"
        self.assertEqual({client}, endpoints)
        text = client.read_text()
        raw_calls = [line.strip() for line in text.splitlines() if ".newCall(" in line]
        self.assertEqual(5, len(raw_calls))
        self.assertTrue(all("DriveEnvironment." in line and "{ client.newCall(it).execute() }" in line for line in raw_calls))


if __name__ == "__main__":
    unittest.main()
