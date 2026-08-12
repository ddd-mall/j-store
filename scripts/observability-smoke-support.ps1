function ConvertFrom-ObservabilityDotEnv {
    param([Parameter(Mandatory = $true)][string]$Path)

    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        $candidate = $line.Trim()
        if (-not $candidate -or $candidate.StartsWith("#")) {
            continue
        }

        $separator = $candidate.IndexOf("=")
        if ($separator -le 0) {
            continue
        }

        $name = $candidate.Substring(0, $separator).Trim()
        if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
            continue
        }

        $value = $candidate.Substring($separator + 1).Trim()
        if ($value.Length -ge 2) {
            $first = $value[0]
            $last = $value[$value.Length - 1]
            if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }
        $values[$name] = $value
    }

    return $values
}

function Resolve-ObservabilitySetting {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][hashtable]$DotEnv,
        [AllowNull()][string]$DefaultValue
    )

    $processValue = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ($processValue) {
        return $processValue
    }
    if ($DotEnv.ContainsKey($Name) -and $DotEnv[$Name]) {
        return $DotEnv[$Name]
    }
    return $DefaultValue
}

function Get-ObservabilitySmokeSettings {
    param([Parameter(Mandatory = $true)][string]$RepositoryRoot)

    $dotEnv = ConvertFrom-ObservabilityDotEnv -Path (Join-Path $RepositoryRoot ".env")
    $jstorePort = Resolve-ObservabilitySetting "JSTORE_PORT" $dotEnv "8080"
    $lokiPort = Resolve-ObservabilitySetting "LOKI_PORT" $dotEnv "3100"
    $alloyPort = Resolve-ObservabilitySetting "ALLOY_PORT" $dotEnv "12345"
    $prometheusPort = Resolve-ObservabilitySetting "PROMETHEUS_PORT" $dotEnv "9090"
    $grafanaPort = Resolve-ObservabilitySetting "GRAFANA_PORT" $dotEnv "3000"

    return [pscustomobject]@{
        JStoreBaseUri = "http://localhost:$jstorePort"
        LokiBaseUri = "http://localhost:$lokiPort"
        AlloyBaseUri = "http://localhost:$alloyPort"
        PrometheusBaseUri = "http://localhost:$prometheusPort"
        GrafanaBaseUri = "http://localhost:$grafanaPort"
        GrafanaUser = Resolve-ObservabilitySetting "GRAFANA_ADMIN_USER" $dotEnv "admin"
        GrafanaPassword = Resolve-ObservabilitySetting "GRAFANA_ADMIN_PASSWORD" $dotEnv $null
    }
}
