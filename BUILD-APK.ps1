# Build Oasis AI APK and copy to OasisAI-debug.apk (repo root).
# Run from repo root:  .\BUILD-APK.ps1

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$Android = Join-Path $Root "android"

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
if (-not (Test-Path $env:JAVA_HOME)) {
    Write-Error "Android Studio JBR not found. Install Android Studio or set JAVA_HOME."
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

Set-Location $Android
Write-Host "==> Building Oasis AI..."
.\gradlew.bat clean assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "BUILD FAILED. Try: Android Studio > Open android folder > Build > Build APK(s)"
    exit $LASTEXITCODE
}

$src = Join-Path $env:LOCALAPPDATA "OasisAI-android-build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $src)) {
    $src = Join-Path $Android "app\build\outputs\apk\debug\app-debug.apk"
}
$dest = Join-Path $Root "OasisAI-debug.apk"
Copy-Item $src $dest -Force
$info = Get-Item $dest
Write-Host ""
Write-Host "SUCCESS"
Write-Host "  Install: $dest"
Write-Host "  Size:    $([math]::Round($info.Length / 1MB, 1)) MB"
Write-Host "  Built:   $($info.LastWriteTime)"
$manifest = Join-Path $Android "app\build.gradle.kts"
if (Test-Path $manifest) {
    $vc = (Select-String -Path $manifest -Pattern 'versionCode\s*=\s*(\d+)' | Select-Object -First 1).Matches.Groups[1].Value
    $vn = (Select-String -Path $manifest -Pattern 'versionName\s*=\s*"([^"]+)"' | Select-Object -First 1).Matches.Groups[1].Value
    Write-Host "  Version: $vn (code $vc)"
}
Write-Host ""
