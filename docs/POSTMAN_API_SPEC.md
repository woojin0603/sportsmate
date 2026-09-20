# SportMap API 명세서 (Postman 검증용)

현재 프로젝트의 컨트롤러 구현 기준입니다. Base URL은 `http://localhost:8080`이며 요청·응답 본문은 별도 표기가 없으면 JSON입니다. 로컬 프로필은 파일형 H2를 사용하며 서버를 재시작해도 저장된 데이터가 남습니다. `build.gradle`로 프로젝트를 연 뒤 서버를 실행하세요. 이 문서는 구현된 API만 설명하며, 아래에 없는 데이터셋 적재·관리 API는 아직 제공하지 않습니다.

로컬 H2에는 화면 검증용 데이터가 자동으로 들어갑니다. 테스트 계정과 초기 프로그램 ID는 [로컬 데모 데이터](DEMO_DATA.md)를 확인하세요.

## Postman 시작 순서

1. 환경 변수 `baseUrl = http://localhost:8080`을 만듭니다. Postman의 쿠키 저장을 켜고 같은 호스트로 요청하세요.
2. `GET {{baseUrl}}/api/csrf`를 호출합니다. 응답의 `token`을 환경 변수 `csrfToken`에 저장합니다. 테스트 탭 스크립트 예: `pm.environment.set("csrfToken", pm.response.json().token);`
3. 모든 `POST`·`PATCH` 요청에 `X-XSRF-TOKEN: {{csrfToken}}`을 추가합니다. Postman이 `XSRF-TOKEN` 쿠키도 함께 보내야 합니다. 시설 적재 요청만 CSRF 검사에서 제외됩니다.
4. 회원가입 후 로그인합니다. 로그인 응답의 `Set-Cookie`에 들어 있는 `ACCESS_TOKEN`을 Postman이 저장하면 보호된 API에 자동 전송됩니다. `Authorization: Bearer <JWT>` 헤더도 지원하지만 로그인 응답 본문에는 JWT가 없습니다.
5. 서버 시작 전에 시설 외부 조회에는 `PUBLIC_FACILITY_SERVICE_KEY`, 시설 적재에는 `IMPORT_KEY` 환경 변수를 설정해야 합니다. 두 값은 이 명세서나 Postman 공유 파일에 넣지 마세요.

공통 페이지 요청은 `page`(0부터 시작, 기본 0), `size`(기본 20, 1~100)입니다. 목록 응답은 `{ "content": [...], "page": { "size": 20, "number": 0, "totalElements": 0, "totalPages": 0 } }` 형식입니다. 실제 메타데이터에는 추가 필드가 있을 수 있습니다. 날짜 `birthDate`는 `YYYY-MM-DD`, 예약 시각은 UTC 오프셋이 있는 ISO-8601 값(예: `2030-01-01T10:00:00Z`)을 사용합니다.

## 회원·인증

| 메서드 | 경로 | 인증 | 요청 | 성공 응답 |
|---|---|---|---|---|
| GET | `/api/csrf` | 불필요 | 없음 | 200, `headerName`, `parameterName`, `token` 및 `XSRF-TOKEN` 쿠키 |
| POST | `/api/users/email/send` | 불필요, CSRF 필요 | `{"email":"hong@example.com"}` | 200, `requestToken`; 버튼 방식 인증 메일 발송 |
| GET | `/api/users/email/confirm?token=...` | 불필요 | 이메일의 인증 버튼 링크 | 200, 인증 완료 안내 HTML |
| POST | `/api/users/email/status` | 불필요, CSRF 필요 | `email`, `requestToken` | 200, `verified`, 완료 시 `verificationToken` |
| POST | `/api/users/signup` | 불필요, CSRF 필요 | 아래 회원가입 JSON | 201, 회원 정보 |
| POST | `/api/users/login` | 불필요, CSRF 필요 | `username`, `password` | 200, 회원 정보 및 `ACCESS_TOKEN` 쿠키 |
| POST | `/api/users/logout` | 로그인·CSRF 필요 | 없음 | 204, 인증 쿠키 삭제 |
| GET | `/api/users/mypage` | 로그인 필요 | 없음 | 200, 회원 정보 |

