param(
    [string]$RedisServerExe = "",
    [string]$RedisCliExe = "",
    [switch]$DownloadRedis,
    [int]$RedisPort = 6380,
    [int]$BackendPort = 8086,
    [int]$CadPort = 9000,
    [int]$WaitSeconds = 90,
    [switch]$KeepRunning
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root ".run\redis-queue-e2e"
$logDir = Join-Path $runDir "logs"
$redisData = Join-Path $runDir "redis-data"
$storageRoot = Join-Path $runDir "storage"
$dbDir = Join-Path $runDir "db"
$javaHome = Join-Path $root ".tools\jdk-17"
$maven = Join-Path $root ".tools\maven\bin\mvn.cmd"
$python = Join-Path $root ".venv\Scripts\python.exe"
$redisVersion = "8.8.0"
$redisPackage = "Redis-$redisVersion-Windows-x64-msys2"
$defaultRedisDir = Join-Path $root ".tools\redis-windows\$redisVersion\$redisPackage"
$defaultRedisServer = Join-Path $defaultRedisDir "redis-server.exe"
$defaultRedisCli = Join-Path $defaultRedisDir "redis-cli.exe"
$redisZip = Join-Path $root ".tools\downloads\$redisPackage.zip"
$redisDownloadUrl = "https://github.com/taizod1024/redis-windows-fork/releases/download/$redisVersion/$redisPackage.zip"
$redisSha256 = "d41cd514ab9d9d20a99feb9d90e9c67aa90af6d983050ffb237a3d8778117238"

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

function Start-ManagedProcess([string]$Name, [string]$FilePath, [string]$ArgumentList, [string]$WorkingDirectory) {
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

function Wait-RedisPing([object]$ProcessInfo, [string]$Cli, [int]$Port, [int]$Seconds) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if ($ProcessInfo -and $ProcessInfo.pid -and -not (Get-Process -Id $ProcessInfo.pid -ErrorAction SilentlyContinue)) {
            throw "$($ProcessInfo.name) exited before Redis became ready. Check logs: $($ProcessInfo.stdout), $($ProcessInfo.stderr)"
        }
        $pong = & $Cli -p $Port ping 2>$null
        if ($LASTEXITCODE -eq 0 -and "$pong".Trim() -eq "PONG") {
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "Redis did not answer PONG on port $Port within ${Seconds}s."
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

function Install-RedisIfNeeded {
    if ($DownloadRedis -or -not (Test-Path $defaultRedisServer) -or -not (Test-Path $defaultRedisCli)) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $redisZip) | Out-Null
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $defaultRedisDir) | Out-Null
        if (-not (Test-Path $redisZip)) {
            Write-Host "Downloading Redis Windows fork to $redisZip"
            Invoke-WebRequest -Uri $redisDownloadUrl -OutFile $redisZip -UseBasicParsing -TimeoutSec 300
        }
        $actual = (Get-FileHash $redisZip -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne $redisSha256) {
            throw "Redis zip SHA256 mismatch. Expected $redisSha256 but got $actual"
        }
        if (Test-Path $defaultRedisDir) {
            Remove-Item -LiteralPath $defaultRedisDir -Recurse -Force
        }
        Expand-Archive -LiteralPath $redisZip -DestinationPath (Split-Path -Parent $defaultRedisDir) -Force
    }
}

if (-not $RedisServerExe -or -not $RedisCliExe) {
    Install-RedisIfNeeded
}
if (-not $RedisServerExe) {
    $RedisServerExe = $defaultRedisServer
}
if (-not $RedisCliExe) {
    $RedisCliExe = $defaultRedisCli
}

Assert-PathExists "Redis server" $RedisServerExe
Assert-PathExists "Redis CLI" $RedisCliExe
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
New-Item -ItemType Directory -Force -Path $logDir, $redisData, $storageRoot, $dbDir | Out-Null

Assert-PortFree "Redis" $RedisPort
Assert-PortFree "Backend" $BackendPort

