param(
    [string]$ProjectName = "shipcad-compose-e2e",
    [switch]$WithObjectStorage,
    [switch]$NoBuild,
    [switch]$KeepRunning,
    [int]$WaitSeconds = 180
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $PSScriptRoot "docker-compose.yml"
$runDir = Join-Path $root ".run\compose-e2e"
$dataDir = Join-Path $runDir "data"
$minioDataDir = Join-Path $runDir "minio-data"
$logDir = Join-Path $runDir "logs"
$overrideFile = Join-Path $runDir "docker-compose.override.yml"
$python = Join-Path $root ".venv\Scripts\python.exe"

function Assert-PathExists([string]$Label, [string]$Path) {
    if (-not (Test-Path $Path)) {
        throw "$Label not found: $Path"
    }
}

function Test-PortOpen([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(300, $false)) {
            return $false
        }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Assert-PortFree([string]$Name, [int]$Port) {
    if (Test-PortOpen $Port) {
        throw "$Name port $Port is already in use. Stop the existing service or run this E2E on a clean dev machine."
    }
}

function Assert-DockerAvailable {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker CLI was not found. Install Docker Desktop or another Docker Engine with Compose support."
    }
    & docker version | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker is installed but the daemon is not reachable."
    }
    & docker compose version | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose plugin is not available."
    }
}

function Invoke-DockerCompose([string[]]$Arguments) {
    $composeArgs = @("compose", "-f", $composeFile, "-f", $overrideFile, "-p", $ProjectName)
    if ($WithObjectStorage) {
        $composeArgs += @("--profile", "object-storage")
    }
    $composeArgs += $Arguments
    & docker @composeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Invoke-DockerComposeBase([string[]]$Arguments) {
    $composeArgs = @("compose", "-f", $composeFile, "-p", $ProjectName)
    $composeArgs += $Arguments
    & docker @composeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Wait-HttpEndpoint([string]$Name, [string]$Url, [int]$Seconds) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    $lastError = ""
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                return
            }
            $lastError = "HTTP $($response.StatusCode)"
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds 2
    }
    throw "$Name did not become ready at $Url within ${Seconds}s. Last error: $lastError"
}

function Write-ComposeOverride {
    $resolvedData = (Resolve-Path $dataDir).Path -replace "\\", "/"
    $resolvedMinio = (Resolve-Path $minioDataDir).Path -replace "\\", "/"
    @"
services:
  backend:
    volumes:
      - type: bind
        source: "$resolvedData"
        target: /app/data
    environment:
      SHIPCAD_STORAGE_ROOT: /app/data
      SHIPCAD_DATASOURCE_URL: "jdbc:h2:file:/app/data/db/shipcad-compose-e2e;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
      SHIPCAD_OBJECT_STORAGE_LOCAL_ROOT: /app/data
      SHIPCAD_OBJECT_STORAGE_CACHE_ROOT: /app/data/object-cache
      SHIPCAD_REVIEW_QUEUE_REDIS_KEY: shipcad:review:queue:compose-e2e
      SHIPCAD_REVIEW_QUEUE_REDIS_PROCESSING_KEY: shipcad:review:processing:compose-e2e
      SHIPCAD_REVIEW_QUEUE_REDIS_POLL_SECONDS: "1"
  minio:
    volumes:
      - type: bind
        source: "$resolvedMinio"
        target: /data
"@ | Set-Content -Encoding UTF8 -Path $overrideFile
}

function Get-BackendHealth {
    Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/health" -TimeoutSec 10
}

Assert-PathExists "Compose file" $composeFile
Assert-PathExists "Python venv" $python
Assert-DockerAvailable

$env:SPRING_PROFILES_ACTIVE = "dev"
$env:MINIO_ROOT_USER = "shipcad-e2e"
$env:MINIO_ROOT_PASSWORD = "ShipCadE2EPassword123"
$env:SHIPCAD_S3_ACCESS_KEY = $env:MINIO_ROOT_USER
$env:SHIPCAD_S3_SECRET_KEY = $env:MINIO_ROOT_PASSWORD

try {
    Invoke-DockerComposeBase @("down", "--remove-orphans")
} catch {
    Write-Host "No previous compose project to stop, or cleanup was already complete."
}

