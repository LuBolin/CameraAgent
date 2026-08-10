#requires -Version 7.0

[CmdletBinding()]
param(
    [string] $Serial,
    [string] $AdbPath = "W:\android_sdk\platform-tools\adb.exe",
    [switch] $AllowExistingApp,
    [switch] $AllowNonUsbTransport
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$appApk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $repoRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
$testClass = "com.bolin.photohelper.capture.CameraSmokeTest"
$runner = "com.bolin.photohelper.test/androidx.test.runner.AndroidJUnitRunner"

if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -eq $adbCommand) {
        throw "ADB was not found at '$AdbPath' or on PATH."
    }
    $AdbPath = $adbCommand.Source
}

foreach ($apk in @($appApk, $testApk)) {
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
        throw "Missing APK: $apk. Run .\gradlew.bat assembleDebug assembleDebugAndroidTest first."
    }
}

$gradleBuildFiles = @(
    Get-Item -LiteralPath (Join-Path $repoRoot "app\build.gradle.kts"), (Join-Path $repoRoot "build.gradle.kts"), (Join-Path $repoRoot "settings.gradle.kts")
)
$appBuildFiles = @($gradleBuildFiles) + @(Get-ChildItem -LiteralPath (Join-Path $repoRoot "app\src\main") -Recurse -File)
$testBuildFiles = @($gradleBuildFiles) + @(Get-ChildItem -LiteralPath (Join-Path $repoRoot "app\src\androidTest") -Recurse -File)
if ((Get-Item -LiteralPath $appApk).LastWriteTimeUtc -lt ($appBuildFiles | Measure-Object LastWriteTimeUtc -Maximum).Maximum -or
    (Get-Item -LiteralPath $testApk).LastWriteTimeUtc -lt ($testBuildFiles | Measure-Object LastWriteTimeUtc -Maximum).Maximum) {
    throw "The APKs are older than their source. Run .\gradlew.bat assembleDebug assembleDebugAndroidTest first."
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory)] [string[]] $AdbArguments,
        [switch] $AllowFailure,
        [int] $TimeoutSeconds = 30
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $AdbPath
    $startInfo.WorkingDirectory = $repoRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in $AdbArguments) { $startInfo.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) { throw "ADB could not be started." }
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $timedOut = -not $process.WaitForExit($TimeoutSeconds * 1000)
    if ($timedOut) {
        try { $process.Kill($true) } catch { }
        $process.WaitForExit()
    }
    $lines = @(($stdout.GetAwaiter().GetResult(), $stderr.GetAwaiter().GetResult()) -join "`n" -split "\r?\n" |
        Where-Object { $_.Length -gt 0 })
    $exitCode = if ($timedOut) { 124 } else { $process.ExitCode }
    if (-not $AllowFailure -and $exitCode -ne 0) {
        throw "ADB failed ($exitCode).`n$($lines -join "`n")"
    }
    [pscustomobject]@{ ExitCode = $exitCode; Lines = $lines; TimedOut = $timedOut }
}

$deviceRows = (Invoke-Adb -AdbArguments @("devices", "-l")).Lines
$devices = @(
    foreach ($line in $deviceRows) {
        if ($line -match "^(?<serial>\S+)\s+(?<state>device|offline|unauthorized|recovery|sideload|bootloader)(?<details>.*)$") {
            [pscustomobject]@{
                Serial = $Matches.serial
                State = $Matches.state
                Details = $Matches.details.Trim()
            }
        }
    }
)

if ($Serial) {
    $selected = @($devices | Where-Object Serial -EQ $Serial)
    if ($selected.Count -eq 0) { throw "The requested device is not visible to ADB." }
    if ($Serial.StartsWith("emulator-", [StringComparison]::OrdinalIgnoreCase)) {
        throw "'$Serial' is an emulator. This runner accepts only a physical Android device."
    }
} else {
    $selected = @($devices | Where-Object { -not $_.Serial.StartsWith("emulator-", [StringComparison]::OrdinalIgnoreCase) })
    if ($selected.Count -eq 0) {
        throw "No physical Android device is visible. Connect it by USB, unlock it, enable USB debugging, and accept the RSA prompt."
    }
    if ($selected.Count -gt 1) {
        throw "More than one physical device is visible. Rerun with -Serial using the value shown by 'adb devices -l'."
    }
}

$device = $selected[0]
if ($device.State -ne "device") {
    throw "The physical device is '$($device.State)'. Unlock it and accept the USB debugging prompt, then rerun."
}
if ($device.Serial.Contains(":")) {
    throw "A network ADB target was selected. Connect the phone by USB and rerun."
}
if ($device.Details -notmatch "(^|\s)usb:" -and -not $AllowNonUsbTransport) {
    throw "ADB did not identify this target as USB. Connect by cable, or use -AllowNonUsbTransport only after verifying the target yourself."
}

function Read-DeviceValue([string[]] $Command) {
    ((Invoke-Adb -AdbArguments (@("-s", $device.Serial, "shell") + $Command)).Lines -join "`n").Trim()
}

