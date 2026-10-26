#!/usr/bin/env python3
"""The provisional app may only add the existing two QA UI hooks to repaired main sources."""
import hashlib
import json
from pathlib import Path
import subprocess

PRODUCT = "3154bf42725d2f881010e6b139d0165c449fef05"
expected = Path(".github/qa/phase11a-integration-ui-hooks.patch").read_bytes()
actual = subprocess.check_output(["git", "diff", "--unified=0", "--no-ext-diff", "--no-color", "--src-prefix=a/",
                                 "--dst-prefix=b/", PRODUCT, "--", "app/src/main"])
assert actual == expected, "Main sources diverged from repaired product beyond the existing QA UI hooks"
result = dict(status="AUTOMATISIERT BESTANDEN", practicalStatus="NOCH OFFEN",
              repairedProductHead=PRODUCT, identicalSharedProductSources=True,
              uiHooksPatchSha256=hashlib.sha256(actual).hexdigest(),
              allowedHookFiles=["AppSettingsScreen.kt", "ReceiptAppUi.kt"])
out = Path("app/build/reports/phase11a-ui/shared-product-source.json")
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps(result))
