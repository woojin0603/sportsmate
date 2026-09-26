param(
  [string]$MySqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$mysql = Join-Path $MySqlBin "mysql.exe"
if (-not (Test-Path -LiteralPath $mysql)) {
  throw "mysql.exe를 찾지 못했습니다: $mysql"
}

function ConvertFrom-SecureValue([Security.SecureString]$value) {
  $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($value)
  try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
  finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function New-RandomSecret([int]$bytes = 36) {
  $buffer = New-Object byte[] $bytes
  [Security.Cryptography.RandomNumberGenerator]::Fill($buffer)
  [Convert]::ToBase64String($buffer)
}

function Escape-ClientValue([string]$value) {
  $value.Replace("\", "\\").Replace('"', '\"')
}

function Escape-PropertyValue([string]$value) {
  if ($value.Contains("`r") -or $value.Contains("`n")) {
    throw "비밀번호에는 줄바꿈을 사용할 수 없습니다."
  }
  $value.Replace("\", "\\")
}

Write-Host "SportMap 제출용 MySQL을 초기화합니다." -ForegroundColor Cyan
$rootPassword = ConvertFrom-SecureValue (Read-Host "MySQL root 비밀번호" -AsSecureString)
$adminPassword = ConvertFrom-SecureValue (Read-Host "제출용 SportMap admin 비밀번호" -AsSecureString)
if ($adminPassword.Length -lt 12) { throw "관리자 비밀번호는 12자 이상으로 입력해 주세요." }

$databasePassword = New-RandomSecret 30
$jwtSecret = New-RandomSecret 48
$importKey = New-RandomSecret 36
$tempClient = Join-Path $env:TEMP "sportmap-mysql-$([guid]::NewGuid()).cnf"
$tempSql = Join-Path $env:TEMP "sportmap-init-$([guid]::NewGuid()).sql"

try {
  @"
[client]
host=127.0.0.1
port=3306
user=root
password="$(Escape-ClientValue $rootPassword)"
default-character-set=utf8mb4
"@ | Set-Content -LiteralPath $tempClient -Encoding utf8

  @"
CREATE DATABASE IF NOT EXISTS sportmap_submission CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'sportmap_app'@'localhost' IDENTIFIED BY '$databasePassword';
ALTER USER 'sportmap_app'@'localhost' IDENTIFIED BY '$databasePassword';
CREATE USER IF NOT EXISTS 'sportmap_app'@'127.0.0.1' IDENTIFIED BY '$databasePassword';
ALTER USER 'sportmap_app'@'127.0.0.1' IDENTIFIED BY '$databasePassword';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON sportmap_submission.* TO 'sportmap_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON sportmap_submission.* TO 'sportmap_app'@'127.0.0.1';
FLUSH PRIVILEGES;
"@ | Set-Content -LiteralPath $tempSql -Encoding utf8

  Get-Content -LiteralPath $tempSql -Raw | & $mysql "--defaults-extra-file=$tempClient"
  if ($LASTEXITCODE -ne 0) { throw "MySQL 데이터베이스 생성에 실패했습니다." }

  $secretPath = Join-Path $projectRoot "config\submission-secrets.properties"
  $localSecretPath = Join-Path $projectRoot "config\local-secrets.properties"
  $publicApiSetting = ""
  if (Test-Path -LiteralPath $localSecretPath) {
    $publicApiLine = Get-Content -LiteralPath $localSecretPath | Where-Object {
      $_ -match '^app\.public-facility\.service-key='
    } | Select-Object -First 1
    if ($publicApiLine) {
      $publicApiSetting = "`n$publicApiLine"
    }
  }
  @"
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/sportmap_submission?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&useSSL=false
spring.datasource.username=sportmap_app
spring.datasource.password=$databasePassword
app.auth.jwt-secret=$jwtSecret
app.auth.secure-cookie=false
app.admin.seed-enabled=true
app.admin.initial-password=$(Escape-PropertyValue $adminPassword)
app.import-key=$importKey$publicApiSetting
"@ | Set-Content -LiteralPath $secretPath -Encoding utf8

  Write-Host "제출용 DB와 비밀 설정을 생성했습니다." -ForegroundColor Green
  Write-Host "DB: sportmap_submission / 사용자: sportmap_app"
  Write-Host "설정: $secretPath"
  Write-Host "다음 단계: powershell -ExecutionPolicy Bypass -File .\tools\start_submission.ps1"
}
finally {
  $rootPassword = $null
  if (Test-Path -LiteralPath $tempClient) { Remove-Item -LiteralPath $tempClient -Force }
  if (Test-Path -LiteralPath $tempSql) { Remove-Item -LiteralPath $tempSql -Force }
}
