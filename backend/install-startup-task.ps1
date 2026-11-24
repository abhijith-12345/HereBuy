# Registers and starts the HereBuy backend as a hidden Windows startup task.
$ErrorActionPreference = 'Stop'

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principalCheck = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principalCheck.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run this script from an elevated PowerShell window to register the Windows boot task.'
}

$pythonPath = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
$runnerPath = Join-Path $PSScriptRoot 'start-background.ps1'
if (-not (Test-Path -LiteralPath $pythonPath)) {
    throw 'The backend virtual environment is missing. Run .\start-local.ps1 once to create it.'
}

$taskName = 'HereBuy FastAPI Backend'
$powerShellPath = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$action = New-ScheduledTaskAction `
    -Execute $powerShellPath `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$runnerPath`"" `
    -WorkingDirectory $PSScriptRoot
$trigger = New-ScheduledTaskTrigger -AtStartup
$taskPrincipal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -RestartCount 999 `
    -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -MultipleInstances IgnoreNew `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -Hidden

Register-ScheduledTask `
    -TaskName $taskName `
    -Action $action `
    -Trigger $trigger `
    -Principal $taskPrincipal `
    -Settings $settings `
    -Description 'Keeps the HereBuy FastAPI backend running and starts it at Windows boot.' `
    -Force | Out-Null
Start-ScheduledTask -TaskName $taskName
Write-Output "Registered and started '$taskName'. Check backend\logs\backend.log for status."
