# SportMap Android 테스트 앱

현재 Spring Boot + React 서비스를 Android WebView에서 실행하는 테스트용 앱입니다. 웹과 같은 API, 로그인 쿠키, 예약, 질문 답변 알림과 PDF 업로드 기능을 사용합니다.

앱 내부에서는 설정한 SportMap 서버 주소만 열립니다. 국민체력100 같은 외부 홈페이지와 다운로드 링크는 휴대폰 기본 브라우저로 전달됩니다. 서버가 꺼져 있거나 네트워크 연결에 실패하면 오류 안내와 **다시 연결** 버튼이 표시됩니다.

디버그 앱은 같은 Wi-Fi의 `http://PC_IP:8080` 접속을 허용합니다. 배포용 release 앱은 개인정보와 로그인 쿠키를 보호하기 위해 HTTPS 주소만 허용합니다.

## 서버 실행

Android 에뮬레이터로 테스트할 때 프로젝트 루트에서 실행합니다.

```powershell
powershell -ExecutionPolicy Bypass -File tools/run_mobile_server.ps1
```

앱의 **서버 설정**에서 스크립트가 보여 준 주소를 입력합니다. Android 에뮬레이터에서는 `http://10.0.2.2:8080`을 사용합니다.

실제 휴대폰으로 테스트할 때만 `-PhysicalDevice` 옵션을 사용합니다. 휴대폰과 PC는 같은 Wi-Fi에 연결되어 있어야 합니다. 외부 네트워크에 모의 인증 기능이 노출되지 않도록 이 모드에서는 테스트용 이메일·SMS 인증이 비활성화됩니다.

```powershell
powershell -ExecutionPolicy Bypass -File tools/run_mobile_server.ps1 -PhysicalDevice
```

## APK 설치

USB 디버깅이 활성화된 기기는 아래 명령으로 설치할 수 있습니다.

```powershell
adb install -r outputs/sportmap-test.apk
```

이 APK는 테스트용 디버그 키로 서명되었습니다. Play Store 배포용 앱은 별도 서명 키와 HTTPS 운영 서버, 푸시 알림 구성이 필요합니다.

## Android Studio에서 빌드

Android Studio에서 `android` 폴더를 프로젝트로 엽니다. SDK 35가 설치된 상태에서 Gradle 동기화 후 `app` 실행 구성을 선택하면 에뮬레이터나 USB 기기에서 실행할 수 있습니다.

명령행 빌드는 Android SDK 경로가 설정된 환경에서 실행합니다.

```powershell
cd android
.\gradlew.bat assembleDebug
```

생성 파일은 `app/build/outputs/apk/debug/app-debug.apk`입니다. 저장소의 `outputs/sportmap-test.apk`는 이전 테스트 빌드이므로 소스 변경 후 새 APK를 다시 만들어야 합니다.

프로젝트 루트에서 아래 스크립트를 실행하면 APK 빌드 후 `outputs/sportmap-test.apk`까지 자동 교체합니다. USB로 연결된 기기에 바로 설치하려면 `-Install`을 붙입니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build_mobile.ps1
powershell -ExecutionPolicy Bypass -File .\tools\build_mobile.ps1 -Install
```
