param(
  [string]$MySqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin",
  [string]$AdminPassword = ""
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
  # Windows PowerShell 5.1의 구형 .NET에서도 동작하는 암호학적 난수 생성기를 사용한다.
  $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
  try {
    $generator.GetBytes($buffer)
    [Convert]::ToBase64String($buffer)
  }
  finally {
    $generator.Dispose()
  }
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

function Write-Utf8WithoutBom([string]$path, [string]$content) {
  # mysql.exe가 설정 파일 첫 줄을 [client]로 인식하도록 UTF-8 BOM 없이 저장한다.
  $encoding = New-Object System.Text.UTF8Encoding($false)
  [IO.File]::WriteAllText($path, $content, $encoding)
}

Write-Host "SportMap 제출용 MySQL을 초기화합니다." -ForegroundColor Cyan
$rootPassword = ConvertFrom-SecureValue (Read-Host "MySQL root 비밀번호" -AsSecureString)
if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
  $AdminPassword = ConvertFrom-SecureValue (
    Read-Host "초기 관리자 비밀번호(12자 이상, 영문 대/소문자·숫자·특수문자 포함)" -AsSecureString
  )
}
if (
  $AdminPassword.Length -lt 12 -or
  $AdminPassword -notmatch '[A-Z]' -or
  $AdminPassword -notmatch '[a-z]' -or
  $AdminPassword -notmatch '[0-9]' -or
  $AdminPassword -notmatch '[^A-Za-z0-9]'
) {
  throw "관리자 비밀번호는 12자 이상이며 영문 대문자, 소문자, 숫자, 특수문자를 모두 포함해야 합니다."
}
if ($AdminPassword -eq "admin1234!") {
  throw "공개된 기본 관리자 비밀번호는 제출 환경에서 사용할 수 없습니다."
}

$databasePassword = New-RandomSecret 30
$jwtSecret = New-RandomSecret 48
$importKey = New-RandomSecret 36
$tempClient = Join-Path $env:TEMP "sportmap-mysql-$([guid]::NewGuid()).cnf"
$tempSql = Join-Path $env:TEMP "sportmap-init-$([guid]::NewGuid()).sql"

try {
  $clientConfig = @"
[client]
host=127.0.0.1
port=3306
user=root
password="$(Escape-ClientValue $rootPassword)"
default-character-set=utf8mb4
"@
  Write-Utf8WithoutBom $tempClient $clientConfig

  $initializationSql = @"
CREATE DATABASE IF NOT EXISTS sportmap_submission CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'sportmap_app'@'localhost' IDENTIFIED BY '$databasePassword';
ALTER USER 'sportmap_app'@'localhost' IDENTIFIED BY '$databasePassword';
CREATE USER IF NOT EXISTS 'sportmap_app'@'127.0.0.1' IDENTIFIED BY '$databasePassword';
ALTER USER 'sportmap_app'@'127.0.0.1' IDENTIFIED BY '$databasePassword';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON sportmap_submission.* TO 'sportmap_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON sportmap_submission.* TO 'sportmap_app'@'127.0.0.1';
FLUSH PRIVILEGES;
"@
  Write-Utf8WithoutBom $tempSql $initializationSql

  Get-Content -LiteralPath $tempSql -Raw | & $mysql "--defaults-extra-file=$tempClient"
  if ($LASTEXITCODE -ne 0) { throw "MySQL 데이터베이스 생성에 실패했습니다." }

  $secretPath = Join-Path $projectRoot "backend\config\submission-secrets.properties"
  $localSecretPath = Join-Path $projectRoot "backend\config\local-secrets.properties"
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
app.auth.secure-cookie=true
app.admin.seed-enabled=true
app.admin.require-strong-password=true
app.admin.initial-password=$(Escape-PropertyValue $AdminPassword)
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
