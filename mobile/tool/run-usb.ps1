param(
    [string]$DeviceId,
    [switch]$SetupOnly
)

$ErrorActionPreference = 'Stop'
$mobileRoot = Split-Path $PSScriptRoot -Parent
$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
$adbPath = if ($adbCommand) { $adbCommand.Source } else { $null }
if (-not $adbPath) {
    $propertiesPath = Join-Path $mobileRoot 'android/local.properties'
    $sdkRoot = $env:ANDROID_HOME
    if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_SDK_ROOT }
    if (Test-Path $propertiesPath) {
        $sdkLine = Get-Content $propertiesPath | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -Last 1
        if ($sdkLine) { $sdkRoot = ($sdkLine -split '=', 2)[1].Replace('\\', '\').Replace('\:', ':') }
    }
    if ($sdkRoot) { $adbPath = Join-Path $sdkRoot 'platform-tools/adb.exe' }
}
if (-not $adbPath -or -not (Test-Path $adbPath)) {
    throw 'Cannot find adb. Install Android SDK platform-tools and set ANDROID_HOME.'
}

$deviceLines = & $adbPath devices
if ($LASTEXITCODE -ne 0) { throw 'Cannot query Android devices.' }
$readyDevices = @($deviceLines | ForEach-Object {
    if ($_ -match '^(\S+)\s+device\s*$') { $Matches[1] }
})
if ($DeviceId) {
    if ($DeviceId -notin $readyDevices) { throw 'Selected device is not ready. Check USB debugging authorization.' }
} elseif ($readyDevices.Count -eq 1) {
    $DeviceId = $readyDevices[0]
} else {
    throw 'Connect and authorize one Android device, or pass -DeviceId with its adb serial.'
}

& $adbPath -s $DeviceId reverse tcp:8080 tcp:8080
if ($LASTEXITCODE -ne 0) { throw 'Cannot forward port 8080 over USB.' }
Write-Host "USB backend forwarding ready for $DeviceId."
if ($SetupOnly) { return }

Push-Location $mobileRoot
try {
    & flutter run -d $DeviceId --dart-define=API_BASE_URL=http://127.0.0.1:8080
    $flutterExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $flutterExitCode
