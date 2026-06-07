param(
    [string]$Dm8Home = $(if ($env:DM8_HOME) { $env:DM8_HOME } else { "D:\dm8task" }),
    [int]$Port = 5237
)

$ErrorActionPreference = "Stop"

$listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if (-not $listener) {
    Write-Host "No process is listening on ShipCAD DM8 port $Port."
    exit 0
}

$expectedServer = [System.IO.Path]::GetFullPath((Join-Path $Dm8Home "bin\dmserver.exe"))
$process = Get-Process -Id $listener.OwningProcess
$actualPath = [System.IO.Path]::GetFullPath($process.Path)
if (-not $actualPath.Equals($expectedServer, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to stop PID $($process.Id): port $Port belongs to $actualPath"
}

Stop-Process -Id $process.Id
Write-Host "ShipCAD DM8 stopped (PID $($process.Id))."
