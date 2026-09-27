param([switch]$Install)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $projectRoot "android"
$sdkCandidates = @(
  $env:ANDROID_SDK_ROOT,
  $env:ANDROID_HOME,
  (Join-Path $env:LOCALAPPDATA "Android\Sdk")
) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }

if (-not $sdkCandidates) {
  throw "Android SDK를 찾지 못했습니다. Android Studio에서 SDK 35를 설치한 뒤 다시 실행해 주세요."
}

[string]$sdk = @($sdkCandidates)[0]
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_USER_HOME = Join-Path $sdk ".sportmap-user"
$javaExecutable = (Get-Command java.exe -ErrorAction Stop).Source
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaExecutable)
$sdkProperty = $sdk.Replace("\", "/")
"sdk.dir=$sdkProperty" | Set-Content -LiteralPath (Join-Path $androidRoot "local.properties") -Encoding ascii

$debugKey = Join-Path $androidRoot ".keys\debug.keystore"
if (-not (Test-Path -LiteralPath $debugKey)) {
  New-Item -ItemType Directory -Path (Split-Path $debugKey) -Force | Out-Null
  & keytool -genkeypair -keystore $debugKey -storepass android -alias androiddebugkey `
    -keypass android -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000
  if ($LASTEXITCODE -ne 0) { throw "Android 디버그 서명 키를 만들지 못했습니다." }
}

Push-Location $androidRoot
try {
  & .\gradlew.bat assembleDebug
  if ($LASTEXITCODE -ne 0) { throw "Android 디버그 APK 빌드에 실패했습니다." }
} finally {
  Pop-Location
}

$sourceApk = Join-Path $androidRoot "app\build\outputs\apk\debug\app-debug.apk"
$outputApk = Join-Path $projectRoot "outputs\sportmap-test.apk"
Copy-Item -LiteralPath $sourceApk -Destination $outputApk -Force
Write-Host "APK 생성 완료: $outputApk" -ForegroundColor Green

if ($Install) {
  $adb = Join-Path $sdk "platform-tools\adb.exe"
  if (-not (Test-Path -LiteralPath $adb)) { throw "adb.exe를 찾지 못했습니다." }
  & $adb install -r $outputApk
  if ($LASTEXITCODE -ne 0) { throw "연결된 기기에 APK를 설치하지 못했습니다." }
}
