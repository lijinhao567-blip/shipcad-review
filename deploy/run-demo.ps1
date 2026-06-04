param(
    [string]$BackendUrl = "http://127.0.0.1:8080",
    [string]$CadUrl = "http://127.0.0.1:9000",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [switch]$Multimodal,
    [int]$VisionPort = 9100,
    [int]$OcrPort = 9200,
    [switch]$NoMockWorkers
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$python = Join-Path $root ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) {
    throw "Python venv not found: $python"
}

& (Join-Path $PSScriptRoot "test-health.ps1") -BackendUrl $BackendUrl -CadUrl $CadUrl -NoFrontend | Out-Host

$goldenArgs = @(
    (Join-Path $root "tools\run_golden_e2e.py"),
    "--base-url", $BackendUrl,
    "--username", $Username,
    "--password", $Password,
    "--keep-going"
)
& $python $goldenArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

if ($Multimodal) {
    $multiArgs = @(
        (Join-Path $root "tools\run_multimodal_evidence_e2e.py"),
        "--base-url", $BackendUrl,
        "--username", $Username,
        "--password", $Password,
        "--vision-port", "$VisionPort",
        "--ocr-port", "$OcrPort"
    )
    if ($NoMockWorkers) {
        $multiArgs += "--no-mock-workers"
    }
    & $python $multiArgs
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

Write-Host "ShipCAD demo acceptance flow passed."
