[CmdletBinding()]
param(
    [string]$ApkPath,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\..\build\device-validation'),
    [switch]$Install
)

$ErrorActionPreference = 'Stop'

function Invoke-Adb {
    param([string[]]$Arguments)

    $output = & adb @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed: adb $($Arguments -join ' ')"
    }
    return ($output | Out-String).Trim()
}

function Read-DeviceProperty {
    param(
        [string]$Serial,
        [string]$Name
    )

    return Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'getprop', $Name)
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw 'adb is unavailable. Add Android SDK platform-tools to PATH.'
}

$deviceLines = & adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\S' }
$authorized = @($deviceLines | Where-Object { $_ -match '\sdevice$' })
$blocked = @($deviceLines | Where-Object { $_ -notmatch '\sdevice$' })
if ($blocked.Count -gt 0) {
    throw 'At least one adb target is offline or unauthorized; resolve it before collecting evidence.'
}
if ($authorized.Count -ne 1) {
    throw "Exactly one authorized Android device is required; found $($authorized.Count)."
}

$serial = ($authorized[0] -split '\s+')[0]
$manufacturer = Read-DeviceProperty -Serial $serial -Name 'ro.product.manufacturer'
$model = Read-DeviceProperty -Serial $serial -Name 'ro.product.model'
$androidVersion = Read-DeviceProperty -Serial $serial -Name 'ro.build.version.release'
$apiLevel = Read-DeviceProperty -Serial $serial -Name 'ro.build.version.sdk'
$buildFingerprint = Read-DeviceProperty -Serial $serial -Name 'ro.build.fingerprint'
$abi = Read-DeviceProperty -Serial $serial -Name 'ro.product.cpu.abi'
$resolution = Invoke-Adb -Arguments @('-s', $serial, 'shell', 'wm', 'size')
$density = Invoke-Adb -Arguments @('-s', $serial, 'shell', 'wm', 'density')
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$safeSerial = $serial -replace '[^A-Za-z0-9._-]', '_'
$resolvedOutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $resolvedOutputDirectory -Force | Out-Null
$reportPath = Join-Path $resolvedOutputDirectory "$timestamp-$safeSerial.md"

$apkSummary = 'Not provided'
$installSummary = 'Not requested'
if ($ApkPath) {
    $resolvedApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
    $hash = (Get-FileHash -LiteralPath $resolvedApkPath -Algorithm SHA256).Hash
    $apkSummary = "$resolvedApkPath (SHA-256: $hash)"
    if ($Install) {
        $installOutput = Invoke-Adb -Arguments @('-s', $serial, 'install', '-r', $resolvedApkPath)
        $installSummary = $installOutput.Replace("`r", ' ').Replace("`n", ' ')
    }
}

$report = @"
# Android Device Baseline

> Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')
> This report captures device and APK facts only. Manual checks in docs/25 must still be executed; this file alone is not a PASS.

| Field | Value |
|---|---|
| Serial | $serial |
| Manufacturer | $manufacturer |
| Model | $model |
| Android | $androidVersion |
| API level | $apiLevel |
| Build fingerprint | $buildFingerprint |
| ABI | $abi |
| Resolution | $($resolution.Replace("`r", ' ').Replace("`n", ' ')) |
| Density | $($density.Replace("`r", ' ').Replace("`n", ' ')) |
| APK | $apkSummary |
| Install command | $installSummary |

## Manual evidence

- [ ] Attach the completed docs/25 checklist for this device.
- [ ] Attach relevant logcat excerpts and screenshots.
- [ ] Record catalog/CDN environment and release commit.
- [ ] Mark optional vehicle-only cases N/A unless this is a vehicle release target.
"@

Set-Content -LiteralPath $reportPath -Value $report -Encoding UTF8
Write-Host "Android device baseline written to $reportPath"
