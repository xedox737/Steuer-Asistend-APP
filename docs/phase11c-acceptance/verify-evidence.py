#!/usr/bin/env python3
"""Recheck archived CI XML and SHA256; run beside evidence/ and manifest.json."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
for entry in manifest['files']:
    path = root / entry['path']
    data = path.read_bytes()
    assert len(data) == entry['bytes'], path
    assert hashlib.sha256(data).hexdigest() == entry['sha256'], path

for variant, expected in [('product', 1179), ('isolated', 1206)]:
    for part, count in [('verify', expected), ('emulator', 70 if variant == 'product' else 73)]:
        cases = []
        failures = []
        for path in (root / 'evidence' / variant / part).rglob('*.xml'):
            if part == 'verify' and 'testDebugUnitTest' not in str(path):
                continue
            if part == 'emulator' and 'androidTest-results' not in str(path):
                continue
            for case in ET.parse(path).iter('testcase'):
                cases.append((case.get('classname'), case.get('name')))
                if any(child.tag in ('failure', 'error', 'skipped') for child in case):
                    failures.append(cases[-1])
        assert len(cases) == len(set(cases)) == count, (variant, part, len(cases))
        assert not failures, (variant, part, failures)
        print(f'{variant}/{part}: {len(cases)} actual unique methods, no failures/skips')
    evidence = json.loads((root / 'evidence' / variant / 'verify' / 'reports' / 'phase11c' / 'connection.json').read_text())
    assert evidence['sdk'] == 36 and evidence['actualMethods'] == 23
    assert evidence['realOAuthVerified'] is False
    repair = json.loads((root / 'evidence' / variant / 'verify' / 'reports' / 'e11a03-repair' / 'repair-summary.json').read_text())
    assert repair['actualRepairMethods'] == 36
    assert repair['originalHashesMatched'] == 60
    assert repair['fullRestoreErrors'] == repair['mergeErrors'] == 0
print('Archive verified. Real Google OAuth, Android16 and installed-key compatibility remain open.')
