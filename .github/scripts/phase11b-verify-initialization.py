#!/usr/bin/env python3
"""Require actual Issue #145 regression methods; existing portfolio/security gates remain separate."""
import hashlib
import json
import os
from pathlib import Path
import re
import xml.etree.ElementTree as ET

source = Path('app/src/test/java/com/example/api/DriveInitializationRegressionTest.kt')
expected = set(re.findall(r'@Test\s+fun\s+(\w+)\s*\(', source.read_text()))
assert len(expected) >= 13, 'Missing initialization coverage'
actual = []
for path in Path('app/build/test-results/testDebugUnitTest').glob('*.xml'):
    for case in ET.parse(path).getroot().iter('testcase'):
        if case.get('classname') != 'com.example.api.DriveInitializationRegressionTest':
            continue
        assert all(case.find(k) is None for k in ('failure', 'error', 'skipped')), case.get('name')
        actual.append(case.get('name'))
assert len(actual) == len(set(actual)) == len(expected) and set(actual) == expected, 'Incomplete/repeated methods'
report = dict(status='AUTOMATISIERT BESTANDEN', practicalStatus='NOCH OFFEN', issue=145,
              commit=os.environ.get('GITHUB_SHA', 'LOCAL'), ciRunId=os.environ.get('GITHUB_RUN_ID', 'LOCAL'),
              actualMethods=len(actual), methods=sorted(actual), testSourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),
              transport='IN_MEMORY_HTTP', realOAuthVerified=False)
out = Path('app/build/reports/phase11b'); out.mkdir(parents=True, exist_ok=True)
(out/'initialization.json').write_text(json.dumps(report, indent=2)+'\n')
print(json.dumps(report))
