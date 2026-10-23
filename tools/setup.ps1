$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$apktoolPath = Join-Path $PSScriptRoot 'apktool.jar'
$expectedApktool = 'dbf930b076c6b9be08d57c449cacefc3bdd6b71ebd59b3066fc0e1f5b14f9423'
if (-not (Test-Path -LiteralPath $apktoolPath)) {
    Invoke-WebRequest -Uri 'https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar' -OutFile $apktoolPath
}
if ((Get-FileHash -LiteralPath $apktoolPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedApktool) {
    throw 'Unexpected Apktool checksum; refusing to execute the downloaded tool.'
}
Write-Output 'Apktool 3.0.3 verified. Place the original APK in baseline/spotilol-1.2.4-build23.apk and run python tools/build.py.'
