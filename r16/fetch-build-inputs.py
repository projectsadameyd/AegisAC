#!/usr/bin/env python3
"""Fetch the five pinned build inputs omitted from the Git checkout."""
import hashlib, json, os, pathlib, shutil, tempfile, urllib.parse, urllib.request, zipfile

ROOT=pathlib.Path(__file__).resolve().parent
INPUTS=['AegisAC-R15-TimerSafety.jar','source/lib/paper-api.jar','source/lib/paper-api-dependencies.jar','original/lib/linkage-api-dependencies.jar','original/lib/grim-corresponding-source.tar.gz']
def main():
    if all((ROOT/p).is_file() for p in INPUTS):
        print('Build inputs already present; build.py verifies the baseline pin.')
        return
    manifest=json.loads((ROOT/'build-inputs.json').read_text())
    assert set(manifest['inputs'])==set(INPUTS),'Unexpected input manifest'
    url=manifest['source_asset_url']; parsed=urllib.parse.urlsplit(url)
    assert parsed.scheme=='https' and parsed.netloc=='github.com' and parsed.path.startswith('/projectsadameyd/AegisAC/releases/download/'),'Unexpected source asset origin'
    with tempfile.TemporaryDirectory(prefix='aegis-inputs-') as tmp:
        archive=pathlib.Path(tmp)/'source.zip'
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'AegisAC-source-build/4.2'}),timeout=120) as response,archive.open('wb') as out:
            shutil.copyfileobj(response,out)
        assert hashlib.sha256(archive.read_bytes()).hexdigest()==manifest['source_asset_sha256'],'Source archive checksum mismatch'
        with zipfile.ZipFile(archive) as z:
            assert z.testzip() is None,'Corrupt source archive'
            for name in INPUTS:
                data=z.read(manifest['source_root']+'/'+name)
                assert hashlib.sha256(data).hexdigest()==manifest['inputs'][name],'Input checksum mismatch: '+name
                dest=ROOT/name;dest.parent.mkdir(parents=True,exist_ok=True)
                temporary=dest.with_suffix(dest.suffix+'.tmp');temporary.write_bytes(data);os.replace(temporary,dest)
    print('Verified build inputs ready.')
if __name__=='__main__':main()