$started = @()
$cadStarted = $false
try {
    if (-not (Test-HttpOk "http://127.0.0.1:$CadPort/health")) {
        Assert-PortFree "CAD Worker" $CadPort
        $cadArgs = "-m uvicorn cad_worker.app.main:app --host 127.0.0.1 --port $CadPort"
        $started += Start-ManagedProcess "cad-worker" $python $cadArgs $root
        $cadStarted = $true
        Wait-HttpEndpoint $started[-1] "http://127.0.0.1:$CadPort/health" $WaitSeconds
    }

    $redisArgs = "--bind 127.0.0.1 --port $RedisPort --save `"`" --appendonly no --dir `"$((Resolve-Path $redisData).Path)`""
    $started += Start-ManagedProcess "redis" $RedisServerExe $redisArgs (Split-Path -Parent $RedisServerExe)
    Wait-RedisPing $started[-1] $RedisCliExe $RedisPort $WaitSeconds

    $dbFile = (Join-Path $dbDir "shipcad-redis-e2e") -replace "\\", "/"
    $env:JAVA_HOME = (Resolve-Path $javaHome).Path
    $env:SPRING_PROFILES_ACTIVE = "dev"
    $env:SERVER_PORT = "$BackendPort"
    $env:SHIPCAD_WORKER_URL = "http://127.0.0.1:$CadPort"
    $env:SHIPCAD_DATASOURCE_URL = "jdbc:h2:file:$dbFile;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
    $env:SHIPCAD_STORAGE_ROOT = $storageRoot
    $env:SHIPCAD_OBJECT_STORAGE_MODE = "local"
    $env:SHIPCAD_OBJECT_STORAGE_LOCAL_ROOT = $storageRoot
    $env:SHIPCAD_REVIEW_QUEUE_MODE = "redis"
    $env:SHIPCAD_REDIS_HOST = "127.0.0.1"
    $env:SHIPCAD_REDIS_PORT = "$RedisPort"
    $env:SHIPCAD_REVIEW_QUEUE_REDIS_KEY = "shipcad:review:queue:e2e"
    $env:SHIPCAD_REVIEW_QUEUE_REDIS_PROCESSING_KEY = "shipcad:review:processing:e2e"
    $env:SHIPCAD_REVIEW_QUEUE_REDIS_POLL_SECONDS = "1"

    $started += Start-ManagedProcess `
        "backend-redis" `
        $maven `
        "-f backend-spring\pom.xml spring-boot:run -q" `
        $root
    Wait-HttpEndpoint $started[-1] "http://127.0.0.1:$BackendPort/api/health" $WaitSeconds

    $health = Invoke-RestMethod -Uri "http://127.0.0.1:$BackendPort/api/health" -TimeoutSec 10
    if ($health.status -ne "ok" -or $health.queue.mode -ne "redis" -or $health.queue.status -ne "ok") {
        throw "Backend health did not report ok Redis queue: $($health | ConvertTo-Json -Depth 8)"
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
        --keep-going 2>&1
    $goldenExitCode = $LASTEXITCODE
    $goldenOutput | Tee-Object -FilePath $goldenLog | Out-Host
    if ($goldenExitCode -ne 0) {
        throw "Golden E2E failed with exit code $goldenExitCode. See log: $goldenLog"
    }

    $queueLength = & $RedisCliExe -p $RedisPort llen $env:SHIPCAD_REVIEW_QUEUE_REDIS_KEY
    $processingLength = & $RedisCliExe -p $RedisPort llen $env:SHIPCAD_REVIEW_QUEUE_REDIS_PROCESSING_KEY
    Write-Host "Redis queue E2E passed."
    Write-Host "Redis: 127.0.0.1:$RedisPort"
    Write-Host "Backend: http://127.0.0.1:$BackendPort"
    Write-Host "Queue length: $queueLength"
    Write-Host "Processing length: $processingLength"
    Write-Host "Logs: $logDir"
} finally {
    if (-not $KeepRunning) {
        $portsToStop = @($BackendPort, $RedisPort)
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
