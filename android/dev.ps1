param(
    [ValidateSet("build", "install", "run", "emulator", "all")]
    [string]$Action = "all"
)

# Oasis AI — build & run from Cursor terminal (no Android Studio needed)
$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

Set-Location $PSScriptRoot

function Start-EmulatorIfNeeded {
    $adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
    $devices = & $adb devices 2>$null | Select-String "device$"
    if (-not $devices) {
        Write-Host "Starting emulator Pixel_7..."
        Start-Process (Join-Path $env:ANDROID_HOME "emulator\emulator.exe") -ArgumentList "-avd", "Pixel_7"
        Write-Host "Waiting for emulator (up to 90s)..."
        & $adb wait-for-device
        Start-Sleep -Seconds 15
    }
}

function Copy-DebugApkToRoot {
    $src = Join-Path $env:LOCALAPPDATA "OasisAI-android-build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $src)) {
        $src = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
    }
    $dest = Join-Path (Split-Path $PSScriptRoot -Parent) "OasisAI-debug.apk"
    if (-not (Test-Path $src)) {
        Write-Error "APK not found. Run build first: .\dev.ps1 build"
    }
    Copy-Item $src $dest -Force
    $mb = [math]::Round((Get-Item $dest).Length / 1MB, 1)
    Write-Host "Copied to: $dest ($mb MB) — install THIS on your phone, not an old app-debug.apk from another folder."
}

switch ($Action) {
    "build" {
        .\gradlew.bat assembleDebug
        if ($LASTEXITCODE -eq 0) { Copy-DebugApkToRoot }
    }
    "install" { .\gradlew.bat installDebug }
    "emulator" { Start-EmulatorIfNeeded }
    "run" {
        Start-EmulatorIfNeeded
        .\gradlew.bat installDebug
        & (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe") shell am start -n com.oasismall.oasisai/.MainActivity
    }
    "all" {
        Start-EmulatorIfNeeded
        .\gradlew.bat installDebug
        & (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe") shell am start -n com.oasismall.oasisai/.MainActivity
    }
}
