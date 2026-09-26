param([string]$MySqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin")

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$secretPath = Join-Path $projectRoot "config\submission-secrets.properties"
$dump = Join-Path $MySqlBin "mysqldump.exe"
if (-not (Test-Path -LiteralPath $secretPath)) { throw "제출용 비밀 설정이 없습니다." }
if (-not (Test-Path -LiteralPath $dump)) { throw "mysqldump.exe를 찾지 못했습니다." }

$properties = @{}
Get-Content -LiteralPath $secretPath | ForEach-Object {
  if ($_ -match '^\s*([^#][^=]*)=(.*)$') { $properties[$matches[1].Trim()] = $matches[2] }
}
$tempClient = Join-Path $env:TEMP "sportmap-backup-$([guid]::NewGuid()).cnf"
$backupDir = Join-Path $projectRoot "backups"
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
$backupPath = Join-Path $backupDir "sportmap_submission_$(Get-Date -Format 'yyyyMMdd_HHmmss').sql"
try {
  @"
[client]
host=127.0.0.1
port=3306
user=$($properties['spring.datasource.username'])
password="$($properties['spring.datasource.password'])"
default-character-set=utf8mb4
"@ | Set-Content -LiteralPath $tempClient -Encoding utf8
  & $dump "--defaults-extra-file=$tempClient" --single-transaction --routines --triggers "--result-file=$backupPath" sportmap_submission
  if ($LASTEXITCODE -ne 0) { throw "백업 생성에 실패했습니다." }
  Write-Host "백업 완료: $backupPath" -ForegroundColor Green
}
finally {
  if (Test-Path -LiteralPath $tempClient) { Remove-Item -LiteralPath $tempClient -Force }
}
