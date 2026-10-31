from pathlib import Path
import hashlib,json,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent
for name,digest in json.loads((root/'manifest.json').read_text()).items():
 p=root/name
 assert p.is_file() and hashlib.sha256(p.read_bytes()).hexdigest()==digest,name

def methods(kind):
 result={}
 for path in (root/kind/'xml').rglob('*.xml'):
  for case in ET.parse(path).getroot().iter('testcase'):
   name=case.get('classname','')+'#'+case.get('name','')
   if not name.split('#')[0] or name.startswith('Gradle Test'):continue
   assert all(case.find(t) is None for t in ['failure','error','skipped']),name
   result[name]=True
 return result
jvm=methods('jvm'); native=methods('emulator')
assert len(jvm)==1213,len(jvm)
assert len(native)==70,len(native)
assert sum('ProductionDriveNamespaceTest#' in k for k in jvm)==23
assert sum('DriveConnectionRegressionTest#' in k or 'NativeDriveSignInTest#' in k for k in jvm)==48
assert 'com.example.e2e.Phase6BFontScale15EmulatorTest#fullScreenMatrixPreservesWordsButtonsAndBackNavigation' in native
identity=json.loads((root/'identity.json').read_text())
assert identity['certificateSha1']=='1876fcd5f5d36526fc44f3ba37aa6cacabfbd98d'
assert identity['singleSigner'] and identity['apkV2Verified'] and identity['stableProductSigningUsed']
assert identity['package']=='com.aistudio.steuerassistent.vnhqkp' and identity['canUpdateVersion103'] is False
policy=json.loads((root/'jvm/reports/reports/product-signing.json').read_text())
assert policy['actualCertificateSha1']==identity['certificateSha1'] and policy['canUpdateVersion103'] is False
repair=json.loads((root/'jvm/reports/reports/e11a03-repair/repair-summary.json').read_text())
assert repair['ok'] and repair['originalHashesMatched']==60 and repair['fullRestoreErrors']==0 and repair['mergeErrors']==0
app=json.loads((root/'app-unchanged.json').read_text())
assert app['entireAppDirectoryUnchanged'] and app['baseAppTree']==app['newAppTree']
auth=json.loads((root/'jvm/reports/reports/phase11d/auth.json').read_text())
assert auth['sdk']==36 and auth['realGoogleOAuthVerified'] is False
print('Verified:1213 JVM,70 native,23 namespace,48 auth,60 original hashes; stable1876 signature verified, no direct legacy103 update; real OAuth/device test remains OPEN.')
