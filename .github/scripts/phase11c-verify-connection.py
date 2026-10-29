#!/usr/bin/env python3
"""Require every Issue 148 regression method in the normal, unmodified SDK36 CI run."""
import hashlib
import json
import os
from pathlib import Path
import re
import xml.etree.ElementTree as ET

source = Path('app/src/test/java/com/example/ui/DriveConnectionRegressionTest.kt')
expected = set(re.findall(r'@Test\s+fun\s+(\w+)\s*\(', source.read_text()))
assert len(expected) >= 23, 'Missing Issue 148 coverage'
actual = []
for path in Path('app/build/test-results/testDebugUnitTest').glob('*.xml'):
    for case in ET.parse(path).getroot().iter('testcase'):
        if case.get('classname') != 'com.example.ui.DriveConnectionRegressionTest':
            continue
        assert all(case.find(k) is None for k in ('failure', 'error', 'skipped')), case.get('name')
        actual.append(case.get('name'))
assert len(actual) == len(set(actual)) == len(expected) and set(actual) == expected, 'Incomplete/repeated methods'
assert '@Config(sdk = [36]' in source.read_text(), 'Android 16 coverage must remain enabled'
report = dict(issue=148, commit=os.environ.get('GITHUB_SHA', 'LOCAL'),
              ciRunId=os.environ.get('GITHUB_RUN_ID', 'LOCAL'), sdk=36,
              testSourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),
              actualMethods=len(actual), methods=sorted(actual),
              transport='IN_MEMORY_HTTP_AND_PLAY_SERVICES_SHADOW', realOAuthVerified=False)
out = Path('app/build/reports/phase11c'); out.mkdir(parents=True, exist_ok=True)
(out/'connection.json').write_text(json.dumps(report, indent=2)+'\n')
print(json.dumps(report))
