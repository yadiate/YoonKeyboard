param(
    [switch]$SkipSim
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
Set-Location $root

function Resolve-CompatibleJavaHome {
    $candidates = @(
        $env:JAVA_HOME,
        $env:JAVA17_HOME,
        $env:JAVA_HOME_17_X64
    )
    $pathJavaHomes = & where.exe java 2>$null |
        ForEach-Object { Split-Path -Parent (Split-Path -Parent $_) }
    $candidates += $pathJavaHomes
    $codexJdks = Join-Path $env:LOCALAPPDATA "Codex\jdks"
    if (Test-Path -LiteralPath $codexJdks) {
        $candidates += Get-ChildItem -LiteralPath $codexJdks -Recurse -Filter java.exe -ErrorAction SilentlyContinue |
            ForEach-Object { Split-Path -Parent (Split-Path -Parent $_.FullName) }
    }
    $candidates += "C:\Users\yadia\AppData\Local\Programs\Rider\jbr"

    foreach ($candidate in ($candidates | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)) {
        $java = Join-Path $candidate "bin\java.exe"
        if (-not (Test-Path -LiteralPath $java)) {
            continue
        }
        $versionLine = (& cmd.exe /c "`"$java`" -version 2>&1" | Select-Object -First 1)
        if ($versionLine -match '"(\d+)') {
            $major = [int]$Matches[1]
            if ($major -ge 17 -and $major -le 21) {
                return $candidate
            }
        }
    }
    return $null
}

$javaHome = Resolve-CompatibleJavaHome
if ([string]::IsNullOrWhiteSpace($javaHome)) {
    throw "Could not find a compatible JDK 17-21 for Gradle."
}
$env:JAVA_HOME = $javaHome
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

$gradle = Join-Path $root "gradlew.bat"
$dist = Join-Path $root "dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$currentApk = Join-Path $dist "YoonKeyboard-current-com.yadiate.yoonkeyboard-v0.2.42.apk"
$legacyReleaseApk = Join-Path $dist "YoonKeyboard-legacy-com.example.eightwayime-v0.2.42-legacyfix-release.apk"
$legacyDebugApk = Join-Path $dist "YoonKeyboard-legacy-com.example.eightwayime-v0.2.42-legacyfix-debug.apk"

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
    "-PkeyboardVersionCode=100034",
    "-PkeyboardVersionName=0.2.42-legacyfix",
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
