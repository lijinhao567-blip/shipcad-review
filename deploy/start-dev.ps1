$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$javaHome = Join-Path $root ".tools\jdk-17"
$maven = Join-Path $root ".tools\maven\bin\mvn.cmd"
$python = Join-Path $root ".venv\Scripts\python.exe"

if (-not (Test-Path $javaHome)) { throw "JDK not found: $javaHome" }
if (-not (Test-Path $maven)) { throw "Maven not found: $maven" }
if (-not (Test-Path $python)) { throw "Python venv not found: $python" }

$env:JAVA_HOME = (Resolve-Path $javaHome).Path

Start-Process powershell -WindowStyle Hidden -ArgumentList "-NoExit", "-Command", "cd '$root'; & '$python' -m uvicorn cad_worker.app.main:app --host 127.0.0.1 --port 9000"
Start-Sleep -Seconds 2
if ($env:START_VISION_WORKER -eq "1") {
    Start-Process powershell -WindowStyle Hidden -ArgumentList "-NoExit", "-Command", "cd '$root'; & '$python' -m uvicorn vision_worker.app.main:app --host 127.0.0.1 --port 9100"
    Start-Sleep -Seconds 2
}
Start-Process powershell -WindowStyle Hidden -ArgumentList "-NoExit", "-Command", "cd '$root'; `$env:JAVA_HOME='$env:JAVA_HOME'; & '$maven' -f backend-spring\pom.xml spring-boot:run"
Start-Sleep -Seconds 8
Start-Process powershell -WindowStyle Hidden -ArgumentList "-NoExit", "-Command", "cd '$root\frontend-vue'; npm run dev"

Write-Host "ShipCAD MVP started:"
Write-Host "Frontend: http://127.0.0.1:5173"
Write-Host "Backend:  http://127.0.0.1:8080/swagger-ui.html"
Write-Host "Worker:   http://127.0.0.1:9000/docs"
if ($env:START_VISION_WORKER -eq "1") {
    Write-Host "Vision:   http://127.0.0.1:9100/docs"
}
