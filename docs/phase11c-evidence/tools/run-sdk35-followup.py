from pathlib import Path
import subprocess, os, re
repo=Path('/workspace/Steuer-Asistend-APP')
cache=Path('/workspace/.setup/gradle-home/caches')
out=Path('/tmp/phase11c-direct'); out.mkdir(exist_ok=True)
android=next(Path('/workspace/.setup/java-home/.m2').rglob('android-all-instrumented-15-*.jar'))
modules=list((cache/'modules-2/files-2.1').rglob('*.jar')) + list(Path('/workspace/.setup/tools/gradle-9.3.1/lib').glob('*.jar'))
latest={}
for p in modules:
 if 'files-2.1' not in str(p): continue
 relative=p.relative_to(cache/'modules-2/files-2.1'); key=tuple(relative.parts[:2]); version=relative.parts[2]
 score=tuple(int(x) for x in re.findall(r'\d+',version))
 if key not in latest or score>latest[key][0]: latest[key]=(score,p)
modules=[p for _,p in latest.values()]
aars=list((cache/'9.3.1/transforms').rglob('classes.jar'))
built=[repo/'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes', repo/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes', repo/'app/build/intermediates/built_in_kotlinc/debugUnitTest/compileDebugUnitTestKotlin/classes',repo/'app/build/intermediates/compile_and_runtime_r_class_jar/debug/processDebugResources/R.jar']
cp=':'.join(str(p) for p in [android]+built+modules+aars)
baseline=out/'fixed-classes'
main_sources=[repo/'app/src/main/java/com/example'/p for p in ['ui/ReceiptViewModel.kt','data/DrivePersistenceRepository.kt','api/GoogleDriveClient.kt','api/DriveAccessFailure.kt','data/DriveConnectionFailure.kt','ui/ReceiptAppUi.kt']]
source=(repo/'app/src/test/java/com/example/ui/DriveConnectionRegressionTest.kt').read_text().replace('sdk = [36]', 'sdk = [35]')
(out/'DriveConnectionRegressionTest.kt').write_text(source)
java='/usr/lib/jvm/java-21-openjdk-amd64/bin/java'
compiler=[p for p in modules if ('kotlin-compiler-embeddable/2.2.10/' in str(p) or 'kotlin-stdlib/' in str(p) or 'kotlin-script-runtime/' in str(p) or 'kotlin-reflect/' in str(p) or 'trove' in str(p) or 'annotations/' in str(p) or 'kotlinx-coroutines-core-jvm/' in str(p))]

resources=repo/'app/build/intermediates/unit_test_config_directory/debugUnitTest/generateDebugUnitTestConfig/out'
cp=':'.join([str(out/'classes'),str(baseline),str(repo/'app/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes'),str(resources),str(repo/'app/src/test/resources'),cp])
junitcp=':'.join(str(p) for p in modules if '/junit/junit/' in str(p) or '/org.hamcrest/' in str(p))
if subprocess.run(['/workspace/.setup/tools/jdk17/bin/javac','-cp',junitcp,'-d',str(out/'runner'),'/tmp/DirectJunitEvidence.java']).returncode: raise SystemExit(2)
cp=str(out/'runner')+':'+cp
classes=['com.example.ui.DriveConnectionRegressionTest','com.example.data.Phase10BPortfolioAcceptanceTest','com.example.ui.AppSettingsComposeTest','com.example.comparison.ReceiptSyncPreservationE2ETest','com.example.data.ReceiptDocumentSyncStateTest','com.example.data.ReceiptDocumentIntegrityAuditTest','com.example.data.FilelessLogbookContractTest','com.example.data.ReceiptOriginalMetadataPreservationTest']
reports=repo/'docs/phase11c-evidence/direct-sdk35'; reports.mkdir(parents=True,exist_ok=True)
failed=[]
for cls in classes:
 log=reports/(cls+'.log')
 cmd=[java,'-Xmx3g','--add-opens=java.base/java.io=ALL-UNNAMED','-Drobolectric.offline=true','-Drobolectric.dependency.dir='+str(android.parent),'-Duser.home=/workspace/.setup/java-home','-cp',cp,'DirectJunitEvidence',cls,str(reports/(cls+'.xml'))]
 with log.open('w') as f: result=subprocess.run(cmd,cwd=repo/'app',stdout=f,stderr=subprocess.STDOUT)
 print(cls, result.returncode,flush=True)
 if result.returncode: failed.append(cls)
raise SystemExit(bool(failed))