$qemu = @(
    Read-DeviceValue @("getprop", "ro.kernel.qemu")
    Read-DeviceValue @("getprop", "ro.boot.qemu")
)
if ($qemu -contains "1") { throw "The selected ADB target reports that it is an emulator." }
$sdk = Read-DeviceValue @("getprop", "ro.build.version.sdk")
if ($sdk -notmatch "^\d+$" -or [int] $sdk -lt 31) {
    throw "Android API 31 or newer is required; the connected device reports '$sdk'."
}

$manufacturer = Read-DeviceValue @("getprop", "ro.product.manufacturer")
$model = Read-DeviceValue @("getprop", "ro.product.model")
$android = Read-DeviceValue @("getprop", "ro.build.version.release")
$buildFingerprint = Read-DeviceValue @("getprop", "ro.build.fingerprint")
$buildIncremental = Read-DeviceValue @("getprop", "ro.build.version.incremental")
$securityPatch = Read-DeviceValue @("getprop", "ro.build.version.security_patch")
$abi = Read-DeviceValue @("getprop", "ro.product.cpu.abi")
$displaySize = Read-DeviceValue @("wm", "size")
$displayDensity = Read-DeviceValue @("wm", "density")
$features = (Invoke-Adb -AdbArguments @("-s", $device.Serial, "shell", "pm", "list", "features")).Lines |
    Where-Object { $_ -match "camera|microphone|sensor.accelerometer|sensor.gyroscope" }
$serialBytes = [Text.Encoding]::UTF8.GetBytes($device.Serial)
$serialHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($serialBytes)).Substring(0, 12)
$appHash = (Get-FileHash -LiteralPath $appApk -Algorithm SHA256).Hash
$testHash = (Get-FileHash -LiteralPath $testApk -Algorithm SHA256).Hash
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportDirectory = Join-Path $repoRoot "outputs\qa\physical-device"
$reportPath = Join-Path $reportDirectory "$timestamp-$serialHash.txt"
$report = [Collections.Generic.List[string]]::new()

$report.Add("Photo Helper physical-device acceptance")
$report.Add("Started: $(Get-Date -Format o)")
$report.Add("Device: $manufacturer $model")
$report.Add("Serial fingerprint: $serialHash")
$report.Add("Android: $android (API $sdk), ABI: $abi")
$report.Add("Build fingerprint: $buildFingerprint")
$report.Add("Build incremental/security patch: $buildIncremental / $securityPatch")
$report.Add("Display: $displaySize; $displayDensity")
$report.Add("App APK SHA-256: $appHash")
$report.Add("Test APK SHA-256: $testHash")
$report.Add("Relevant features:")
foreach ($feature in $features) { $report.Add("  $feature") }
$report.Add("")

$packageProbe = Invoke-Adb -AdbArguments @("-s", $device.Serial, "shell", "pm", "path", "com.bolin.photohelper") -AllowFailure
$packageProbeText = ($packageProbe.Lines -join "`n").Trim()
$appAlreadyInstalled = switch ($packageProbe.ExitCode) {
    0 {
        if ($packageProbeText -notmatch "^package:") { throw "Package Manager returned an unexpected app lookup result; no install was attempted." }
        $true
    }
    1 {
        if ($packageProbeText) { throw "Package Manager could not safely determine whether Photo Helper is installed; no install was attempted." }
        $false
    }
    default { throw "Package Manager could not determine whether Photo Helper is installed; no install was attempted." }
}
if ($appAlreadyInstalled -and -not $AllowExistingApp) {
    throw "Photo Helper is already installed. To preserve its private data, remove it first or explicitly rerun with -AllowExistingApp."
}

[int] $originalStayAwake = 0
$stayAwakeText = Read-DeviceValue @("settings", "get", "global", "stay_on_while_plugged_in")
$null = [int]::TryParse($stayAwakeText, [ref] $originalStayAwake)
$originallyAwake = (Read-DeviceValue @("dumpsys", "power")) -match "mWakefulness=Awake"
$failure = $null
$passed = $false
$stayAwakeChanged = $false
$remoteAppApk = "/data/local/tmp/photohelper-$timestamp-app.apk"
$remoteTestApk = "/data/local/tmp/photohelper-$timestamp-test.apk"

