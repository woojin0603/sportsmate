# Railway 제출 배포

## 결정한 구성

- 프로젝트 이름: `sportsmate-contest`
- 애플리케이션 서비스 이름: `sportmap`
- 데이터베이스 서비스 이름: `MySQL`
- 운영 프로필: `submission`
- 공개 주소: Railway가 `Generate Domain` 실행 후 발급하는 `https://<발급값>.up.railway.app`

Railway 도메인의 발급값은 서비스를 만든 뒤에 확정됩니다. 배포 전 임의 주소를 코드나 APK에 넣지 않습니다. 발급된 주소 하나를 웹 주소, 이메일 인증 링크 기준 주소, Android 서버 주소로 함께 사용합니다.

## 1. GitHub 저장소 연결

Railway에서 `New Project` → `Deploy from GitHub repo`를 선택하고 이 저장소를 연결합니다. 애플리케이션 서비스 이름은 `sportmap`으로 지정합니다. 저장소 루트의 `Dockerfile`이 React를 빌드해 Spring Boot 정적 파일에 넣고 실행 JAR를 만듭니다.

## 2. MySQL 추가

프로젝트 Canvas에서 `+ New` → `Database` → `MySQL`을 선택합니다. MySQL은 외부 Public Access를 켜지 않고 같은 Railway 프로젝트의 사설 네트워크로만 연결합니다.

`sportmap` 서비스의 Variables에 다음 값을 등록합니다. `${{...}}` 형식은 Railway Raw Editor에 그대로 입력합니다.

```text
SPRING_PROFILES_ACTIVE=submission
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul&useSSL=false&allowPublicKeyRetrieval=true
DB_USER=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
JWT_SECRET=<32바이트 이상의 무작위 비밀값>
IMPORT_KEY=<32바이트 이상의 무작위 비밀값>
PUBLIC_FACILITY_API_URL=https://apis.data.go.kr/B551014/SRVC_API_SFMS_FACI/TODZ_API_SFMS_FACI
PUBLIC_FACILITY_SERVICE_KEY=<공공데이터 인증키>
SECURE_COOKIE=true
ADMIN_SEED_ENABLED=false
EMAIL_MODE=live
MAIL_PUBLIC_BASE_URL=https://${{RAILWAY_PUBLIC_DOMAIN}}
```

DB와 JWT, 공공데이터 인증키는 GitHub에 올리지 않습니다. Railway의 sealed variable 또는 서비스 Variables로만 저장합니다.

## 3. HTTPS 주소 확정

`sportmap` 서비스의 `Settings` → `Networking` → `Public Networking`에서 `Generate Domain`을 누릅니다. 생성된 전체 주소를 최종 제출 URL로 사용합니다. Railway가 TLS 인증서를 자동으로 적용하므로 주소는 `https://`로 열립니다.

배포 후 아래 두 주소를 확인합니다.

```text
https://<발급값>.up.railway.app/
https://<발급값>.up.railway.app/api/health
```

`/api/health`가 `200`과 `UP`을 반환해야 정상입니다.

## 4. 이메일 발송 방식

Railway Free·Trial·Hobby 요금제는 외부 SMTP를 차단합니다. 현재 Gmail SMTP 구현을 그대로 사용할 경우 Railway Pro 이상이 필요합니다. Hobby로 운영하려면 Resend, SendGrid, Mailgun, Postmark 같은 HTTPS 메일 API 어댑터를 사용해야 합니다. 메일 공급자 키가 준비되기 전에는 `EMAIL_MODE=live` 배포가 시작되더라도 회원가입 메일 전송은 실패하므로 최종 공개 전에 반드시 실제 수신 테스트를 진행합니다.

## 5. Android 최종 빌드

Railway 발급 주소가 정상 동작한 다음 Android의 운영 서버 주소를 그 HTTPS 주소로 지정하고 동일한 서명키로 release APK를 다시 빌드합니다. APK에는 DB, JWT, 공공데이터 및 메일 비밀값을 포함하지 않습니다.
