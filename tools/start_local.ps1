$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $projectRoot "backend"
Set-Location -LiteralPath $backendRoot
$env:SPRING_PROFILES_ACTIVE = "local"
Write-Host "SportMap을 H2 개발 프로필로 시작합니다." -ForegroundColor Cyan
& ".\gradlew.bat" bootRun
exit $LASTEXITCODE
