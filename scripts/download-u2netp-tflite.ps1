# Downloads U2NetP ONNX (~4.4 MB) into Android assets. Fully offline on phone after rebuild.
# Run from repo root: .\scripts\download-u2netp-tflite.ps1

$ErrorActionPreference = "Stop"
$destDir = Join-Path $PSScriptRoot "..\android\app\src\main\assets"
$destOnnx = Join-Path $destDir "u2netp.onnx"

New-Item -ItemType Directory -Force -Path $destDir | Out-Null

$url = "https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx"
Write-Host "Downloading $url ..."
curl.exe -L --retry 3 --max-time 300 -o $destOnnx $url
$size = (Get-Item $destOnnx).Length
if ($size -lt 4000000) {
    Write-Error "Download failed or too small ($size bytes). Expected ~4.5 MB."
}
Write-Host "SUCCESS: $destOnnx ($([math]::Round($size / 1MB, 2)) MB)"
Write-Host "Rebuild APK: .\BUILD-APK.ps1"
