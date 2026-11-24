# Runs Uvicorn in a restart loop and appends all backend output to one log file.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$pythonPath = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
$logDirectory = Join-Path $PSScriptRoot 'logs'
$apiLogPath = Join-Path $logDirectory 'api.log'

if (-not (Test-Path -LiteralPath $pythonPath)) {
    throw "Backend virtual environment is missing: $pythonPath"
}
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

while ($true) {
    if ((Test-Path -LiteralPath $apiLogPath) -and (Get-Item -LiteralPath $apiLogPath).Length -gt 10MB) {
        Clear-Content -LiteralPath $apiLogPath
    }

    $startedAt = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    "[$startedAt] Starting HereBuy FastAPI on 127.0.0.1:8001" | Add-Content -LiteralPath $apiLogPath
    try {
        $command = '""{0}" -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --no-access-log >> "{1}" 2>&1"' -f $pythonPath, $apiLogPath
        & $env:ComSpec /d /s /c $command
        $exitCode = $LASTEXITCODE
    }
    catch {
        $exitCode = 1
        "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] Could not start Uvicorn: $($_.Exception.Message)" | Add-Content -LiteralPath $apiLogPath
    }
    $stoppedAt = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    "[$stoppedAt] Uvicorn exited with code $exitCode; restarting in 5 seconds" | Add-Content -LiteralPath $apiLogPath
    Start-Sleep -Seconds 5
}
