# SportMap Android 테스트 앱

현재 Spring Boot + React 서비스를 Android WebView에서 실행하는 테스트용 앱입니다. 웹과 같은 API, 로그인 쿠키, 예약, 질문 답변 알림과 PDF 업로드 기능을 사용합니다.

## 서버 실행

실제 휴대폰과 PC를 같은 Wi-Fi에 연결하고 프로젝트 루트에서 실행합니다.

```powershell
powershell -ExecutionPolicy Bypass -File tools/run_mobile_server.ps1
```

앱의 **서버 설정**에서 스크립트가 보여 준 주소를 입력합니다. Android 에뮬레이터에서는 `http://10.0.2.2:8080`을 사용합니다.

## APK 설치

USB 디버깅이 활성화된 기기는 아래 명령으로 설치할 수 있습니다.

```powershell
adb install -r outputs/sportmap-test.apk
```

이 APK는 테스트용 디버그 키로 서명되었습니다. Play Store 배포용 앱은 별도 서명 키와 HTTPS 운영 서버, 푸시 알림 구성이 필요합니다.
