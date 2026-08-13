[CmdletBinding()]
param(
    [switch]$KeepRunning,
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "observability-smoke-support.ps1")
$composeFiles = @(
    "-f", (Join-Path $repositoryRoot "docker-compose.postgres.yml"),
    "-f", (Join-Path $repositoryRoot "docker-compose.observability.yml")
)

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$ComposeArguments)
    & docker compose @composeFiles @ComposeArguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: $($ComposeArguments -join ' ')"
    }
}

function Wait-Http {
    param([string]$Uri, [hashtable]$Headers = @{})
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri $Uri -Headers $Headers -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return $response
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Uri"
}

function Wait-LokiQuery {
    param([string]$Marker)
    $query = [uri]::EscapeDataString("{service_name=`"j-store`"} |= `"$Marker`"")
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $result = Invoke-RestMethod -Uri "$($settings.LokiBaseUri)/loki/api/v1/query_range?query=$query&limit=20"
            if ($result.data.result.Count -gt 0) {
                return $result
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)
    throw "Loki did not return marker $Marker"
}

function Query-Prometheus {
    param([string]$Expression)
    $query = [uri]::EscapeDataString($Expression)
    return Invoke-RestMethod -Uri "$($settings.PrometheusBaseUri)/api/v1/query?query=$query"
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI is required. Install Docker Engine/Desktop and Docker Compose v2."
}
if (-not (Test-Path (Join-Path $repositoryRoot ".env"))) {
    throw "Create an untracked .env from .env.example and replace every change-me value."
}

$settings = Get-ObservabilitySmokeSettings -RepositoryRoot $repositoryRoot
$grafanaUser = $settings.GrafanaUser
$grafanaPassword = $settings.GrafanaPassword
if (-not $grafanaPassword -or $grafanaPassword -like "change-me*") {
    throw "GRAFANA_ADMIN_PASSWORD must be set to a non-example value in .env or the process environment."
}

Push-Location $repositoryRoot
try {
    & .\gradlew.bat :j-store-boot:bootJar --console=plain
    if ($LASTEXITCODE -ne 0) { throw "bootJar build failed" }

    Invoke-Compose config --quiet
    Invoke-Compose up --detach --build

    Wait-Http "$($settings.LokiBaseUri)/ready" | Out-Null
    Wait-Http "$($settings.AlloyBaseUri)/-/ready" | Out-Null
    Wait-Http "$($settings.PrometheusBaseUri)/-/ready" | Out-Null
    Wait-Http "$($settings.GrafanaBaseUri)/api/health" | Out-Null
    Wait-Http "$($settings.JStoreBaseUri)/actuator/health" | Out-Null

    $correlationId = "smoke-$([guid]::NewGuid().ToString('N'))"
    $traceId = [guid]::NewGuid().ToString("N")
    $spanId = [guid]::NewGuid().ToString("N").Substring(0, 16)
    $headers = @{
        "X-Correlation-ID" = $correlationId
        "traceparent" = "00-$traceId-$spanId-01"
    }
    $response = Invoke-WebRequest -Uri "$($settings.JStoreBaseUri)/actuator/health" -Headers $headers
    if ($response.Headers["X-Correlation-ID"] -ne $correlationId) {
        throw "Application did not return the supplied correlation ID"
    }
    Wait-LokiQuery $correlationId | Out-Null
    Wait-LokiQuery $traceId | Out-Null

    $up = Query-Prometheus 'up{job="j-store"}'
    if ($up.status -ne "success" -or $up.data.result.Count -eq 0 -or $up.data.result[0].value[1] -ne "1") {
        throw "Prometheus is not successfully scraping j-store"
    }
    $outbox = Query-Prometheus 'jstore_outbox_oldest_ready_lag{transportId="all"}'
    if ($outbox.status -ne "success" -or $outbox.data.result.Count -eq 0) {
        throw "Outbox meters are absent from the Prometheus registry"
    }

    $basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${grafanaUser}:${grafanaPassword}"))
    $grafanaHeaders = @{ Authorization = "Basic $basic" }
    Wait-Http "$($settings.GrafanaBaseUri)/api/datasources/uid/loki/health" $grafanaHeaders | Out-Null
    Wait-Http "$($settings.GrafanaBaseUri)/api/datasources/uid/prometheus/health" $grafanaHeaders | Out-Null

    Invoke-Compose stop loki
    $bufferedId = "buffered-$([guid]::NewGuid().ToString('N'))"
    Invoke-WebRequest -Uri "$($settings.JStoreBaseUri)/actuator/health" -Headers @{"X-Correlation-ID" = $bufferedId} | Out-Null
    Invoke-Compose restart alloy
    Invoke-Compose start loki
    Wait-Http "$($settings.LokiBaseUri)/ready" | Out-Null
    Wait-LokiQuery $bufferedId | Out-Null

    $drops = Query-Prometheus 'sum(loki_write_dropped_entries_total)'
    if ($drops.data.result.Count -gt 0 -and [double]$drops.data.result[0].value[1] -gt 0) {
        throw "Alloy reported dropped log entries during smoke test"
    }
    $parseErrors = Query-Prometheus 'sum(loki_source_docker_target_parsing_errors_total)'
    if ($parseErrors.data.result.Count -gt 0 -and [double]$parseErrors.data.result[0].value[1] -gt 0) {
        throw "Alloy reported Docker log parsing errors during smoke test"
    }

    Write-Host "Observability smoke test passed. correlation_id=$correlationId trace_id=$traceId buffered_id=$bufferedId"
} finally {
    if (-not $KeepRunning -and (Get-Command docker -ErrorAction SilentlyContinue)) {
        Invoke-Compose down
    }
    Pop-Location
}
