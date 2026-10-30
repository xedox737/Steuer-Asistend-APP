from pathlib import Path
import hashlib,json,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent
manifest=json.loads((root/'manifest.json').read_text())
for name,digest in manifest.items():
 p=root/name
 assert p.is_file() and hashlib.sha256(p.read_bytes()).hexdigest()==digest,name

def methods(kind):
 result={}
 for p in (root/kind/'xml').rglob('*.xml'):
  for case in ET.parse(p).getroot().iter('testcase'):
   name=case.get('classname','')+'#'+case.get('name','')
   if not name.split('#')[0] or name.startswith('Gradle Test'):continue
   assert not any(case.find(t) is not None for t in ['failure','error','skipped']),name
   result[name]=True
 return result
first=ET.parse(root/'failed-emulator-attempt-1/TEST-results.xml').getroot()
assert first.get('tests')=='70' and first.get('failures')=='69' and first.get('skipped')=='0'
assert "Pixel Launcher isn't responding" in (root/'failed-emulator-attempt-1/failure-window.xml').read_text()
jvm=methods('jvm'); emulator=methods('emulator')
assert len(jvm)==1213,len(jvm)
assert len(emulator)==70,len(emulator)
assert sum('ProductionDriveNamespaceTest#' in k for k in jvm)==23
assert sum('DriveConnectionRegressionTest#' in k or 'NativeDriveSignInTest#' in k for k in jvm)==48
repair=json.loads((root/'jvm/reports/reports/e11a03-repair/repair-summary.json').read_text())
assert repair['ok'] and repair['originalHashesMatched']==60 and repair['fullRestoreErrors']==0 and repair['mergeErrors']==0
installed=json.loads((root/'installed-103-identity.json').read_text())
assert installed['certificateSha1']=='cf438d2ed21d0fd5f8b0ab81bf518a5178a77385'
assert installed['apkSignatureVerified'] and installed['matchesRelease103AssetDigest']
ci=json.loads((root/'ci-only-identity.json').read_text())
assert ci['signatureVerified'] and not ci['matchesInstalledCertificate'] and not ci['productUpdateApproved']
auth=json.loads((root/'jvm/reports/reports/phase11d/auth.json').read_text())
assert auth['sdk']==36 and auth['realGoogleOAuthVerified'] is False
assert all(r['unchanged'] for r in json.loads((root/'diagnostic-protected-sources.json').read_text()))
print('Verified:1213 JVM,70 emulator,23 namespace,48 auth,60 original hashes; product update and real-device acceptance remain OPEN.')
