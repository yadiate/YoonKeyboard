param(
    [string]$ApkPath,
    [switch]$RemoveLegacy,
    [switch]$AllowLegacy,
    [switch]$SetIme
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $root "dist\YoonKeyboard-current-com.yadiate.yoonkeyboard-v0.2.42.apk"
}
$resolvedApk = Resolve-Path -LiteralPath $ApkPath

$adb = "C:\Users\yadia\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path -LiteralPath $adb)) {
    $adb = "adb"
}

$currentPackage = "com.yadiate.yoonkeyboard"
$legacyPackage = "com.example.eightwayime"

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    & $adb @AdbArgs
}

function Test-PackageInstalled {
    param([string]$PackageName)
    $output = & $adb shell pm path $PackageName 2>$null
    return $LASTEXITCODE -eq 0 -and ($output | Where-Object { $_ -match "^package:" })
}

$devices = & $adb devices |
    Select-Object -Skip 1 |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_ -match "\sdevice$" }

if (($devices | Measure-Object).Count -ne 1) {
    throw "Expected exactly one adb device. Connected devices:`n$(& $adb devices)"
}

if ((Test-PackageInstalled $legacyPackage) -and -not $AllowLegacy) {
    if (-not $RemoveLegacy) {
        throw "Legacy package $legacyPackage is installed. Re-run with -RemoveLegacy, or use -AllowLegacy only if you intentionally want both packages."
    }

    Write-Host "Removing legacy package $legacyPackage before installing current APK..."
    $uninstallOutput = & $adb uninstall $legacyPackage
    $uninstallText = ($uninstallOutput -join "`n")
    if ($uninstallText -notmatch "Success") {
        throw "Failed to uninstall $legacyPackage. adb output:`n$uninstallText"
    }
}

Write-Host "Installing $resolvedApk"
$installOutput = & $adb install -r $resolvedApk
$installText = ($installOutput -join "`n")
if ($installText -notmatch "Success") {
    throw "Install failed. adb output:`n$installText"
}

if ((Test-PackageInstalled $legacyPackage) -and -not $AllowLegacy) {
    throw "Legacy package $legacyPackage is still installed after install. Do not test until it is removed."
}

if (-not (Test-PackageInstalled $currentPackage)) {
    throw "Current package $currentPackage is not installed after install."
}

$imeList = (& $adb shell ime list -s) | ForEach-Object { $_.Trim() }
$currentIme = $imeList | Where-Object { $_ -like "$currentPackage/*" } | Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($currentIme)) {
    throw "Current IME was not found in adb ime list -s. Output:`n$($imeList -join "`n")"
}

if ($SetIme) {
    & $adb shell ime enable $currentIme | Out-Host
    & $adb shell ime set $currentIme | Out-Host
}

$defaultIme = (& $adb shell settings get secure default_input_method).Trim()
if ($defaultIme -notlike "$currentPackage/*") {
    Write-Warning "Default IME is '$defaultIme', not '$currentIme'. Use -SetIme or select the current YoonKeyboard before testing."
}

Write-Host ""
Write-Host "Installed current package:"
& $adb shell dumpsys package $currentPackage |
    Select-String "versionCode|versionName" |
    Select-Object -First 4 |
    ForEach-Object { $_.Line.Trim() }

Write-Host ""
Write-Host "Available YoonKeyboard IMEs:"
$imeList | Where-Object { $_ -match "yoonkeyboard|eightwayime" }
