param([switch]$Offline)
$ErrorActionPreference='Stop'
$r17Repo=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
Push-Location -LiteralPath $r17Repo
try {
    $r17Manifest=Get-Content -LiteralPath 'r17\companion\build-inputs.json' -Raw|ConvertFrom-Json
    foreach($r17Input in $r17Manifest.inputs){
        $r17Path=Join-Path $r17Repo ('r17\inputs\'+$r17Input.path)
        if(-not(Test-Path -LiteralPath $r17Path)){
            if($Offline){throw "Missing offline input: $($r17Input.path)"}
            New-Item -ItemType Directory -Path (Split-Path -Parent $r17Path) -Force|Out-Null
            Invoke-WebRequest -Uri $r17Input.url -OutFile $r17Path -UseBasicParsing -TimeoutSec 180
        }
        if((Get-FileHash -LiteralPath $r17Path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $r17Input.sha256){throw "Input checksum mismatch: $($r17Input.path)"}
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $r17Api=[IO.Compression.ZipFile]::OpenRead((Join-Path $r17Repo 'r17\inputs\fabric-api-0.141.6.jar'))
    try{foreach($r17Name in @('fabric-api-base-1.0.5+4ebb5c083e.jar','fabric-networking-api-v1-5.1.6+6b6d71a53e.jar')){
        $r17Entry=$r17Api.GetEntry('META-INF/jars/'+$r17Name)
        if($null -eq $r17Entry){throw "Missing Fabric API module: $r17Name"}
        $r17Source=$r17Entry.Open();$r17Dest=[IO.File]::Create((Join-Path $r17Repo ('r17\inputs\'+$r17Name)))
        try{$r17Source.CopyTo($r17Dest)}finally{$r17Dest.Dispose();$r17Source.Dispose()}
    }}finally{$r17Api.Dispose()}
    # Minecraft's published library URLs and SHA-1 hashes are development inputs only.
    if(-not $Offline){
        $r17Mc=Get-Content -LiteralPath 'r17\inputs\minecraft-client-metadata-1.21.11.json' -Raw|ConvertFrom-Json
        foreach($r17Library in $r17Mc.libraries){
            if(($r17Library.name -split ':').Length -gt 3){continue}
            $r17Artifact=$r17Library.downloads.artifact;if($null -eq $r17Artifact){continue}
            $r17Path=Join-Path $r17Repo ('r17\inputs\companion-libraries\'+$r17Artifact.path)
            if(-not(Test-Path -LiteralPath $r17Path)){New-Item -ItemType Directory -Path (Split-Path -Parent $r17Path) -Force|Out-Null;Invoke-WebRequest -Uri $r17Artifact.url -OutFile $r17Path -UseBasicParsing -TimeoutSec 90}
            if((Get-FileHash -LiteralPath $r17Path -Algorithm SHA1).Hash.ToLowerInvariant() -ne $r17Artifact.sha1){throw "Minecraft library checksum mismatch: $($r17Library.name)"}
        }
    }
    $r17ToolCp=((Get-ChildItem -LiteralPath 'r17\inputs\companion-tooling' -File -Filter '*.jar').FullName -join [IO.Path]::PathSeparator)
    & java -Xmx1024m -XX:ActiveProcessorCount=2 -cp $r17ToolCp 'r17\tools\BuildCompanion.java'
    if($LASTEXITCODE -ne 0){throw 'Companion build failed'}
    $r17Cp=@((Join-Path $r17Repo 'r17\build\AegisAC-Companion-R17-preview2-1.21.11.jar'),(Join-Path $r17Repo 'r17\build\companion-direct\minecraft-intermediary'),(Join-Path $r17Repo 'r17\inputs\fabric-loader-0.19.5.jar'),(Join-Path $r17Repo 'r17\inputs\fabric-api-base-1.0.5+4ebb5c083e.jar'),(Join-Path $r17Repo 'r17\inputs\fabric-networking-api-v1-5.1.6+6b6d71a53e.jar'))
    foreach($r17LibRoot in @('r17\inputs\companion-libraries','r17\server-test\libraries')){if(Test-Path -LiteralPath $r17LibRoot){$r17Cp+=(Get-ChildItem -LiteralPath $r17LibRoot -Recurse -File -Filter '*.jar').FullName}}
    & java -Xmx256m -XX:ActiveProcessorCount=2 -cp ($r17Cp -join [IO.Path]::PathSeparator) 'r17\tests\CompanionCodecSmoke.java'
    if($LASTEXITCODE -ne 0){throw 'Companion payload codec verification failed'}
}finally{Pop-Location}
