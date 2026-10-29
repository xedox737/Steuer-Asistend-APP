"""Verify archived hashes and actually executed CI methods, not real Google OAuth."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
manifest = json.loads((root/'manifest.json').read_text())
for entry in manifest['files']:
    data = (root/entry['path']).read_bytes()
    assert len(data) == entry['bytes'], entry['path']
    assert hashlib.sha256(data).hexdigest() == entry['sha256'], entry['path']
for part, expected in [('verify', 1203), ('emulator', 70)]:
    cases = []
    for path in (root/part).rglob('*.xml'):
        if part == 'verify' and 'testDebugUnitTest' not in str(path):
            continue
        if part == 'emulator' and 'androidTest-results' not in str(path):
            continue
        for case in ET.parse(path).iter('testcase'):
            assert not any(child.tag in ('failure', 'error', 'skipped') for child in case), case.attrib
            cases.append((case.get('classname'), case.get('name')))
    assert len(cases) == len(set(cases)) == expected, (part, len(cases))
    print(part, len(cases), 'unique methods, no failures/errors/skips')
auth = json.loads((root/'verify/reports/phase11d/auth.json').read_text())
assert auth['sdk'] == 36 and auth['realGoogleOAuthVerified'] is False
assert {c['actualMethods'] for c in auth['classes']} == {10, 37}
repair = json.loads((root/'verify/reports/e11a03-repair/repair-summary.json').read_text())
assert repair['actualRepairMethods'] == 36 and repair['originalHashesMatched'] == 60
assert repair['fullRestoreErrors'] == repair['mergeErrors'] == 0
apk = json.loads((root/'ci-apk-summary.json').read_text())
assert apk['signatureVerifiedIndependently'] is True
assert apk['matchesReportedCertificate'] is False
assert apk['installedCertificateVerified'] is False
assert apk['matchesInstalledCertificate'] is None
assert apk['installationAllowedAsProductUpdate'] is False
print('Archive verified. Practical release remains NO-GO: real OAuth, device and compatible signing are open.')
