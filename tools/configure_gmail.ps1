param(
  [string]$ConfigPath = "backend/config/local-secrets.properties",
  [string]$PublicBaseUrl = ""
)

$ErrorActionPreference = "Stop"

# 프로젝트 루트를 기준으로 비밀 설정 파일 위치를 확정한다.
$projectRoot = Split-Path -Parent $PSScriptRoot
$resolvedConfigPath = Join-Path $projectRoot $ConfigPath
$configDirectory = Split-Path -Parent $resolvedConfigPath
if (-not (Test-Path -LiteralPath $configDirectory)) {
  New-Item -ItemType Directory -Path $configDirectory | Out-Null
}

# Gmail 주소와 화면에 노출되지 않는 앱 비밀번호를 입력받는다.
$email = (Read-Host "Gmail 주소").Trim()
if ($email -notmatch '^[^\s@]+@gmail\.com$') {
  throw "올바른 Gmail 주소를 입력해 주세요."
}
$securePassword = Read-Host "Google 앱 비밀번호(입력 내용은 표시되지 않음)" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
  $appPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer).Replace(" ", "")
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}
if ($appPassword -notmatch '^[A-Za-z]{16}$') {
  throw "Google 앱 비밀번호는 공백을 제외한 영문 16자리여야 합니다. 일반 계정 비밀번호는 사용할 수 없습니다."
}

if ([string]::IsNullOrWhiteSpace($PublicBaseUrl)) {
  $PublicBaseUrl = (Read-Host "공개 HTTPS 주소(예: https://sportsmate.example.com)").Trim()
}
try {
  $publicUri = [Uri]$PublicBaseUrl
} catch {
  throw "공개 주소 형식이 올바르지 않습니다."
}
if (
  $publicUri.Scheme -ne "https" -or
  [string]::IsNullOrWhiteSpace($publicUri.Host) -or
  $publicUri.UserInfo -or
  $publicUri.Query -or
  $publicUri.Fragment -or
  ($publicUri.AbsolutePath -and $publicUri.AbsolutePath -ne "/")
) {
  throw "공개 주소는 경로와 쿼리가 없는 HTTPS 주소여야 합니다."
}
$PublicBaseUrl = $PublicBaseUrl.TrimEnd("/")

# 기존 공공데이터 키와 관리자 설정은 유지하고 메일 설정만 교체한다.
$mailKeys = @(
  "spring.mail.host",
  "spring.mail.port",
  "spring.mail.username",
  "spring.mail.password",
  "app.mail.from",
  "app.mail.mode",
  "app.mail.public-base-url"
)
$existingLines = if (Test-Path -LiteralPath $resolvedConfigPath) {
  Get-Content -LiteralPath $resolvedConfigPath
} else {
  @()
}
$preservedLines = $existingLines | Where-Object {
  $line = $_
  -not ($mailKeys | Where-Object { $line -match "^\s*$([regex]::Escape($_))\s*=" })
}
$mailLines = @(
  "spring.mail.host=smtp.gmail.com",
  "spring.mail.port=587",
  "spring.mail.username=$email",
  "spring.mail.password=$appPassword",
  "app.mail.from=$email",
  "app.mail.mode=live",
  "app.mail.public-base-url=$PublicBaseUrl"
)
@($preservedLines; ""; "# Gmail 회원가입 인증 메일"; $mailLines) |
  Set-Content -LiteralPath $resolvedConfigPath -Encoding UTF8

Write-Host "메일 설정을 저장했습니다: $resolvedConfigPath"
Write-Host "실제 이메일 링크 발송 모드가 활성화됐습니다."
Write-Host "Spring Boot를 완전히 종료한 뒤 다시 실행해 주세요."
