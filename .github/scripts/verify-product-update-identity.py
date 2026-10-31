"""Verify the persistent product certificate; do not imply legacy update compatibility."""
import json
from pathlib import Path
from apk_signing import require_product_signing_identity, APPROVED_PRODUCT_SHA1, LEGACY_VERSION_103_SHA1

report = json.loads(Path("app/build/reports/e11a03-repair/apk-debug.json").read_text())
require_product_signing_identity(report)
Path("app/build/reports/product-signing.json").write_text(json.dumps(dict(
    package=report["package"], approvedCertificateSha1=APPROVED_PRODUCT_SHA1,
    actualCertificateSha1=report["signingSha1"], legacyVersion103Sha1=LEGACY_VERSION_103_SHA1,
    canUpdateVersion103=report["signingSha1"].lower() == LEGACY_VERSION_103_SHA1,
    installationMode="fresh-install-baseline"), indent=2))
print("Verified persistent product certificate; fresh install required for version 103")
