$scriptDirectory = Split-Path -Parent $PSScriptRoot
. (Join-Path $scriptDirectory "observability-smoke-support.ps1")
$settingNames = @(
    "JSTORE_PORT",
    "LOKI_PORT",
    "ALLOY_PORT",
    "PROMETHEUS_PORT",
    "GRAFANA_PORT",
    "GRAFANA_ADMIN_USER",
    "GRAFANA_ADMIN_PASSWORD"
)

Describe "observability smoke settings" {
    BeforeEach {
        $script:originalSettingValues = @{}
        foreach ($name in $settingNames) {
            $script:originalSettingValues[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
            [Environment]::SetEnvironmentVariable($name, $null, "Process")
        }
    }

    AfterEach {
        foreach ($name in $settingNames) {
            [Environment]::SetEnvironmentVariable($name, $script:originalSettingValues[$name], "Process")
        }
    }

    It "uses custom compose ports and Grafana user from the repository env file" {
        @(
            "JSTORE_PORT=18080"
            "LOKI_PORT=13100"
            "ALLOY_PORT=12346"
            "PROMETHEUS_PORT=19090"
            "GRAFANA_PORT=13000"
            "GRAFANA_ADMIN_USER=operator"
            "GRAFANA_ADMIN_PASSWORD=synthetic-password"
        ) | Set-Content (Join-Path $TestDrive ".env")

        $settings = Get-ObservabilitySmokeSettings -RepositoryRoot $TestDrive

        $settings.JStoreBaseUri | Should Be "http://localhost:18080"
        $settings.LokiBaseUri | Should Be "http://localhost:13100"
        $settings.AlloyBaseUri | Should Be "http://localhost:12346"
        $settings.PrometheusBaseUri | Should Be "http://localhost:19090"
        $settings.GrafanaBaseUri | Should Be "http://localhost:13000"
        $settings.GrafanaUser | Should Be "operator"
        $settings.GrafanaPassword | Should Be "synthetic-password"
    }

    It "prefers process environment settings over the repository env file" {
        "LOKI_PORT=13100" | Set-Content (Join-Path $TestDrive ".env")
        [Environment]::SetEnvironmentVariable("LOKI_PORT", "23100", "Process")

        $settings = Get-ObservabilitySmokeSettings -RepositoryRoot $TestDrive

        $settings.LokiBaseUri | Should Be "http://localhost:23100"
    }

    It "publishes every observability endpoint on loopback only" {
        $repositoryRoot = Split-Path -Parent $scriptDirectory
        $compose = Get-Content -Raw (Join-Path $repositoryRoot "docker-compose.observability.yml")

        @(
            '127.0.0.1:${JSTORE_PORT:-8080}:8080'
            '127.0.0.1:${LOKI_PORT:-3100}:3100'
            '127.0.0.1:${ALLOY_PORT:-12345}:12345'
            '127.0.0.1:${PROMETHEUS_PORT:-9090}:9090'
            '127.0.0.1:${GRAFANA_PORT:-3000}:3000'
        ) | ForEach-Object {
            $compose | Should Match ([regex]::Escape($_))
        }
    }
}
