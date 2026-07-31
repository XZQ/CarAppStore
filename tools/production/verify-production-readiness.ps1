[CmdletBinding()]
param(
    [string]$EnvironmentFile = (Join-Path $PSScriptRoot 'production.env')
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$allowedKeys = @(
    'CARAPPSTORE_CATALOG_PROD_URL',
    'CARAPPSTORE_DOWNLOAD_PROD_BASE_URL',
    'CARAPPSTORE_CATALOG_AUTH_HEADER',
    'CARAPPSTORE_CATALOG_AUTH_VALUE',
    'CARAPPSTORE_DOWNLOAD_AUTH_MODE',
    'CARAPPSTORE_DOWNLOAD_AUTH_HEADER',
    'CARAPPSTORE_DOWNLOAD_AUTH_VALUE',
    'CARAPPSTORE_RELEASE_STORE_FILE',
    'CARAPPSTORE_RELEASE_STORE_PASSWORD',
    'CARAPPSTORE_RELEASE_KEY_ALIAS',
    'CARAPPSTORE_RELEASE_KEY_PASSWORD'
)

function Import-ProductionEnvironment {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        Write-Host "Environment file not found; using existing process environment: $Path"
        return
    }

    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) {
            continue
        }
        $separator = $trimmed.IndexOf('=')
        if ($separator -le 0) {
            throw "Invalid environment line; expected KEY=VALUE."
        }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        if ($name -notin $allowedKeys) {
            throw "Unsupported production environment key: $name"
        }
        if (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))
        ) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
    Write-Host "Loaded production configuration keys from $Path (values redacted)."
}

function Assert-Java17OrNewer {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $versionOutput = (& java -version 2>&1 | Out-String)
        $javaExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($javaExitCode -ne 0) {
        throw 'Java is unavailable. Configure JAVA_HOME to JDK 17 or newer.'
    }
    $match = [regex]::Match($versionOutput, 'version\s+"(?<major>\d+)')
    if (-not $match.Success -or [int]$match.Groups['major'].Value -lt 17) {
        throw 'Production build requires JDK 17 or newer. Configure JAVA_HOME before running this script.'
    }
}

function Find-ApkSigner {
    $sdkRoot = $env:ANDROID_SDK_ROOT
    if (-not $sdkRoot) {
        $sdkRoot = $env:ANDROID_HOME
    }
    if (-not $sdkRoot) {
        $localProperties = Join-Path $repoRoot 'local.properties'
        if (Test-Path -LiteralPath $localProperties) {
            $sdkLine = Get-Content -LiteralPath $localProperties -Encoding UTF8 |
                Where-Object { $_ -match '^sdk\.dir=' } |
                Select-Object -First 1
            if ($sdkLine) {
                $sdkRoot = $sdkLine.Substring($sdkLine.IndexOf('=') + 1).Replace('\:', ':').Replace('\\', '\')
            }
        }
    }
    if (-not $sdkRoot -or -not (Test-Path -LiteralPath $sdkRoot)) {
        throw 'Android SDK path is unavailable. Configure ANDROID_SDK_ROOT or local.properties.'
    }
    $candidate = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Directory |
        Sort-Object { [version]$_.Name } -Descending |
        ForEach-Object { Join-Path $_.FullName 'apksigner.bat' } |
        Where-Object { Test-Path -LiteralPath $_ } |
        Select-Object -First 1
    if (-not $candidate) {
        throw 'apksigner.bat was not found in Android SDK build-tools.'
    }
    return $candidate
}

Push-Location $repoRoot
try {
    Import-ProductionEnvironment -Path $EnvironmentFile
    Assert-Java17OrNewer
    & (Join-Path $repoRoot 'gradlew.bat') assembleProductionRelease --no-daemon --stacktrace
    if ($LASTEXITCODE -ne 0) {
        throw "Production Gradle build failed with exit code $LASTEXITCODE."
    }

    $apkPath = Join-Path $repoRoot 'app\build\outputs\apk\release\app-release.apk'
    if (-not (Test-Path -LiteralPath $apkPath)) {
        throw "Signed release APK was not found: $apkPath"
    }
    $apkSigner = Find-ApkSigner
    & $apkSigner verify --verbose --print-certs $apkPath
    if ($LASTEXITCODE -ne 0) {
        throw "APK signature verification failed with exit code $LASTEXITCODE."
    }
    Write-Host "Production release readiness passed: $apkPath"
}
finally {
    Pop-Location
}
