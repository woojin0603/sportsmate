param([int]$Port = 8080)

$projectRoot = Split-Path -Parent $PSScriptRoot
$addresses = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
  Where-Object {
    $_.IPAddress -notlike '127.*' -and
    $_.IPAddress -notlike '169.254.*' -and
    $_.InterfaceAlias -notmatch 'Loopback|vEthernet|Bluetooth'
  } |
  Select-Object -ExpandProperty IPAddress -Unique

Write-Host "SportMap 모바일 테스트 서버를 시작합니다." -ForegroundColor Green
Write-Host "에뮬레이터 주소: http://10.0.2.2:$Port"
foreach ($address in $addresses) {
  Write-Host "실제 휴대폰 주소: http://${address}:$Port"
}
Write-Host "휴대폰과 PC는 같은 Wi-Fi에 연결되어 있어야 합니다."

Push-Location $projectRoot
try {
  & .\gradlew.bat bootRun --args="--server.address=0.0.0.0 --server.port=$Port"
} finally {
  Pop-Location
}
