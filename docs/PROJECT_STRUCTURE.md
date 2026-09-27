# 프로젝트 구조와 유지보수

## 하위 프로젝트

| 경로 | 역할 | 여는 방법 |
|---|---|---|
| `backend` | Spring Boot API, 인증, DB, 공공데이터 연동 | IntelliJ에서 `backend/build.gradle` 열기 |
| `web` | React + Vite 웹 화면 | VS Code 또는 터미널에서 `web` 열기 |
| `android` | 웹 서비스를 표시하는 Android 앱 | Android Studio에서 `android` 열기 |
| `tools` | 세 프로젝트의 공통 실행·빌드·데이터 도구 | 저장소 루트에서 실행 |
| `docs` | API, 배포, 운영 문서 | 저장소와 함께 관리 |

## 개발 실행

백엔드는 저장소 루트를 작업 디렉터리로 사용하며, 루트에서 `gradlew.bat bootRun` 또는 `tools/start_local.ps1`로 실행합니다. 웹 개발 서버는 `web`에서 `npm run dev`로 실행합니다. Android 에뮬레이터용 백엔드는 루트에서 `tools/run_mobile_server.ps1`로 실행합니다.

## 배포 빌드

`web`의 `npm run build:spring`은 React 결과를 `backend/src/main/resources/static`으로 복사합니다. 이후 `backend`에서 `gradlew.bat bootJar`를 실행하면 웹과 API를 포함한 JAR이 만들어집니다.

## 비밀 설정

공공데이터 키, SMTP 비밀번호, MySQL 비밀번호는 `backend/config/*-secrets.properties` 또는 서버 환경변수에만 둡니다. Android APK와 React 소스에는 인증키를 넣지 않습니다.
