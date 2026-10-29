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
cmd=[java,'-Xmx3g','-cp',':'.join(map(str,compiler)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-module-name','app','-jvm-target','11','-Xplugin='+str(next((cache/'modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compose-compiler-plugin-embeddable/2.2.10').rglob('*.jar'))),'-Xfriend-paths='+str(built[0]),'-classpath',str(next(Path('/workspace/.setup/android-sdk/platforms').glob('android-36*/android.jar')))+':'+cp,'-d',str(baseline)]+list(map(str,main_sources))
if subprocess.run(cmd).returncode: raise SystemExit(2)
cp=str(baseline)+':'+cp
_=subprocess.run([java,'-Xmx2g','-cp',':'.join(map(str,compiler)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','11','-classpath',cp,'-d',str(out/'classes'),str(out/'DriveConnectionRegressionTest.kt')])
if _.returncode: raise SystemExit(_.returncode)
resources=repo/'app/build/intermediates/unit_test_config_directory/debugUnitTest/generateDebugUnitTestConfig/out'
run_cp=':'.join([str(out/'classes'),str(resources),str(repo/'app/src/test/resources'),cp])
cmd=[java,'-Xmx3g','-Drobolectric.offline=true','-Drobolectric.dependency.dir='+str(android.parent),'-Duser.home=/workspace/.setup/java-home','-cp',run_cp,'org.junit.runner.JUnitCore','com.example.ui.DriveConnectionRegressionTest']
result=subprocess.run(cmd,cwd=repo/'app'); raise SystemExit(result.returncode)
