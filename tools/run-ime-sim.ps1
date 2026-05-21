$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $scriptDir
$javaHome = $env:JAVA_HOME
if ([string]::IsNullOrWhiteSpace($javaHome)) {
    $javaHome = "C:\Users\yadia\AppData\Local\Programs\Rider\jbr"
}

$javac = Join-Path $javaHome "bin\javac.exe"
$java = Join-Path $javaHome "bin\java.exe"
$androidHome = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($androidHome)) {
    $androidHome = "C:\Users\yadia\AppData\Local\Android\Sdk"
}
$androidJar = Join-Path $androidHome "platforms\android-35\android.jar"
$buildDir = Join-Path $root "build\ime-sim"
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null

$sources = @(
    (Join-Path $root "tools\ime-sim\android\util\DisplayMetrics.java"),
    (Join-Path $root "app\src\main\java\com\yadiate\yoonkeyboard\hangul\CalibrationPlanner.java"),
    (Join-Path $root "app\src\main\java\com\yadiate\yoonkeyboard\hangul\Consonant.java"),
    (Join-Path $root "app\src\main\java\com\yadiate\yoonkeyboard\hangul\GestureCalibration.java"),
    (Join-Path $root "app\src\main\java\com\yadiate\yoonkeyboard\hangul\GestureVowelMapper.java"),
    (Join-Path $root "app\src\main\java\com\yadiate\yoonkeyboard\hangul\HangulComposer.java"),
    (Join-Path $root "tools\ime-sim\ImeSimulationRunner.java")
)

& $javac -encoding UTF-8 -cp $androidJar -d $buildDir $sources
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
& $java -cp "$buildDir;$androidJar" ImeSimulationRunner @args
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
