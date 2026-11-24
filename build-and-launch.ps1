param(
    [string]$DeviceSerial
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$initScriptPath = Join-Path $env:TEMP 'herebuy-isolated-build.init.gradle'
$apkPath = Join-Path $projectRoot 'build-isolated\docs\app\outputs\apk\debug\app-debug.apk'

try {
    Push-Location $projectRoot

    if (-not (Test-Path -LiteralPath $adbPath)) {
        throw "Android Debug Bridge was not found at $adbPath. Install Android SDK platform-tools first."
    }

    # Keep Gradle output isolated from IDE file locks in the regular app/build directory.
    @'
gradle.beforeProject { project ->
    if (project.path == ':app') {
        project.layout.buildDirectory.set(project.rootProject.file('build-isolated/docs/app'))
    }
}
'@ | Set-Content -LiteralPath $initScriptPath -Encoding Ascii

    Write-Host 'Building HereBuy debug APK...'
    & (Join-Path $projectRoot 'gradlew.bat') -I $initScriptPath --no-daemon :app:assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE."
    }

    if (-not (Test-Path -LiteralPath $apkPath)) {
        throw "Build finished, but the APK was not found at $apkPath."
    }

    $connectedDevices = @(& $adbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match '^\S+\s+device$' })
    if ($DeviceSerial) {
        if (-not ($connectedDevices | Where-Object { $_ -match "^$([regex]::Escape($DeviceSerial))\s+device$" })) {
            throw "Device '$DeviceSerial' is not connected and authorized."
        }
    } elseif ($connectedDevices.Count -eq 1) {
        $DeviceSerial = ($connectedDevices[0] -split '\s+')[0]
    } elseif ($connectedDevices.Count -eq 0) {
        throw 'No authorized Android device was found. Connect and unlock a phone with USB debugging enabled.'
    } else {
        throw "More than one device is connected. Run this script with -DeviceSerial <serial>. Devices:`n$($connectedDevices -join "`n")"
    }

    Write-Host "Installing on device $DeviceSerial..."
    & $adbPath -s $DeviceSerial install -r $apkPath
    if ($LASTEXITCODE -ne 0) {
        throw "APK installation failed with exit code $LASTEXITCODE."
    }

    & $adbPath -s $DeviceSerial shell am force-stop com.nearnest
    & $adbPath -s $DeviceSerial shell am start -n com.nearnest/.MainActivity
    if ($LASTEXITCODE -ne 0) {
        throw "Could not launch HereBuy (exit code $LASTEXITCODE)."
    }

    Write-Host 'HereBuy was built, installed, and launched successfully.' -ForegroundColor Green
    Pop-Location
    exit 0
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    if ((Get-Location).Path -eq $projectRoot) { Pop-Location }
    exit 1
}
