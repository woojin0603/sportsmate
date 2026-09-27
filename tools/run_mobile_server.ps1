param(
  [int]$Port = 8080,
  [switch]$PhysicalDevice
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $projectRoot "backend"
$requestedPort = $Port
$busyPorts = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port
while ($busyPorts -contains $Port) {
  $Port++
  if ($Port -gt ($requestedPort + 20)) {
    throw "사용 가능한 테스트 서버 포트를 찾지 못했습니다."
  }
}
if ($Port -ne $requestedPort) {
  Write-Warning "$requestedPort 포트가 사용 중이므로 $Port 포트를 사용합니다."
}
$localSecretPath = Join-Path $backendRoot "config\local-secrets.properties"
$hasPublicApiKey = -not [string]::IsNullOrWhiteSpace($env:PUBLIC_FACILITY_SERVICE_KEY)
if (-not $hasPublicApiKey -and (Test-Path -LiteralPath $localSecretPath)) {
  $hasPublicApiKey = [bool](Select-String -LiteralPath $localSecretPath -Pattern '^\s*app\.public-facility\.service-key=.+$' -Quiet)
}
if (-not $hasPublicApiKey) {
  Write-Warning "체육시설 API 인증키가 없습니다. .\tools\configure_public_api.ps1을 먼저 실행해 주세요."
}
$addresses = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
  Where-Object {
    $_.IPAddress -notlike '127.*' -and
    $_.IPAddress -notlike '169.254.*' -and
    $_.InterfaceAlias -notmatch 'Loopback|vEthernet|Bluetooth'
  } |
  Select-Object -ExpandProperty IPAddress -Unique

Write-Host "SportMap 모바일 테스트 서버를 시작합니다." -ForegroundColor Green
Write-Host "에뮬레이터 주소: http://10.0.2.2:$Port"
if ($PhysicalDevice) {
  foreach ($address in $addresses) {
    Write-Host "실제 휴대폰 주소: http://${address}:$Port"
  }
  Write-Warning "실제 휴대폰 모드에서는 모의 이메일/SMS 인증이 비활성화됩니다."
} else {
  Write-Host "Android 에뮬레이터 전용으로 실행합니다."
}

Push-Location $backendRoot
try {
  $env:SPRING_PROFILES_ACTIVE = "local"
  $mobileDatabase = "jdbc:h2:file:./sportmap-mobile-local;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
  $serverAddress = if ($PhysicalDevice) { "0.0.0.0" } else { "127.0.0.1" }
  $verificationModes = if ($PhysicalDevice) { " --app.mail.mode=disabled --app.sms.mode=disabled" } else { "" }
  $bootArguments = "--server.address=$serverAddress --server.port=$Port --spring.datasource.url=$mobileDatabase --app.demo.enabled=true$verificationModes"
  & .\gradlew.bat bootRun --args=$bootArguments
  if ($LASTEXITCODE -ne 0) { throw "모바일 테스트 서버를 시작하지 못했습니다." }
} finally {
  Pop-Location
}
