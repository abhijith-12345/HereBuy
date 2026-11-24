$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$pythonPath = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
$logDirectory = Join-Path $PSScriptRoot 'logs'
$logPath = Join-Path $logDirectory 'backend.log'
$previousLogPath = Join-Path $logDirectory 'backend.previous.log'

if (-not (Test-Path -LiteralPath $pythonPath)) {
    throw "Backend virtual environment is missing: $pythonPath"
}
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

while ($true) {
    if ((Test-Path -LiteralPath $logPath) -and (Get-Item -LiteralPath $logPath).Length -gt 10MB) {
        Move-Item -LiteralPath $logPath -Destination $previousLogPath -Force
    }

    $startedAt = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    $runStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $stdoutPath = Join-Path $logDirectory "uvicorn-$runStamp.stdout.log"
    $stderrPath = Join-Path $logDirectory "uvicorn-$runStamp.stderr.log"
    "[$startedAt] Starting HereBuy FastAPI on 127.0.0.1:8001" | Add-Content -LiteralPath $logPath
    try {
        $process = Start-Process -FilePath $pythonPath `
            -ArgumentList @('-m', 'uvicorn', 'app.main:app', '--host', '127.0.0.1', '--port', '8001') `
            -WorkingDirectory $PSScriptRoot -WindowStyle Hidden `
            -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru
        "[$startedAt] Uvicorn process id $($process.Id)" | Add-Content -LiteralPath $logPath
        $process.WaitForExit()
        $exitCode = $process.ExitCode
    }
    catch {
        $exitCode = 1
        "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] Could not start Uvicorn: $($_.Exception.Message)" |
            Add-Content -LiteralPath $logPath
    }
    $stoppedAt = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    "[$stoppedAt] Uvicorn exited with code $exitCode; restarting in 5 seconds" | Add-Content -LiteralPath $logPath
    Start-Sleep -Seconds 5
}
