<#
.SYNOPSIS
    Build, install, and launch Photo Helper on an emulator or connected device.

.DESCRIPTION
    One command for the terminal-only loop: edit in an editor, run this, see the
    app. Boots an emulator if none is attached, writes the two gitignored files
    Gradle needs (local.properties, the debug keystore), runs the Gradle task,
    launches the app, and reports a crash if the process dies.

    Targets Windows PowerShell 5.1 - no pwsh required.

.EXAMPLE
    .\scripts\run-app.ps1
    .\scripts\run-app.ps1 -Clean
    .\scripts\run-app.ps1 -Task assembleDebug -NoLaunch
    .\scripts\run-app.ps1 -Avd Pixel_7
#>
[CmdletBinding()]
param(
    [string] $Avd,
    [string] $Task = "installDebug",
    [string] $SdkPath,
    [switch] $NoLaunch,
    [switch] $Clean
)

$ErrorActionPreference = "Stop"

function Write-Step($message) { Write-Host "==> $message" -ForegroundColor Cyan }
function Write-Ok($message)   { Write-Host "    $message" -ForegroundColor Green }
function Write-Warn($message) { Write-Host "    $message" -ForegroundColor Yellow }

# PowerShell 5.1 turns a native exe's stderr into ErrorRecords, which makes
# chatty-but-harmless adb subcommands look like failures. Swallow both streams.
function Invoke-Quiet {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try { & $args[0] @($args[1..($args.Length - 1)]) 2>&1 | Out-Null }
    finally { $ErrorActionPreference = $previous }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$appGradle = Join-Path $repoRoot "app\build.gradle.kts"
Write-Step "Project: $repoRoot"

# --- Android SDK -------------------------------------------------------------
if (-not $SdkPath) { $SdkPath = $env:ANDROID_HOME }
if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
if (-not $SdkPath) { $SdkPath = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not (Test-Path -LiteralPath $SdkPath -PathType Container)) {
    throw "Android SDK not found at '$SdkPath'. Pass -SdkPath or set ANDROID_HOME."
}
# The emulator resolves AVD system images through these, so export them.
$env:ANDROID_HOME = $SdkPath
$env:ANDROID_SDK_ROOT = $SdkPath

$adb = Join-Path $SdkPath "platform-tools\adb.exe"
$emulator = Join-Path $SdkPath "emulator\emulator.exe"
if (-not (Test-Path -LiteralPath $adb -PathType Leaf)) {
    throw "adb not found at '$adb'. Install Platform-Tools via the SDK Manager."
}
Write-Ok "SDK: $SdkPath"

# --- Files Android Studio generates behind the scenes -------------------------
# Both are gitignored, so a fresh clone has neither and Gradle fails without them.
$localProperties = Join-Path $repoRoot "local.properties"
if (-not (Test-Path -LiteralPath $localProperties -PathType Leaf)) {
    "sdk.dir=$($SdkPath -replace '\', '/')" | Out-File -FilePath $localProperties -Encoding ascii
    Write-Ok "created local.properties"
}

# The debug build type signs with signingConfigs.localDebug, which points at a
# keystore that is deliberately kept out of version control.
if (Test-Path -LiteralPath $appGradle -PathType Leaf) {
    $gradleText = Get-Content -LiteralPath $appGradle -Raw
    if ($gradleText -match 'storeFile\s*=\s*rootProject\.file\("([^"]+)"\)') {
        $keystoreName = $Matches[1]
        $keystorePath = Join-Path $repoRoot $keystoreName
        if (-not (Test-Path -LiteralPath $keystorePath -PathType Leaf)) {
            Write-Step "Generating missing debug keystore: $keystoreName"
            $keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
            if (-not $keytool) {
                $studioJbr = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
                if (Test-Path -LiteralPath $studioJbr -PathType Leaf) {
                    $keytool = $studioJbr
                } else {
                    throw "keytool not found. Install a JDK or set JAVA_HOME."
                }
            }
            & $keytool -genkeypair -keystore $keystorePath -alias androiddebugkey `
                -storepass android -keypass android -keyalg RSA -keysize 2048 `
                -validity 10000 -dname "CN=Android Debug,O=Android,C=US" | Out-Null
            Write-Ok "keystore created"
        }
    }
}

# --- Device ------------------------------------------------------------------
$attached = & $adb devices | Select-String -Pattern "\tdevice$"
if (-not $attached) {
    if (-not $Avd) {
        $available = & $emulator -list-avds | Where-Object { $_.Trim() -ne "" }
        if (-not $available) {
            throw "No AVD exists. Create one in Android Studio: Tools > Device Manager."
        }
        $Avd = ($available | Select-Object -First 1).Trim()
    }
    Write-Step "Booting emulator '$Avd' (a cold start takes 1-2 minutes)"
    Start-Process -FilePath $emulator `
        -ArgumentList @("-avd", $Avd, "-netdelay", "none", "-netspeed", "full") `
        -WorkingDirectory (Split-Path $emulator -Parent) -WindowStyle Minimized

    & $adb wait-for-device
    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline) {
        $booted = & $adb shell getprop sys.boot_completed 2>$null
        if ("$booted".Trim() -eq "1") { break }
        Start-Sleep -Seconds 3
    }
    if ((Get-Date) -ge $deadline) { throw "Emulator '$Avd' did not finish booting in 5 minutes." }
    Invoke-Quiet $adb shell input keyevent 82   # dismiss the lock screen
    Write-Ok "emulator ready"
} else {
    Write-Ok "device already attached"
}

# --- Build and install --------------------------------------------------------
$gradlew = Join-Path $repoRoot "gradlew.bat"
$gradleTasks = @()
if ($Clean) { $gradleTasks += "clean" }
$gradleTasks += $Task

Write-Step "gradlew $($gradleTasks -join ' ')"
Push-Location $repoRoot
try {
    & $gradlew @gradleTasks --console=plain
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($exitCode -ne 0) { throw "Gradle failed with exit code $exitCode." }
Write-Ok "installed"

# --- Launch -------------------------------------------------------------------
if (-not $NoLaunch -and (Test-Path -LiteralPath $appGradle -PathType Leaf)) {
    $applicationId = $null
    if ((Get-Content -LiteralPath $appGradle -Raw) -match 'applicationId\s*=\s*"([^"]+)"') {
        $applicationId = $Matches[1]
    }
    if ($applicationId) {
        Write-Step "Launching $applicationId"
        Invoke-Quiet $adb shell monkey -p $applicationId -c android.intent.category.LAUNCHER 1
        Start-Sleep -Seconds 3
        $processId = & $adb shell pidof $applicationId 2>$null
        if ("$processId".Trim()) {
            Write-Ok "running (pid $($processId.Trim()))"
        } else {
            Write-Warn "process is not running - recent crash log:"
            & $adb logcat -d -b crash -t 40
        }
    }
}

Write-Step "Done."
