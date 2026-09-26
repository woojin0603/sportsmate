param([string]$MySqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin")

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$secretPath = Join-Path $projectRoot "config\submission-secrets.properties"
$mysql = Join-Path $MySqlBin "mysql.exe"
if (-not (Test-Path -LiteralPath $secretPath)) { throw "제출용 비밀 설정이 없습니다." }

$properties = @{}
Get-Content -LiteralPath $secretPath | ForEach-Object {
  if ($_ -match '^\s*([^#][^=]*)=(.*)$') { $properties[$matches[1].Trim()] = $matches[2] }
}
$tempClient = Join-Path $env:TEMP "sportmap-check-$([guid]::NewGuid()).cnf"
try {
  @"
[client]
host=127.0.0.1
port=3306
user=$($properties['spring.datasource.username'])
password="$($properties['spring.datasource.password'])"
database=sportmap_submission
default-character-set=utf8mb4
"@ | Set-Content -LiteralPath $tempClient -Encoding utf8
  & $mysql "--defaults-extra-file=$tempClient" --execute="SELECT DATABASE() AS db, CURRENT_USER() AS account, VERSION() AS version; SHOW TABLES;"
  if ($LASTEXITCODE -ne 0) { throw "제출용 MySQL 연결 확인에 실패했습니다." }
}
finally {
  if (Test-Path -LiteralPath $tempClient) { Remove-Item -LiteralPath $tempClient -Force }
}
