# Creates the local backend environment, installs dependencies, and starts Uvicorn with reload.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$venvPython = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
$logDirectory = Join-Path $PSScriptRoot 'logs'
$apiLogPath = Join-Path $logDirectory 'api.log'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
if ((Test-Path -LiteralPath $apiLogPath) -and (Get-Item -LiteralPath $apiLogPath).Length -gt 10MB) {
    Clear-Content -LiteralPath $apiLogPath
}

if (-not (Test-Path -LiteralPath $venvPython)) {
    py -3 -m venv .venv
}

if (-not (Test-Path -LiteralPath '.env')) {
    $secret = & $venvPython -c "import secrets; print(secrets.token_urlsafe(48))"
    $envText = Get-Content '.env.example' -Raw
    $envText = $envText.Replace('replace-this-with-a-random-secret-of-at-least-32-characters', $secret.Trim())
    Set-Content -LiteralPath '.env' -Value $envText -NoNewline
}

& $venvPython -m pip install -r requirements.txt
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $venvPython -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload --no-access-log *>> $apiLogPath
