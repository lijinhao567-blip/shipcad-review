param(
    [switch]$WithVision,
    [switch]$WithOcr,
    [switch]$NoFrontend,
    [switch]$Force,
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 5173,
    [int]$CadPort = 9000,
    [int]$VisionPort = 9100,
    [int]$OcrPort = 9200,
    [int]$WaitSeconds = 90
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root ".run"
$logDir = Join-Path $runDir "logs"
$statePath = Join-Path $runDir "dev-processes.json"
$javaHome = Join-Path $root ".tools\jdk-17"
$maven = Join-Path $root ".tools\maven\bin\mvn.cmd"
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
        throw "$Name port $Port is already in use. Run deploy\stop-dev.ps1 or choose another port."
    }
}

function Normalize-ProcessPathEnvironment {
    $processEnvironment = [Environment]::GetEnvironmentVariables("Process")
    if ($processEnvironment.Contains("PATH") -and $processEnvironment.Contains("Path")) {
        $pathValue = [Environment]::GetEnvironmentVariable("Path", "Process")
        if (-not $pathValue) {
            $pathValue = [Environment]::GetEnvironmentVariable("PATH", "Process")
        }
        [Environment]::SetEnvironmentVariable("PATH", $null, "Process")
        [Environment]::SetEnvironmentVariable("Path", $pathValue, "Process")
    }
}

function Start-ManagedProcess([string]$Name, [string]$FilePath, [string[]]$ArgumentList, [string]$WorkingDirectory, [int]$Port, [string]$Url) {
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
        port = $Port
        url = $Url
        stdout = $outLog
        stderr = $errLog
    }
}

function Wait-HttpEndpoint([object]$ProcessInfo, [string]$HealthPath, [int]$Seconds) {
    $name = $ProcessInfo.name
    $url = "$($ProcessInfo.url)$HealthPath"
    $deadline = (Get-Date).AddSeconds($Seconds)
    $lastError = ""
    while ((Get-Date) -lt $deadline) {
        if ($ProcessInfo.pid -and -not (Get-Process -Id $ProcessInfo.pid -ErrorAction SilentlyContinue)) {
            throw "$name exited before it became ready. Check logs: $($ProcessInfo.stdout), $($ProcessInfo.stderr)"
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
    throw "$name did not become ready at $url within ${Seconds}s. Last error: $lastError"
}

Assert-PathExists "JDK" $javaHome
Assert-PathExists "Maven" $maven
Assert-PathExists "Python venv" $python
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

if ($Force -and (Test-Path $statePath)) {
    & (Join-Path $PSScriptRoot "stop-dev.ps1") | Out-Host
}

Assert-PortFree "CAD Worker" $CadPort
Assert-PortFree "Backend" $BackendPort
if (-not $NoFrontend) { Assert-PortFree "Frontend" $FrontendPort }
if ($WithVision) { Assert-PortFree "Vision Worker" $VisionPort }
if ($WithOcr) { Assert-PortFree "OCR Worker" $OcrPort }

$env:JAVA_HOME = (Resolve-Path $javaHome).Path
$env:SHIPCAD_WORKER_URL = "http://127.0.0.1:$CadPort"
$env:SHIPCAD_VISION_URL = "http://127.0.0.1:$VisionPort"
$env:SHIPCAD_OCR_URL = "http://127.0.0.1:$OcrPort"
$env:SHIPCAD_CORS_ORIGIN = "http://127.0.0.1:$FrontendPort"
Normalize-ProcessPathEnvironment

$processes = @()
$processes += Start-ManagedProcess "cad-worker" $python @("-m", "uvicorn", "cad_worker.app.main:app", "--host", "127.0.0.1", "--port", "$CadPort") $root $CadPort "http://127.0.0.1:$CadPort"
if ($WithVision) {
    $processes += Start-ManagedProcess "vision-worker" $python @("-m", "uvicorn", "vision_worker.app.main:app", "--host", "127.0.0.1", "--port", "$VisionPort") $root $VisionPort "http://127.0.0.1:$VisionPort"
}
if ($WithOcr) {
    $processes += Start-ManagedProcess "ocr-worker" $python @("-m", "uvicorn", "ocr_worker.app.main:app", "--host", "127.0.0.1", "--port", "$OcrPort") $root $OcrPort "http://127.0.0.1:$OcrPort"
}
$processes += Start-ManagedProcess "backend" $maven @("-f", "backend-spring\pom.xml", "spring-boot:run") $root $BackendPort "http://127.0.0.1:$BackendPort"
if (-not $NoFrontend) {
    $npm = "npm.cmd"
    $processes += Start-ManagedProcess "frontend" $npm @("run", "dev", "--", "--host", "127.0.0.1", "--port", "$FrontendPort") (Join-Path $root "frontend-vue") $FrontendPort "http://127.0.0.1:$FrontendPort"
}

$state = [pscustomobject]@{
    root = $root
    startedAt = (Get-Date).ToString("o")
    backendUrl = "http://127.0.0.1:$BackendPort"
    frontendUrl = "http://127.0.0.1:$FrontendPort"
    cadUrl = "http://127.0.0.1:$CadPort"
    visionUrl = "http://127.0.0.1:$VisionPort"
    ocrUrl = "http://127.0.0.1:$OcrPort"
    withVision = [bool]$WithVision
    withOcr = [bool]$WithOcr
    noFrontend = [bool]$NoFrontend
    processes = $processes
}
$state | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 -Path $statePath

$processByName = @{}
foreach ($entry in $processes) { $processByName[$entry.name] = $entry }
Wait-HttpEndpoint $processByName["cad-worker"] "/health" $WaitSeconds
Wait-HttpEndpoint $processByName["backend"] "/api/health" $WaitSeconds
if ($WithVision) { Wait-HttpEndpoint $processByName["vision-worker"] "/health" $WaitSeconds }
if ($WithOcr) { Wait-HttpEndpoint $processByName["ocr-worker"] "/health" $WaitSeconds }
if (-not $NoFrontend) { Wait-HttpEndpoint $processByName["frontend"] "" $WaitSeconds }

Write-Host "ShipCAD development stack started."
Write-Host "Frontend: http://127.0.0.1:$FrontendPort"
Write-Host "Backend:  http://127.0.0.1:$BackendPort/swagger-ui.html"
Write-Host "CAD:      http://127.0.0.1:$CadPort/docs"
if ($WithVision) { Write-Host "Vision:   http://127.0.0.1:$VisionPort/docs" }
if ($WithOcr) { Write-Host "OCR:      http://127.0.0.1:$OcrPort/docs" }
Write-Host "Logs:     $logDir"
Write-Host ""
& (Join-Path $PSScriptRoot "test-health.ps1") `
    -BackendUrl "http://127.0.0.1:$BackendPort" `
    -FrontendUrl "http://127.0.0.1:$FrontendPort" `
    -CadUrl "http://127.0.0.1:$CadPort" `
    -VisionUrl "http://127.0.0.1:$VisionPort" `
    -OcrUrl "http://127.0.0.1:$OcrPort" `
    -IncludeVision:$WithVision `
    -IncludeOcr:$WithOcr `
    -NoFrontend:$NoFrontend
