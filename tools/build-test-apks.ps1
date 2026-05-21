param(
    [switch]$SkipSim
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
Set-Location $root

$javaHome = "C:\Users\yadia\AppData\Local\Programs\Rider\jbr"
if (Test-Path -LiteralPath $javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}

$gradle = Join-Path $root "gradlew.bat"
$dist = Join-Path $root "dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$currentApk = Join-Path $dist "YoonKeyboard-current-com.yadiate.yoonkeyboard-v0.2.34.apk"
$legacyReleaseApk = Join-Path $dist "YoonKeyboard-legacy-com.example.eightwayime-v0.2.34-legacyfix-release.apk"
$legacyDebugApk = Join-Path $dist "YoonKeyboard-legacy-com.example.eightwayime-v0.2.34-legacyfix-debug.apk"

if (-not $SkipSim) {
    & powershell -ExecutionPolicy Bypass -File (Join-Path $root "tools\run-ime-sim.ps1")
    if ($LASTEXITCODE -ne 0) {
        throw "IME simulation failed."
    }
}

& $gradle clean assembleRelease
if ($LASTEXITCODE -ne 0) {
    throw "Current release build failed."
}
Copy-Item -LiteralPath (Join-Path $root "app\build\outputs\apk\release\app-release.apk") `
    -Destination $currentApk -Force

$legacyProps = @(
    "-PkeyboardApplicationId=com.example.eightwayime",
    "-PkeyboardVersionCode=100033",
    "-PkeyboardVersionName=0.2.34-legacyfix",
    "-PkeyboardAppLabel=YoonKeyboard LEGACY REMOVE"
)

& $gradle clean assembleRelease @legacyProps
if ($LASTEXITCODE -ne 0) {
    throw "Legacy release build failed."
}
Copy-Item -LiteralPath (Join-Path $root "app\build\outputs\apk\release\app-release.apk") `
    -Destination $legacyReleaseApk -Force

& $gradle clean assembleDebug @legacyProps
if ($LASTEXITCODE -ne 0) {
    throw "Legacy debug build failed."
}
Copy-Item -LiteralPath (Join-Path $root "app\build\outputs\apk\debug\app-debug.apk") `
    -Destination $legacyDebugApk -Force

& $gradle clean assembleRelease
if ($LASTEXITCODE -ne 0) {
    throw "Final current release build failed."
}
Copy-Item -LiteralPath (Join-Path $root "app\build\outputs\apk\release\app-release.apk") `
    -Destination $currentApk -Force

Write-Host ""
Write-Host "Built APKs:"
Get-FileHash -Algorithm SHA256 $currentApk, $legacyReleaseApk, $legacyDebugApk |
    Format-Table -AutoSize
