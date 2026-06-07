param(
    [string]$MinioExe = "",
    [switch]$DownloadMinio,
    [int]$MinioPort = 9002,
    [int]$MinioConsolePort = 9001,
    [int]$BackendPort = 8085,
    [int]$CadPort = 9000,
    [string]$Bucket = "shipcad-e2e",
    [string]$AccessKey = "shipcadadmin",
    [string]$SecretKey = "shipcadadmin123",
    [int]$WaitSeconds = 90,
    [switch]$KeepRunning
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root ".run\object-storage-e2e"
$logDir = Join-Path $runDir "logs"
$minioData = Join-Path $runDir "minio-data"
$objectCache = Join-Path $runDir "object-cache"
$dbDir = Join-Path $runDir "db"
$javaHome = Join-Path $root ".tools\jdk-17"
$maven = Join-Path $root ".tools\maven\bin\mvn.cmd"
$python = Join-Path $root ".venv\Scripts\python.exe"
$defaultMinio = Join-Path $root ".tools\minio\minio.exe"
$minioDownloadUrl = "https://dl.min.io/server/minio/release/windows-amd64/minio.exe"

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

function Test-HttpOk([string]$Url) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 400
    } catch {
        return $false
    }
}

function Assert-PortFree([string]$Name, [int]$Port) {
    if (Test-PortOpen $Port) {
        throw "$Name port $Port is already in use."
    }
}

