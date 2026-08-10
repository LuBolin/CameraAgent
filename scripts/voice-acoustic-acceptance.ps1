#requires -Version 7.0

[CmdletBinding()]
param(
    [string] $Serial,
    [string] $AdbPath = "W:\android_sdk\platform-tools\adb.exe",
    [string] $EdgePath = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    [string] $PlaybackEndpointName = "PHL 243S7",
    [string] $PlaybackFile,
    [string] $ExpectedTranscript,
    [ValidateRange(250, 5000)] [int] $ReadyGuardMilliseconds = 750,
    [switch] $ReuseInstalledApks,
    [switch] $KeepTestPackage
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$appApk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $repoRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
$fixturePath = Join-Path $repoRoot "test-fixtures\device-stage.html"
$testClass = "com.bolin.photohelper.capture.VoiceAcousticSmokeTest"
$runner = "com.bolin.photohelper.test/androidx.test.runner.AndroidJUnitRunner"

foreach ($path in @($AdbPath, $EdgePath, $appApk, $testApk, $fixturePath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing required file: $path" }
}
$resolvedPlaybackFile = if ($PlaybackFile) { (Resolve-Path -LiteralPath $PlaybackFile).Path } else { $null }
if ($ExpectedTranscript -and $ExpectedTranscript.Length -gt 100) {
    throw "ExpectedTranscript must be at most 100 characters."
}

$gradleFiles = @(
    Get-Item -LiteralPath (Join-Path $repoRoot "app\build.gradle.kts"), (Join-Path $repoRoot "build.gradle.kts"), (Join-Path $repoRoot "settings.gradle.kts")
)
$appSources = @($gradleFiles) + @(Get-ChildItem -LiteralPath (Join-Path $repoRoot "app\src\main") -Recurse -File)
$testSources = @($gradleFiles) + @(Get-ChildItem -LiteralPath (Join-Path $repoRoot "app\src\androidTest") -Recurse -File)
if ((Get-Item -LiteralPath $appApk).LastWriteTimeUtc -lt ($appSources | Measure-Object LastWriteTimeUtc -Maximum).Maximum -or
    (Get-Item -LiteralPath $testApk).LastWriteTimeUtc -lt ($testSources | Measure-Object LastWriteTimeUtc -Maximum).Maximum) {
    throw "The APKs are older than their source. Run .\gradlew.bat assembleDebug assembleDebugAndroidTest first."
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory)] [string[]] $Arguments,
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
    foreach ($argument in $Arguments) { $startInfo.ArgumentList.Add($argument) }
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
    $text = (($stdout.GetAwaiter().GetResult(), $stderr.GetAwaiter().GetResult()) -join "`n").Trim()
    $exitCode = if ($timedOut) { 124 } else { $process.ExitCode }
    if (-not $AllowFailure -and $exitCode -ne 0) {
        throw "ADB failed ($exitCode) while running: adb $($Arguments -join ' ')`n$text"
    }
    [pscustomobject]@{ ExitCode = $exitCode; Text = $text; TimedOut = $timedOut }
}

function Invoke-CdpExpression {
    param(
        [Parameter(Mandatory)] [string] $WebSocketUrl,
        [Parameter(Mandatory)] [string] $Expression
    )
    $socket = [Net.WebSockets.ClientWebSocket]::new()
    $cancellation = [Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(10))
    try {
        $socket.ConnectAsync([Uri]$WebSocketUrl, $cancellation.Token).GetAwaiter().GetResult()
        $command = @{
            id = 1
            method = "Runtime.evaluate"
            params = @{
                expression = $Expression
                returnByValue = $true
                userGesture = $true
            }
        } | ConvertTo-Json -Compress -Depth 5
        $commandBytes = [Text.Encoding]::UTF8.GetBytes($command)
        $socket.SendAsync(
            [ArraySegment[byte]]::new($commandBytes),
            [Net.WebSockets.WebSocketMessageType]::Text,
            $true,
            $cancellation.Token
        ).GetAwaiter().GetResult()

        while (-not $cancellation.IsCancellationRequested) {
            $message = [IO.MemoryStream]::new()
            do {
                $buffer = [byte[]]::new(8192)
                $result = $socket.ReceiveAsync(
                    [ArraySegment[byte]]::new($buffer),
                    $cancellation.Token
                ).GetAwaiter().GetResult()
                $message.Write($buffer, 0, $result.Count)
            } while (-not $result.EndOfMessage)
            $response = [Text.Encoding]::UTF8.GetString($message.ToArray()) | ConvertFrom-Json
            if ($response.id -eq 1) {
                if ($null -ne $response.error) { throw "Edge rejected the fixture command." }
                if ($null -ne $response.result.exceptionDetails) { throw "The Edge fixture command threw an exception." }
                return $response.result.result.value
            }
        }
        throw "Edge did not acknowledge the fixture command."
    } finally {
        $socket.Dispose()
        $cancellation.Dispose()
    }
}

