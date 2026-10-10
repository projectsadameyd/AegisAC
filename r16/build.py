#!/usr/bin/env python3
from pathlib import Path
import argparse,copy,json,zipfile,hashlib,re,subprocess,struct,os,shutil,tempfile
ROOT=Path(__file__).resolve().parent
VERSIONS=['1.8.8','1.12.2','1.16.5','1.17.1','1.18.2','1.19.2','1.19.4','1.20.1','1.20.2','1.20.4','1.20.6','1.21.1','1.21.3','1.21.4','1.21.5','1.21.8','1.21.10','1.21.11','26.1.2','26.2','26.3']
BASE=ROOT/'AegisAC-R15-TimerSafety.jar'
PIN='e9c83d7dcb88c291abcfca2bee477cc20a98dcd011f42c9e4c1bc26232495f3f'
def run(command):subprocess.run(list(map(str,command)),check=True)
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--jdk',type=Path);parser.add_argument('--output',type=Path,default=ROOT/'build');a=parser.parse_args()
 jdk=a.jdk or Path(os.environ.get('JAVA_HOME',''))
 if not (jdk/'bin/javac').is_file():
  installed=shutil.which('javac')
  if not installed:raise SystemExit('Install JDK 21 or newer and pass --jdk /path/to/jdk')
  jdk=Path(installed).resolve().parent.parent
 assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN
 out=a.output.resolve();out.mkdir(parents=True,exist_ok=True);results=[]
 cp=os.pathsep.join(map(str,[BASE,ROOT/'source/lib/paper-api.jar',ROOT/'source/lib/paper-api-dependencies.jar',ROOT/'original/lib/linkage-api-dependencies.jar']))
 with tempfile.TemporaryDirectory(prefix='aegis-build-') as tmp:
  tmp=Path(tmp);classes=tmp/'classes';classes.mkdir()
  run([jdk/'bin/javac','--release','17','-proc:none','-cp',cp,'-d',classes,*sorted((ROOT/'port-source/main/java').rglob('*.java'))])
  own_classes={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')}
  assert not any('/command/' in n for n in own_classes if n.startswith('net/donutac/'))
  sensor=(ROOT/'port-source/main/java/dev/aegisac/SensorPlugin.java').read_text();variants={}
  for profile in ['legacy','biome','meta','lifecycle']:
   text=sensor
   if profile!='lifecycle':
    text=re.sub(r'^.*getLifecycleManager\(\).*$', '',text,flags=re.M);text=re.sub(r'^.*public String namespace\(\).*$', '',text,flags=re.M)
   if profile in ['legacy','biome']:text=re.sub(r'^.*getPluginMeta\(\).*$', '',text,flags=re.M)
   if profile=='legacy':text=re.sub(r'^.*getDefaultBiomeProvider\(.*$', '',text,flags=re.M)
   file=tmp/profile/'src/dev/aegisac/SensorPlugin.java';file.parent.mkdir(parents=True);file.write_text(text);dest=tmp/profile/'classes';dest.mkdir()
   run([jdk/'bin/javac','--release','17','-proc:none','-cp',str(classes)+os.pathsep+cp,'-d',dest,file])
   variants[profile]=(dest/'dev/aegisac/SensorPlugin.class').read_bytes()
  with zipfile.ZipFile(BASE) as base:
   native={n:base.read(n) for n in base.namelist() if n.startswith('ac/grim/') and n.endswith('.class') and n!='ac/grim/grimac/manager/init/start/CommandRegister.class'};assert len(native)==5930
   for v in VERSIONS:
    num=tuple(map(int,v.split('.')));profile='legacy' if num<(1,17) else 'biome' if num<(1,19,4) else 'meta' if num<(1,20,6) else 'lifecycle'
    java=25 if v.startswith('26.') else 21 if num>=(1,20,6) else 17
    api=None if num<(1,13) else '.'.join(v.split('.')[:2]) if num<(1,20,5) else v
    text=(ROOT/'port-source/main/resources/plugin.yml').read_text();text=re.sub(r'^version:.*$',f'version: 4.2.0-r16-vpnguard1-mc{v}',text,flags=re.M)
    text=re.sub(r'^api-version:.*\n',("api-version: '"+api+"'\n") if api else '',text,flags=re.M)
    metadata={'minecraft_version':v,'runtime_java_minimum':java,'aegis_build':'4.2.0-r16-vpnguard1','sensor_profile':profile,'grim_engine':'2.3.74-abb95b6','timer_limit_auto_sanctions':False,'validation':'rebuilt preview; see QA_REPORT.md'}
    own={p.relative_to(ROOT/'port-source/main/resources').as_posix():p.read_bytes() for p in (ROOT/'port-source/main/resources').rglob('*') if p.is_file()};own.update(own_classes);own.update({'dev/aegisac/SensorPlugin.class':variants[profile],'native-punishments.yml':(ROOT/'port-source/main/resources/native-punishments.yml').read_bytes(),'plugin.yml':text.encode(),'aegis-compatibility.json':json.dumps(metadata,indent=2).encode()})
    jar=out/v/f'AegisAC-MC-{v}.jar';jar.parent.mkdir(parents=True,exist_ok=True);temporary=jar.with_suffix('.tmp')
    with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED) as z:
     for item in base.infolist():
      if item.filename.startswith('net/donutac/anticheat/command/'):continue
      z.writestr(copy.copy(item),own.pop(item.filename,base.read(item.filename)))
     for n,data in own.items():z.writestr(n,data)
    with zipfile.ZipFile(temporary) as z:
     assert z.testzip() is None
     assert all(z.read(n)==data for n,data in native.items())
     assert all(struct.unpack('>H',z.read(n)[6:8])[0]<=61 for n in z.namelist() if n.endswith('.class') and n.startswith(('dev/aegisac/','net/donutac/')))
     assert not re.search(rb'https://(?:discord(?:app)?\.com)/api/webhooks/[^\s"\x27]{40,}',z.read('donut-default.yml'))
    os.replace(temporary,jar)
    metadata.update({'jar':str(jar.relative_to(out)),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'size_bytes':jar.stat().st_size,'unchanged_native_grim_classes':len(native)})
    results.append(metadata);print('BUILT',v,profile,flush=True)
  testdir=tmp/'tests';testdir.mkdir();testcp=os.pathsep.join(map(str,[out/'1.21.11/AegisAC-MC-1.21.11.jar',ROOT/'source/lib/paper-api.jar',ROOT/'source/lib/paper-api-dependencies.jar']))
  run([jdk/'bin/javac','--release','21','-proc:none','-cp',testcp,'-d',testdir,*sorted((ROOT/'port-source/test/java').rglob('*.java'))])
  regressions=[]
  for name in ['NativeActionRegressionTest','DispatcherHistoryRegressionTest','TimerSafetyRegressionTest','VpnSourceRecoveryRegressionTest']:
   extra=[ROOT/'original-punishments.yml',ROOT/'port-source/main/resources/native-punishments.yml'] if name=='TimerSafetyRegressionTest' else []
   p=subprocess.run(list(map(str,[jdk/'bin/java','-Xmx256m','-XX:ActiveProcessorCount=2','-Xverify:all','-cp',str(testdir)+os.pathsep+testcp,'dev.aegisac.'+name,*extra])),capture_output=True,text=True)
   print(name,p.returncode,p.stdout,p.stderr,flush=True);p.check_returncode();regressions.append({'suite':name,'exit':p.returncode,'output':p.stdout})
  (out/'regression-results.json').write_text(json.dumps(regressions,indent=2))
 (out/'build-manifest.json').write_text(json.dumps(results,indent=2));print('ALL 21 BUILDS AND ALL REGRESSION SUITES PASS',flush=True)
if __name__=='__main__':main()