function Start-ManagedProcess([string]$Name, [string]$FilePath, [string[]]$ArgumentList, [string]$WorkingDirectory) {
    $outLog = Join-Path $logDir "$Name.out.log"
    $errLog = Join-Path $logDir "$Name.err.log"
    if (Test-Path $outLog) { Remove-Item -LiteralPath $outLog -Force }
    if (Test-Path $errLog) { Remove-Item -LiteralPath $errLog -Force }
    $process = Start-Process -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -PassThru `
        -WindowStyle Hidden
    [pscustomobject]@{
        name = $Name
        pid = $process.Id
        stdout = $outLog
        stderr = $errLog
    }
}

function Wait-HttpEndpoint([object]$ProcessInfo, [string]$Url, [int]$Seconds) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    $lastError = ""
    while ((Get-Date) -lt $deadline) {
        if ($ProcessInfo -and $ProcessInfo.pid -and -not (Get-Process -Id $ProcessInfo.pid -ErrorAction SilentlyContinue)) {
            throw "$($ProcessInfo.name) exited before it became ready. Check logs: $($ProcessInfo.stdout), $($ProcessInfo.stderr)"
        }
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
    throw "Endpoint did not become ready at $Url within ${Seconds}s. Last error: $lastError"
}

function Stop-PortOwner([string]$Name, [int]$Port) {
    $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    foreach ($ownerProcessId in @($connections | Select-Object -ExpandProperty OwningProcess -Unique)) {
        if ($ownerProcessId) {
            Write-Host "Stopping $Name port owner on ${Port}: pid $ownerProcessId"
            Stop-Process -Id $ownerProcessId -Force -ErrorAction SilentlyContinue
        }
    }
}

function Stop-ManagedProcesses([object[]]$Processes, [int[]]$Ports) {
    $items = @($Processes)
    for ($i = $items.Count - 1; $i -ge 0; $i--) {
        $entry = $items[$i]
        if ($entry.pid) {
            Stop-Process -Id $entry.pid -Force -ErrorAction SilentlyContinue
        }
    }
    Start-Sleep -Seconds 2
    foreach ($port in @($Ports | Select-Object -Unique)) {
        Stop-PortOwner "managed service" $port
    }
}

if (-not $MinioExe) {
    $MinioExe = $defaultMinio
}

if ($DownloadMinio -or -not (Test-Path $MinioExe)) {
    if ($MinioExe -ne $defaultMinio) {
        throw "Automatic download only writes to project .tools path. Omit -MinioExe or download manually to: $MinioExe"
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $defaultMinio) | Out-Null
    Write-Host "Downloading MinIO server to $defaultMinio"
    Invoke-WebRequest -Uri $minioDownloadUrl -OutFile $defaultMinio -UseBasicParsing -TimeoutSec 300
}

Assert-PathExists "MinIO server" $MinioExe
Assert-PathExists "JDK" $javaHome
Assert-PathExists "Maven" $maven
Assert-PathExists "Python venv" $python

if (Test-Path $runDir) {
    $resolvedRun = (Resolve-Path $runDir).Path
    $resolvedRoot = (Resolve-Path $root).Path
    if (-not $resolvedRun.StartsWith($resolvedRoot)) {
        throw "Refusing to clean unexpected run directory: $resolvedRun"
    }
    Remove-Item -LiteralPath $resolvedRun -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $logDir, $minioData, $objectCache, $dbDir | Out-Null

Assert-PortFree "MinIO API" $MinioPort
Assert-PortFree "MinIO Console" $MinioConsolePort
Assert-PortFree "Backend" $BackendPort

$started = @()
$cadStarted = $false
try {
    if (-not (Test-HttpOk "http://127.0.0.1:$CadPort/health")) {
        Assert-PortFree "CAD Worker" $CadPort
        $started += Start-ManagedProcess `
            "cad-worker" `
            $python `
            @("-m", "uvicorn", "cad_worker.app.main:app", "--host", "127.0.0.1", "--port", "$CadPort") `
            $root
        $cadStarted = $true
        Wait-HttpEndpoint $started[-1] "http://127.0.0.1:$CadPort/health" $WaitSeconds
    }

    $env:MINIO_ROOT_USER = $AccessKey
    $env:MINIO_ROOT_PASSWORD = $SecretKey
    $started += Start-ManagedProcess `
        "minio" `
        $MinioExe `
        @("server", $minioData, "--address", "127.0.0.1:$MinioPort", "--console-address", "127.0.0.1:$MinioConsolePort") `
        $root
    Wait-HttpEndpoint $started[-1] "http://127.0.0.1:$MinioPort/minio/health/live" $WaitSeconds

    $dbFile = (Join-Path $dbDir "shipcad-minio-e2e") -replace "\\", "/"
    $env:JAVA_HOME = (Resolve-Path $javaHome).Path
    $env:SPRING_PROFILES_ACTIVE = "dev"
    $env:SERVER_PORT = "$BackendPort"
    $env:SHIPCAD_WORKER_URL = "http://127.0.0.1:$CadPort"
    $env:SHIPCAD_DATASOURCE_URL = "jdbc:h2:file:$dbFile;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
    $env:SHIPCAD_STORAGE_ROOT = $runDir
    $env:SHIPCAD_OBJECT_STORAGE_MODE = "s3"
    $env:SHIPCAD_OBJECT_STORAGE_CACHE_ROOT = $objectCache
    $env:SHIPCAD_S3_ENDPOINT = "http://127.0.0.1:$MinioPort"
    $env:SHIPCAD_S3_REGION = "us-east-1"
    $env:SHIPCAD_S3_BUCKET = $Bucket
    $env:SHIPCAD_S3_ACCESS_KEY = $AccessKey
    $env:SHIPCAD_S3_SECRET_KEY = $SecretKey
    $env:SHIPCAD_S3_PATH_STYLE = "true"
    $env:SHIPCAD_S3_CREATE_BUCKET = "true"

    $started += Start-ManagedProcess `
        "backend-s3" `
        $maven `
        @("-f", "backend-spring\pom.xml", "spring-boot:run", "-q") `
        $root
    Wait-HttpEndpoint $started[-1] "http://127.0.0.1:$BackendPort/api/health" $WaitSeconds

    $health = Invoke-RestMethod -Uri "http://127.0.0.1:$BackendPort/api/health" -TimeoutSec 10
    if ($health.status -ne "ok" -or $health.storage.mode -ne "s3" -or $health.storage.status -ne "ok") {
        throw "Backend health did not report ok S3 storage: $($health | ConvertTo-Json -Depth 6)"
    }

    $healthLog = Join-Path $logDir "health-check.log"
    $healthOutput = & powershell.exe `
        -NoProfile `
        -ExecutionPolicy Bypass `
        -File (Join-Path $PSScriptRoot "test-health.ps1") `
        -BackendUrl "http://127.0.0.1:$BackendPort" `
        -CadUrl "http://127.0.0.1:$CadPort" `
        -NoFrontend 2>&1
    $healthExitCode = $LASTEXITCODE
    $healthOutput | Tee-Object -FilePath $healthLog | Out-Host
    if ($healthExitCode -ne 0) {
        throw "Health check failed with exit code $healthExitCode. See log: $healthLog"
    }

    $goldenLog = Join-Path $logDir "golden-e2e.log"
    $goldenOutput = & $python (Join-Path $root "tools\run_golden_e2e.py") `
        --base-url "http://127.0.0.1:$BackendPort" `
        --keep-going `
        --evict-upload-cache 2>&1
    $goldenExitCode = $LASTEXITCODE
    $goldenOutput | Tee-Object -FilePath $goldenLog | Out-Host
    if ($goldenExitCode -ne 0) {
        throw "Golden E2E failed with exit code $goldenExitCode. See log: $goldenLog"
    }

    Write-Host "Object storage E2E passed against MinIO."
    Write-Host "MinIO API: http://127.0.0.1:$MinioPort"
    Write-Host "MinIO console: http://127.0.0.1:$MinioConsolePort"
    Write-Host "Logs: $logDir"
} finally {
    if (-not $KeepRunning) {
        $portsToStop = @($BackendPort, $MinioPort, $MinioConsolePort)
        if ($cadStarted) {
            $portsToStop += $CadPort
        }
        Stop-ManagedProcesses $started $portsToStop
        if (-not $cadStarted) {
            Write-Host "Existing CAD Worker was reused and left running."
        }
    } else {
        Write-Host "KeepRunning requested; started services are still running."
    }
}
