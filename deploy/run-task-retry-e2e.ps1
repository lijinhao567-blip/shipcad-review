param(
    [string]$BackendUrl = "http://127.0.0.1:8080",
    [string]$CadUrl = "http://127.0.0.1:9000",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [int]$PollSeconds = 45
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$python = Join-Path $root ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) {
    throw "Python venv not found: $python"
}

& (Join-Path $PSScriptRoot "test-health.ps1") `
    -BackendUrl $BackendUrl `
    -CadUrl $CadUrl `
    -NoFrontend | Out-Host

& $python (Join-Path $root "tools\run_task_retry_e2e.py") `
    --base-url $BackendUrl `
    --username $Username `
    --password $Password `
    --poll-seconds $PollSeconds

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "ShipCAD task retry E2E passed."
