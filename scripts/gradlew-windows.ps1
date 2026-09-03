Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$gradleArguments = @($args)

if (-not [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform(
        [System.Runtime.InteropServices.OSPlatform]::Windows
    )) {
    throw "scripts/gradlew-windows.ps1 can only run on Windows."
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$gradleWrapper = Join-Path $repositoryRoot "gradlew.bat"
$configuredJavaTemp = [Environment]::GetEnvironmentVariable(
    "JSTORE_JAVA_TMPDIR",
    [EnvironmentVariableTarget]::Process
)
$javaTempDirectory = if ([string]::IsNullOrWhiteSpace($configuredJavaTemp)) {
    Join-Path ([System.IO.Path]::GetPathRoot($repositoryRoot)) "jstore-jvm-tmp"
} else {
    $configuredJavaTemp
}

if (-not [System.IO.Path]::IsPathRooted($javaTempDirectory)) {
    throw "JSTORE_JAVA_TMPDIR must be an absolute Windows path: $javaTempDirectory"
}

$javaTempDirectory = [System.IO.Path]::GetFullPath($javaTempDirectory)
New-Item -ItemType Directory -Path $javaTempDirectory -Force | Out-Null

$previousTemp = [Environment]::GetEnvironmentVariable(
    "TEMP",
    [EnvironmentVariableTarget]::Process
)
$previousTmp = [Environment]::GetEnvironmentVariable(
    "TMP",
    [EnvironmentVariableTarget]::Process
)
$gradleExitCode = 1

try {
    [Environment]::SetEnvironmentVariable(
        "TEMP",
        $javaTempDirectory,
        [EnvironmentVariableTarget]::Process
    )
    [Environment]::SetEnvironmentVariable(
        "TMP",
        $javaTempDirectory,
        [EnvironmentVariableTarget]::Process
    )

    & $gradleWrapper @gradleArguments
    $gradleExitCode = $LASTEXITCODE
} finally {
    [Environment]::SetEnvironmentVariable(
        "TEMP",
        $previousTemp,
        [EnvironmentVariableTarget]::Process
    )
    [Environment]::SetEnvironmentVariable(
        "TMP",
        $previousTmp,
        [EnvironmentVariableTarget]::Process
    )
}

exit $gradleExitCode
