param(
    [string]$BackendUrl = "http://127.0.0.1:8080"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$python = Join-Path $root ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) {
    throw "Python venv not found: $python"
}

& $python (Join-Path $root "tools\run_access_control_e2e.py") --base-url $BackendUrl
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
