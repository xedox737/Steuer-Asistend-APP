"""Require actual SDK36 native callback and consent regressions; never claim real OAuth."""
import hashlib
import json
import os
from pathlib import Path
import re
import xml.etree.ElementTree as ET

sources = [Path('app/src/test/java/com/example/api/NativeDriveSignInTest.kt'),
           Path('app/src/test/java/com/example/ui/DriveConnectionRegressionTest.kt')]
actual = {}
for path in Path('app/build/test-results/testDebugUnitTest').glob('*.xml'):
    for case in ET.parse(path).iter('testcase'):
        key = case.get('classname')
        if key not in ('com.example.api.NativeDriveSignInTest', 'com.example.ui.DriveConnectionRegressionTest'):
            continue
        assert not any(child.tag in ('failure', 'error', 'skipped') for child in case), case.attrib
        actual.setdefault(key, []).append(case.get('name'))
reports = []
for path in sources:
    text = path.read_text()
    assert '@Config(sdk = [36]' in text, path
    package = re.search(r'^package (\S+)', text)[1]
    key = package + '.' + path.stem
    expected = set(re.findall(r'@Test\s+fun\s+(\w+)\s*\(', text))
    methods = actual.get(key, [])
    assert len(methods) == len(set(methods)) == len(expected) and set(methods) == expected, key
    reports.append(dict(className=key, actualMethods=len(methods), methods=sorted(methods),
                        sourceSha256=hashlib.sha256(path.read_bytes()).hexdigest()))
assert len(reports[0]['methods']) >= 10 and len(reports[1]['methods']) >= 35
out = Path('app/build/reports/phase11d'); out.mkdir(parents=True, exist_ok=True)
report = dict(commit=os.getenv('GITHUB_SHA', 'LOCAL'), run=os.getenv('GITHUB_RUN_ID', 'LOCAL'),
              sdk=36, realGoogleOAuthVerified=False, classes=reports)
(out/'auth.json').write_text(json.dumps(report, indent=2)+'\n')
print(json.dumps(report))
