param(
    [switch]$SkipLint,
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$workRoot = $repoRoot

# Gradle's Windows test worker can corrupt a non-ASCII working-directory path even though
# compilation succeeds. Run through a stable ASCII junction when the checkout path needs it.
if ($repoRoot -match '[^\x00-\x7F]') {
    $junction = Join-Path $env:LOCALAPPDATA 'Temp\kanxi-gradle-workspace'
    if (Test-Path -LiteralPath $junction) {
        $item = Get-Item -LiteralPath $junction -Force
        $targets = @($item.Target)
        if ($item.LinkType -ne 'Junction' -or $targets -notcontains $repoRoot) {
            throw "The verification junction already exists but points elsewhere: $junction"
        }
    } else {
        New-Item -ItemType Junction -Path $junction -Target $repoRoot | Out-Null
    }
    $workRoot = $junction
}

function Get-JavaMajorVersion([string]$javaHome) {
    if (-not $javaHome) { return 0 }
    $releaseFile = Join-Path $javaHome 'release'
    if (-not (Test-Path -LiteralPath $releaseFile)) { return 0 }
    $versionLine = Get-Content -LiteralPath $releaseFile |
        Where-Object { $_ -match '^JAVA_VERSION=' } |
        Select-Object -First 1
    if (-not $versionLine -or $versionLine -notmatch '"(?:(?:1\.)?(\d+))') { return 0 }
    return [int]$Matches[1]
}

if ((Get-JavaMajorVersion $env:JAVA_HOME) -lt 17) {
    $javaCandidates = @(
        'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot',
        'C:\Program Files\Android\Android Studio\jbr'
    )
    $env:JAVA_HOME = $javaCandidates |
        Where-Object { (Get-JavaMajorVersion $_) -ge 17 } |
        Select-Object -First 1
}
if ((Get-JavaMajorVersion $env:JAVA_HOME) -lt 17) {
    throw 'JDK 17 was not found. Install Microsoft OpenJDK 17 or Android Studio first.'
}

if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
if (-not (Test-Path (Join-Path $env:ANDROID_HOME 'platforms\android-36'))) {
    throw 'Android SDK platform 36 was not found.'
}

$tasks = @()
if (-not $SkipTests) { $tasks += ':app:testDebugUnitTest' }
if (-not $SkipLint) { $tasks += ':app:lintDebug' }
$tasks += ':app:assembleDebug'

Push-Location $workRoot
try {
    & '.\gradlew.bat' @tasks '--no-daemon' '--max-workers=1'
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

$apk = Join-Path $repoRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apk)) {
    throw "Gradle completed but the APK was not found: $apk"
}
Write-Host "Verification passed. APK: $apk"
