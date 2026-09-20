# SportMap — 공공 체육시설 탐색 서비스

React + Vite 화면은 [frontend/README.md](frontend/README.md)에 실행법과 화면별 API 연결을 정리했습니다. 개발 중에는 Spring Boot 실행 후 `frontend`에서 `npm run dev`를 실행할 수 있습니다. Spring Boot 하나로 화면까지 제공하려면 `frontend`에서 `npm run build:spring`을 실행하고 Spring Boot를 재시작하세요. Vite 산출물 폴더는 `build`가 아니라 `dist`입니다. `http://127.0.0.1:8080/`에서 화면을 확인하고, 직접 입력한 `/programs` 같은 주소도 새로고침되는지 확인할 수 있습니다.

국민체력100 PDF 업로드와 A~D 참고 평가는 [docs/FITNESS_ASSESSMENT.md](docs/FITNESS_ASSESSMENT.md)에 기준·API·OCR 설치 조건을 정리했습니다. 백엔드를 다시 실행해야 새 API와 `age` 응답 필드가 적용됩니다.

결과지가 없는 사용자는 `/fitness` 화면의 **국민체력100 측정 예약하기** 버튼으로 공식 [체력측정 예약 페이지](https://nfa.kspo.or.kr/reserve/selectReserveStep1.kspo)를 새 탭에서 열 수 있습니다. 공식 홈페이지에서 별도 회원가입·로그인이 필요할 수 있으며, SportMap은 예약을 대신 확정하지 않습니다.

Windows에서 스캔본 PDF를 시험하려면 프로젝트 루트에서 `python tools/install_ocr.py`를 한 번 실행하고 Spring Boot를 재시작하세요. OCR 실행 파일과 언어 데이터는 `.local-tools/`에 설치되며 Git에서 제외됩니다.

수정된 관계도는 [docs/ERD.md](docs/ERD.md)에 있습니다.

Java 17, Spring Boot 3.3, JPA, Querydsl, H2/MySQL 기반 시작 프로젝트입니다. `build.gradle`을 IntelliJ의 Gradle 프로젝트로 열 수 있습니다.

회원가입에는 이메일 인증이 필요합니다. 실행 전에 서버의 SMTP 설정을 환경변수로 지정하세요. 사용자는 메일의 **이메일 인증 완료** 버튼을 눌러 인증하며, 원래 회원가입 화면이 완료 상태를 자동 확인합니다. 메일 계정의 비밀번호는 저장소나 React 코드에 넣지 않습니다. SMTP 설정이 없으면 발송 API는 503을 반환하며 회원가입은 진행되지 않습니다.

로컬 IntelliJ 실행에서는 [`config/local-secrets.properties.example`](config/local-secrets.properties.example)을 참고해 Git에서 제외된 `config/local-secrets.properties`에 같은 항목을 추가할 수도 있습니다. Gmail은 일반 로그인 비밀번호가 아니라 Google 계정의 2단계 인증에서 발급한 앱 비밀번호를 사용해야 합니다.

Windows에서는 프로젝트 루트에서 `powershell -ExecutionPolicy Bypass -File tools/configure_gmail.ps1`을 실행하면 Gmail 주소와 앱 비밀번호를 화면에 노출하지 않고 입력할 수 있습니다. 설정 후 Spring Boot를 완전히 재시작해야 합니다.

```powershell
$env:MAIL_HOST='smtp.example.com'
$env:MAIL_PORT='587'
$env:MAIL_USERNAME='메일 계정'
$env:MAIL_PASSWORD='SMTP 비밀번호 또는 앱 비밀번호'
$env:MAIL_FROM='noreply@example.com'
```

가입 화면에서 이메일을 입력하고 **이메일 인증하기**를 누르면 6자리 번호가 발송됩니다. 받은 번호를 10분 안에 확인한 뒤 회원가입할 수 있습니다. 재발송은 1분 뒤 가능하고, 번호 확인은 최대 5회 시도할 수 있습니다. 기존 데모 계정의 로그인에는 영향을 주지 않습니다.

로컬 H2로 실행하면 제공받은 2026년 7월 공공 프로그램 파일에서 추린 실제 프로그램 240건과 해당 시설이 자동 적재됩니다. 테스트 회원·게시글 및 자체 예약 예시도 함께 생성됩니다. 화면별 테스트 주소와 로그인 계정은 [docs/DEMO_DATA.md](docs/DEMO_DATA.md)에 있습니다. 로컬 H2는 프로젝트 루트의 `sportmap-local.mv.db` 파일에 저장되어 서버를 재시작해도 회원·예약 정보가 남습니다. 기존 메모리 H2에만 있던 회원은 파일 DB로 이전되지 않으므로 한 번 다시 가입해야 합니다.

## ERD 수정 요약

| 원본 | 수정 | 이유 |
|---|---|---|
| Region의 중복 지역명·주소 | `regions(code, name, parent_id)` | 행정구역 코드를 고유키로 관리 |
| Center와 Facility의 모호한 중복 | `facilities`로 통합 | 두 공공 시설 데이터 간 동일 시설 중복 가능 |
| Facility_Cost를 예약이 참조 | `reservations.program_id` | 요금은 예약 대상이 아님 |
| Program에 시설 참조 없음 | `programs.facility_id` | 시설별 강좌 조회 가능 |
| User_Ability에 단일 최신값 | `member_abilities(member_id, measured_on)` | 측정 이력 보존 |
| Program_Ability 점수 복사 | 추천 운동의 연령 범위와 운동명 분리 | 공공 추천 데이터와 사용자 측정값을 혼동하지 않음 |
| 시설 데이터의 출처 없음 | `facility_sources(dataset_code, source_key, fetched_at, raw_json)` | 중복 제거, 추적, 재수집 |
| 연령 추천·지역 보급 통계 누락 | `exercise_recommendations`, `region_supplies` | 시설 정보와 통계의 단위가 다름 |
| QnA/Comment/Review의 작성자 문자열 | `member` 외래키 | 작성자 무결성 확보 |

시설 요금은 현재 `programs.fee`에 저장합니다. 시설 단독 대관이 확인되면 별도 `facility_rates`를 추가하세요. 공공데이터가 예약 가능 여부와 실시간 잔여 좌석을 제공하지 않으므로 공공 프로그램의 `bookingSupported=false`를 유지하고 운영기관 예약 확정으로 표시하지 않습니다.

## 데이터셋 배치

1. 체육생활이용정보 → 별도 지역·종목 수요 통계 테이블로 확장 예정. 지역/종목 단위의 집계치이며 개별 시설에 바로 합치지 않습니다.
2. 전국체육시설현황 데이터 → `facilities`, `facility_sources`.
3. 지역별 공공체육시설 보급현황정보 → `region_supplies`.
4. 국민연령별 추천운동정보 → `exercise_recommendations`.
5. 공공체육시설 프로그램정보 → `programs`. 제공된 대용량 JSON의 시설명·주소·지역과 강좌 기간·시간·정원·요금을 별도 수집기로 적재합니다.
6. 전국공공체육시설 데이터 → `facilities`, `facility_sources`. 2번과 주소·명칭·좌표 기준으로 중복 판정해야 합니다.

시설 수집 설정 `config/facilities.example.json`은 [전국체육시설 정보 API](https://www.data.go.kr/data/15113986/openapi.do)의 실제 URL과 확인된 응답 필드로 채웠습니다. 이 API에는 행정구역 코드가 없어 현재 `cp_nm`(시도명)을 임시 지역 키로 사용합니다. 공식 행정구역 코드를 추가할 때 지역 정규화가 필요합니다. 제공된 프로그램 JSON은 `tools/import_programs.py`로 적재할 수 있습니다. 통계·추천 데이터의 별도 매퍼는 아직 구현되지 않았습니다.

## 실행

```powershell
# IntelliJ에서 build.gradle을 열거나
.\gradlew.bat bootRun
# MySQL: 데이터베이스 sportmap을 먼저 생성하고 환경변수 설정
$env:SPRING_PROFILES_ACTIVE='mysql'
$env:DB_URL='jdbc:mysql://localhost:3306/sportmap?serverTimezone=Asia/Seoul'
$env:DB_USER='sportmap'
$env:DB_PASSWORD='...'
$env:JWT_SECRET='32바이트-이상의-임의-비밀값을-설정하세요'
$env:IMPORT_KEY='긴-임의의-비밀키'
.\gradlew.bat bootRun
```

조회: `GET /api/facilities?keyword=수영&regionCode=...&publicOnly=true`, `GET /api/facilities/{id}`, `GET /api/programs?facilityId=1`, `GET /api/recommendations?age=30`.

연령별 운동정보 CSV 5,040건은 `src/main/resources/age-exercise-recommendations.json`에 포함했습니다. 서버 첫 실행 시 `exercise_recommendations`에 한 번 적재하며, `/recommendations` 화면에서 연령·BMI 분류·성별·체력 등급을 선택하면 준비운동→본운동→마무리운동 순으로 각 단계 5개씩 표시합니다. API 예시: `GET /api/recommendations?age=30&bmi=정상&sex=F&grade=참가증`. CSV의 `COAW_FLAG_NM` 값(1·2·3등급, 참가증)을 그대로 사용하며, PDF 평가의 A~D 등급과 동일한 척도로 간주하지 않습니다. 원본에 10대 미만 추천은 없고 `70대 이상`은 조회 상한인 120세까지 적용했습니다.

추가 제공된 체력측정 운동처방 JSON은 `tools/build_fitness_prescriptions.mjs`로 익명 집계했습니다. 일반 처방(2026년 3~7월), 장애인 처방(2025년 8~12월), 지역 체력측정 처방(2026년 7월 배포본)을 각각 분리합니다. 지역 데이터의 2025년 7월·2026년 2월 파일은 7월 배포본과 시작 기록이 겹치는 누적 스냅샷이므로 중복 집계를 피하려고 최신 배포본만 집계했습니다. 원본의 회원 식별자·개별 검사 수치·측정일은 프로젝트에 넣지 않았고, 연령대·성별·공식 등급·장애 유형별로 **같은 처방이 5건 이상인 경우** 빈도 상위 사례만 `src/main/resources/fitness-prescription-catalog.json`에 보관합니다. `/recommendations` 화면의 처방 사례는 통계적 참고자료이며 개인별 처방이나 PDF 평가의 A~D 등급과 직접 연결하지 않습니다. `GET /api/fitness/prescriptions?age=30&source=GENERAL&sex=F`로 조회할 수 있습니다.

지역 체력측정 자료에서 체력인증센터의 명칭·주소·연락처·운영시간 79곳을 추출했습니다. `/fitness-centers` 화면과 `GET /api/fitness/centers?keyword=서울`에서 찾을 수 있습니다. 원본 좌표와 연락처는 최신 정보와 다를 수 있으므로 방문 전에 센터에 확인해야 합니다. 원본 파일을 다시 집계하려면 `node tools/build_fitness_prescriptions.mjs "<JSON 파일 폴더>"`를 실행하고, React 변경사항은 `frontend`에서 `npm run build:spring`으로 반영하세요.

센터 79곳 모두 원본의 `REPRSNT_TEL_NO` 전화번호가 있으며, 센터 화면에서 하이픈을 넣어 표시하고 누르면 전화 앱으로 연결합니다. 지역·센터명뿐 아니라 전화번호 숫자로도 검색할 수 있습니다. 출장 센터 중에는 본 센터와 같은 대표번호를 사용하는 곳이 있습니다.

목록 응답은 `content` 배열과 `page` 메타데이터(`totalElements`, `totalPages`, `number`, `size`)를 사용합니다.

첨부 API 명세의 읽기 경로에 맞춰 `GET /api/regions`, `GET /api/programs`(키워드·페이징), `GET /api/programs/{id}`, `GET /api/facilities/{id}/reviews`, `GET /api/notices`, `GET /api/notices/{id}`, `GET /api/qna`, `GET /api/qna/{id}`도 제공합니다.

### 회원 인증과 쓰기 API

`POST /api/users/signup`은 `fullName`(성명), `username`(아이디), `password`, `birthDate`(`YYYY-MM-DD`), `email`, `phoneNumber`, `gender`(`MALE`, `FEMALE`, `OTHER`)를 받습니다. 비밀번호는 BCrypt 해시로 저장하고 응답에는 포함하지 않습니다. `POST /api/users/login`은 15분 만료 JWT를 `HttpOnly; SameSite=Strict` 쿠키에 넣어 돌려줍니다. 운영 MySQL 프로필은 `JWT_SECRET`(UTF-8 32바이트 이상)을 반드시 설정해야 하고 쿠키에는 `Secure`가 적용됩니다. 로컬 H2 프로필은 실행할 때 임시 비밀키를 생성하며 HTTP 테스트를 위해 `Secure`를 끕니다. 서버를 재시작하면 로컬 토큰은 무효화됩니다.

아이디는 영문자로 시작하는 영문·숫자·밑줄 4~30자입니다. 웹 입력란에는 영어 언어 힌트를 주지만 브라우저는 데스크톱 운영체제의 한/영 입력 모드를 강제로 전환할 수 없으므로, 한글 입력 상태라면 한/영 키로 전환해야 합니다.

회원가입 요청 예시:

```json
{
  "fullName": "홍길동",
  "username": "hong1234",
  "password": "ExamplePassword123!",
  "birthDate": "1998-05-10",
  "email": "hong@example.com",
  "phoneNumber": "010-1234-5678",
  "gender": "MALE"
}
```

브라우저는 먼저 `GET /api/csrf`로 CSRF 토큰과 `XSRF-TOKEN` 쿠키를 받고, 모든 POST/PATCH 요청에 `X-XSRF-TOKEN: <token>` 헤더를 보냅니다. 그 다음 회원가입·로그인을 요청하세요. 로그아웃은 `POST /api/users/logout`, 내 정보는 `GET /api/users/mypage`입니다. JWT 쿠키는 로그아웃 시 브라우저에서 삭제되지만 이미 탈취된 토큰은 15분 만료 전까지 유효할 수 있습니다.

인증 회원은 `POST /api/qna`, `POST /api/qna/{id}/comments`, `POST /api/facilities/{facilityId}/reviews`, `POST /api/reservations`, `PATCH /api/reservations/{id}/cancel`을 사용할 수 있습니다. 예약 조회는 `GET /api/reservations`와 `GET /api/reservations/{id}`입니다. 자체 운영 프로그램의 예약 확정은 `bookingSupported=true`이고 `capacity`가 설정된 경우에만 허용합니다. 공공데이터 프로그램은 운영기관의 실시간 잔여석·접수 API가 없어 `POST /api/reservations`에 `{"programId":1}`만 보내면 자체 신청 내역을 `REQUESTED`로 기록합니다. 이 상태는 운영기관 예약 확정이 아니며, 실제 접수는 응답의 `registrationUrl`에서 해야 합니다. 같은 회원의 중복 신청은 409, 종료된 강좌 신청은 409입니다. 취소 후에는 재신청할 수 있습니다.

### 관리자 화면과 권한

로컬 H2 프로필에서는 최초 실행 때 `admin` 계정을 준비합니다. 초기 비밀번호는 `qwer1234!`이며 `config/local-secrets.properties`에 `app.admin.initial-password=<새 비밀번호>`를 넣어 변경할 수 있습니다. 로컬 서버가 시작될 때 지정한 비밀번호로 동기화됩니다. 공개 ngrok 링크를 열기 전에는 반드시 초기 비밀번호를 바꾸고 Spring Boot를 재시작하세요. MySQL 프로필에서는 이 데모 관리자 계정을 자동 생성하지 않습니다.

로그인하면 일반 계정은 `USER`, 관리자 계정은 `ADMIN` 역할이 JWT에 들어갑니다. 관리자는 `/admin` 화면에서 회원·시설·프로그램·예약·공지 건수를 보고, 회원 역할 변경과 공지 작성·수정·삭제를 할 수 있습니다. 서버의 `/api/admin/**`는 JWT와 현재 DB 권한을 모두 확인하므로 화면 주소를 직접 입력해도 일반 사용자는 관리 데이터를 볼 수 없습니다. 관리자 권한 해제는 즉시 적용되고, 새 관리자 권한은 다시 로그인해야 토큰에 반영됩니다. 기존 H2 회원의 빈 역할 값은 `USER`로 처리합니다.

관리 API: `GET /api/admin/overview`, `GET /api/admin/users?page=0&size=20`, `PATCH /api/admin/users/{id}/role` (`{"role":"ADMIN"}` 또는 `USER`), `POST /api/admin/notices`, `PUT /api/admin/notices/{id}`, `DELETE /api/admin/notices/{id}`. 변경 요청에는 일반 API와 동일하게 CSRF 토큰이 필요합니다.

### 제공된 공공 프로그램 JSON 적재

화면 확인용 미리보기 240건은 `src/main/resources/public-programs-preview.json`에 포함되어 있어 별도 적재 작업 없이 `/programs`와 시설 목록에서 조회할 수 있습니다. 이 자료는 파일 배포 시점의 정보이므로 현재 운영·접수 상태를 보장하지 않습니다. 미리보기를 다시 만들려면 `python tools/build_sample_programs.py <원본 JSON 경로> src/main/resources/public-programs-preview.json`을 실행하세요. 원본 파일 전체를 사용하려면 아래 수집기를 실행합니다.

`KS_PUBLIC_ALSFC_PROGRM_INFO_202607.json`은 약 900MB, 396,693건이며 2024~2026년 프로그램이 섞여 있습니다. 서버에 `IMPORT_KEY`를 설정한 후 별도 터미널에서 같은 값을 설정하고 다음 명령을 실행하세요. 수집기는 파일을 한 줄씩 읽어 메모리 사용을 제한하며, 기본으로 종료된 프로그램을 건너뜁니다. 전체 적재에는 상당한 시간이 걸리므로 먼저 `--limit 20`으로 확인하세요.

```powershell
$env:IMPORT_KEY='서버와-동일한-키'
python tools/import_programs.py 'C:\Users\kwi07\OneDrive\Desktop\mysports\KS_PUBLIC_ALSFC_PROGRM_INFO_202607.json' --limit 20
```

적재 API는 `POST /api/import/programs`이며 `X-Import-Key` 헤더가 필요합니다. 동일 원천 키 재수집은 기존 프로그램을 갱신하고 동일 행은 한 번만 전송합니다. 이 파일에는 안정적인 강좌 ID가 없어 시설·강좌명·기간·요일·시간·대상·종목·가격·정원으로 식별자를 만듭니다. 따라서 가격이나 정원이 다른 행은 별도 프로그램으로 보존합니다. 강좌 응답에 `beginsOn`, `endsOn`, `registrationUrl`을 추가했습니다. 파일의 `PROGRM_RCRIT_NMPR_CO`는 모집 정원이지 실시간 빈자리 수가 아니므로, 공공 강좌의 `bookingSupported`는 false입니다. `PROGRM_BEGIN_DE`·`PROGRM_END_DE`는 운영 기간이고 접수 기간이 아닙니다. 기존 시설 데이터와 자동으로 같은 시설이라고 합치지 않으며 프로그램 파일을 별도 출처로 추적합니다.

### 체육시설 공공데이터 실시간 조회

로컬 IntelliJ 실행에서는 Git에서 제외된 `config/local-secrets.properties`를 자동으로 읽습니다. 제공받은 인증키는 해당 파일에만 저장되어 있으며, 설정 변경 후에는 Spring Boot 서버를 다시 시작해야 합니다. 프로젝트를 공유할 때 이 파일은 제외하세요. IntelliJ 실행 구성의 작업 디렉터리는 프로젝트 루트(`sportmap`)로 설정합니다. 환경변수 `PUBLIC_FACILITY_SERVICE_KEY`를 사용하는 방법도 그대로 지원합니다.

기본 요청주소는 제공된 서비스 주소에 작업 경로를 붙인 `https://apis.data.go.kr/B551014/SRVC_API_SFMS_FACI/TODZ_API_SFMS_FACI`입니다. 기본 서비스 주소만 호출하면 오류 코드 12가 반환되어 작업 경로를 추가했습니다. Java 서비스와 Python 수집기에서 HTTPS 응답 `resultCode=00`을 확인했습니다. `PUBLIC_FACILITY_SERVICE_KEY`에 발급받은 인증키를 환경변수로 설정합니다. 인증키는 인코딩된 형태 또는 원문 형태 모두 사용할 수 있으며 저장소에 넣지 않습니다.

```powershell
$env:PUBLIC_FACILITY_SERVICE_KEY='발급받은-인증키'
.\gradlew.bat bootRun
```

`GET /api/facilities/external?pageNo=1&numOfRows=10&faci_nm=센터럴 피트니스&faci_gb_nm=신고&fcob_nm=체력단련장업&ftype_nm=체력단련장&cp_nm=서울특별시&cpb_nm=중구`는 사진의 요청변수명을 그대로 전달합니다. `resultType=JSON`과 `serviceKey`는 서버가 설정합니다. 공공 API 응답은 필드 명세가 확인될 때까지 원형 JSON으로 반환합니다.

수집: `PUBLIC_FACILITY_SERVICE_KEY`, `IMPORT_KEY`를 지정한 다음 `python tools/collect.py config/facilities.example.json`을 실행합니다. 공개 조회 API와 달리 적재 API는 `X-Import-Key` 헤더가 필요하며 키가 비어 있으면 접근할 수 없습니다.

회귀 검사는 H2 서버를 띄우고 동일한 `IMPORT_KEY`를 설정한 뒤 `./scripts/smoke.ps1`을 실행합니다. 회원가입·로그인, 시설 재적재/검색, 리뷰, Q&A 대댓글, 예약의 없는 프로그램 처리, 로그아웃을 확인합니다.

이번 수정의 실행 결과와 Gradle 검증 제한은 [docs/VALIDATION.md](docs/VALIDATION.md)에 기록했습니다.

로컬 H2는 실행 시 스키마가 생성되고 종료 시 사라집니다. MySQL 프로필의 `ddl-auto=update`는 개발용이며 제출·운영 환경에서는 Flyway 마이그레이션과 로그인 시도 제한을 추가해야 합니다.
