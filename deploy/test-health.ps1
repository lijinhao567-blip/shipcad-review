param(
    [string]$BackendUrl = "http://127.0.0.1:8080",
    [string]$FrontendUrl = "http://127.0.0.1:5173",
    [string]$CadUrl = "http://127.0.0.1:9000",
    [string]$VisionUrl = "http://127.0.0.1:9100",
    [string]$OcrUrl = "http://127.0.0.1:9200",
    [switch]$IncludeVision,
    [switch]$IncludeOcr,
    [switch]$NoFrontend,
    [switch]$Json,
    [int]$TimeoutSeconds = 5
)

$ErrorActionPreference = "Stop"

function Test-Endpoint([string]$Name, [string]$Url, [bool]$Required) {
    $result = [ordered]@{
        name = $Name
        url = $Url
        required = $Required
        ok = $false
        statusCode = $null
        detail = ""
    }
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec $TimeoutSeconds
        $result.statusCode = [int]$response.StatusCode
        $result.ok = $response.StatusCode -ge 200 -and $response.StatusCode -lt 400
        if ($response.Content) {
            try {
                $body = $response.Content | ConvertFrom-Json
                if ($body.status) {
                    $result.detail = "status=$($body.status)"
                }
                if ($body.database) {
                    $result.detail = "$($result.detail) database=$($body.database.status)".Trim()
                }
                if ($body.queue) {
                    $queueDetail = "queue=$($body.queue.status)"
                    if ($body.queue.mode) {
                        $queueDetail = "$queueDetail/$($body.queue.mode)"
                    }
                    if ($null -ne $body.queue.queuedCount) {
                        $queueDetail = "$queueDetail queued=$($body.queue.queuedCount)"
                    }
                    if ($null -ne $body.queue.processingCount) {
                        $queueDetail = "$queueDetail processing=$($body.queue.processingCount)"
                    }
                    $result.detail = "$($result.detail) $queueDetail".Trim()
                }
                if ($body.storage) {
                    $storageDetail = "storage=$($body.storage.status)"
                    if ($body.storage.mode) {
                        $storageDetail = "$storageDetail/$($body.storage.mode)"
                    }
                    if ($body.storage.bucket) {
                        $storageDetail = "$storageDetail bucket=$($body.storage.bucket)"
                    }
                    $result.detail = "$($result.detail) $storageDetail".Trim()
                }
                if ($body.engine) {
                    $result.detail = "$($result.detail) engine=$($body.engine)".Trim()
                }
            } catch {
                $result.detail = "HTTP $($response.StatusCode)"
            }
        } else {
            $result.detail = "HTTP $($response.StatusCode)"
        }
    } catch {
        $result.detail = $_.Exception.Message
    }
    [pscustomobject]$result
}

$checks = @()
$checks += Test-Endpoint "backend-api" "$BackendUrl/api/health" $true
$checks += Test-Endpoint "backend-openapi" "$BackendUrl/swagger-ui.html" $true
$checks += Test-Endpoint "cad-worker" "$CadUrl/health" $true
$checks += Test-Endpoint "cad-capabilities" "$CadUrl/capabilities" $true
if (-not $NoFrontend) {
    $checks += Test-Endpoint "frontend" $FrontendUrl $true
}
if ($IncludeVision) {
    $checks += Test-Endpoint "vision-worker" "$VisionUrl/health" $true
    $checks += Test-Endpoint "vision-capabilities" "$VisionUrl/capabilities" $true
}
if ($IncludeOcr) {
    $checks += Test-Endpoint "ocr-worker" "$OcrUrl/health" $true
    $checks += Test-Endpoint "ocr-capabilities" "$OcrUrl/capabilities" $true
}

$failed = @($checks | Where-Object { $_.required -and -not $_.ok })
$summary = [pscustomobject]@{
    status = if ($failed.Count -eq 0) { "ok" } else { "failed" }
    checkedAt = (Get-Date).ToString("o")
    checks = $checks
}

if ($Json) {
    $summary | ConvertTo-Json -Depth 6
} else {
    $checks | Format-Table name, ok, statusCode, url, detail -AutoSize
    Write-Host "Health status: $($summary.status)"
}

if ($failed.Count -gt 0) {
    exit 1
}