회원가입 예시:

```json
{
  "fullName": "홍길동",
  "username": "hong1234",
  "password": "ExamplePassword123!",
  "birthDate": "1998-05-10",
  "email": "hong@example.com",
  "emailVerificationToken": "이메일 인증 응답의 verificationToken",
  "phoneNumber": "010-1234-5678",
  "gender": "MALE"
}
```

회원가입 전에 이메일 발송 → 메일의 인증 버튼 클릭 → `requestToken`으로 상태 확인 → 응답의 `verificationToken`을 회원가입 본문에 넣는 순서로 요청합니다. 인증 링크는 10분 유효하고 재발송 간격은 1분입니다. 메일 전송에는 서버의 `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` 설정이 필요합니다. 설정이 없거나 발송이 실패하면 503입니다. `fullName` 필수·최대 50자, `username` 영문자로 시작하는 영문·숫자·밑줄 4~30자, `password` 8~72자, `birthDate` 과거 날짜, `email` 이메일 형식·최대 255자, `phoneNumber` 숫자·하이픈 10~15자, `gender`는 `MALE`·`FEMALE`·`OTHER` 중 하나입니다. 로그인 본문 예시는 `{"username":"hong1234","password":"ExamplePassword123!"}`입니다. 회원 정보 응답에는 `id`, `fullName`, `username`, `birthDate`, `email`, `phoneNumber`, `gender`가 있으며 비밀번호는 없습니다. 토큰 만료 시간은 15분입니다.

## 시설·지역·외부 공공데이터

| 메서드 | 경로 | 요청 변수/본문 | 성공 응답 |
|---|---|---|---|
| GET | `/api/regions` | 없음 | 200, `[{id, code, name, parentId}]` |
| GET | `/api/facilities` | `keyword`, `regionCode`, `publicOnly`(true/false), `page`, `size` 모두 선택 | 200, 시설 페이지 |
| GET | `/api/facilities/{id}` | 시설 ID | 200, 시설 객체 |
| GET | `/api/facilities/external` | `pageNo`(기본 1), `numOfRows`(기본 10), 아래 필터 선택 | 200, 공공데이터 원본 JSON |
| POST | `/api/import/facilities` | `X-Import-Key` 헤더 및 아래 적재 JSON | 200, 저장된 시설 ID 숫자 |
| POST | `/api/import/programs` | `X-Import-Key` 헤더 및 프로그램 수집 JSON | 200, 저장된 프로그램 ID 숫자 |

시설 응답 필드: `id`, `name`, `regionCode`, `roadAddress`, `type`, `latitude`, `longitude`, `phone`, `website`, `publicFacility`, `reservable`. 목록은 공통 페이지 형식입니다. 시설이 없으면 상세 조회는 404입니다.

외부 공공데이터 필터는 첨부된 요청변수와 동일합니다: `faci_nm`(시설명), `faci_gb_nm`(시설구분명), `fcob_nm`(업종명), `ftype_nm`(시설유형명), `cp_nm`(시도명), `cpb_nm`(시군구명). 예: `GET {{baseUrl}}/api/facilities/external?pageNo=1&numOfRows=10&cp_nm=서울특별시&cpb_nm=중구`. 서버가 `serviceKey`와 `resultType=JSON`을 붙여 공공 API를 호출하므로 Postman 요청에는 공공데이터 인증키를 넣지 않습니다. 외부 API가 반환한 JSON 구조를 그대로 전달하며, 키 미설정·외부 API 오류 시 503이 반환될 수 있습니다.

시설 적재는 수집기용 API입니다. `IMPORT_KEY`와 같은 값을 `X-Import-Key`로 보내야 하며, 미설정·불일치 시 403입니다. 같은 `datasetCode`·`sourceKey` 조합을 다시 보내면 기존 시설을 갱신합니다.

