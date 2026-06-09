param(
    [string]$BackendUrl = "http://127.0.0.1:8080",
    [string]$FrontendUrl = "http://127.0.0.1:5173",
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 5173,
    [int]$CadPort = 9000,
    [int]$BrowserDebugPort = 9334,
    [int]$WaitSeconds = 180,
    [string]$Sample = "",
    [string]$BrowserPath = "",
    [switch]$ReuseRunning,
    [switch]$KeepRunning,
    [switch]$Headed
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$node = "node"

if (-not $Sample) {
    $Sample = Join-Path $root "datasets\rules\cases\invalid_layer_name.dxf"
}

Push-Location $root
try {
    if (-not $ReuseRunning) {
        & (Join-Path $PSScriptRoot "start-dev.ps1") `
            -Force `
            -BackendPort $BackendPort `
            -FrontendPort $FrontendPort `
            -CadPort $CadPort `
            -WaitSeconds $WaitSeconds | Out-Host
    }

    $args = @(
        "tools\run_dxf_viewer_issue_focus_smoke.mjs",
        "--backend-url", $BackendUrl,
        "--frontend-url", $FrontendUrl,
        "--sample", $Sample,
        "--browser-debug-port", "$BrowserDebugPort",
        "--timeout-ms", "$($WaitSeconds * 1000)"
    )
    if ($BrowserPath) {
        $args += @("--browser-path", $BrowserPath)
    }
    if ($Headed) {
        $args += "--headed"
    }

    & $node @args
    if ($LASTEXITCODE -ne 0) {
        throw "DXF issue focus smoke failed with exit code $LASTEXITCODE"
    }
} finally {
    try {
        if (-not $ReuseRunning -and -not $KeepRunning) {
            & (Join-Path $PSScriptRoot "stop-dev.ps1") | Out-Host
        }
    } finally {
        Pop-Location
    }
}
