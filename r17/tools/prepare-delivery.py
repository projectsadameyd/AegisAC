"""Package the validated preview and prepare an atomic source commit; never includes local credentials."""
import hashlib,json,pathlib,subprocess,zipfile

ROOT=pathlib.Path(__file__).resolve().parents[2]
R17=ROOT/'r17'
BUILD=R17/'build'

def main():
    jar=BUILD/'AegisAC-R17-NativeGuard-preview1-1.21.11.jar'
    expected=(BUILD/'SHA256SUMS.txt').read_text().split()[0]
    assert hashlib.sha256(jar.read_bytes()).hexdigest()==expected,'Artifact checksum changed'
    regression=json.loads((BUILD/'regression-results.json').read_text())
    assert len(regression['suites'])==7 and all(s['exit']==0 for s in regression['suites']),'Incomplete regression result'
    legitimate=json.loads((R17/'results/legitimate-client.json').read_text())
    remote=json.loads((R17/'results/remote-client.json').read_text())
    assert legitimate['kicked'] is None and not legitimate['errors'] and legitimate['obfuscatedInteriorSamples']>0,'Client smoke evidence missing'
    assert remote.get('guardKickVerified') and not remote['errors'],'Branded kick evidence missing'
    assert expected in (R17/'QA_REPORT.md').read_text(),'QA report references a different artifact'
    delivery=BUILD/'delivery';delivery.mkdir(exist_ok=True)
    archive=delivery/'AegisAC-R17-preview1.zip'
    entries={jar.name:jar.read_bytes(),'SHA256SUMS.txt':(BUILD/'SHA256SUMS.txt').read_bytes(),
             'README.md':(R17/'README.md').read_bytes(),'QA_REPORT.md':(R17/'QA_REPORT.md').read_bytes(),
             'R17-THIRD_PARTY_NOTICES.md':(R17/'THIRD_PARTY_NOTICES.md').read_bytes(),
             'r17-guard.example.yml':(R17/'src/main/resources/r17-guard.yml').read_bytes()}
    with zipfile.ZipFile(jar) as z:
        for name in ['LICENSE-AegisAC-GPL-3.0.txt','LICENSE-DonutAntiCheat-MIT.txt','THIRD_PARTY_NOTICES.md']:
            entries[name]=z.read(name)
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,content in sorted(entries.items()):
            entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;z.writestr(entry,content)
    with zipfile.ZipFile(archive) as z:assert z.testzip() is None
    paths=subprocess.check_output(['git','ls-files','--others','--exclude-standard','--','r17'],cwd=ROOT,text=True).splitlines()
    assert paths and all(p.startswith('r17/') for p in paths)
    assert not any('/inputs/' in p or '/node_modules/' in p or '/server-test/' in p or '/python-runtime/' in p or '/library-helpers/' in p for p in paths)
    upserts=[{'path':name,'content':(ROOT/name).read_text(encoding='utf-8-sig'),'encoding':'utf-8'} for name in paths]
    payload={'owner':'projectsadameyd','repo':'AegisAC','branch':'development/r17-native-guard','base_branch':'main',
             'message':'Add R17 native-stack preview with remote interaction guard and anti-Xray setup',
             'upserts':upserts,'force':False,'max_retries':0}
    (BUILD/'source-commit.json').write_text(json.dumps(payload,ensure_ascii=True),encoding='utf-8')
    summary={'archive':str(archive),'archive_bytes':archive.stat().st_size,'archive_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),
             'jar_sha256':expected,'source_files':len(paths),'source_payload_bytes':(BUILD/'source-commit.json').stat().st_size}
    (BUILD/'delivery-summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
    print(json.dumps(summary))

if __name__=='__main__':main()
