# Build VisioPRO-media.zip for sideloading preset product images (~100 MB).
# Run from repo root:  .\BUILD-VISIOPRO-MEDIA.ps1
# Install on phone: Settings -> Install VisioPRO images

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$PackDir = Join-Path $Root "packs\visiopro-media"
$Dest = Join-Path $Root "VisioPRO-media.zip"

$products = Join-Path $PackDir "visiopro\fv_print\products"
if (-not (Test-Path $products)) {
    Write-Error "Missing $products - run asset move or restore from git."
}

if (Test-Path $Dest) { Remove-Item $Dest -Force }
Compress-Archive -Path (Join-Path $PackDir "*") -DestinationPath $Dest -CompressionLevel Optimal
$mb = [math]::Round((Get-Item $Dest).Length / 1MB, 1)
Write-Host ("Created {0} ({1} MB)" -f $Dest, $mb)
Write-Host "Copy to phone, then Visio Ai -> Settings -> Install VisioPRO images"
