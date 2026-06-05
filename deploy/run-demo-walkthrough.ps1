param(
    [string]$BackendUrl = "http://127.0.0.1:8080",
    [string]$CadUrl = "http://127.0.0.1:9000",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$Sample = "",
    [string]$RightSample = "",
    [string]$Output = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$python = Join-Path $root ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) {
    throw "Python venv not found: $python"
}

& (Join-Path $PSScriptRoot "test-health.ps1") -BackendUrl $BackendUrl -CadUrl $CadUrl -NoFrontend | Out-Host

$walkthroughArgs = @(
    (Join-Path $root "tools\run_demo_walkthrough.py"),
    "--base-url", $BackendUrl,
    "--username", $Username,
    "--password", $Password
)

if ($Sample) {
    $walkthroughArgs += @("--sample", $Sample)
}
if ($RightSample) {
    $walkthroughArgs += @("--right-sample", $RightSample)
}
if ($Output) {
    $walkthroughArgs += @("--output", $Output)
}

& $python $walkthroughArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "ShipCAD demo walkthrough summary generated."
