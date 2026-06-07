param(
    [string]$Dm8Home = $(if ($env:DM8_HOME) { $env:DM8_HOME } else { "D:\dm8task" }),
    [string]$IniPath = $(if ($env:SHIPCAD_DM8_INI) { $env:SHIPCAD_DM8_INI } else { "D:\dm8task\data\SHIPCAD\dm.ini" }),
    [int]$Port = 5237
)

$ErrorActionPreference = "Stop"

$listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "ShipCAD DM8 is already listening on port $Port (PID $($listener.OwningProcess))."
    exit 0
}

$server = Join-Path $Dm8Home "bin\dmserver.exe"
if (-not (Test-Path -LiteralPath $server)) {
    throw "DM8 server executable not found: $server"
}
if (-not (Test-Path -LiteralPath $IniPath)) {
    throw "ShipCAD DM8 configuration not found: $IniPath"
}

$process = Start-Process `
    -FilePath $server `
    -ArgumentList @($IniPath, "-noconsole") `
    -WorkingDirectory (Split-Path $server) `
    -WindowStyle Hidden `
    -PassThru

$deadline = (Get-Date).AddSeconds(30)
do {
    Start-Sleep -Milliseconds 500
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
} while (-not $listener -and -not $process.HasExited -and (Get-Date) -lt $deadline)

if (-not $listener) {
    throw "ShipCAD DM8 did not start on port $Port. Process ID: $($process.Id)"
}

Write-Host "ShipCAD DM8 started on port $Port (PID $($listener.OwningProcess))."
