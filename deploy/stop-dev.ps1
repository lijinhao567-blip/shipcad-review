param(
    [string]$StatePath = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
if (-not $StatePath) {
    $StatePath = Join-Path $root ".run\dev-processes.json"
}

if (-not (Test-Path $StatePath)) {
    Write-Host "No dev process state file found: $StatePath"
    exit 0
}

$state = Get-Content -Raw -Encoding UTF8 -Path $StatePath | ConvertFrom-Json

foreach ($entry in @($state.processes)) {
    $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
    if ($process) {
        Write-Host "Stopping $($entry.name) pid=$($entry.pid)"
        Stop-Process -Id $entry.pid -Force -ErrorAction SilentlyContinue
    }
}

Start-Sleep -Seconds 2

foreach ($entry in @($state.processes)) {
    if (-not $entry.port) { continue }
    $listenerPids = @()
    $listeners = Get-NetTCPConnection -LocalPort ([int]$entry.port) -State Listen -ErrorAction SilentlyContinue
    foreach ($listener in @($listeners)) {
        if ($listener.OwningProcess -and $listener.OwningProcess -ne 0) {
            $listenerPids += [int]$listener.OwningProcess
        }
    }
    if (-not $listenerPids.Count) {
        $pattern = ":$($entry.port)\s+.*LISTENING\s+(\d+)"
        $netstatMatches = netstat -ano | Select-String -Pattern $pattern
        foreach ($match in @($netstatMatches)) {
            if ($match.Matches.Count -gt 0) {
                $listenerPids += [int]$match.Matches[0].Groups[1].Value
            }
        }
    }
    foreach ($listenerProcessId in ($listenerPids | Sort-Object -Unique)) {
        if ($listenerProcessId -and $listenerProcessId -ne 0) {
            Write-Host "Stopping listener on port $($entry.port), pid=$listenerProcessId"
            Stop-Process -Id $listenerProcessId -Force -ErrorAction SilentlyContinue
        }
    }
}

Remove-Item -LiteralPath $StatePath -Force
Write-Host "ShipCAD development stack stopped."
