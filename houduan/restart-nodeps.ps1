param(
    [int]$HealthTimeoutSeconds = 90
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Set-Location -Path $PSScriptRoot

function Stop-Port8080 {
    $listenPids = netstat -ano |
        Select-String 'LISTENING' |
        Select-String ':8080' |
        ForEach-Object { ($_ -split '\s+')[-1] } |
        Select-Object -Unique

    foreach ($id in $listenPids) {
        try {
            Stop-Process -Id ([int]$id) -Force -ErrorAction Stop
        } catch {
            # ignore non-critical stop errors
        }
    }
}

function Wait-Health([int]$Seconds) {
    for ($i = 0; $i -lt $Seconds; $i++) {
        try {
            $resp = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8080/api/test' -TimeoutSec 2
            if ($resp.StatusCode -eq 200) {
                return $true
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    return $false
}

Write-Host "[restart-nodeps.ps1] Building latest jar..."
& "$PSScriptRoot\run.ps1" -SkipTests package

Write-Host "[restart-nodeps.ps1] Stopping current backend on :8080..."
Stop-Port8080
Start-Sleep -Seconds 1

$javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path $javaExe)) {
    $javaExe = 'java'
}

$jarPath = Join-Path $PSScriptRoot 'target\houduan-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path $jarPath)) {
    throw "Jar not found: $jarPath"
}

$stdoutPath = Join-Path $PSScriptRoot 'target\nodeps-runtime.out.log'
$stderrPath = Join-Path $PSScriptRoot 'target\nodeps-runtime.err.log'
if (Test-Path $stdoutPath) { Remove-Item $stdoutPath -Force }
if (Test-Path $stderrPath) { Remove-Item $stderrPath -Force }

Write-Host "[restart-nodeps.ps1] Starting backend from jar..."
$process = Start-Process -FilePath $javaExe `
    -ArgumentList @('-jar', $jarPath, '--spring.profiles.active=nodeps') `
    -WorkingDirectory $PSScriptRoot `
    -RedirectStandardOutput $stdoutPath `
    -RedirectStandardError $stderrPath `
    -PassThru

Write-Host "[restart-nodeps.ps1] Waiting health: http://127.0.0.1:8080/api/test"
if (Wait-Health -Seconds $HealthTimeoutSeconds) {
    Write-Host "[restart-nodeps.ps1] Backend is UP. PID=$($process.Id)"
    Write-Host "[restart-nodeps.ps1] Logs: $stdoutPath"
    exit 0
}

Write-Host "[restart-nodeps.ps1] Backend failed to become healthy in ${HealthTimeoutSeconds}s."
if (Test-Path $stdoutPath) {
    Write-Host "[restart-nodeps.ps1] Last stdout logs:"
    Get-Content -Path $stdoutPath -Tail 60
}
if (Test-Path $stderrPath) {
    Write-Host "[restart-nodeps.ps1] Last stderr logs:"
    Get-Content -Path $stderrPath -Tail 60
}
exit 1