```json
{
  "datasetCode": "POSTMAN_TEST",
  "sourceKey": "test-facility-001",
  "name": "테스트 체육관",
  "regionCode": "서울특별시",
  "regionName": "서울특별시",
  "roadAddress": "서울 중구 테스트로 1",
  "type": "체육관",
  "latitude": 37.5,
  "longitude": 127.0,
  "phone": "02-1234-5678",
  "publicFacility": true,
  "reservable": false,
  "rawJson": "{}"
}
```

`datasetCode`, `sourceKey`, `name`은 필수이며 나머지는 선택입니다. `regionCode`를 주면 기존 지역을 찾거나 새 지역을 생성합니다. 적재 API는 JWT가 필요하지 않습니다.

## 프로그램·추천 운동

| 메서드 | 경로 | 요청 | 성공 응답 |
|---|---|---|---|
| GET | `/api/programs` | `facilityId` 또는 `keyword`, `region`, `page`, `size` | 200, 프로그램 페이지 |
| GET | `/api/programs/regions` | 없음 | 200, 프로그램이 존재하는 지역의 `province`, `city`, `locality`, `displayName` 배열 |
| GET | `/api/programs/{id}` | 프로그램 ID | 200, 프로그램 객체 |
| GET | `/api/recommendations?age=30` | `age` 필수, 0~120 | 200, 연령에 맞는 추천 운동 목록 |

프로그램 응답 필드: `id`, `facilityId`, `operatingOrganization`, `regionName`, `name`, `sportType`, `scheduleText`, `eligibility`, `fee`, `capacity`, `bookingSupported`, `beginsOn`, `endsOn`, `registrationUrl`. `operatingOrganization`은 프로그램과 연결된 시설명이며 `regionName`은 시설 주소·지역 계층에서 시·도부터 조합한 표시명입니다. `region`에는 `서울특별시`, `광진구`, `퇴계동`처럼 주소에 포함된 행정구역을 입력할 수 있고 `keyword`와 동시에 적용됩니다. `facilityId`가 있으면 그 시설의 프로그램을 조회합니다. 없는 프로그램 상세는 404입니다. 추천 운동은 현재 DB에 적재된 자료가 없으면 빈 목록이 정상입니다. 프로그램은 `tools/import_programs.py` 또는 내부 적재 API로만 등록할 수 있습니다. 적재 API의 본문 예시는 다음과 같습니다.

```json
{
  "sourceKey": "program-202609-001",
  "facilitySourceKey": "facility-seoul-001",
  "facilityName": "테스트 수영장",
  "address": "서울특별시 성동구 무수막길 69",
  "regionCode": "1120000000",
  "regionName": "성동구",
  "facilityType": "수영장",
  "phone": "02-2204-7900",
  "latitude": 37.5518979,
  "longitude": 127.0207529,
  "name": "저녁 수영",
  "sportType": "수영",
  "scheduleText": "월수금 / 19:00~19:50",
  "eligibility": "성인",
  "fee": 49500,
  "capacity": 25,
  "beginsOn": "2026-09-01",
  "endsOn": "2026-09-30",
  "registrationUrl": "https://sports.happysd.or.kr"
}
```

`sourceKey`, `facilitySourceKey`, `facilityName`, `name`, `beginsOn`, `endsOn`은 필수입니다. `sourceKey`는 64자 이하, `facilitySourceKey`는 200자 이하이며 동일 `sourceKey`를 다시 적재하면 해당 프로그램을 갱신합니다. 실제 파일 전체 적재는 [README](../README.md)의 스트리밍 수집기를 사용하세요.

## 예약 (로그인 필요)

| 메서드 | 경로 | 요청 | 성공 응답 |
|---|---|---|---|
| POST | `/api/reservations` | 공공 프로그램은 `programId`만 필수, 자체 예약은 `startsAt`·`endsAt`도 필수; CSRF | 201, 예약 객체 |
| GET | `/api/reservations` | `page`, `size` 선택 | 200, 내 예약 페이지 |
| GET | `/api/reservations/{id}` | 내 예약 ID | 200, 예약 객체 |
| PATCH | `/api/reservations/{id}/cancel` | 내 예약 ID; CSRF | 200, 상태가 `CANCELLED`인 예약 객체 |

