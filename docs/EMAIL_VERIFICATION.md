# 이메일 인증 — 외부 발송 없는 시연과 SMTP 전환

기본 `local` 프로필에서는 이메일과 SMS 모두 실제로 발송하지 않습니다. 이메일 입력 후 화면에 표시되는 **테스트용 이메일 인증 링크 열기**를 누르면 실제 메일의 링크와 같은 서버 인증 경로를 거칩니다. 공모전 개발·로컬 시연용 기능으로, 메일 주소의 실제 소유 여부를 증명하지 않습니다.

## 시연 순서

1. 서버를 재시작합니다. Spring 화면만 사용하면 `http://127.0.0.1:8080`, Vite 개발 화면은 `http://127.0.0.1:5173`을 사용합니다. React를 수정한 후 Spring 화면에 반영하려면 `frontend`에서 `npm run build:spring`을 실행하고 서버를 재시작합니다.
2. 회원가입에 아직 가입하지 않은 이메일 형식의 주소(예: `demo@example.com`)를 입력하고 **이메일 인증하기**를 누릅니다. SMTP 계정은 필요 없습니다.
3. **실제 이메일은 발송되지 않습니다** 안내 아래의 **테스트용 이메일 인증 링크 열기**를 누릅니다.
4. 새 탭에서 **이메일 인증 완료**를 확인하고 원래 회원가입 탭으로 돌아옵니다. 약 2.5초 간격의 상태 확인으로 인증 완료가 표시됩니다.
5. 휴대폰 번호 입력 → 화면의 테스트 인증번호 입력 → 휴대폰 인증 완료 후 회원가입합니다. 생성한 계정으로 로그인할 수 있습니다.

이메일을 바꾸거나 로그인/회원가입 화면을 전환하면 화면의 인증 상태가 초기화됩니다. 링크 발송부터 가입까지 10분 안에 완료해야 하며, 재발송은 60초 후 가능합니다. 재발송하면 이전 링크·요청 토큰·가입 증명은 무효화됩니다. 링크와 가입 증명은 각각 한 번만 사용할 수 있습니다.

## 모드 설정

| 설정 | 동작 |
|---|---|
| `EMAIL_MODE=mock` | 실제 발송 없이 개발용 링크를 반환. `local` 기본값 |
| `EMAIL_MODE=live` | 기존 SMTP 어댑터로 실제 발송. 개발용 링크를 API에 반환하지 않음 |
| `EMAIL_MODE=disabled` | 발송 API가 503 반환. `local` 외 기본값 |

`mock`은 활성 프로필이 모두 `local`/`test`이고 `server.address`가 `127.0.0.1` 또는 `::1`일 때만 허용합니다. 운영 프로필과 함께 쓰거나 외부 주소에 바인딩하면 서버 시작이 실패합니다. 공개 터널/리버스 프록시로 로컬 모의 서버를 노출하지 마세요. 모의 모드는 이메일 소유 확인을 하지 않으므로 공개 서비스에는 사용할 수 없습니다.

모의 인증 기록은 실제 발송 모드에서 확인·상태 조회·가입에 쓸 수 없습니다. 변경 전 만들어진 인증 기록도 재인증해야 합니다. 기존 회원 로그인에는 영향을 주지 않습니다. 시연 회원이 있는 DB 자체를 운영으로 옮기지 마세요.

## 실제 이메일 발송으로 전환

```powershell
$env:EMAIL_MODE='live'
$env:MAIL_PUBLIC_BASE_URL='https://실제서비스도메인'
$env:MAIL_HOST='smtp.example.com'
$env:MAIL_PORT='587'
$env:MAIL_USERNAME='발송 계정'
$env:MAIL_PASSWORD='SMTP 비밀번호 또는 앱 비밀번호'
$env:MAIL_FROM='noreply@example.com'
.\gradlew.bat bootRun
```

`MAIL_PUBLIC_BASE_URL`은 인증 링크를 열 때 접근할 공개 HTTPS 주소이며 경로·쿼리·사용자정보 없이 지정합니다. 예: `https://sportsmate.example.com`. 요청의 Host 헤더로 링크를 만들지 않습니다. SMTP 정보만 추가해도 로컬에서는 계속 모의 모드이므로, 실제 발송 테스트 시 `EMAIL_MODE=live`를 명시해야 합니다. 테스트를 마치면 `EMAIL_MODE=mock`으로 복구합니다.

실제 SMTP 설정이 누락되거나 발송이 실패하면 성공으로 처리하지 않고 503 오류를 반환합니다. 메일 계정·키·비밀번호는 서버 환경변수 또는 Git 제외 설정 파일에만 보관합니다. 실제 SMTP 발송·수신은 이번 자동 테스트에서 수행하지 않습니다.

다른 메일 API 공급자를 쓰려면 `member/mail/VerificationEmailSender`를 구현하고 `liveVerificationEmailSender` 빈을 기존 SMTP 어댑터 대신 등록하세요. 인증 화면과 가입 API는 그대로 사용합니다. 하나의 live 빈만 등록해야 합니다.

## API와 검증

- `/api/users/email/send`는 `requestToken`, `expiresAt`, `retryAfterSeconds`, `mock`을 반환합니다. `developmentConfirmationUrl`은 모의 모드에만 있습니다. 모의 링크는 같은 출처의 상대경로이므로 Vite 프록시와 Spring 화면에서 모두 열 수 있습니다.
- `/api/users/email/confirm?token=...`은 메일 링크와 테스트 링크가 공통으로 사용하는 1회 인증 경로입니다.
- `/api/users/email/status`는 확인된 이메일과 요청 토큰에 가입 증명을 반환합니다. 반복 조회해도 같은 증명을 반환하므로 중복 상태 조회가 기존 증명을 무효화하지 않습니다.
- 발송/상태 API는 기존 CSRF 보호를 유지합니다. 토큰 포함 응답과 완료 페이지에는 `Cache-Control: no-store`, 완료 페이지에는 `Referrer-Policy: no-referrer`를 적용합니다.
- DB에는 확인/요청/가입 토큰 원문을 저장하지 않습니다. 링크 확인과 가입 시 DB 잠금을 사용해 중복 사용을 막습니다. 가입 트랜잭션에서 이메일과 휴대폰 증명을 함께 검증합니다.
- `email_verifications.mock_delivery` 열이 추가됩니다. 로컬 `ddl-auto=update`는 자동 반영하며, 스키마 `validate`를 사용하는 환경은 먼저 nullable boolean 열을 마이그레이션해야 합니다. 기존 null 값은 재인증 대상입니다.
- 이메일 발송 제한은 기존 이메일별 60초 간격입니다. SMS의 전체/IP별 발송량 제한과는 별개입니다. 실제 공개 서비스 전에는 이메일 공급자 발송 한도, 요청 IP별 제한 및 남용 방지를 추가 구성하세요.

`./gradlew.bat test`로 정상 흐름, 만료·재발송·재사용, 모의/실제 모드 분리, SMTP 실패 처리, CSRF, 이메일+SMS 인증 후 가입·로그인을 검증합니다. 테스트는 메모리 DB와 모의 SMTP 전송을 사용합니다.
