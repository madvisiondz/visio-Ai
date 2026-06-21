# Inspect Photoshop PSD templates → JSON specs + optional previews.
# Drop .psd files in templates/psd-inbox/ then run:
#   .\scripts\INSPECT-PSD.ps1
#   .\scripts\INSPECT-PSD.ps1 -File "C:\path\to\template.psd"
#   .\scripts\INSPECT-PSD.ps1 -Preview -Layers

param(
    [string]$File = "",
    [switch]$Preview,
    [switch]$Layers,
    [switch]$NodeToo
)

$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root

Write-Host "==> Visio Ai PSD inspector" -ForegroundColor Cyan

# Python deps
$req = Join-Path $Root "tools\psd\requirements.txt"
if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Error "Python not found. Install Python 3.10+ and re-run."
}
python -m pip install -q -r $req

$pyArgs = @("tools\psd\inspect_psd.py")
if ($File) { $pyArgs += $File } else { $pyArgs += "--all" }
if ($Preview) { $pyArgs += "--preview" }
if ($Layers) { $pyArgs += "--layers" }

python @pyArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($NodeToo) {
    $nodeDir = Join-Path $Root "tools\psd-node"
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
        Write-Warning "Node.js not found — skipping ag-psd cross-check."
    } else {
        Push-Location $nodeDir
        if (-not (Test-Path "node_modules")) { npm install --silent }
        if ($File) {
            node inspect.mjs (Resolve-Path $File).Path
        } else {
            node inspect.mjs --all
        }
        Pop-Location
    }
}

Write-Host "`nOutputs:" -ForegroundColor Green
Write-Host "  JSON specs  → templates\psd-specs\"
Write-Host "  Previews    → templates\psd-previews\  (with -Preview)"
Write-Host "`nShare the .psd-spec.json files when ready to clone a template in the app."