```json
{"programId":1}
```

예약 응답 필드: `id`, `programId`, `startsAt`, `endsAt`, `status` (`REQUESTED`, `CONFIRMED`, `CANCELLED`). 위 예시는 제공된 공공데이터 프로그램의 **자체 신청 내역**을 만들며 `REQUESTED`가 반환됩니다. 운영기관의 예약 확정이나 잔여석 확보를 뜻하지 않습니다. 실제 접수는 프로그램의 `registrationUrl`에서 진행해야 합니다. 동일 회원·프로그램의 중복 신청 및 종료된 프로그램 신청은 409, 시작·종료 시각을 임의로 함께 보내면 400입니다. `startsAt`·`endsAt` 응답은 강좌 운영 기간을 나타냅니다. 타인의 신청 조회는 404이며, 신청 취소 후 재신청할 수 있습니다. 자체 운영 프로그램은 종전대로 `startsAt`·`endsAt`을 요청에 보내고, 예약 가능한 프로그램의 정원 조건이 충족될 때만 `CONFIRMED`됩니다.

## 리뷰·공지·Q&A

| 메서드 | 경로 | 요청 | 성공 응답 |
|---|---|---|---|
| GET | `/api/facilities/{facilityId}/reviews` | `page`, `size` 선택 | 200, 리뷰 페이지 |
| POST | `/api/facilities/{facilityId}/reviews` | 로그인·CSRF, `rating`, `content` | 201, 리뷰 객체 |
| GET | `/api/notices` | `page`, `size` 선택 | 200, 공지 페이지 |
| GET | `/api/notices/{id}` | 공지 ID | 200, 공지 객체 |
| GET | `/api/qna` | `page`, `size` 선택 | 200, 질문 페이지 |
| GET | `/api/qna/{id}` | 질문 ID | 200, 질문 객체 |
| POST | `/api/qna` | 로그인·CSRF, `title`, `content` | 201, 질문 객체 |
| GET | `/api/qna/{id}/comments` | 질문 ID | 200, 댓글 배열 |
| POST | `/api/qna/{id}/comments` | 로그인·CSRF, `parentId`(선택), `content` | 201, 댓글 객체 |

리뷰 본문 예시 `{"rating":5,"content":"시설이 깨끗합니다"}`. 평점은 1~5, 내용은 필수·최대 2000자입니다. 리뷰 응답: `id`, `rating`, `content`, `createdAt`.

질문 본문 예시 `{"title":"예약 문의","content":"이용 가능 시간을 알려주세요"}`. 제목은 필수·최대 200자, 내용은 필수·최대 5000자입니다. 질문/공지 응답: `id`, `title`, `content`, `createdAt`.

댓글 본문 예시 `{"parentId":null,"content":"확인 부탁드립니다"}`. 대댓글은 같은 질문에 속한 기존 댓글의 ID를 `parentId`로 지정합니다. 내용은 필수·최대 3000자입니다. 댓글 응답: `id`, `parentId`, `authorName`, `content`, `createdAt`. 공지 작성 API는 아직 없습니다.

## 확인할 오류 응답

| 상황 | 예상 HTTP 상태 |
|---|---|
| 유효성 검사 실패·잘못된 페이징·잘못된 예약 시각 | 400 |
| 로그인하지 않고 보호 API 호출·잘못된 로그인 정보 | 401 |
| 쓰기 요청의 CSRF 토큰 누락/불일치·적재 키 불일치 | 403 |
| 없는 상세 자원·타인의 예약 조회 | 404 |
| 중복 아이디/이메일 등 가입 충돌·예약 불가/정원 초과 | 409 |
| 공공데이터 인증키 미설정·외부 공공 API 실패 | 503 |

Spring Boot 오류 응답 본문의 필드·문구는 상황에 따라 달라질 수 있으므로 Postman 검증은 우선 HTTP 상태 코드와 정상 응답의 주요 필드를 기준으로 하세요.
