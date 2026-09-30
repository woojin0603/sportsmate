param(
  [string]$ConfigPath = "backend/config/submission-secrets.properties"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$resolvedConfigPath = Join-Path $projectRoot $ConfigPath
if (-not (Test-Path -LiteralPath $resolvedConfigPath)) {
  throw "제출용 비밀 설정 파일이 없습니다: $resolvedConfigPath"
}

function ConvertFrom-SecureValue([Security.SecureString]$value) {
  $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($value)
  try {
    [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
  }
  finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
  }
}

$adminPassword = ConvertFrom-SecureValue (
  Read-Host "새 제출용 관리자 비밀번호(12자 이상, 영문 대/소문자·숫자·특수문자 포함)" -AsSecureString
)
if (
  $adminPassword.Length -lt 12 -or
  $adminPassword -cnotmatch '[A-Z]' -or
  $adminPassword -cnotmatch '[a-z]' -or
  $adminPassword -notmatch '[0-9]' -or
  $adminPassword -notmatch '[^A-Za-z0-9]' -or
  $adminPassword -eq "admin1234!"
) {
  throw "관리자 비밀번호 조건을 충족하지 않습니다."
}
if ($adminPassword.Contains([Environment]::NewLine)) {
  throw "비밀번호에는 줄바꿈을 사용할 수 없습니다."
}

$managedKeys = @(
  "app.admin.seed-enabled",
  "app.admin.require-strong-password",
  "app.admin.initial-password"
)
$preservedLines = Get-Content -LiteralPath $resolvedConfigPath | Where-Object {
  $line = $_
  -not ($managedKeys | Where-Object {
    $line -match "^\s*$([regex]::Escape($_))\s*="
  })
}
$escapedPassword = $adminPassword.Replace("\", "\\")
$newLines = @(
  $preservedLines
  ""
  "# 제출용 관리자 초기 설정"
  "app.admin.seed-enabled=true"
  "app.admin.require-strong-password=true"
  "app.admin.initial-password=$escapedPassword"
)
$newLines | Set-Content -LiteralPath $resolvedConfigPath -Encoding UTF8
$adminPassword = $null

Write-Host "제출용 관리자 비밀번호 설정을 저장했습니다." -ForegroundColor Green
Write-Host "다음 제출 프로필 시작 시 기존 admin 계정의 비밀번호가 갱신됩니다."