$deviceRows = (Invoke-Adb -Arguments @("devices", "-l")).Text -split "\r?\n"
$devices = @(
    foreach ($line in $deviceRows) {
        if ($line -match "^(?<serial>\S+)\s+(?<state>device|offline|unauthorized)(?<details>.*)$") {
            [pscustomobject]@{ Serial = $Matches.serial; State = $Matches.state; Details = $Matches.details.Trim() }
        }
    }
)
if ($Serial) {
    $selected = @($devices | Where-Object Serial -EQ $Serial)
} else {
    $selected = @($devices | Where-Object { -not $_.Serial.StartsWith("emulator-", [StringComparison]::OrdinalIgnoreCase) })
}
if ($selected.Count -ne 1) { throw "Exactly one physical Android device must be selected." }
$device = $selected[0]
if ($device.State -ne "device" -or $device.Serial.Contains(":") -or $device.Serial.StartsWith("emulator-")) {
    throw "The selected target must be an unlocked physical USB device."
}

Add-Type -AssemblyName System.Windows.Forms
$portraitDisplays = @(
    [System.Windows.Forms.Screen]::AllScreens | Where-Object {
        -not $_.Primary -and $_.Bounds.Height -gt $_.Bounds.Width
    }
)
if ($portraitDisplays.Count -ne 1) { throw "Exactly one non-primary portrait display is required." }
$display = $portraitDisplays[0]
if ($display.Bounds.Width -ne 1080 -or $display.Bounds.Height -ne 1920) {
    throw "The portrait display must be 1080x1920; found $($display.Bounds.Width)x$($display.Bounds.Height)."
}

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public enum AudioRole { Console, Multimedia, Communications }
public enum AudioFlow { Render, Capture, All }

[ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
public class MMDeviceEnumeratorObject { }

[ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("D666063F-1587-4E43-81F1-B948E807363F")]
public interface IMMDevice {
    int Activate(ref Guid iid, uint context, IntPtr activation, out IntPtr instance);
    int OpenPropertyStore(uint access, out IntPtr properties);
    int GetId([MarshalAs(UnmanagedType.LPWStr)] out string id);
    int GetState(out uint state);
}

[ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("A95664D2-9614-4F35-A746-DE8DB63617E6")]
public interface IMMDeviceEnumerator {
    int EnumAudioEndpoints(AudioFlow flow, uint stateMask, out IntPtr devices);
    int GetDefaultAudioEndpoint(AudioFlow flow, AudioRole role, out IMMDevice device);
    int GetDevice([MarshalAs(UnmanagedType.LPWStr)] string id, out IMMDevice device);
    int RegisterEndpointNotificationCallback(IntPtr client);
    int UnregisterEndpointNotificationCallback(IntPtr client);
}

[ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("5CDF2C82-841E-4546-9722-0CF74078229A")]
public interface IAudioEndpointVolume {
    int RegisterControlChangeNotify(IntPtr notify);
    int UnregisterControlChangeNotify(IntPtr notify);
    int GetChannelCount(out uint count);
    int SetMasterVolumeLevel(float levelDb, Guid context);
    int SetMasterVolumeLevelScalar(float level, Guid context);
    int GetMasterVolumeLevel(out float levelDb);
    int GetMasterVolumeLevelScalar(out float level);
    int SetChannelVolumeLevel(uint channel, float levelDb, Guid context);
    int SetChannelVolumeLevelScalar(uint channel, float level, Guid context);
    int GetChannelVolumeLevel(uint channel, out float levelDb);
    int GetChannelVolumeLevelScalar(uint channel, out float level);
    int SetMute([MarshalAs(UnmanagedType.Bool)] bool mute, Guid context);
    int GetMute([MarshalAs(UnmanagedType.Bool)] out bool mute);
}

[ComImport, Guid("870AF99C-171D-4F9E-AF0D-E63DF40C2BC9")]
public class PolicyConfigObject { }

[ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("F8679F50-850A-41CF-9C72-430F290290C8")]
public interface IPolicyConfig {
    int GetMixFormat(string id, IntPtr format);
    int GetDeviceFormat(string id, int isDefault, IntPtr format);
    int ResetDeviceFormat(string id);
    int SetDeviceFormat(string id, IntPtr endpointFormat, IntPtr mixFormat);
    int GetProcessingPeriod(string id, int isDefault, IntPtr defaultPeriod, IntPtr minimumPeriod);
    int SetProcessingPeriod(string id, IntPtr period);
    int GetShareMode(string id, IntPtr mode);
    int SetShareMode(string id, IntPtr mode);
    int GetPropertyValue(string id, IntPtr key, IntPtr value);
    int SetPropertyValue(string id, IntPtr key, IntPtr value);
    int SetDefaultEndpoint([MarshalAs(UnmanagedType.LPWStr)] string id, AudioRole role);
    int SetEndpointVisibility(string id, int visible);
}

public static class AudioRouting {
    private static IAudioEndpointVolume EndpointVolume(string id) {
        var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumeratorObject();
        IMMDevice device;
        Marshal.ThrowExceptionForHR(enumerator.GetDevice(id, out device));
        var iid = typeof(IAudioEndpointVolume).GUID;
        IntPtr instance;
        Marshal.ThrowExceptionForHR(device.Activate(ref iid, 23, IntPtr.Zero, out instance));
        return (IAudioEndpointVolume)Marshal.GetObjectForIUnknown(instance);
    }

    public static string GetDefault(AudioRole role) {
        var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumeratorObject();
        IMMDevice device;
        Marshal.ThrowExceptionForHR(enumerator.GetDefaultAudioEndpoint(AudioFlow.Render, role, out device));
        string id;
        Marshal.ThrowExceptionForHR(device.GetId(out id));
        return id;
    }

    public static void SetDefault(string id, AudioRole role) {
        var policy = (IPolicyConfig)new PolicyConfigObject();
        Marshal.ThrowExceptionForHR(policy.SetDefaultEndpoint(id, role));
    }

    public static float GetVolume(string id) {
        float level;
        Marshal.ThrowExceptionForHR(EndpointVolume(id).GetMasterVolumeLevelScalar(out level));
        return level;
    }

    public static void SetVolume(string id, float level) {
        Marshal.ThrowExceptionForHR(EndpointVolume(id).SetMasterVolumeLevelScalar(level, Guid.Empty));
    }

    public static bool GetMute(string id) {
        bool mute;
        Marshal.ThrowExceptionForHR(EndpointVolume(id).GetMute(out mute));
        return mute;
    }

    public static void SetMute(string id, bool mute) {
        Marshal.ThrowExceptionForHR(EndpointVolume(id).SetMute(mute, Guid.Empty));
    }
}
'@

$renderRoot = "Registry::HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Render"
$targetEndpoints = @(
    Get-ChildItem -LiteralPath $renderRoot | Where-Object {
        (Get-ItemProperty -LiteralPath $_.PSPath).DeviceState -eq 1
    } | ForEach-Object {
        $properties = Get-ItemProperty -LiteralPath (Join-Path $_.PSPath "Properties")
        [pscustomobject]@{
            Id = "{0.0.0.00000000}.$($_.PSChildName)"
            Name = $properties.'{a45c254e-df1c-4efd-8020-67d146a850e0},2'
        }
    } | Where-Object Name -EQ $PlaybackEndpointName
)
if ($targetEndpoints.Count -ne 1) { throw "Active playback endpoint '$PlaybackEndpointName' was not found uniquely." }
$targetEndpoint = $targetEndpoints[0]
$originalPlaybackVolume = [AudioRouting]::GetVolume($targetEndpoint.Id)
$originalPlaybackMute = [AudioRouting]::GetMute($targetEndpoint.Id)
$originalAudio = @{}
foreach ($role in [Enum]::GetValues([AudioRole])) { $originalAudio[$role] = [AudioRouting]::GetDefault($role) }

$stayAwakeText = (Invoke-Adb -Arguments @("-s", $device.Serial, "shell", "settings", "get", "global", "stay_on_while_plugged_in")).Text
[int] $originalStayAwake = 0
$null = [int]::TryParse($stayAwakeText, [ref] $originalStayAwake)
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$voiceRunId = $timestamp
$playbackMarkerPath = "/sdcard/Android/data/com.bolin.photohelper/files/voice-playback-$voiceRunId.ready"
$playbackCompletedMarkerPath = "/sdcard/Android/data/com.bolin.photohelper/files/voice-playback-$voiceRunId.spoken"
$serialHash = [Convert]::ToHexString(
    [Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($device.Serial))
).Substring(0, 12)
$reportDirectory = Join-Path $repoRoot "outputs\qa\voice-acoustic"
$reportPath = Join-Path $reportDirectory "$timestamp-$serialHash.txt"
$report = [Collections.Generic.List[string]]::new()
$instrumentProcess = $null
$instrumentStdout = $null
$instrumentStderr = $null
$instrumentText = ""
$instrumentOutputReported = $false
$passed = $false
$failure = $null

try {
    foreach ($role in [Enum]::GetValues([AudioRole])) { [AudioRouting]::SetDefault($targetEndpoint.Id, $role) }
    [AudioRouting]::SetVolume($targetEndpoint.Id, 0.60)
    [AudioRouting]::SetMute($targetEndpoint.Id, $false)
    foreach ($role in [Enum]::GetValues([AudioRole])) {
        if ([AudioRouting]::GetDefault($role) -ne $targetEndpoint.Id) { throw "Windows did not select $PlaybackEndpointName for $role audio." }
    }
    if ([Math]::Abs([AudioRouting]::GetVolume($targetEndpoint.Id) - 0.60) -gt 0.01 -or
        [AudioRouting]::GetMute($targetEndpoint.Id)) {
        throw "Windows did not set the normalized secondary-display playback level."
    }

    $fixtureUrl = [Uri]::new($fixturePath).AbsoluteUri
    $fixtureRunUrl = "${fixtureUrl}?run=$voiceRunId"
    if ($ExpectedTranscript) {
        $fixtureRunUrl += "&speech=$([Uri]::EscapeDataString($ExpectedTranscript))"
    }
    $fixtureDirectoryUrl = ([Uri]::new((Split-Path -Parent $fixturePath))).AbsoluteUri.TrimEnd("/") + "/"
    $edgeDebugPort = 9224
    $edgeProfile = Join-Path $repoRoot ".tools\edge-voice-profile-clean"
    New-Item -ItemType Directory -Force -Path $edgeProfile | Out-Null
    $edgeArguments = @(
        "--disable-extensions",
        "--disable-sync",
        "--no-default-browser-check",
        "--remote-debugging-port=$edgeDebugPort",
        "--remote-debugging-address=127.0.0.1",
        "--user-data-dir=$edgeProfile",
        "--no-first-run",
        "--window-position=$($display.Bounds.X),$($display.Bounds.Y)",
        "--window-size=$($display.Bounds.Width),$($display.Bounds.Height)",
        "--start-fullscreen",
        "--app=$fixtureRunUrl"
    )
    $edgeStartInfo = [Diagnostics.ProcessStartInfo]::new()
    $edgeStartInfo.FileName = $EdgePath
    $edgeStartInfo.UseShellExecute = $true
    foreach ($argument in $edgeArguments) { $edgeStartInfo.ArgumentList.Add($argument) }
    $null = [Diagnostics.Process]::Start($edgeStartInfo)
    $edgeTarget = $null
    $edgeNavigationRequested = $false
    $edgeDeadline = [DateTime]::UtcNow.AddSeconds(20)
    while ([DateTime]::UtcNow -lt $edgeDeadline -and $null -eq $edgeTarget) {
        Start-Sleep -Milliseconds 250
        $edgeTargets = try {
            @(Invoke-RestMethod -Uri "http://127.0.0.1:$edgeDebugPort/json/list" -TimeoutSec 2)
        } catch { @() }
        $edgeTarget = $edgeTargets | Where-Object {
            $_.type -eq "page" -and $_.url -eq $fixtureRunUrl
        } | Select-Object -First 1
        if ($null -eq $edgeTarget -and -not $edgeNavigationRequested) {
            $reusableFixtureTarget = $edgeTargets | Where-Object {
                $_.type -eq "page" -and (
                    ($_.url -split "\?", 2)[0] -eq $fixtureUrl -or
                    $_.url.StartsWith($fixtureDirectoryUrl, [StringComparison]::OrdinalIgnoreCase)
                )
            } | Select-Object -First 1
            if ($null -ne $reusableFixtureTarget) {
                $encodedFixtureRunUrl = $fixtureRunUrl | ConvertTo-Json -Compress
                $null = Invoke-CdpExpression `
                    -WebSocketUrl $reusableFixtureTarget.webSocketDebuggerUrl `
                    -Expression "location.replace($encodedFixtureRunUrl); true"
                $edgeNavigationRequested = $true
            }
        }
    }
    if ($null -eq $edgeTarget) { throw "The controllable Edge fixture window did not appear." }
    foreach ($staleTarget in @($edgeTargets | Where-Object {
        $_.type -eq "page" -and
        $_.id -ne $edgeTarget.id -and
        $_.url.StartsWith($fixtureDirectoryUrl, [StringComparison]::OrdinalIgnoreCase)
    })) {
        try {
            $null = Invoke-CdpExpression `
                -WebSocketUrl $staleTarget.webSocketDebuggerUrl `
                -Expression "speechSynthesis.cancel(); true"
            $null = Invoke-RestMethod `
                -Uri "http://127.0.0.1:$edgeDebugPort/json/close/$($staleTarget.id)" `
                -TimeoutSec 2
        } catch { }
    }
    $edgeGeometry = Invoke-CdpExpression -WebSocketUrl $edgeTarget.webSocketDebuggerUrl -Expression @"
({
    screenX,
    screenY,
    screenWidth: screen.width,
    screenHeight: screen.height,
    title: document.title
})
"@
    if ($edgeGeometry.screenX -ne $display.Bounds.X -or
        $edgeGeometry.screenWidth -ne $display.Bounds.Width -or
        $edgeGeometry.screenHeight -ne $display.Bounds.Height) {
        throw "Edge did not land on the required portrait display."
    }

    $null = Invoke-Adb -Arguments @("-s", $device.Serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP") -AllowFailure
    $null = Invoke-Adb -Arguments @("-s", $device.Serial, "shell", "wm", "dismiss-keyguard") -AllowFailure
    $null = Invoke-Adb -Arguments @(
        "-s", $device.Serial, "shell", "settings", "put", "global", "stay_on_while_plugged_in", ($originalStayAwake -bor 2).ToString()
    )
    if ($ReuseInstalledApks) {
        foreach ($installed in @(
            @{ Package = "com.bolin.photohelper"; LocalPath = $appApk },
            @{ Package = "com.bolin.photohelper.test"; LocalPath = $testApk }
        )) {
            $packagePathText = (Invoke-Adb -Arguments @(
                "-s", $device.Serial, "shell", "pm", "path", $installed.Package
            )).Text
            if ($packagePathText -notmatch "(?m)^package:(.+)$") {
                throw "Installed package $($installed.Package) has no readable base APK."
            }
            $installedPath = $Matches[1].Trim()
            $installedHashText = (Invoke-Adb -Arguments @(
                "-s", $device.Serial, "shell", "sha256sum", $installedPath
            )).Text
            $installedHash = ($installedHashText -split "\s+", 2)[0].ToUpperInvariant()
            $localHash = (Get-FileHash -LiteralPath $installed.LocalPath -Algorithm SHA256).Hash
            if ($installedHash -ne $localHash) {
                throw "Installed package $($installed.Package) does not match the current APK."
            }
        }
    } else {
        $null = Invoke-Adb -Arguments @("-s", $device.Serial, "uninstall", "com.bolin.photohelper.test") -AllowFailure
        $remoteAppApk = "/data/local/tmp/photohelper-$voiceRunId-app.apk"
        $remoteTestApk = "/data/local/tmp/photohelper-$voiceRunId-test.apk"
        try {
            $null = Invoke-Adb -Arguments @("-s", $device.Serial, "push", $appApk, $remoteAppApk) -TimeoutSeconds 180
            $null = Invoke-Adb -Arguments @("-s", $device.Serial, "shell", "pm", "install", "-r", "-t", $remoteAppApk) -TimeoutSeconds 180
            $null = Invoke-Adb -Arguments @("-s", $device.Serial, "push", $testApk, $remoteTestApk) -TimeoutSeconds 180
            $null = Invoke-Adb -Arguments @("-s", $device.Serial, "shell", "pm", "install", "-r", "-t", $remoteTestApk) -TimeoutSeconds 180
        } finally {
            $null = Invoke-Adb -Arguments @("-s", $device.Serial, "shell", "rm", "-f", $remoteAppApk, $remoteTestApk) -AllowFailure
        }
    }
    $null = Invoke-Adb -Arguments @("-s", $device.Serial, "logcat", "-c")

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $AdbPath
    $startInfo.WorkingDirectory = $repoRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $instrumentArguments = @(
        "-s", $device.Serial, "shell", "am", "instrument", "-w", "-r",
        "-e", "voiceRunId", $voiceRunId
    )
    if ($ExpectedTranscript) {
        $encodedExpectedTranscript = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($ExpectedTranscript))
        $instrumentArguments += @("-e", "voiceExpectedTranscriptBase64", $encodedExpectedTranscript)
    }
    $instrumentArguments += @("-e", "class", $testClass, $runner)
    foreach ($argument in $instrumentArguments) { $startInfo.ArgumentList.Add($argument) }
    $instrumentProcess = [Diagnostics.Process]::new()
    $instrumentProcess.StartInfo = $startInfo
    if (-not $instrumentProcess.Start()) { throw "Voice instrumentation could not start." }
    $instrumentStdout = $instrumentProcess.StandardOutput.ReadToEndAsync()
    $instrumentStderr = $instrumentProcess.StandardError.ReadToEndAsync()

    $listening = $false
    $listeningDeadline = [DateTime]::UtcNow.AddSeconds(40)
    while ([DateTime]::UtcNow -lt $listeningDeadline -and -not $instrumentProcess.HasExited) {
        $marker = Invoke-Adb -Arguments @(
            "-s", $device.Serial, "shell", "cat", $playbackMarkerPath
        ) -AllowFailure -TimeoutSeconds 10
        if ($marker.Text.Trim() -eq "ready") {
            $listening = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $listening) { throw "The phone never published the voice playback marker." }

    $captureReady = $false
    $captureReadyDeadline = [DateTime]::UtcNow.AddSeconds(8)
    while ([DateTime]::UtcNow -lt $captureReadyDeadline -and -not $instrumentProcess.HasExited) {
        $voiceLog = Invoke-Adb -Arguments @(
            "-s", $device.Serial, "logcat", "-d", "-v", "brief", "-s", "PhotoHelperVoice:I", "*:S"
        ) -AllowFailure -TimeoutSeconds 10
        if ($voiceLog.Text -match "(?m)PhotoHelperVoice\([^)]*\): ready source=APP_PCM_CAPTURE\r?$") {
            $captureReady = $true
            $captureReadyAt = Get-Date
            $captureReadyEvidence = @(
                $voiceLog.Text -split "\r?\n" |
                    Where-Object { $_ -match "PhotoHelperVoice\([^)]*\): ready source=APP_PCM_CAPTURE\r?$" }
            )[-1]
            break
        }
        Start-Sleep -Milliseconds 250
    }
    if (-not $captureReady) { throw "Android never reported that app-owned microphone capture was ready." }

    Start-Sleep -Milliseconds $ReadyGuardMilliseconds
    $playbackTriggeredAt = Get-Date
    if ($resolvedPlaybackFile) {
        $encodedPlaybackUrl = ([Uri]::new($resolvedPlaybackFile).AbsoluteUri | ConvertTo-Json -Compress)
        $playbackExpression = @"
window.__photoHelperAcceptanceAudio?.pause();
const audio = new Audio($encodedPlaybackUrl);
window.__photoHelperAcceptanceAudio = audio;
document.title = "Photo Helper device target · queued";
audio.onplaying = () => { document.title = "Photo Helper device target · speaking"; };
audio.onended = () => { document.title = "Photo Helper device target · spoken"; };
audio.onerror = () => { document.title = "Photo Helper device target · speech error"; };
void audio.play();
true
"@
        $null = Invoke-CdpExpression -WebSocketUrl $edgeTarget.webSocketDebuggerUrl -Expression $playbackExpression
    } else {
        $null = Invoke-CdpExpression -WebSocketUrl $edgeTarget.webSocketDebuggerUrl -Expression "document.querySelector('button[title]').click(); true"
    }
    $playbackStarted = $true
    $playbackCompleted = $false
    $playbackDeadline = [DateTime]::UtcNow.AddSeconds(6)
    while ([DateTime]::UtcNow -lt $playbackDeadline) {
        $currentTargets = try {
            @(Invoke-RestMethod -Uri "http://127.0.0.1:$edgeDebugPort/json/list" -TimeoutSec 2)
        } catch { @() }
        $currentTarget = $currentTargets | Where-Object id -EQ $edgeTarget.id | Select-Object -First 1
        if ($null -eq $currentTarget) {
            Start-Sleep -Milliseconds 50
            continue
        }
        $title = $currentTarget.title
        if ($title -like "*speech error*") { throw "Edge playback reported an error." }
        if ($playbackStarted -and $title -like "*spoken*") {
            $playbackCompleted = $true
            break
        }
        Start-Sleep -Milliseconds 50
    }
    if (-not $playbackCompleted) { throw "Edge playback did not report completion." }
    $playbackCompletedAt = Get-Date
    $null = Invoke-Adb -Arguments @(
        "-s", $device.Serial, "shell", "touch", $playbackCompletedMarkerPath
    )

    if (-not $instrumentProcess.WaitForExit(90000)) {
        try { $instrumentProcess.Kill($true) } catch { }
        $instrumentProcess.WaitForExit()
        throw "Voice instrumentation exceeded 90 seconds."
    }
    $instrumentText = (($instrumentStdout.GetAwaiter().GetResult(), $instrumentStderr.GetAwaiter().GetResult()) -join "`n").Trim()
    $voiceTrace = (Invoke-Adb -Arguments @(
        "-s", $device.Serial, "logcat", "-d", "-v", "time", "-s", "PhotoHelperVoice:I", "*:S"
    ) -AllowFailure -TimeoutSeconds 10).Text
    $requiredVoiceMarkers = if ($ExpectedTranscript) {
        @("VOICE_GATE transcript=EXPECTED_CONTROL source=EDGE_SECONDARY_DISPLAY")
    } else {
        @(
            "VOICE_GATE chain=EDGE_SPEAKER>PHONE_MIC>APP_PCM_CAPTURE>PFD_ON_DEVICE_STT>COMPOUND>APPLY_BOTH>VERIFY_SETPOINTS>RESET",
            "VOICE_GATE silence=NO_TRANSCRIPT stale_buffer=false camera_unchanged=true"
        )
    }
    $captureReadyCount = ([regex]::Matches(
        $voiceTrace,
        "(?m)PhotoHelperVoice\([^)]*\): ready source=APP_PCM_CAPTURE\r?$"
    )).Count
    $finishRequestedCount = ([regex]::Matches(
        $voiceTrace,
        "(?m)PhotoHelperVoice\([^)]*\): finish_requested source=APP_PCM_CAPTURE\r?$"
    )).Count
    $captureCompleteCount = ([regex]::Matches(
        $voiceTrace,
        "(?m)PhotoHelperVoice\([^)]*\): capture_complete speech=(?:true|false)\r?$"
    )).Count
    $voiceTracePassed = $ExpectedTranscript -or (
        $captureReady -and
        $captureReadyCount -ge 1 -and
        $finishRequestedCount -eq 2 -and
        $captureCompleteCount -eq 2 -and
        $voiceTrace -match "capture_complete speech=true" -and
        $voiceTrace -match "decode_ready source=APP_PCM_CAPTURE" -and
        $voiceTrace -match "results count=.*nonEmpty=true"
    )
    $passed = $instrumentProcess.ExitCode -eq 0 -and
        $instrumentText -match "OK \(1 test\)" -and
        @($requiredVoiceMarkers | Where-Object { -not $instrumentText.Contains($_) }).Count -eq 0 -and
        $voiceTracePassed -and
        $instrumentText -notmatch "FAILURES!!!|INSTRUMENTATION_FAILED"
    if (-not $passed) {
        $failure = if ($ExpectedTranscript) {
            "The expected-transcript acoustic control did not produce its expected transcript."
        } else {
            "The acoustic speech chain did not reach compound Apply and Reset."
        }
    }

    $report.Add("Photo Helper voice acoustic acceptance")
    $report.Add("Started: $timestamp")
    $report.Add("Device: $((Invoke-Adb -Arguments @('-s', $device.Serial, 'shell', 'getprop', 'ro.product.model')).Text)")
    $report.Add("Serial fingerprint: $serialHash")
    $report.Add("APK preparation: $(if ($ReuseInstalledApks) { 'installed SHA-256 values verified' } else { 'fresh app/test install' })")
    $report.Add("Display: $($display.DeviceName) $($display.Bounds.Width)x$($display.Bounds.Height) at $($display.Bounds.X),$($display.Bounds.Y)")
    $report.Add("Playback endpoint: $PlaybackEndpointName ($($targetEndpoint.Id))")
    $report.Add("Playback source: $(if ($resolvedPlaybackFile) { $resolvedPlaybackFile } else { 'Edge local speech synthesis' })")
    if ($resolvedPlaybackFile) {
        $report.Add("Playback file SHA-256: $((Get-FileHash -LiteralPath $resolvedPlaybackFile -Algorithm SHA256).Hash)")
    }
    $report.Add("Playback volume during test: 60%")
    $report.Add("Android app-owned microphone capture ready before playback: confirmed")
    $report.Add("Post-ready playback guard: $ReadyGuardMilliseconds ms")
    $report.Add("Microphone capture ready observed (host clock): $($captureReadyAt.ToString('o'))")
    $report.Add("Microphone capture ready evidence: $captureReadyEvidence")
    $report.Add("Playback trigger (host clock): $($playbackTriggeredAt.ToString('o'))")
    $report.Add("Playback complete (host clock): $($playbackCompletedAt.ToString('o'))")
    $report.Add("Recognizer trace (device clock):")
    foreach ($line in $voiceTrace -split "\r?\n") { $report.Add($line) }
    $report.Add("Edge playback: completed")
    $report.Add("Edge display geometry: $($edgeGeometry.screenWidth)x$($edgeGeometry.screenHeight) at $($edgeGeometry.screenX),$($edgeGeometry.screenY)")
    $report.Add("Fixture SHA-256: $((Get-FileHash -LiteralPath $fixturePath -Algorithm SHA256).Hash)")
    $report.Add("App APK SHA-256: $((Get-FileHash -LiteralPath $appApk -Algorithm SHA256).Hash)")
    $report.Add("Test APK SHA-256: $((Get-FileHash -LiteralPath $testApk -Algorithm SHA256).Hash)")
    $report.Add("Instrumentation output:")
    foreach ($line in $instrumentText -split "\r?\n") { $report.Add($line) }
    $instrumentOutputReported = $true
} catch {
    $failure = $_.Exception.Message
} finally {
    if ($null -ne $edgeTarget) {
        try {
            $null = Invoke-CdpExpression `
                -WebSocketUrl $edgeTarget.webSocketDebuggerUrl `
                -Expression "speechSynthesis.cancel(); window.__photoHelperAcceptanceAudio?.pause(); true"
        } catch { }
    }
    if ($null -ne $instrumentProcess -and -not $instrumentProcess.HasExited) {
        try { $instrumentProcess.Kill($true) } catch { }
    }
    if (-not $instrumentText -and $null -ne $instrumentStdout) {
        $instrumentText = (($instrumentStdout.GetAwaiter().GetResult(), $instrumentStderr.GetAwaiter().GetResult()) -join "`n").Trim()
    }
    if ($instrumentText -and -not $instrumentOutputReported) {
        $report.Add("Instrumentation output:")
        foreach ($line in $instrumentText -split "\r?\n") { $report.Add($line) }
    }
    $cleanupFailures = [Collections.Generic.List[string]]::new()
    try {
        [AudioRouting]::SetVolume($targetEndpoint.Id, $originalPlaybackVolume)
        [AudioRouting]::SetMute($targetEndpoint.Id, $originalPlaybackMute)
        if ([Math]::Abs([AudioRouting]::GetVolume($targetEndpoint.Id) - $originalPlaybackVolume) -gt 0.01 -or
            [AudioRouting]::GetMute($targetEndpoint.Id) -ne $originalPlaybackMute) {
            $cleanupFailures.Add("The secondary-display volume did not restore exactly.")
        }
    } catch {
        $cleanupFailures.Add("Could not restore the secondary-display volume.")
    }
    foreach ($role in [Enum]::GetValues([AudioRole])) {
        try { [AudioRouting]::SetDefault($originalAudio[$role], $role) } catch {
            $cleanupFailures.Add("Could not restore the $role playback endpoint.")
        }
    }
    foreach ($role in [Enum]::GetValues([AudioRole])) {
        try {
            if ([AudioRouting]::GetDefault($role) -ne $originalAudio[$role]) {
                $cleanupFailures.Add("The $role playback endpoint did not restore exactly.")
            }
        } catch {
            $cleanupFailures.Add("Could not verify the restored $role playback endpoint.")
        }
    }
    if ($cleanupFailures.Count -gt 0) {
        $passed = $false
        if (-not $failure) { $failure = "Host audio cleanup failed." }
        foreach ($cleanupFailure in $cleanupFailures) { $report.Add("Cleanup failure: $cleanupFailure") }
    }
    $finalStayAwake = $originalStayAwake -bor 2
    $null = Invoke-Adb -Arguments @(
        "-s", $device.Serial, "shell", "settings", "put", "global", "stay_on_while_plugged_in", $finalStayAwake.ToString()
    ) -AllowFailure
    $null = Invoke-Adb -Arguments @("-s", $device.Serial, "shell", "am", "force-stop", "com.bolin.photohelper.test") -AllowFailure
    if (-not $KeepTestPackage) {
        $null = Invoke-Adb -Arguments @("-s", $device.Serial, "uninstall", "com.bolin.photohelper.test") -AllowFailure
    }
    $null = Invoke-Adb -Arguments @("-s", $device.Serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP") -AllowFailure
    $null = Invoke-Adb -Arguments @("-s", $device.Serial, "shell", "wm", "dismiss-keyguard") -AllowFailure
    $null = Invoke-Adb -Arguments @(
        "-s", $device.Serial, "shell", "am", "start", "-n", "com.bolin.photohelper/.MainActivity"
    ) -AllowFailure
    $finalStayAwakeText = (Invoke-Adb -Arguments @(
        "-s", $device.Serial, "shell", "settings", "get", "global", "stay_on_while_plugged_in"
    ) -AllowFailure).Text
    [int] $verifiedStayAwake = 0
    if (-not [int]::TryParse($finalStayAwakeText, [ref] $verifiedStayAwake) -or ($verifiedStayAwake -band 2) -eq 0) {
        $passed = $false
        if (-not $failure) { $failure = "The phone USB stay-awake setting was not preserved." }
    }
    if ($failure) {
        $logs = (Invoke-Adb -Arguments @(
            "-s", $device.Serial, "logcat", "-d", "-v", "time", "-s", "PhotoHelperVoice:I", "*:S"
        ) -AllowFailure -TimeoutSeconds 30).Text -split "\r?\n"
        $report.Add("Failure: $failure")
        $report.Add("Filtered recognizer logcat:")
        foreach ($line in $logs) { $report.Add($line) }
    }
    $report.Add("Finished: $(Get-Date -Format o)")
    New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
    [IO.File]::WriteAllLines($reportPath, $report, [Text.UTF8Encoding]::new($false))
}

if (-not $passed) { throw "Voice acoustic acceptance failed. Report: $reportPath" }
if ($ExpectedTranscript) {
    Write-Host "PASS: secondary-display speech produced the expected transcript."
} else {
    Write-Host "PASS: secondary-display speech reached one compound Apply and Reset; silence reused no audio."
}
Write-Host "Report: $reportPath"
