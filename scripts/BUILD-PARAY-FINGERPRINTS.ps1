# Option A — bulk PARAY CLIP fingerprints on this PC ($0).
# Requires: Python 3 + scripts/requirements-paray.txt

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

Write-Host "Installing PARAY script dependencies..."
python -m pip install -r "$PSScriptRoot\requirements-paray.txt" -q

Write-Host "Building PARAY fingerprint index from product_images/ ..."
python "$PSScriptRoot\build_paray_embeddings.py" @args

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$Out = Join-Path $Root "exports\paray\paray_fingerprint_index.json"
if (Test-Path $Out) {
    $mb = [math]::Round((Get-Item $Out).Length / 1MB, 1)
    Write-Host ""
    Write-Host "Done. Index: $Out ($mb MB)"
    Write-Host "On phone: Settings -> Import PARAY fingerprints"
}