if (Test-Path $runDir) {
    $resolvedRun = (Resolve-Path $runDir).Path
    $resolvedRoot = (Resolve-Path $root).Path
    if (-not $resolvedRun.StartsWith($resolvedRoot)) {
        throw "Refusing to clean unexpected run directory: $resolvedRun"
    }
    Remove-Item -LiteralPath $resolvedRun -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $dataDir, $minioDataDir, $logDir | Out-Null
Write-ComposeOverride

Assert-PortFree "Backend" 8080
Assert-PortFree "Frontend" 5173
Assert-PortFree "CAD Worker" 9000
Assert-PortFree "Valkey" 6379
if ($WithObjectStorage) {
    Assert-PortFree "MinIO API" 9002
    Assert-PortFree "MinIO Console" 9001
    $env:SHIPCAD_OBJECT_STORAGE_MODE = "s3"
} else {
    $env:SHIPCAD_OBJECT_STORAGE_MODE = "local"
}

$upArgs = @("up", "-d")
if (-not $NoBuild) {
    $upArgs += "--build"
}

try {
    Invoke-DockerCompose $upArgs

    if ($WithObjectStorage) {
        Wait-HttpEndpoint "MinIO" "http://127.0.0.1:9002/minio/health/live" $WaitSeconds
    }
    Wait-HttpEndpoint "CAD Worker" "http://127.0.0.1:9000/health" $WaitSeconds
    Wait-HttpEndpoint "Backend" "http://127.0.0.1:8080/api/health" $WaitSeconds
    Wait-HttpEndpoint "Frontend" "http://127.0.0.1:5173" $WaitSeconds

    $health = Get-BackendHealth
    if ($health.status -ne "ok" -or $health.queue.mode -ne "redis" -or $health.queue.status -ne "ok") {
        throw "Backend health did not report ok Redis/Valkey queue: $($health | ConvertTo-Json -Depth 8)"
    }
    $expectedStorageMode = if ($WithObjectStorage) { "s3" } else { "local" }
    if ($health.storage.mode -ne $expectedStorageMode -or $health.storage.status -ne "ok") {
        throw "Backend health did not report ok $expectedStorageMode storage: $($health | ConvertTo-Json -Depth 8)"
    }

    $healthLog = Join-Path $logDir "health-check.log"
    $healthOutput = & powershell.exe `
        -NoProfile `
        -ExecutionPolicy Bypass `
        -File (Join-Path $PSScriptRoot "test-health.ps1") 2>&1
    $healthExitCode = $LASTEXITCODE
    $healthOutput | Tee-Object -FilePath $healthLog | Out-Host
    if ($healthExitCode -ne 0) {
        throw "Health check failed with exit code $healthExitCode. See log: $healthLog"
    }

    $goldenLog = Join-Path $logDir "golden-e2e.log"
    $goldenArgs = @((Join-Path $root "tools\run_golden_e2e.py"), "--base-url", "http://127.0.0.1:8080", "--keep-going")
    if ($WithObjectStorage) {
        $goldenArgs += "--evict-upload-cache"
    }
    $goldenOutput = & $python @goldenArgs 2>&1
    $goldenExitCode = $LASTEXITCODE
    $goldenOutput | Tee-Object -FilePath $goldenLog | Out-Host
    if ($goldenExitCode -ne 0) {
        throw "Golden E2E failed with exit code $goldenExitCode. See log: $goldenLog"
    }

    Write-Host "Docker Compose E2E passed."
    Write-Host "Frontend: http://127.0.0.1:5173"
    Write-Host "Backend:  http://127.0.0.1:8080/swagger-ui.html"
    Write-Host "CAD:      http://127.0.0.1:9000/docs"
    if ($WithObjectStorage) {
        Write-Host "MinIO:    http://127.0.0.1:9001"
    }
    Write-Host "Logs:     $logDir"
} finally {
    if (-not $KeepRunning) {
        try {
            Invoke-DockerCompose @("down", "--remove-orphans")
        } catch {
            Write-Warning "Compose cleanup failed: $($_.Exception.Message)"
        }
    } else {
        Write-Host "KeepRunning requested; compose services are still running."
    }
}
