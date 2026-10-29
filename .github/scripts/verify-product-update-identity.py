"""Fail closed before publishing an APK as an update for the existing original app."""
import json
from pathlib import Path
from apk_signing import require_product_update_identity

report = json.loads(Path("app/build/reports/e11a03-repair/apk-debug.json").read_text())
require_product_update_identity(report)
print("Verified built product APK matches installed version 102 signing identity")
