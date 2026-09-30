# SportMap

SportMap은 공공 체육시설과 프로그램을 찾고, 국민체력100 결과와 연령 조건에 맞는 운동을 확인하며, 신청 일정을 관리할 수 있는 서비스입니다. 웹과 Android 앱은 같은 Spring Boot API를 사용합니다.

## 주요 기능

- 지역·시설명·시설 유형을 기준으로 공공 체육시설 검색
- 지도와 목록을 이용한 시설 위치 확인
- 공공체육 프로그램 검색 및 운영기관 접수 링크 연결
- 국민체력100 결과지 PDF 분석과 A~D 참고등급 제공
- 연령·성별·BMI·체력 조건에 따른 단계별 운동 추천
- 체력인증센터 지역 검색과 전화 연결
- 회원가입, 이메일 인증, 로그인, 비밀번호 변경
- 프로그램 신청 기록, 예약 조회 및 취소
- 공지사항, Q&A, 관리자 답변 알림
- 회원탈퇴 7일 유예 및 사용자·관리자 계정 복구
- 관리자 회원 등록·권한 변경·복구·삭제, 공지 및 사이트 설정 관리

> SportMap의 체력 A~D 등급은 운동 추천을 위한 참고값이며 국민체력100 공식 인증등급이나 의료 진단을 대신하지 않습니다.

## 기술 구성

| 구분 | 사용 기술 |
|---|---|
| Backend | Java 17, Spring Boot 3.3, Spring Security, JPA, Querydsl |
| Database | H2(개발), MySQL(제출·운영) |
| Web | React 19, Vite 7, Leaflet |
| Android | Java, Android WebView, SDK 35 |
| 문서 분석 | Apache PDFBox, Tesseract OCR(스캔본 선택 사항) |

## 프로젝트 구조

```text
sportmap/
├─ backend/   API, 인증, 데이터베이스, 공공데이터 연동
├─ web/       React 웹 화면
├─ android/   Android WebView 앱
├─ tools/     실행, 빌드, 데이터 적재 스크립트
└─ docs/      API, 배포, 데이터 및 검증 문서
```

## 로컬 실행

### 백엔드와 통합 웹

```powershell
cd backend
.\gradlew.bat bootRun
```

브라우저에서 `http://127.0.0.1:8080`으로 접속합니다. 로컬 프로필은 파일형 H2 DB를 사용하므로 재시작 후에도 데이터가 유지됩니다.

### 웹 개발 서버

```powershell
cd web
npm install
npm run dev
```

React 변경사항을 Spring Boot 정적 파일에 반영하려면 다음 명령을 사용합니다.

```powershell
npm run build:spring
```

### 실제 휴대폰용 서버

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\run_mobile_server.ps1 -PhysicalDevice
```

PC와 휴대폰이 같은 Wi-Fi에 연결되어 있어야 합니다. 앱 상단의 `서버 설정`에서 스크립트에 표시된 주소를 입력합니다. 공개 배포에서는 고정 HTTPS 주소를 사용해야 합니다.

## 심사용 계정

| 구분 | 아이디 | 비밀번호 |
|---|---|---|
| 관리자 | `admin` | `admin1234!` |
| 일반 사용자 | `test` | `qwer1234!` |

관리자 화면에서는 회원의 가입·탈퇴 상태와 권한을 확인하고, 회원 등록, 권한 변경, 탈퇴 복구 및 영구 삭제를 수행할 수 있습니다.

## Android 서명 APK 빌드

`android/keystore.properties`와 개인 서명키가 준비된 환경에서 실행합니다.

```powershell
cd android
.\gradlew.bat clean assembleRelease
```

결과 파일은 `android/app/build/outputs/apk/release/app-release.apk`에 생성됩니다. 서명키와 비밀번호 파일은 Git에 포함하지 않습니다.

## 데이터와 보안

- 공공데이터 API 키, SMTP 비밀번호, DB 비밀번호, JWT 비밀값은 환경변수 또는 Git에서 제외된 설정 파일로 관리합니다.
- 비밀번호는 BCrypt 해시로 저장하고 API 응답에 포함하지 않습니다.
- 인증은 15분 만료 JWT와 HttpOnly 쿠키를 사용하며 변경 요청은 CSRF 토큰을 확인합니다.
- 로그인은 아이디별 5회 실패 시 10분 동안 제한합니다.
- 회원탈퇴 요청 후 7일 동안 복구할 수 있으며, 기간이 끝난 계정과 활동 데이터는 자동으로 영구 삭제됩니다.
- Android APK와 React 코드에는 공공데이터 인증키를 넣지 않습니다.

## 공공데이터 활용

- 전국 체육시설 정보 OPEN API
- 공공체육시설 프로그램정보
- 국민연령별 추천운동정보
- 체력측정 운동처방 정보
- 지역별 체력인증센터 정보

시설·프로그램은 명칭, 주소, 지역, 운영 기간 등을 정규화해 검색에 사용합니다. 운동처방 자료는 개인식별값을 제거하고 일정 건수 이상인 사례만 집계합니다. 공공 프로그램의 실시간 접수 확정 여부는 제공되지 않으므로 SportMap의 신청 기록과 운영기관의 실제 접수를 구분합니다.

## 검증

```powershell
cd backend
.\gradlew.bat test
```

배포 상태는 `GET /api/health`로 확인합니다. 서버와 DB가 정상일 때 `200 UP`, DB 연결에 실패하면 내부 접속정보 없이 `503 DOWN`을 반환합니다.

## 문서

- [프로젝트 구조](docs/PROJECT_STRUCTURE.md)
- [Postman API 명세](docs/POSTMAN_API_SPEC.md)
- [이메일 인증](docs/EMAIL_VERIFICATION.md)
- [국민체력100 결과지 평가](docs/FITNESS_ASSESSMENT.md)
- [ERD](docs/ERD.md)
- [MySQL 제출 환경 구성](docs/SUBMISSION_MYSQL.md)
- [릴리스 점검표](docs/RELEASE_CHECKLIST.md)
- [공모전 제출 정리](docs/CONTEST_SUBMISSION.md)

## 제출 전 확인

1. MySQL과 공개 HTTPS 서버를 준비합니다.
2. SMTP, 공공데이터 API, DB, JWT 비밀값을 서버 환경변수로 등록합니다.
3. `npm run build:spring`으로 최신 웹 화면을 백엔드에 반영합니다.
4. 같은 서명키로 release APK를 다시 빌드합니다.
5. Wi-Fi와 모바일 데이터에서 회원가입부터 예약·탈퇴 복구까지 점검합니다.
6. 심사 기간 동안 서버와 DB를 유지하고 장애 상황에 대비한 시연 영상을 준비합니다.

Railway를 이용한 최종 HTTPS 배포 순서와 환경변수는 [Railway 제출 배포](docs/RAILWAY_DEPLOYMENT.md)를 따릅니다.
