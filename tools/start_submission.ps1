$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$secretPath = Join-Path $projectRoot "config\submission-secrets.properties"
if (-not (Test-Path -LiteralPath $secretPath)) {
  throw "제출용 설정이 없습니다. 먼저 .\tools\initialize_submission_mysql.ps1을 실행하세요."
}

Set-Location -LiteralPath $projectRoot
$env:SPRING_PROFILES_ACTIVE = "submission"
Write-Host "SportMap을 제출용 MySQL 프로필로 시작합니다." -ForegroundColor Cyan
Write-Host "종료하려면 Ctrl+C를 누르세요."
& ".\gradlew.bat" bootRun
exit $LASTEXITCODE
