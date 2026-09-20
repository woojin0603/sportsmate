# 로컬 H2 데모 데이터

`local` 프로필로 서버를 실행하면 프로젝트 루트의 H2 파일 DB에 화면 테스트용 데이터가 자동으로 들어갑니다. 데모 데이터는 `mysql` 프로필에는 들어가지 않습니다. 로컬 서버는 `127.0.0.1`에서만 접속됩니다. H2 파일은 서버 재시작 후에도 남고 원천 데이터는 중복 적재되지 않습니다. 데모 데이터를 끄려면 `app.demo.enabled=false`로 실행하세요.

```powershell
cd 'C:\Users\kwi07\Documents\Codex\2026-09-13\rnr\outputs\sportmap'
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

테스트 로그인 계정은 **`demo_user` / `Demo1234!`**입니다. 이 계정은 로컬 H2 전용이며 운영용 계정이 아닙니다. Postman에서는 먼저 `GET /api/csrf`를 호출해 받은 토큰을 `X-XSRF-TOKEN` 헤더에 넣고 `POST /api/users/login`을 호출하세요. 자세한 인증 절차는 [Postman API 명세](POSTMAN_API_SPEC.md)에 있습니다.

| 화면 | 테스트 API | 초기 데이터 |
|---|---|---|
| 시설 지도·목록 | `GET /api/facilities` | 성동 수영장, 생활체육관, 야외 운동장 3곳과 좌표 |
| 시설 상세·리뷰 | `GET /api/facilities/1`, `GET /api/facilities/1/reviews` | 수영장 리뷰 1건 |
| 지역 선택 | `GET /api/regions` | 서울특별시, 성동구 |
| 프로그램 목록·상세 | `GET /api/programs`, `GET /api/programs/1`, `GET /api/programs/2` | 공공 수영 신청 프로그램, 자체 배드민턴 예약 프로그램 |
| 예약·내 신청 | `GET /api/reservations` (로그인 필요) | 공공 수영 프로그램 신청 `REQUESTED` 1건 |
| 공지사항 | `GET /api/notices` | 데모 공지 1건 |
| Q&A·댓글 | `GET /api/qna`, `GET /api/qna/1/comments` | 질문·댓글 각 1건 |
| 추천 운동 | `GET /api/recommendations?age=30` | 걷기 1건 |
| 회원 정보 | `GET /api/users/mypage` (로그인 필요) | 데모 회원 정보 |

프로그램 ID 1은 공공 프로그램 예시입니다. `POST /api/reservations`에 `{"programId":1}`을 보내면 **앱 내부 신청 `REQUESTED`**가 생성되며 운영기관 예약 확정은 아닙니다. 데모 계정에는 이미 신청 내역이 있어 중복 신청은 409가 정상입니다.

프로그램 ID 2는 자체 예약 테스트용으로 `bookingSupported=true`, `capacity=1`입니다. `POST /api/reservations`에 미래의 겹치는 시각을 보내면 첫 회원은 `CONFIRMED`, 두 번째 회원은 정원 초과로 409가 됩니다. 첫 예약을 취소하면 다시 예약할 수 있습니다.

```json
{
  "programId": 2,
  "startsAt": "2030-01-01T10:00:00Z",
  "endsAt": "2030-01-01T11:00:00Z"
}
```

위 ID는 새 H2 DB의 초기 상태 기준입니다. 다른 데이터를 적재한 뒤에는 `GET /api/programs` 응답의 ID를 확인하세요. 제공된 공공 프로그램 파일을 실제로 적재하려면 [README](../README.md)의 `tools/import_programs.py` 절차를 사용합니다.
