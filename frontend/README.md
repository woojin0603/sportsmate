# SportMap 웹 화면

React 19 + Vite 7 기반 화면입니다. 시설 검색·지도, 프로그램 탐색·상세, 연령별 추천, 예약 내역, 공지·Q&A, 회원가입·로그인·마이페이지를 제공합니다. API 경로는 `/api`이고 개발 서버가 `http://127.0.0.1:8080`의 Spring Boot 서버로 전달합니다.

## 로컬 실행

1. `outputs/sportmap`에서 `build.gradle`을 IntelliJ로 열고 Spring Boot를 8080 포트로 실행합니다. 기본 H2 프로필에는 제공된 파일의 공공 프로그램 미리보기 240건과 해당 시설, 데모 회원이 자동 생성됩니다.
2. 별도 터미널에서 아래 명령을 실행합니다.

```powershell
cd frontend
npm ci
npm run dev
```

브라우저에서 `http://127.0.0.1:5173`을 엽니다. 데모 로그인은 `demo_user` / `Demo1234!`입니다. 프로덕션 빌드는 `npm run build`, 로컬 미리보기는 `npm run preview` 후 `http://127.0.0.1:4173`입니다. 빌드 결과를 배포할 때는 같은 출처의 `/api`를 Spring Boot로 프록시해야 합니다.

## 화면과 데이터

- 시설 지도와 검색: 메인 화면은 기본으로 적재된 `/api/facilities`를 보여주며 시·도를 고르면 `/api/facilities/external`의 실시간 공공 API 자료로 전환합니다. 시·도 → 시·군·구 → 읍·면·동 순으로 필터링할 수 있습니다. 마지막 단계는 원천 API에 검색변수가 없어 해당 시·군·구 시설을 가져와 서버에서 필터링하고 10분간 캐시합니다. Spring 실행 환경에 `PUBLIC_FACILITY_SERVICE_KEY`를 설정해야 합니다.
- 프로그램: `/api/programs`, `/api/programs/{id}`
- 연령별 추천: `/api/recommendations?age=...`
- 예약 내역: `/api/reservations`, 예약 신청·취소
- 커뮤니티: `/api/notices`, `/api/qna`, 질문·댓글
- 회원: `/api/users/email/send`, `/api/users/email/confirm`, `/api/users/email/status`, `/api/users/signup`, `/api/users/login`, `/api/users/mypage`, `/api/users/logout`. 회원가입 전 메일의 인증 버튼 클릭이 필요하며 서버 SMTP 환경변수 설정은 루트 `README.md`를 참고하세요.
- 나의 체력: `/fitness` 화면에서 PDF 업로드·추출값 확인·A~D 참고 등급 저장. 백엔드 `/api/fitness/*` 사용

공공 프로그램의 `REQUESTED`는 앱 내부의 **신청 기록**입니다. 운영기관 예약 확정이나 실시간 잔여석을 뜻하지 않으며, 실제 접수는 프로그램의 `registrationUrl`에서 확인해야 합니다. 쓰기 요청은 백엔드의 CSRF 토큰을 받아 전송하고 JWT 인증 쿠키는 브라우저가 처리합니다. 인증키와 비밀값을 프론트엔드 코드에 넣지 마세요.

공공 프로그램 미리보기는 2026년 7월 배포 파일에 기반하므로 방문·접수 전에 운영기관의 최신 정보를 확인하세요.
