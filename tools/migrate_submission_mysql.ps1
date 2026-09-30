param([string]$MySqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin")

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$secretPath = Join-Path $projectRoot "backend\config\submission-secrets.properties"
$mysql = Join-Path $MySqlBin "mysql.exe"
if (-not (Test-Path -LiteralPath $secretPath)) { throw "제출용 비밀 설정이 없습니다." }
if (-not (Test-Path -LiteralPath $mysql)) { throw "mysql.exe를 찾지 못했습니다: $mysql" }

$properties = @{}
Get-Content -LiteralPath $secretPath | ForEach-Object {
  if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
    $properties[$matches[1].Trim()] = $matches[2]
  }
}
if (
  [string]::IsNullOrWhiteSpace($properties['spring.datasource.username']) -or
  [string]::IsNullOrWhiteSpace($properties['spring.datasource.password'])
) {
  throw "제출용 MySQL 계정 설정이 비어 있습니다."
}

$tempClient = Join-Path $env:TEMP "sportmap-migrate-$([guid]::NewGuid()).cnf"
try {
  $clientConfig = @"
[client]
host=127.0.0.1
port=3306
user=$($properties['spring.datasource.username'])
password="$($properties['spring.datasource.password'])"
database=sportmap_submission
default-character-set=utf8mb4
"@
  [IO.File]::WriteAllText(
    $tempClient,
    $clientConfig,
    (New-Object System.Text.UTF8Encoding($false))
  )

  $migrationSql = @"
SET @column_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'sportmap_submission'
    AND TABLE_NAME = 'members'
    AND COLUMN_NAME = 'deletion_requested_at'
);
SET @statement = IF(
  @column_exists = 0,
  'ALTER TABLE members ADD COLUMN deletion_requested_at DATETIME(6) NULL',
  'SELECT ''members.deletion_requested_at already exists'' AS migration'
);
PREPARE migration_statement FROM @statement;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

-- 제거된 SMS 기능의 과거 테이블은 운영 계정의 최소 권한을 유지하기 위해 삭제하지 않는다.
-- 필요하면 DB 관리자가 백업 후 별도로 정리한다.
"@

  & $mysql "--defaults-extra-file=$tempClient" "--execute=$migrationSql"
  if ($LASTEXITCODE -ne 0) { throw "제출용 MySQL 스키마 갱신에 실패했습니다." }
  Write-Host "제출용 MySQL 스키마 갱신을 완료했습니다." -ForegroundColor Green
}
finally {
  if (Test-Path -LiteralPath $tempClient) {
    Remove-Item -LiteralPath $tempClient -Force
  }
}
