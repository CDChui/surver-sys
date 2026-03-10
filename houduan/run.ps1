param(
    [string[]]$Goals = @("compile"),
    [switch]$SkipTests,
    [string]$JavaHome,
    [switch]$PersistJavaHome,
    [switch]$Hint,
    [switch]$NoDeps,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-JavaHomeFromJavaCommand {
    try {
        $javaCommand = Get-Command java -ErrorAction Stop
        $javaExeDir = Split-Path $javaCommand.Source -Parent
        return Split-Path $javaExeDir -Parent
    } catch {
        return $null
    }
}

function Ensure-JavaHome {
    if ($JavaHome -and $JavaHome.Trim()) {
        $env:JAVA_HOME = $JavaHome.Trim()
    }

    if (-not $env:JAVA_HOME -or -not $env:JAVA_HOME.Trim()) {
        $detected = Resolve-JavaHomeFromJavaCommand
        if ($detected) {
            $env:JAVA_HOME = $detected
        }
    }

    if (-not $env:JAVA_HOME -or -not $env:JAVA_HOME.Trim()) {
        throw "JAVA_HOME is not set and auto-detect failed."
    }

    $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (-not (Test-Path $javaExe)) {
        throw "JAVA_HOME is invalid: $env:JAVA_HOME"
    }

    $javaBin = Join-Path $env:JAVA_HOME "bin"
    if (($env:Path -split ';') -notcontains $javaBin) {
        $env:Path = "$javaBin;$env:Path"
    }

    if ($PersistJavaHome) {
        setx JAVA_HOME $env:JAVA_HOME | Out-Null
        Write-Host "[run.ps1] Persisted JAVA_HOME=$env:JAVA_HOME (effective in new shell)"
    }
}

function Ensure-MavenWrapper {
    $mvnw = Join-Path $PSScriptRoot "mvnw.cmd"
    if (-not (Test-Path $mvnw)) {
        throw "mvnw.cmd not found in project root: $PSScriptRoot"
    }
    return $mvnw
}

function Show-Hint {
    Write-Host ""
    Write-Host "[run.ps1] Quick commands"
    Write-Host "  0) Start deps (docker):  .\deps.ps1 up"
    Write-Host "  1) Compile (skip tests): .\run.ps1 -SkipTests compile"
    Write-Host "  2) Run tests:            .\run.ps1 test"
    Write-Host "  3) Package jar:          .\run.ps1 -SkipTests package"
    Write-Host "  4) Run app (nodeps):     .\run.ps1 -NoDeps -SkipTests spring-boot:run"
    Write-Host "  5) Persist JAVA_HOME:    .\run.ps1 -PersistJavaHome -SkipTests compile"
    Write-Host "  6) Custom JAVA_HOME:     .\run.ps1 -JavaHome 'C:\Program Files\Microsoft\jdk-17.0.11.9-hotspot' test"
    Write-Host ""
    Write-Host "[run.ps1] Tip: append maven flags after goals, e.g."
    Write-Host "  .\run.ps1 test -Dspring.profiles.active=dev"
    Write-Host ""
}

if ($Hint) {
    Show-Hint
    return
}

Ensure-JavaHome
$mvnwCmd = Ensure-MavenWrapper

# 固定到后端目录执行，避免 nodeps 相对路径数据文件在不同启动目录下漂移
Set-Location -Path $PSScriptRoot

Write-Host "[run.ps1] JAVA_HOME=$env:JAVA_HOME"
& java -version

$invokeArgs = @()
if ($SkipTests) {
    $invokeArgs += "-DskipTests"
}
if (-not $Goals -or $Goals.Count -eq 0) {
    $invokeArgs += "compile"
} else {
    $invokeArgs += $Goals
}
if ($MavenArgs) {
    $invokeArgs += $MavenArgs
}

if ($NoDeps) {
    $isSpringBootRun = $invokeArgs -contains "spring-boot:run"
    if ($isSpringBootRun) {
        $hasProfileArg = $invokeArgs | Where-Object {
            $_ -like "-Dspring-boot.run.profiles=*" -or
            $_ -like "-Dspring.profiles.active=*"
        }
        if (-not $hasProfileArg) {
            $invokeArgs += "-Dspring-boot.run.profiles=nodeps"
        }
    }
}

Write-Host "[run.ps1] Running: .\mvnw.cmd $($invokeArgs -join ' ')"
& $mvnwCmd @invokeArgs
$code = $LASTEXITCODE

if ($code -ne 0) {
    throw "Maven failed with exit code: $code"
}

Write-Host "[run.ps1] Done."
Write-Host "[run.ps1] Need command hints? Run: .\run.ps1 -Hint"