Write-Host "Testing $manufacturer $model on Android $android (API $sdk)..."
try {
    $null = Invoke-Adb -AdbArguments @("-s", $device.Serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP") -AllowFailure
    $null = Invoke-Adb -AdbArguments @("-s", $device.Serial, "shell", "wm", "dismiss-keyguard") -AllowFailure
    $null = Invoke-Adb -AdbArguments @("-s", $device.Serial, "shell", "settings", "put", "global", "stay_on_while_plugged_in", ($originalStayAwake -bor 2).ToString())
    $stayAwakeChanged = $true
    $null = Invoke-Adb -AdbArguments @("-s", $device.Serial, "uninstall", "com.bolin.photohelper.test") -AllowFailure

    $null = Invoke-Adb -AdbArguments @("-s", $device.Serial, "push", $appApk, $remoteAppApk) -TimeoutSeconds 180
    $installApp = Invoke-Adb -AdbArguments @("-s", $device.Serial, "shell", "pm", "install", "-r", "-t", $remoteAppApk) -TimeoutSeconds 180
    $null = Invoke-Adb -AdbArguments @("-s", $device.Serial, "push", $testApk, $remoteTestApk) -TimeoutSeconds 180
    $installTest = Invoke-Adb -AdbArguments @("-s", $device.Serial, "shell", "pm", "install", "-r", "-t", $remoteTestApk) -TimeoutSeconds 180
    $report.Add("Existing app explicitly allowed: $appAlreadyInstalled")
    $report.Add("App install: $($installApp.Lines -join ' ')")
    $report.Add("Test install: $($installTest.Lines -join ' ')")
    $report.Add("")

    $instrument = Invoke-Adb -AdbArguments @(
        "-s", $device.Serial,
        "shell", "am", "instrument", "-w", "-r",
        "-e", "class", $testClass,
        $runner
    ) -AllowFailure -TimeoutSeconds 600
    $report.Add("Instrumentation exit code: $($instrument.ExitCode)")
    $report.Add("Instrumentation output:")
    foreach ($line in $instrument.Lines) { $report.Add($line) }
    $instrumentText = $instrument.Lines -join "`n"
    $passed = $instrument.ExitCode -eq 0 -and
        $instrumentText -match "OK \(7 tests\)" -and
        $instrumentText -match "INSTRUMENTATION_CODE: -1" -and
        $instrumentText -match "PHYSICAL_GATE cameraId=.*horizontalFovDegrees=.*jankPercent=" -and
        $instrumentText -match "chainedEv=.*lumaTrials=.*captureEv=.*captureIso=.*captureExposureNs=" -and
        $instrumentText -match "PHYSICAL_GATE zoom=.*chain=COMMENT>APPLY>VERIFY>RESET" -and
        $instrumentText -match "PHYSICAL_GATE compound=ZOOM_IN\+WHITE_BALANCE_COOLER .*chain=COMMENT>APPLY_BOTH>VERIFY_SETPOINTS>RESET" -and
        $instrumentText -match "PHYSICAL_GATE (?:focusMode=\w+ )?tapFraction=.*tapPx=.*markerPx=.*focus=LOCKED" -and
        $instrumentText -notmatch "FAILURES!!!|INSTRUMENTATION_FAILED"
    if (-not $passed) {
        $failure = if ($instrument.TimedOut) { "Instrumentation exceeded 600 seconds." } else { "Instrumentation or its required evidence did not report a complete 7-test pass." }
    }
} catch {
    $failure = $_.Exception.Message
} finally {
    $null = Invoke-Adb -AdbArguments @("-s", $device.Serial, "shell", "rm", "-f", $remoteAppApk, $remoteTestApk) -AllowFailure
    $null = Invoke-Adb -AdbArguments @("-s", $device.Serial, "shell", "am", "force-stop", "com.bolin.photohelper.test") -AllowFailure
    $null = Invoke-Adb -AdbArguments @("-s", $device.Serial, "shell", "am", "force-stop", "com.bolin.photohelper") -AllowFailure
    $testCleanup = Invoke-Adb -AdbArguments @("-s", $device.Serial, "uninstall", "com.bolin.photohelper.test") -AllowFailure
    if ($stayAwakeChanged) {
        $null = Invoke-Adb -AdbArguments @("-s", $device.Serial, "shell", "settings", "put", "global", "stay_on_while_plugged_in", $originalStayAwake.ToString()) -AllowFailure
    }
    if (-not $appAlreadyInstalled) {
        $appCleanup = Invoke-Adb -AdbArguments @("-s", $device.Serial, "shell", "pm", "clear", "com.bolin.photohelper") -AllowFailure
        $report.Add("Fresh app reset to onboarding: $($appCleanup.Lines -join ' ')")
    }
    if (-not $originallyAwake) {
        $null = Invoke-Adb -AdbArguments @("-s", $device.Serial, "shell", "input", "keyevent", "KEYCODE_SLEEP") -AllowFailure
    }
    $report.Add("Test package cleanup: $($testCleanup.Lines -join ' ')")
    if ($failure) { $report.Add("Failure: $failure") }
    $report.Add("Finished: $(Get-Date -Format o)")
    New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
    [IO.File]::WriteAllLines($reportPath, $report, [Text.UTF8Encoding]::new($false))
}

if (-not $passed) { throw "Physical-camera acceptance failed. Report: $reportPath" }
Write-Host "PASS: all 7 physical CameraX tests passed."
Write-Host "Report: $reportPath"
Write-Host "The test package and exact test-created photos were removed; the debug app remains installed."
