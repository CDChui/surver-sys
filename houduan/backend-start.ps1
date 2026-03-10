Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Set-Location 'd:\code\surver-sys\houduan'
.\run.ps1 -JavaHome 'C:\Program Files\Microsoft\jdk-17.0.11.9-hotspot' -NoDeps -SkipTests spring-boot:run
