$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$configDirectory = Join-Path $projectRoot "backend\config"
$secretPath = Join-Path $configDirectory "local-secrets.properties"

function ConvertFrom-SecureValue([Security.SecureString]$value) {
  $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($value)
  try {
    return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
  } finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
  }
}

Write-Host "공공 체육시설 API 인증키를 로컬 비밀 설정에 저장합니다." -ForegroundColor Cyan
$serviceKey = ConvertFrom-SecureValue (Read-Host "data.go.kr 인증키" -AsSecureString)
if ([string]::IsNullOrWhiteSpace($serviceKey)) {
  throw "인증키가 비어 있습니다."
}
if ($serviceKey.Contains("`r") -or $serviceKey.Contains("`n")) {
  throw "인증키에는 줄바꿈을 사용할 수 없습니다."
}

New-Item -ItemType Directory -Path $configDirectory -Force | Out-Null
$lines = if (Test-Path -LiteralPath $secretPath) {
  @(Get-Content -LiteralPath $secretPath)
} else {
  @()
}
$setting = "app.public-facility.service-key=$serviceKey"
$replaced = $false
$updated = foreach ($line in $lines) {
  if ($line -match '^\s*app\.public-facility\.service-key=') {
    $replaced = $true
    $setting
  } else {
    $line
  }
}
if (-not $replaced) {
  $updated = @($updated) + $setting
}
$updated | Set-Content -LiteralPath $secretPath -Encoding utf8
$serviceKey = $null

Write-Host "인증키 설정 완료: $secretPath" -ForegroundColor Green
Write-Host "실행 중인 Spring Boot 서버를 완전히 종료한 뒤 다시 시작해 주세요."
